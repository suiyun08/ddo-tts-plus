import type { Bookmark } from '@/book'

/**
 * 书签定位工具，规则与手机端 BookmarkMarker 一致：
 * 去掉空白后按书签原文在正文中匹配，多处命中时取离书签位置最近的一处。
 * 手机和网页的章节位置口径不同（手机含标题和段首缩进），位置只作就近参考。
 */

/** 去空白后超过这个长度的书签视为整页书签，不画下划线 */
export const PAGE_MARK_MIN_LENGTH = 200
/** 整页书签跳转时只用开头这些字定位 */
const JUMP_KEY_LENGTH = 30

export const MARK_CLASS = 'bookmark-mark'

export const normalizeText = (text: string) => text.replace(/\s+/g, '')

export const isPageBookmark = (bookmark: Bookmark) =>
  normalizeText(bookmark.bookText).length > PAGE_MARK_MIN_LENGTH

/** 章节正文中每个非空白字符的位置 */
type CharPoint = {
  node: Text
  offset: number
  paragraph: number
  /** 网页口径的章节位置 */
  pos: number
}

export type ChapterTextIndex = {
  text: string
  points: CharPoint[]
}

/**
 * 建立章节正文索引
 * @param paragraphs 各段落元素
 * @param paragraphStarts 各段落在网页口径下的起始位置
 */
export const buildTextIndex = (
  paragraphs: HTMLElement[],
  paragraphStarts: number[],
): ChapterTextIndex => {
  let text = ''
  const points: CharPoint[] = []
  paragraphs.forEach((element, paragraph) => {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
    let offsetInParagraph = 0
    let node = walker.nextNode() as Text | null
    while (node) {
      const data = node.data
      for (let i = 0; i < data.length; i++) {
        if (!/\s/.test(data[i])) {
          text += data[i]
          points.push({
            node,
            offset: i,
            paragraph,
            pos: (paragraphStarts[paragraph] ?? 0) + offsetInParagraph + i,
          })
        }
      }
      offsetInParagraph += data.length
      node = walker.nextNode() as Text | null
    }
  })
  return { text, points }
}

/**
 * 找到书签在索引中的位置，返回 [起始下标, 长度]
 * @param forJump 整页书签只按开头定位，用于跳转
 */
export const locateBookmark = (
  index: ChapterTextIndex,
  bookmark: Bookmark,
  forJump = false,
): [number, number] | undefined => {
  let key = normalizeText(bookmark.bookText)
  if (!key) return
  if (key.length > PAGE_MARK_MIN_LENGTH) {
    if (!forJump) return
    key = key.slice(0, JUMP_KEY_LENGTH)
  }
  let best = -1
  let bestDistance = Number.MAX_SAFE_INTEGER
  let found = index.text.indexOf(key)
  while (found >= 0) {
    const distance = Math.abs(index.points[found].pos - bookmark.chapterPos)
    if (distance < bestDistance) {
      best = found
      bestDistance = distance
    }
    found = index.text.indexOf(key, found + 1)
  }
  if (best < 0) return
  return [best, key.length]
}

/** 去掉已有的书签标记 */
export const clearMarks = (root: HTMLElement) => {
  root.querySelectorAll('span.' + MARK_CLASS).forEach(span => {
    const parent = span.parentNode
    if (!parent) return
    while (span.firstChild) parent.insertBefore(span.firstChild, span)
    parent.removeChild(span)
    parent.normalize()
  })
}

/**
 * 给书签文字加虚线下划线标记
 * 同一文本节点内的区间合并后，从后往前包裹，避免前面的偏移失效
 */
export const applyMarks = (
  index: ChapterTextIndex,
  bookmarks: Bookmark[],
) => {
  const segments = new Map<Text, [number, number][]>()
  for (const bookmark of bookmarks) {
    const located = locateBookmark(index, bookmark)
    if (!located) continue
    const [start, length] = located
    for (let i = start; i < start + length; i++) {
      const { node, offset } = index.points[i]
      const list = segments.get(node) ?? []
      const last = list[list.length - 1]
      if (last && offset <= last[1] + 1 && offset >= last[0]) {
        last[1] = Math.max(last[1], offset)
      } else {
        list.push([offset, offset])
      }
      segments.set(node, list)
    }
  }
  segments.forEach((list, node) => {
    const merged = list
      .sort((a, b) => a[0] - b[0])
      .reduce<[number, number][]>((acc, cur) => {
        const last = acc[acc.length - 1]
        if (last && cur[0] <= last[1] + 1) {
          last[1] = Math.max(last[1], cur[1])
        } else {
          acc.push([cur[0], cur[1]])
        }
        return acc
      }, [])
    for (let i = merged.length - 1; i >= 0; i--) {
      const [from, to] = merged[i]
      const range = document.createRange()
      range.setStart(node, from)
      range.setEnd(node, to + 1)
      const span = document.createElement('span')
      span.className = MARK_CLASS
      range.surroundContents(span)
    }
  })
}

export const formatBookmarkTime = (time: number) => {
  const date = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 点击坐标处的文本节点和偏移（caretPositionFromPoint 为标准接口，caretRangeFromPoint 兼容旧版 Safari/Chrome） */
type CaretDocument = Document & {
  caretPositionFromPoint?: (
    x: number,
    y: number,
  ) => { offsetNode: Node; offset: number } | null
  caretRangeFromPoint?: (x: number, y: number) => Range | null
}
export const caretFromPoint = (
  x: number,
  y: number,
): { node: Node; offset: number } | undefined => {
  const doc = document as CaretDocument
  const position = doc.caretPositionFromPoint?.(x, y)
  if (position) return { node: position.offsetNode, offset: position.offset }
  const range = doc.caretRangeFromPoint?.(x, y)
  if (range) return { node: range.startContainer, offset: range.startOffset }
}
