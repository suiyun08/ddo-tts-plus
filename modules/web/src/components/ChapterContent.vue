<template>
  <div class="title" data-chapterpos="0" ref="titleRef">{{ title }}</div>
  <div
    v-for="(para, index) in contents"
    :key="index"
    ref="paragraphRef"
    :data-chapterpos="chapterPos[index]"
  >
    <img
      class="full"
      v-if="/^\s*<img[^>]*src[^>]+>$/.test(String(para))"
      :src="getImageSrc(para)"
      @error.once="proxyImage"
      loading="lazy"
    />
    <p v-else :style="{ fontFamily, fontSize }" v-html="replaceImage(para)" @error.capture="handleImgLoadError" />
  </div>
</template>

<script setup lang="ts">
import { isLegadoUrl } from '@/utils/utils'
import API from '@api'
import jump from '@/plugins/jump'
import type { webReadConfig } from '@/web'
import type { Bookmark } from '@/book'
import {
  MARK_CLASS,
  applyMarks,
  buildTextIndex,
  clearMarks,
  locateBookmark,
} from '@/utils/bookmark'

const store = useBookStore()
const readWidth = computed(() => store.config.readWidth)
const _fontSize = computed(() => store.config.fontSize)
const bookUrl = computed(() => store.readingBook.bookUrl)

const props = defineProps<{
  chapterIndex: number
  contents: Array<string>
  title: string
  spacing: webReadConfig['spacing']
  fontFamily: string
  fontSize: string
  bookmarks?: Bookmark[]
}>()

const imgPattern = /<img[^>]*src=['"]([^'"]*(?:['"][^>]+\})?)['"][^>]*>/g

const replaceImage = (content: string) => {
  return content.replace(imgPattern, (match, src) => {
    if (isLegadoUrl(src)) {
      const proxySrc = API.getProxyImageUrl(
        bookUrl.value,
        src,
        _fontSize.value * 2,
      )
      return match.replace(src, proxySrc)
    }
    return match
  })
}

const getImageSrc = (content: string) => {
  const imgPattern = /<img[^>]*src=['"]([^'"]*(?:['"][^>]+\})?)['"][^>]*>/
  const src = content.match(imgPattern)![1] //reg tested in template
  if (isLegadoUrl(src))
    return API.getProxyImageUrl(
      bookUrl.value,
      src,
      readWidth.value,
    )
  return src
}
const proxyImage = (event: Event) => {
  /* 获取IMG标签原始的src
    <img src="/test" />
    假设location.href = http://example.com
    event.target.src 返回 http://example.com/test
    (event.target as HTMLImageElement)?.getAttribute("src")  返回/test
  */
  const src = (event.target as HTMLImageElement)?.getAttribute("src")
  if (src != null && src.length > 0) {
    (event.target as HTMLImageElement).src = API.getProxyImageUrl(
      bookUrl.value,
      src,
      readWidth.value,
    )
  }
}

/**
 * 处理传入的IMG标签错误事件，自动替换图片的代理链接
 */
const handleImgLoadError = (event: Event) => {
  if ((event.target as HTMLElement)?.tagName === "IMG") {
    console.log("[ChapterContent]: IMG Load Error, replace src:",
      (event.target as HTMLImageElement)?.getAttribute("src"), "=>",
      API.getProxyImageUrl(
        bookUrl.value,
        (event.target as HTMLImageElement)?.getAttribute("src") ?? "",
        readWidth.value,
      )
    )
    proxyImage(event)
  }
}

const calculateWordCount = (paragraph: string) => {
  //内嵌图片文字为1
  const imagePlaceHolder = ' '
  return paragraph.replace(imgPattern, imagePlaceHolder).length
}
const chapterPos = computed(() => {
  let pos = -1
  return Array.from(props.contents, content => {
    pos += calculateWordCount(content) + 1 //计算上一段的换行符
    return pos
  })
})

const titleRef = ref<HTMLElement>()
const paragraphRef = ref<HTMLParagraphElement[]>()
const scrollToReadedLength = (length: number) => {
  if (length === 0) return
  const paragraphIndex = chapterPos.value.findIndex(
    wordCount => wordCount >= length,
  )
  if (paragraphIndex === -1) return
  nextTick(() => {
    jump(paragraphRef.value![paragraphIndex], {
      duration: 0,
    })
  })
}

// 书签：虚线标注、跳转、计算选区位置
/** 按章节位置排序的段落元素（v-for 的 ref 数组不保证顺序） */
const sortedParagraphs = () =>
  [...(paragraphRef.value ?? [])].sort(
    (a, b) =>
      Number((a as HTMLElement).dataset.chapterpos) -
      Number((b as HTMLElement).dataset.chapterpos),
  ) as HTMLElement[]
/** 各段落在网页口径下的起始位置 */
const paragraphStarts = computed(() =>
  props.contents.map(
    (content, index) => chapterPos.value[index] - calculateWordCount(content),
  ),
)
const buildIndex = () => buildTextIndex(sortedParagraphs(), paragraphStarts.value)

