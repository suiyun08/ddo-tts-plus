<template>
  <div class="cata-wrapper">
    <virtual-list
      :style="{ height: listHeight + 'px', overflow: 'auto' }"
      :class="{ night: isNight, day: !isNight }"
      ref="virtualListRef"
      data-key="index"
      wrap-class="data-wrapper"
      item-class="cata"
      :data-sources="virtualListdata"
      :data-component="CatalogItem"
      :estimate-size="ITEM_HEIGHT"
      :extra-props="{ gotoChapter, currentChapterIndex, isRead }"
    />
  </div>
</template>

<script setup lang="ts">
import VirtualList from 'vue3-virtual-scroll-list'
import { useWindowSize } from '@vueuse/core'
import '../assets/fonts/popfont.css'
import CatalogItem from './CatalogItem.vue'
import type { BookChapter } from '@/book'

const store = useBookStore()

const { catalog, miniInterface, isNight } = storeToRefs(store)

const ITEM_HEIGHT = 40
/** 浮窗上下留白、标题栏和底部内边距 */
const PANEL_CHROME = 80 + 62 + 20

//虚拟列表 数据源
const virtualListdata = computed(() => {
  const catalogValue = catalog.value
  if (miniInterface.value) return catalogValue

  // pc端 virtualListIitem有2个章节
  const length = Math.ceil(catalogValue.length / 2)
  const virtualListDataSource = new Array<{
    index: number
    catas: BookChapter[]
  }>(length)

  let i = 0
  while (i < length) {
    virtualListDataSource[i] = {
      index: i,
      catas: catalogValue.slice(2 * i, 2 * i + 2),
    }
    i++
  }
  return virtualListDataSource
})

// 列表高度随窗口高度和章节数量伸缩
const { height: windowHeight } = useWindowSize()
const listHeight = computed(() =>
  Math.max(
    ITEM_HEIGHT * 3,
    Math.min(
      virtualListdata.value.length * ITEM_HEIGHT,
      windowHeight.value - PANEL_CHROME,
    ),
  ),
)

// 已读章节（手机和网页读完的）
const readSet = computed(() => new Set(store.readChapters))
const isRead = (chapter: BookChapter) => readSet.value.has(chapter.url)

//打开目录 滚动到当前章节
const virtualListRef = ref()
const currentChapterIndex = computed(() => store.readingBook.chapterIndex)
const virtualListIndex = computed(() => {
  const index = currentChapterIndex.value
  if (miniInterface.value) return index
  // pc端 virtualListIitem有2个章节
  return Math.floor(index / 2)
})
onMounted(() => {
  nextTick(() => {
    // 当前章节放在列表中间
    const offset = Math.floor(listHeight.value / ITEM_HEIGHT / 2)
    virtualListRef.value?.scrollToIndex(Math.max(virtualListIndex.value - offset, 0))
  })
})

// 点击加载对应章节内容
const emit = defineEmits<{ gotoChapter: [index: number] }>()
const gotoChapter = (chapter: BookChapter) => {
  emit('gotoChapter', catalog.value.indexOf(chapter))
}
</script>

<style lang="scss" scoped>
.cata-wrapper {
  :deep(.data-wrapper) {
    .cata {
      height: 40px;
      cursor: pointer;
      font:
        15px / 40px PingFangSC-Regular,
        HelveticaNeue-Light,
        'Helvetica Neue Light',
        'Microsoft YaHei',
        sans-serif;
    }
  }

  .night {
    :deep(.cata) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    }
  }

  .day {
    :deep(.cata) {
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
    }
  }
}
</style>
