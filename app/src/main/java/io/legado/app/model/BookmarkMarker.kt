package io.legado.app.model

import android.graphics.DashPathEffect
import android.graphics.Paint
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
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
 * 因此按书签原文在正文中匹配定位：去掉空白后匹配，多处命中时取离书签位置最近的一处。
 * - 选中文字的书签：返回其在章节中的位置区间，正文画虚线下划线
 * - 整页书签（原文超过 [PAGE_MARK_MIN_LENGTH]）：不画线，书签所在页显示角标
 */
object BookmarkMarker {

    /** 去空白后超过这个长度的书签视为整页书签 */
    private const val PAGE_MARK_MIN_LENGTH = 200

    class ChapterMarks(
        /** 需要画下划线的章节位置区间（闭区间） */
        val ranges: List<IntRange>,
        /** 整页书签的章节位置 */
        val pagePositions: List<Int>,
    )

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

    fun hasPageMark(page: TextPage): Boolean {
        if (page.isMsgPage || page.lineSize == 0) return false
        val marks = getMarks(page.textChapter) ?: return false
        if (marks.pagePositions.isEmpty()) return false
        val start = page.chapterPosition
        val end = start + page.charSize
        return marks.pagePositions.any { it in start until end }
    }

    fun underlineColor(): Int = ThemeStore.accentColor

    private fun resolve(chapter: TextChapter, list: List<Bookmark>, pageSize: Int): ChapterMarks {
        val chapterMarks = list.filter { it.chapterIndex == chapter.position }
        if (chapterMarks.isEmpty()) return ChapterMarks(emptyList(), emptyList())
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
        val ranges = arrayListOf<IntRange>()
        val pagePositions = arrayListOf<Int>()
        for (bookmark in chapterMarks) {
            val key = bookmark.bookText.filterNot { it.isWhitespace() }
            if (key.isEmpty()) continue
            if (key.length > PAGE_MARK_MIN_LENGTH) {
                pagePositions.add(bookmark.chapterPos)
                continue
            }
            var best = -1
            var bestDistance = Int.MAX_VALUE
            var index = normalized.indexOf(key)
            while (index >= 0) {
                val distance = abs(posMap[index] - bookmark.chapterPos)
                if (distance < bestDistance) {
                    best = index
                    bestDistance = distance
                }
                index = normalized.indexOf(key, index + 1)
            }
            if (best >= 0) {
                ranges.add(posMap[best]..posMap[best + key.length - 1])
            }
        }
        return ChapterMarks(ranges, pagePositions)
    }

}
