package io.legado.app.service

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Edge 大声朗读的语音合成。
 *
 * 2026-10 起微软逐步停用旧地址 speech.platform.bing.com（返回 403），Edge 浏览器已改用
 * api.msedgeservices.com。这里优先连新地址，失败再试旧地址，并记住能用的地址。
 *
 * 每段文字单独等待合成结果：超过 [IDLE_TIMEOUT_MS] 没有收到音频、连接断开或被拒绝都会报错并重试，
 * 重试仍失败则抛出异常交给朗读服务暂停并提示，不会一直卡住导致没有声音。
 */
class EdgeSpeakFetch {
    companion object {
        private const val TAG = "EdgeSpeakFetch"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"

        /** 微软朗读服务地址：新地址在前，旧地址兜底 */
        private val ENDPOINTS = listOf(
            "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1" +
                    "?Ocp-Apim-Subscription-Key=$TRUSTED_CLIENT_TOKEN",
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                    "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN",
        )

        /** 连接建立的最长等待时间 */
        private const val CONNECT_TIMEOUT_MS = 10_000L
        /** 合成过程中超过这么久没有收到任何数据就认为失败 */
        private const val IDLE_TIMEOUT_MS = 15_000L
        /** 每段文字最多尝试的次数 */
        private const val MAX_ATTEMPTS = 3
        /** 一个连接最多复用的次数，避免单个连接太久被服务端断开 */
        private const val MAX_REUSE = 20

        // DRM 相关参数
        private const val WIN_EPOCH_SECONDS = 11644473600L
        private const val S_TO_NS = 1e9

        private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
        private val CHROMIUM_MAJOR_VERSION: String = CHROMIUM_FULL_VERSION.split(".", limit = 2)[0]
        private const val SEC_MS_GEC_VERSION: String = "1-$CHROMIUM_FULL_VERSION"

        val BASE_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROMIUM_MAJOR_VERSION}.0.0.0 Safari/537.36 Edg/${CHROMIUM_MAJOR_VERSION}.0.0.0",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        val WSS_HEADERS = mapOf(
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "Origin" to "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
            "Sec-WebSocket-Version" to "13",
        )

        /** 手机时钟与微软服务器的偏差（秒），签名与时间有关，手机时间不准时按服务器时间校正 */
        @Volatile
        private var clockSkewSeconds = 0.0

        fun generateSecMsGec(clockSkewSeconds: Double): String {
            val now = Instant.now().epochSecond + clockSkewSeconds
            var ticks = now + WIN_EPOCH_SECONDS
            // 向下取整到最近的5分钟（300秒）
            ticks -= ticks.toLong() % 300
            // 转换为100纳秒单位
            ticks *= (S_TO_NS / 100)
            val strToHash = String.format(Locale.US, "%.0f%s", ticks, TRUSTED_CLIENT_TOKEN)
            return sha256(strToHash)
        }

        private fun sha256(input: String): String {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(input.toByteArray())
                return bytesToHex(hash).uppercase(Locale.getDefault())
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("SHA-256 algorithm not available", e)
            }
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val hexArray = "0123456789ABCDEF".toCharArray()
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }
    }

    /** 一段文字的合成任务，只接收带自己 RequestId 的消息，避免串到别的段落 */
    private class SynthesisJob(val requestId: String) {
        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        @Volatile
        var lastDataTime = System.currentTimeMillis()
        @Volatile
        var error: Throwable? = null

        fun fail(t: Throwable) {
            if (error == null) error = t
            done.countDown()
        }
    }

    /** 当前可复用的连接 */
    private class Connection(val endpoint: Int) {
        lateinit var socket: WebSocket
        val opened = CountDownLatch(1)
        @Volatile
        var openError: Throwable? = null
        @Volatile
        var alive = false
        @Volatile
        var job: SynthesisJob? = null
        var useCount = 0
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var connection: Connection? = null
    /** 上次成功的地址，下次优先使用 */
    @Volatile
    private var preferredEndpoint = 0

    /**
     * 合成一段文字，返回完整的 mp3 音频。失败时抛出 IOException
     */
    @Synchronized
    fun synthesizeText(
        speakText: String,
        rate: Int,
        voice: String = DEFAULT_VOICE
    ): InputStream {
        Log.i(TAG, "speakText: $speakText")
        val ssml = mkSSML(removeSpecialCharacters(speakText), voice, processRate(rate))
        var lastError: Throwable? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            // 第二次起换一个地址试；都失败过一轮后回到首选地址
            val endpoint = (preferredEndpoint + attempt) % ENDPOINTS.size
            try {
                val audio = synthesizeOnce(ssml, endpoint, reuse = attempt == 0)
                preferredEndpoint = connection?.endpoint ?: endpoint
                return ByteArrayInputStream(audio)
            } catch (e: Throwable) {
                lastError = e
                Log.w(TAG, "第${attempt + 1}次合成失败（地址$endpoint）: $e")
                closeConnection()
            }
        }
        throw IOException("朗读服务连接失败", lastError)
    }

    private fun synthesizeOnce(ssml: String, endpoint: Int, reuse: Boolean): ByteArray {
        val current = connection?.takeIf {
            reuse && it.alive && it.useCount < MAX_REUSE
        } ?: openConnection(endpoint)
        current.useCount++
        val job = SynthesisJob(connectID())
        current.job = job
        if (!sendSSMLMessage(current.socket, job.requestId, ssml)) {
            throw IOException("发送朗读文本失败")
        }
        // 等待合成结束；持续收到数据就继续等，超过空闲时间没有数据则失败
        while (!job.done.await(500, TimeUnit.MILLISECONDS)) {
            if (System.currentTimeMillis() - job.lastDataTime > IDLE_TIMEOUT_MS) {
                throw IOException("朗读服务超过 ${IDLE_TIMEOUT_MS / 1000} 秒没有响应")
            }
        }
        job.error?.let { throw it }
        if (job.audio.size() == 0) throw IOException("朗读服务没有返回音频")
        return job.audio.toByteArray()
    }

    private fun openConnection(endpoint: Int): Connection {
        closeConnection()
        val url = ENDPOINTS[endpoint] +
                "&ConnectionId=${connectID()}" +
                "&Sec-MS-GEC=${generateSecMsGec(clockSkewSeconds)}" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
        val requestBuilder = Request.Builder().url(url)
        BASE_HEADERS.forEach { (key, value) -> requestBuilder.header(key, value) }
        WSS_HEADERS.forEach { (key, value) -> requestBuilder.header(key, value) }
        requestBuilder.header("Cookie", "muid=${connectID().uppercase(Locale.US)};")

        val conn = Connection(endpoint)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket onOpen 地址$endpoint")
                conn.alive = true
                sendSpeechConfig(webSocket)
                conn.opened.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val job = conn.job ?: return
                if (!text.contains(job.requestId)) return
                job.lastDataTime = System.currentTimeMillis()
                if (text.contains("Path:turn.end")) {
                    job.done.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val job = conn.job ?: return
                val message = bytes.toByteArray()
                if (message.size < 2) return
                // 前 2 字节是头部长度（大端），头部是文本，之后是音频数据
                val headerLength =
                    ((message[0].toInt() and 0xFF) shl 8) or (message[1].toInt() and 0xFF)
                if (headerLength + 2 > message.size) return
                val header = String(message, 2, headerLength, Charsets.UTF_8)
                if (!header.contains(job.requestId)) return
                job.lastDataTime = System.currentTimeMillis()
                val audioLength = message.size - headerLength - 2
                if (audioLength > 0) {
                    job.audio.write(message, headerLength + 2, audioLength)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket onClosed $code $reason")
                conn.alive = false
                val error = IOException("朗读连接已关闭: $code $reason")
                conn.openError = error
                conn.opened.countDown()
                conn.job?.fail(error)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.i(TAG, "WebSocket onFailure: $t ${response?.code}")
                conn.alive = false
                if (response?.code == 403) adjustClockSkew(response)
                val error = if (response != null) {
                    IOException("朗读服务拒绝连接: HTTP ${response.code}", t)
                } else t
                conn.openError = error
                conn.opened.countDown()
                conn.job?.fail(error)
            }
        }
        conn.socket = client.newWebSocket(requestBuilder.build(), listener)
        connection = conn
        if (!conn.opened.await(CONNECT_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS)) {
            throw IOException("连接朗读服务超时")
        }
        conn.openError?.let { throw it }
        return conn
    }

    /** 被拒绝时按服务器时间校正手机时钟偏差（签名依赖时间，手机时间不准会被拒绝） */
    private fun adjustClockSkew(response: Response) {
        val serverTime = response.headers.getDate("Date")?.time ?: return
        val skew = (serverTime - System.currentTimeMillis()) / 1000.0
        if (abs(skew) > 60) {
            Log.i(TAG, "手机时间与服务器相差 $skew 秒，已校正")
            clockSkewSeconds = skew
        }
    }

    private fun closeConnection() {
        connection?.let {
            it.alive = false
            it.job?.fail(IOException("连接已关闭"))
            it.socket.cancel()
        }
        connection = null
    }

    // 移除文本中的特殊字符和表情，保留常用文章标点符号
    private fun removeSpecialCharacters(text: String): String {
        val pattern = Pattern.compile("[^\\w\\s\u4e00-\u9fff，。！？；：、（）《》【】“”‘’]")
        val matcher = pattern.matcher(text)
        return matcher.replaceAll("")
    }

    // 构造SSML文本
    private fun mkSSML(
        text: String,
        voice: String,
        rate: String,
        pitch: String = "+0Hz",
        volume: String = "+0%"
    ): String {
        return String.format(
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                    "<voice name='%s'>" +
                    "<prosody pitch='%s' rate='%s' volume='%s'>%s</prosody>" +
                    "</voice>" +
                    "</speak>",
            voice, pitch, rate, volume, text
        )
    }

    // 发送speech.config消息
    private fun sendSpeechConfig(wss: WebSocket) {
        val speechConfig =
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        val speechConfigMsg = String.format(
            "X-Timestamp:%s\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n%s\r\n",
            timestamp, speechConfig
        )
        wss.send(speechConfigMsg)
    }

    // 发送SSML消息
    private fun sendSSMLMessage(wss: WebSocket, requestId: String, ssml: String): Boolean {
        val sdf = SimpleDateFormat("EEE MMM d yyyy HH:mm:ss zzz", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        val ssmlMsg = String.format(
            "X-RequestId:%s\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:%sZ\r\nPath:ssml\r\n\r\n%s",
            requestId, timestamp, ssml
        )
        return wss.send(ssmlMsg)
    }

    // 生成无破折号的UUID
    private fun connectID(): String {
        return UUID.randomUUID().toString().replace("-".toRegex(), "")
    }

    // 生成带符号的百分比字符串
    private fun processRate(rate: Int): String {
        val rateOffset = rate - 12
        return if (rateOffset > 0) "+$rateOffset%" else "$rateOffset%"
    }

    fun release() {
        closeConnection()
    }
}
