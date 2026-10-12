<template>
  <div class="bookmark-wrapper" @click.stop>
    <div class="list" v-if="bookmarks.length > 0">
      <div
        class="item"
        v-for="bookmark in bookmarks"
        :key="bookmark.time"
        :class="{ current: bookmark.chapterIndex === currentChapterIndex }"
        @click="emit('jump', bookmark)"
      >
        <div class="meta">
          <span class="chapter">{{ bookmark.chapterName }}</span>
          <span class="time">{{ formatTime(bookmark.time) }}</span>
        </div>
        <div class="text">{{ bookmark.bookText }}</div>
        <div class="note" v-if="bookmark.content">{{ bookmark.content }}</div>
        <div class="actions" @click.stop>
          <el-button size="small" text @click="emit('edit', bookmark)">编辑</el-button>
          <el-button size="small" text type="danger" @click="emit('remove', bookmark)">
            删除
          </el-button>
        </div>
      </div>
    </div>
    <div class="empty" v-else>
      还没有书签。选中正文文字后点「加书签」，或点右上角按钮给当前位置加书签。
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Bookmark } from '@/book'
import { formatBookmarkTime as formatTime } from '@/utils/bookmark'

defineProps<{
  bookmarks: Bookmark[]
  currentChapterIndex: number
}>()
const emit = defineEmits<{
  jump: [bookmark: Bookmark]
  edit: [bookmark: Bookmark]
  remove: [bookmark: Bookmark]
}>()
</script>

<style lang="scss" scoped>
.bookmark-wrapper {
  .item {
    padding: 12px;
    margin-bottom: 8px;
    border-radius: 12px;
    background: rgba(128, 128, 128, 0.07);
    cursor: pointer;
    transition: background-color 0.15s;

    &:hover {
      background: rgba(128, 128, 128, 0.13);
    }

    &.current .chapter {
      color: #ed4259;
    }

    .meta {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      opacity: 0.7;
    }

    .text {
      margin-top: 4px;
      font-size: 14px;
      line-height: 1.6;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-decoration: underline dashed rgba(237, 66, 89, 0.6);
      text-underline-offset: 0.3em;
    }

    .note {
      margin-top: 4px;
      font-size: 13px;
      opacity: 0.8;
      white-space: pre-wrap;
    }

    .actions {
      text-align: right;
      margin-top: 2px;
    }
  }

  .empty {
    font-size: 14px;
    opacity: 0.7;
    line-height: 1.6;
    padding: 12px 0;
  }
}
</style>
