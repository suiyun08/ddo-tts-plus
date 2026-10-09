package io.legado.app.model

import android.graphics.DashPathEffect
import android.graphics.Paint
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * 阅读页书签标记
 *
 * 书签不记录文字长度，且手机与网页的章节位置口径不同（手机含标题和段首缩进），
 * 因此按书签原文在正文中匹配定位：去掉空白后匹配，多处命中时取离书签位置最近的一处，
 * 匹配到的文字画虚线下划线。
 * 旧版菜单书签记录的是整页原文（超过 [LONG_TEXT_LENGTH]），只标出其第一句。
 */
object BookmarkMarker {

    /** 去空白后超过这个长度的书签（旧版整页书签）只标出第一句 */
    private const val LONG_TEXT_LENGTH = 200

    /** 第一句最多取这么多字 */
    private const val SENTENCE_MAX_LENGTH = 50
    private const val SENTENCE_ENDS = "。！？!?…；;"
    private const val CLOSING_QUOTES = "”’」』\"'）)"

    class ChapterMarks(
        /** 需要画下划线的章节位置区间（闭区间）及对应书签 */
        val marks: List<Pair<IntRange, Bookmark>>,
    ) {
        val ranges: List<IntRange> = marks.map { it.first }
    }

    private class Cache(
        val chapter: WeakReference<TextChapter>,
        val version: Int,
        val pageSize: Int,
        val marks: ChapterMarks,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var bookKey: String? = null

    @Volatile
    private var bookmarks: List<Bookmark> = emptyList()

    @Volatile
    private var version = 0
    /** 按对象身份缓存（TextChapter 是 data class，重新排版后会与旧对象 equals），只保留最近几章 */
    private val cache = ArrayList<Cache>()
    private const val CACHE_SIZE = 6

    val underlinePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f.dpToPx()
            pathEffect = DashPathEffect(floatArrayOf(4f.dpToPx(), 3f.dpToPx()), 0f)
        }
    }

    /**
     * 打开或切换书籍时调用，持续监听这本书的书签变化
     */
    fun observe(book: Book) {
        val key = "${book.name}\u0000${book.author}"
        if (key == bookKey && job?.isActive == true) return
        bookKey = key
        job?.cancel()
        bookmarks = emptyList()
        version++
        job = scope.launch {
            appDb.bookmarkDao.flowByBook(book.name, book.author)
                .catch { }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    bookmarks = list
                    version++
                    refreshReadView()
                }
        }
    }

    private fun refreshReadView() {
        listOf(ReadBook.prevTextChapter, ReadBook.curTextChapter, ReadBook.nextTextChapter)
            .forEach { chapter ->
                chapter?.pages?.forEach { it.invalidateAll() }
            }
        ReadBook.callBack?.upContent(resetPageOffset = false)
    }

    fun getMarks(chapter: TextChapter): ChapterMarks? {
        val list = bookmarks
        if (list.isEmpty()) return null
        val curVersion = version
        val pageSize = chapter.pages.size
        synchronized(cache) {
            cache.firstOrNull { it.chapter.get() === chapter }?.let {
                if (it.version == curVersion && it.pageSize == pageSize) return it.marks
            }
        }
        val marks = try {
            resolve(chapter, list, pageSize)
        } catch (e: Exception) {
            // 章节仍在排版时可能并发修改，下次绘制再算
            return null
        }
        synchronized(cache) {
            cache.removeAll { it.chapter.get().let { c -> c == null || c === chapter } }
            cache.add(Cache(WeakReference(chapter), curVersion, pageSize, marks))
            if (cache.size > CACHE_SIZE) cache.removeAt(0)
        }
        return marks
    }

    fun underlineColor(): Int = ThemeStore.accentColor

    private fun resolve(chapter: TextChapter, list: List<Bookmark>, pageSize: Int): ChapterMarks {
        val chapterMarks = list.filter { it.chapterIndex == chapter.position }
        if (chapterMarks.isEmpty()) return ChapterMarks(emptyList())
        // 构建去空白的章节文本，以及每个字符对应的章节位置
        val text = StringBuilder()
        val posMap = ArrayList<Int>()
        val pages = chapter.pages
        for (i in 0 until pageSize) {
            val lines = pages[i].lines
            for (j in lines.indices) {
                val line = lines[j]
                val lineText = line.text
                for (k in lineText.indices) {
                    val c = lineText[k]
                    if (!c.isWhitespace()) {
                        text.append(c)
                        posMap.add(line.chapterPosition + k)
                    }
                }
            }
        }
        val normalized = text.toString()
        val marks = arrayListOf<Pair<IntRange, Bookmark>>()
        for (bookmark in chapterMarks) {
            val key = matchKey(bookmark.bookText)
            if (key.isEmpty()) continue
            val best = findNearest(normalized, posMap, key, bookmark.chapterPos)
            if (best >= 0) {
                marks.add(posMap[best]..posMap[best + key.length - 1] to bookmark)
            }
        }
        return ChapterMarks(marks)
    }

    /**
     * 章节位置上的书签（点击正文时查询）
     */
    fun bookmarksAt(chapter: TextChapter, chapterPos: Int): List<Bookmark> {
        val marks = getMarks(chapter) ?: return emptyList()
        return marks.marks.filter { chapterPos in it.first }.map { it.second }
    }

    /**
     * 用于在正文中匹配的书签文字（去空白），旧版整页书签只取第一句
     */
    private fun matchKey(bookText: String): String {
        val key = bookText.filterNot { it.isWhitespace() }
        if (key.length <= LONG_TEXT_LENGTH) return key
        return firstSentence(bookText).filterNot { it.isWhitespace() }
    }

    /**
     * 取第一句：到第一个句末标点（含紧跟的引号）或换行为止，最多 [SENTENCE_MAX_LENGTH] 字。
     * 菜单「添加书签」只记录这一句，正文只画一小段虚线；网页端 firstSentence 与此一致
     */
    fun firstSentence(text: String): String {
        val trimmed = text.trimStart()
        val builder = StringBuilder()
        var i = 0
        while (i < trimmed.length && builder.length < SENTENCE_MAX_LENGTH) {
            val c = trimmed[i]
            if (c == '\n') break
            builder.append(c)
            i++
            if (c in SENTENCE_ENDS) {
                while (i < trimmed.length && trimmed[i] in CLOSING_QUOTES) {
                    builder.append(trimmed[i])
                    i++
                }
                break
            }
        }
        return builder.toString().trim()
    }

    /**
     * 在去空白的章节文本中查找 key，多处命中时返回离 hintPos 最近的下标，找不到返回 -1
     */
    private fun findNearest(text: String, posMap: List<Int>, key: String, hintPos: Int): Int {
        var best = -1
        var bestDistance = Int.MAX_VALUE
        var index = text.indexOf(key)
        while (index >= 0) {
            val distance = abs(posMap[index] - hintPos)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
            index = text.indexOf(key, index + 1)
        }
        return best
    }

}
