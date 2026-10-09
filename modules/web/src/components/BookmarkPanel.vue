<template>
  <div class="bookmark-wrapper" :style="popupTheme" @click.stop>
    <div class="header">
      <div class="title">书签</div>
      <el-button size="small" text @click="emit('addCurrent')">
        给当前位置加书签
      </el-button>
    </div>
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
      还没有书签。选中正文文字后点「加书签」，或点上方按钮给当前位置加书签。
    </div>
  </div>
</template>

<script setup lang="ts">
import settings from '../config/themeConfig'
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
  addCurrent: []
}>()

const store = useBookStore()
const popupTheme = computed(() => ({
  background: settings.themes[store.theme].popup,
}))
</script>

<style lang="scss" scoped>
.bookmark-wrapper {
  margin: -16px;
  padding: 18px 20px 20px 25px;

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .title {
      font-size: 18px;
      font-weight: 400;
      font-family: FZZCYSK;
      color: #ed4259;
      border-bottom: 1px solid #ed4259;
    }
  }

  .list {
    max-height: 360px;
    overflow: auto;
  }

  .item {
    padding: 10px 4px;
    border-bottom: 1px solid rgba(128, 128, 128, 0.2);
    cursor: pointer;

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
