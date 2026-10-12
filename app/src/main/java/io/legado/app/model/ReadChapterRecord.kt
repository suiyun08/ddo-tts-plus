package io.legado.app.model

import io.legado.app.help.CacheManager
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray

/**
 * 读完的章节记录，供网页目录区分已读章节。
 * 读到章节最后一页才算读完（手机翻页、滚动、听书和网页端阅读都算），跳过的章节不会记入。
 * 按书名+作者存放在缓存表中（不改数据库结构），值为读完章节的地址列表
 */
object ReadChapterRecord {

    private val memory = HashMap<String, MutableSet<String>>()

    private fun key(name: String, author: String) =
        "readChapters_" + MD5Utils.md5Encode16("$name|$author")

    private fun load(key: String): MutableSet<String> {
        return memory.getOrPut(key) {
            GSON.fromJsonArray<String>(CacheManager.get(key)).getOrNull()
                ?.toMutableSet() ?: mutableSetOf()
        }
    }

    @Synchronized
    fun get(name: String, author: String): List<String> {
        return load(key(name, author)).toList()
    }

    @Synchronized
    fun add(name: String, author: String, urls: Collection<String>) {
        val key = key(name, author)
        val set = load(key)
        if (set.addAll(urls.filter { it.isNotEmpty() })) {
            CacheManager.put(key, GSON.toJson(set))
        }
    }

}
