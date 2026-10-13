package io.legado.app.ui.book.read.config

import android.content.Context
import io.legado.app.R
import io.legado.app.model.ReadAloud

/**
 * Edge 朗读的语音（嗓音）：可选列表、当前选择，以及切换后立即生效
 */
object EdgeVoice {

    private const val PREFS = "TTS_CONFIG"
    private const val KEY = "tts_edge_voice"
    private const val DEFAULT = "晓晓@zh-CN-XiaoxiaoNeural"

    /** 显示名@微软语音名 */
    val options = listOf(
        "晓晓@zh-CN-XiaoxiaoNeural",
        "小艺@zh-CN-XiaoyiNeural",
        "云健@zh-CN-YunjianNeural",
        "云希@zh-CN-YunxiNeural",
        "云夏@zh-CN-YunxiaNeural",
        "云扬@zh-CN-YunyangNeural",
        "小北（辽宁方言）@zh-CN-liaoning-XiaobeiNeural",
        "小妮（陕西方言）@zh-CN-shaanxi-XiaoniNeural"
    )

    /** 当前朗读引擎是不是 Edge */
    val isEdgeEngine: Boolean
        get() = ReadAloud.ttsEngine?.contains("edgeinner") == true

    fun current(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT) ?: DEFAULT
    }

    fun currentName(context: Context): String = current(context).substringBefore("@")

    /**
     * 弹出语音列表；选中后保存，正在朗读时立即从当前段落用新语音重新合成
     */
    fun choose(context: Context, onChanged: (() -> Unit)? = null) {
        context.menuSelector(
            context.getString(R.string.read_aloud_voice),
            options.map { it.substringBefore("@") },
            options.indexOf(current(context))
        ) { index ->
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, options[index]).commit()
            ReadAloud.upTtsSpeechRate(context)
            onChanged?.invoke()
        }
    }
}
