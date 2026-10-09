<template>
  <div
    class="bookmark-popover"
    ref="popoverRef"
    :style="popoverStyle"
    @click.stop
  >
    <div class="header">
      <span class="chapter">{{ bookmarks[0]?.chapterName }}</span>
      <span class="close" title="关闭" @click="emit('close')">✕</span>
    </div>
    <div class="list">
      <div class="item" v-for="bookmark in bookmarks" :key="bookmark.time">
        <div class="text">{{ bookmark.bookText }}</div>
        <div class="note" v-if="bookmark.content">{{ bookmark.content }}</div>
        <div class="footer">
          <span class="time">{{ formatBookmarkTime(bookmark.time) }}</span>
          <span>
            <el-button size="small" text @click="emit('edit', bookmark)">编辑</el-button>
            <el-button size="small" text type="danger" @click="emit('remove', bookmark)">
              删除
            </el-button>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Bookmark } from '@/book'
import { formatBookmarkTime } from '@/utils/bookmark'

/** 点击正文书签文字后显示的书签浮窗 */
const props = defineProps<{
  bookmarks: Bookmark[]
  /** 点击位置横坐标（视口坐标） */
  x: number
  /** 被点击那一行的顶部和底部（视口坐标） */
  lineTop: number
  lineBottom: number
  background: string
}>()
const emit = defineEmits<{
  close: []
  edit: [bookmark: Bookmark]
  remove: [bookmark: Bookmark]
}>()

const MARGIN = 16
const GAP = 8
const popoverRef = ref<HTMLElement>()
const position = ref({ left: 0, top: 0, visible: false })
const width = computed(() => Math.min(360, window.innerWidth - MARGIN * 2))

const popoverStyle = computed(() => ({
  width: width.value + 'px',
  left: position.value.left + 'px',
  top: position.value.top + 'px',
  visibility: (position.value.visible ? 'visible' : 'hidden') as
    | 'visible'
    | 'hidden',
  background: props.background,
}))

// 先隐藏渲染测量高度，上方放得下就显示在被点击行上方，否则显示在下方
const place = () => {
  const height = popoverRef.value?.offsetHeight ?? 0
  const left = Math.min(
    Math.max(props.x - width.value / 2, MARGIN),
    window.innerWidth - MARGIN - width.value,
  )
  let top = props.lineTop - height - GAP
  if (top < MARGIN) top = props.lineBottom + GAP
  position.value = { left, top, visible: true }
}
watch(
  () => [props.bookmarks, props.x, props.lineTop],
  () => {
    position.value.visible = false
    nextTick(place)
  },
)
onMounted(() => nextTick(place))
</script>

<style lang="scss" scoped>
.bookmark-popover {
  position: fixed;
  z-index: 300;
  max-height: 40vh;
  overflow: auto;
  box-sizing: border-box;
  padding: 10px 12px 6px 16px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
  cursor: default;
  font-family: PingFangSC-Regular, HelveticaNeue-Light, 'Helvetica Neue Light',
    'Microsoft YaHei', sans-serif;

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    opacity: 0.7;

    .close {
      cursor: pointer;
      padding: 2px 6px;
      font-size: 14px;
    }
  }

  .item {
    padding: 6px 0 2px;

    & + .item {
      border-top: 1px solid rgba(128, 128, 128, 0.2);
    }

    .text {
      font-size: 15px;
      line-height: 1.6;
      white-space: pre-wrap;
    }

    .note {
      margin-top: 4px;
      font-size: 13px;
      line-height: 1.5;
      opacity: 0.75;
      white-space: pre-wrap;
    }

    .footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .time {
        font-size: 11px;
        opacity: 0.6;
      }
    }
  }
}
</style>