const renderMarks = () => {
  const paragraphs = sortedParagraphs()
  paragraphs.forEach(clearMarks)
  const bookmarks = props.bookmarks ?? []
  if (bookmarks.length === 0) return
  try {
    applyMarks(buildIndex(), bookmarks)
  } catch (e) {
    console.warn('[ChapterContent]: 书签标注失败', e)
  }
}
onMounted(renderMarks)
onUpdated(renderMarks)
watch(() => props.bookmarks, () => nextTick(renderMarks), { deep: true })

/** 跳转到书签所在位置，找不到原文时按位置跳转 */
const scrollToBookmark = (bookmark: Bookmark) => {
  const index = buildIndex()
  const located = locateBookmark(index, bookmark)
  if (!located) {
    scrollToReadedLength(bookmark.chapterPos)
    return
  }
  const point = index.points[located[0]]
  const mark = point.node.parentElement?.closest('.' + MARK_CLASS) as
    | HTMLElement
    | null
  const target = mark ?? sortedParagraphs()[point.paragraph]
  nextTick(() => {
    jump(target, { duration: 0, offset: -window.innerHeight / 3 })
    if (mark) {
      mark.classList.add('flash')
      setTimeout(() => mark.classList.remove('flash'), 1600)
    }
  })
}

/** 计算选区起点在网页口径下的章节位置，不在本章正文内返回 undefined */
const positionOf = (node: Node, offset: number) => {
  const paragraphs = sortedParagraphs()
  const paragraph = paragraphs.findIndex(element => element.contains(node))
  if (paragraph < 0) return
  const element = paragraphs[paragraph]
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let length = 0
  let text = walker.nextNode()
  while (text && text !== node) {
    length += (text as Text).data.length
    text = walker.nextNode()
  }
  if (node.nodeType === Node.TEXT_NODE) length += offset
  return paragraphStarts.value[paragraph] + length
}

/** 某个文本位置上的书签（点击正文时查询），光标在字前或字后都算点中该字 */
const bookmarksAt = (node: Node, offset: number) => {
  const bookmarks = props.bookmarks ?? []
  if (bookmarks.length === 0) return []
  const index = buildIndex()
  const hits = [offset, offset - 1]
    .map(o => index.points.findIndex(p => p.node === node && p.offset === o))
    .filter(i => i >= 0)
  if (hits.length === 0) return []
  return bookmarks.filter(bookmark => {
    const located = locateBookmark(index, bookmark)
    if (!located) return false
    const [start, length] = located
    return hits.some(i => i >= start && i < start + length)
  })
}

/** 当前阅读位置所在段落的纯文本和起始位置 */
const paragraphAt = (length: number) => {
  let index = chapterPos.value.findIndex(wordCount => wordCount >= length)
  if (index < 0) index = 0
  const content = props.contents[index]
  if (content === undefined) return
  const text = content.replace(/<[^>]+>/g, '').trim()
  return { text, start: paragraphStarts.value[index] }
}

defineExpose({
  chapterIndex: props.chapterIndex,
  scrollToReadedLength,
  scrollToBookmark,
  positionOf,
  paragraphAt,
  bookmarksAt,
})
let intersectionObserver: IntersectionObserver | null = null
const emit = defineEmits(['readedLengthChange'])
onMounted(() => {
  intersectionObserver = new IntersectionObserver(
    entries => {
      for (const { target, isIntersecting } of entries) {
        if (isIntersecting) {
          emit(
            'readedLengthChange',
            props.chapterIndex,
            parseInt((target as HTMLElement).dataset.chapterpos as string),
          )
        }
      }
    },
    {
      rootMargin: `0px 0px -${window.innerHeight - 24}px 0px`,
    },
  )
  intersectionObserver.observe(titleRef.value!)
  paragraphRef.value!.forEach(element => {
    intersectionObserver!.observe(element)
  })
})

onUnmounted(() => {
  intersectionObserver?.disconnect()
  intersectionObserver = null
})
</script>

<style lang="scss" scoped>
.title {
  margin-bottom: 57px;
  font:
    24px / 32px PingFangSC-Regular,
    HelveticaNeue-Light,
    'Helvetica Neue Light',
    'Microsoft YaHei',
    sans-serif;
}

p {
  display: block;
  word-wrap: break-word;
  /*   word-break: break-all; */
  letter-spacing: calc(v-bind('props.spacing.letter') * 1em);
  line-height: calc(1 + v-bind('props.spacing.line'));
  margin: calc(v-bind('props.spacing.paragraph') * 1em) 0;

  :deep(img) {
    height: 1em;
  }

  :deep(.bookmark-mark) {
    cursor: pointer;
    text-decoration: underline dashed;
    text-decoration-color: #ed4259;
    text-decoration-thickness: 1px;
    text-underline-offset: 0.3em;
    transition: background-color 0.3s;
  }

  :deep(.bookmark-mark.flash) {
    background-color: rgba(237, 66, 89, 0.18);
  }
}

.full {
  display: block;
  width: 100%;
}
</style>
