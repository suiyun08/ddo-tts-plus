package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.model.ReadChapterRecord
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug

/**
 * 网页端书签接口，与手机端共用同一张书签表
 */
object BookmarkController {

    /**
     * 获取一本书的全部书签
     */
    fun getBookmarks(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val name = parameters["name"]?.firstOrNull()
        if (name.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数name不能为空")
        }
        val author = parameters["author"]?.firstOrNull() ?: ""
        val bookmarks = appDb.bookmarkDao.getByBook(name, author)
            .sortedWith(compareBy({ it.chapterIndex }, { it.chapterPos }))
        return returnData.setData(bookmarks)
    }

    /**
     * 新增或修改书签，time 为书签主键，不传则新建
     */
    fun saveBookmark(postData: String?): ReturnData {
        val returnData = ReturnData()
        val bookmark = parse(postData) ?: return returnData.setErrorMsg("格式不对")
        if (bookmark.bookName.isEmpty()) {
            return returnData.setErrorMsg("书名不能为空")
        }
        appDb.bookmarkDao.insert(bookmark)
        return returnData.setData(bookmark)
    }

    /**
     * 删除书签
     */
    fun deleteBookmark(postData: String?): ReturnData {
        val returnData = ReturnData()
        val bookmark = parse(postData) ?: return returnData.setErrorMsg("格式不对")
        appDb.bookmarkDao.delete(bookmark)
        return returnData.setData("")
    }

    /**
     * 获取一本书读完的章节地址
     */
    fun getReadChapters(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val name = parameters["name"]?.firstOrNull()
        if (name.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数name不能为空")
        }
        val author = parameters["author"]?.firstOrNull() ?: ""
        return returnData.setData(ReadChapterRecord.get(name, author))
    }

    /**
     * 记录网页端读完的章节：{name, author, urls}
     */
    fun saveReadChapters(postData: String?): ReturnData {
        val returnData = ReturnData()
        val data = GSON.fromJsonObject<ReadChapters>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull()
        if (data == null || data.name.isNullOrEmpty() || data.urls == null) {
            return returnData.setErrorMsg("格式不对")
        }
        ReadChapterRecord.add(data.name, data.author ?: "", data.urls)
        return returnData.setData("")
    }

    private class ReadChapters(
        val name: String?,
        val author: String?,
        val urls: List<String>?
    )

    /**
     * Gson 不会使用数据类的默认值，缺失字段会是 null，这里统一补齐
     */
    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    private fun parse(postData: String?): Bookmark? {
        val raw = GSON.fromJsonObject<Bookmark>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull() ?: return null
        return Bookmark(
            time = if (raw.time > 0) raw.time else System.currentTimeMillis(),
            bookName = raw.bookName ?: "",
            bookAuthor = raw.bookAuthor ?: "",
            chapterIndex = raw.chapterIndex,
            chapterPos = raw.chapterPos,
            chapterName = raw.chapterName ?: "",
            bookText = raw.bookText ?: "",
            content = raw.content ?: ""
        )
    }

}
