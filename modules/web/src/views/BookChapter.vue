<template>
  <div
    class="chapter-wrapper"
    :style="bodyTheme"
    :class="{ night: isNight, day: !isNight }"
    @click="onWrapperClick"
  >
    <div class="tool-bar" :style="leftBarTheme">
      <div class="tools">
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="popCataVisible"
          popper-class="pop-cata"
        >
          <PopCatalog @getContent="getContent" class="popup" />
          <template #reference>
            <div class="tool-icon" :class="{ 'no-point': false }">
              <div class="iconfont">&#58905;</div>
              <div class="icon-text">目录</div>
            </div>
          </template>
        </el-popover>
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="bookmarkVisible"
          popper-class="pop-cata"
        >
          <BookmarkPanel
            class="popup"
            :bookmarks="bookmarks"
            :currentChapterIndex="chapterIndex"
            @jump="jumpToBookmark"
            @edit="editBookmark"
            @remove="removeBookmark"
            @addCurrent="addCurrentBookmark"
          />
          <template #reference>
            <div class="tool-icon" :class="{ 'no-point': noPoint }">
              <div class="iconfont"><el-icon><CollectionTag /></el-icon></div>
              <div class="icon-text">书签</div>
            </div>
          </template>
        </el-popover>
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="readSettingsVisible"
          popper-class="pop-setting"
        >
          <read-settings class="popup" />
          <template #reference>
            <div class="tool-icon" :class="{ 'no-point': noPoint }">
              <div class="iconfont">&#58971;</div>
              <div class="icon-text">设置</div>
            </div>
          </template>
        </el-popover>
        <div class="tool-icon" @click="toShelf">
          <div class="iconfont">&#58892;</div>
          <div class="icon-text">书架</div>
        </div>
        <div class="tool-icon" :class="{ 'no-point': noPoint }" @click="toTop">
          <div class="iconfont">&#58914;</div>
          <div class="icon-text">顶部</div>
        </div>
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="toBottom"
        >
          <div class="iconfont">&#58915;</div>
          <div class="icon-text">底部</div>
        </div>
      </div>
    </div>
    <div class="read-bar" :style="rightBarTheme">
      <div class="tools">
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="toPreChapter"
        >
          <div class="iconfont">&#58920;</div>
          <span v-if="miniInterface">上一章</span>
        </div>
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="toNextChapter"
        >
          <span v-if="miniInterface">下一章</span>
          <div class="iconfont">&#58913;</div>
        </div>
      </div>
    </div>
    <div
      class="selection-bookmark"
      v-if="selectionButton"
      :style="{ top: selectionButton.top + 'px', left: selectionButton.left + 'px' }"
      @mousedown.prevent
      @touchstart.stop
      @click.stop="addSelectionBookmark"
    >
      加书签
    </div>
    <BookmarkPopover
      v-if="bookmarkPopover"
      v-bind="bookmarkPopover"
      :background="popupColor"
      @close="bookmarkPopover = null"
      @edit="editBookmark"
      @remove="removeBookmark"
    />
    <BookmarkEditor ref="bookmarkEditor" @changed="loadBookmarks" />
    <div
      v-if="pageMarker"
      class="page-marker"
      :class="{ fading: pageMarker.fading }"
      :style="{
        top: pageMarker.top + 'px',
        left: pageMarker.left + 'px',
        height: pageMarker.height + 'px',
      }"
    ></div>
    <div class="chapter-bar"></div>
    <div
      class="chapter"
      ref="content"
      :style="chapterTheme"
      @click="onContentClick"
    >
      <div class="content">
        <div class="top-bar" ref="top"></div>
        <div
          v-for="data in chapterData"
          :key="data.index"
          :chapterIndex="data.index"
          ref="chapter"
        >
          <chapter-content
            ref="chapterRef"
            :chapterIndex="data.index"
            :contents="data.content"
            :title="data.title"
            :spacing="store.config.spacing"
            :fontSize="fontSize"
            :fontFamily="fontFamily"
            :bookmarks="bookmarksByChapter.get(data.index)"
            @readedLengthChange="onReadedLengthChange"
            v-if="showContent"
          />
        </div>
        <div class="loading" ref="loading"></div>
        <div class="bottom-bar" ref="bottom"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import jump from '@/plugins/jump'
import settings from '@/config/themeConfig'
import API from '@api'
import { useLoading } from '@/hooks/loading'
import { useDebounceFn } from '@vueuse/shared'
import { isNullOrBlank } from '@/utils/utils'
import { CollectionTag } from '@element-plus/icons-vue'
import BookmarkPanel from '@/components/BookmarkPanel.vue'
import BookmarkPopover from '@/components/BookmarkPopover.vue'
import BookmarkEditor from '@/components/BookmarkEditor.vue'
import { caretFromPoint, firstSentence } from '@/utils/bookmark'
import type { Bookmark } from '@/book'

const content = ref()
// loading spinner
const { isLoading, loadingWrapper } = useLoading(content, '正在获取信息')
const store = useBookStore()

const {
  catalog,
  popCataVisible,
  readSettingsVisible,
  miniInterface,
  showContent,
  bookProgress,
  theme,
  isNight,
} = storeToRefs(store)

const chapterPos = computed({
  get: () => store.readingBook.chapterPos,
  set: value => (store.readingBook.chapterPos = value),
})
const chapterIndex = computed({
  get: () => store.readingBook.chapterIndex,
  set: value => (store.readingBook.chapterIndex = value),
})
const isSeachBook = computed({
  get: () => store.readingBook.isSeachBook,
  set: value => (store.readingBook.isSeachBook = value),
})

// 当前阅读书籍readingBook持久化
watch(
  () => store.readingBook,
  book => {
    // 保存localStorage
    // localStorage.setItem(book.bookUrl, JSON.stringify(book));
    // 最近阅读
    localStorage.setItem('readingRecent', JSON.stringify(book))
    //保存 sessionStorage
    sessionStorage.setItem('chapterIndex', book.chapterIndex.toString())
    sessionStorage.setItem('chapterPos', book.chapterPos.toString())
  },
  { deep: 1 },
)

// 无限滚动
const infiniteLoading = computed(() => store.config.infiniteLoading)
let scrollObserver: IntersectionObserver | null
const loading = ref()
watchEffect(() => {
  if (!infiniteLoading.value) {
    scrollObserver?.disconnect()
  } else {
    scrollObserver?.observe(loading.value)
  }
})
const loadMore = () => {
  const index = chapterData.value.slice(-1)[0].index
  if (catalog.value.length - 1 > index) {
    getContent(index + 1, false)
    store.saveBookProgress() // 保存的是上一章的进度，不是预载的本章进度
  }
}
// IntersectionObserver回调 底部加载
const onReachBottom = (entries: IntersectionObserverEntry[]) => {
  if (isLoading.value) return
  for (const { isIntersecting } of entries) {
    if (!isIntersecting) return
    loadMore()
  }
}

// 字体
const fontFamily = computed(() => {
  if (store.config.font >= 0) {
    return settings.fonts[store.config.font]
  }
  return store.config.customFontName
})
const fontSize = computed(() => {
  return store.config.fontSize + 'px'
})

// 主题部分
const bodyColor = computed(() => settings.themes[theme.value].body)
const chapterColor = computed(() => settings.themes[theme.value].content)
const popupColor = computed(() => settings.themes[theme.value].popup)

const readWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 130 + 'px'
  } else {
    return window.innerWidth + 'px'
  }
})
const popupWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 33
  } else {
    return window.innerWidth - 33
  }
})
const bodyTheme = computed(() => {
  return {
    background: bodyColor.value,
  }
})
const chapterTheme = computed(() => {
  return {
    background: chapterColor.value,
    width: readWidth.value,
  }
})
const showToolBar = ref(false)
const leftBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginLeft: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 68) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})
const rightBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginRight: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 52) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})

/**
 * pc移动端判断 最大阅读宽度修正
 * 阅读宽度最小为640px 加上工具栏 68px 52px 取较大值 为 776px
 */
const onResize = () => {
  // 窗口尺寸变化后行位置会变，翻页标记失效
  clearPageMarker()
  store.setMiniInterface(window.innerWidth < 776)
  const width = store.config.readWidth /**包含padding */
  checkPageWidth(width)
}
/** 判断阅读宽度是否超出页面或者低于默认值640 */
const checkPageWidth = (readWidth: number) => {
  if (store.miniInterface) return
  if (readWidth < 640) store.config.readWidth = 640
  if (readWidth + 2 * 68 > window.innerWidth) store.config.readWidth -= 160
}
watch(
  () => store.config.readWidth,
  width => checkPageWidth(width),
)
// 顶部底部跳转
const top = ref()
const bottom = ref()
const toTop = () => {
  jump(top.value)
}
const toBottom = () => {
  jump(bottom.value)
}

// 书架路由切换
const router = useRouter()
const toShelf = () => {
  router.push('/')
}

// 获取章节内容
const chapterData = ref<{ index: number; content: string[]; title: string }[]>(
  [],
)
const noPoint = ref(true)
const getContent = (index: number, reloadChapter = true, chapterPos = 0) => {
  if (reloadChapter) {
    //展示进度条
    store.setShowContent(false)
    //强制滚回顶层
    jump(top.value, { duration: 0 })
    //从目录，按钮切换章节时保存进度 预加载时不保存
    saveReadingBookProgressToBrowser(index, chapterPos)
    chapterData.value = []
    clearPageMarker()
  }
  const bookUrl = store.readingBook.bookUrl
  const { title, index: chapterIndex } = catalog.value[index]

  loadingWrapper(
    API.getBookContent(bookUrl, chapterIndex).then(
      res => {
        if (res.data.isSuccess) {
          const data = res.data.data
          const content = data.split(/\n+/)
          chapterData.value.push({ index, content, title })
          if (reloadChapter) toChapterPos(chapterPos)
        } else {
          ElMessage({ message: res.data.errorMsg, type: 'error' })
          const content = [res.data.errorMsg]
          chapterData.value.push({ index, content, title })
        }
        store.setContentLoading(true)
        noPoint.value = false
        store.setShowContent(true)
        if (!res.data.isSuccess) {
          throw res.data
        }
      },
      err => {
        const content = ['获取章节内容失败！']
        chapterData.value.push({ index, content, title })
        store.setShowContent(true)
        throw err
      },
    ),
  )
}

// 章节进度跳转和计算
const chapter = ref()
const chapterRef = ref()
/** 切换章节后需要跳转到的书签 */
let pendingBookmark: Bookmark | null = null
const toChapterPos = (pos: number) => {
  nextTick(() => {
    if (chapterRef.value.length !== 1) return
    const target = pendingBookmark
    pendingBookmark = null
    if (pendingChapterEnd) {
      // 往前翻进入上一章：停在章末，并标出最后一行
      pendingChapterEnd = false
      // 用 jump 滚动：切章开头排队的「回到顶部」也走 jump，同一个 jump 以最后一次为准，不会被覆盖
      jump(bottom.value, {
        duration: 0,
        callback: () => {
          const lines = collectLines()
          const last = lines[lines.length - 1]
          if (last) showPageMarker(last, 'right')
        },
      })
    } else if (target) chapterRef.value[0].scrollToBookmark(target)
    else chapterRef.value[0].scrollToReadedLength(pos)
  })
}

// 书签
const bookmarkVisible = ref(false)
const bookmarks = ref<Bookmark[]>([])
const bookmarksByChapter = computed(() => {
  const map = new Map<number, Bookmark[]>()
  for (const bookmark of bookmarks.value) {
    const list = map.get(bookmark.chapterIndex) ?? []
    list.push(bookmark)
    map.set(bookmark.chapterIndex, list)
  }
  return map
})
const loadBookmarks = async () => {
  const { name, author } = store.readingBook
  if (isNullOrBlank(name)) return
  try {
    const res = await API.getBookmarks(name, author ?? '')
    if (res.data.isSuccess) bookmarks.value = res.data.data
  } catch (e) {
    console.warn('[BookChapter]: 获取书签失败', e)
  }
}
// 打开书签面板时刷新，获取手机端新加的书签
watch(bookmarkVisible, visible => {
  if (visible) loadBookmarks()
})

const findChapterContent = (index: number) =>
  (chapterRef.value ?? []).find(
    (item: { chapterIndex: number }) => item.chapterIndex === index,
  )

const jumpToBookmark = (bookmark: Bookmark) => {
  bookmarkVisible.value = false
  const loaded = findChapterContent(bookmark.chapterIndex)
  if (loaded) {
    loaded.scrollToBookmark(bookmark)
    return
  }
  if (typeof catalog.value[bookmark.chapterIndex] === 'undefined') {
    ElMessage.error('书签所在章节不存在')
    return
  }
  pendingBookmark = bookmark
  store.setContentLoading(true)
  getContent(bookmark.chapterIndex, true, bookmark.chapterPos)
  store.saveBookProgress()
}

const saveNewBookmark = async (bookmark: Omit<Bookmark, 'time' | 'content'>) => {
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt('备注（可不填）', '添加书签', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPlaceholder: '写点想法…',
      inputType: 'textarea',
    })
    note = value ?? ''
  } catch {
    return false
  }
  const res = await API.saveBookmark({
    ...bookmark,
    time: Date.now(),
    content: note,
  })
  if (!res.data.isSuccess) {
    ElMessage.error(res.data.errorMsg)
    return false
  }
  ElMessage.success('已添加书签')
  await loadBookmarks()
  return true
}

// 选中文字后浮出「加书签」按钮
const selectionButton = ref<{ top: number; left: number } | null>(null)
const getContentSelection = () => {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) return
  const range = selection.getRangeAt(0)
  if (!content.value?.contains(range.commonAncestorContainer)) return
  if (!selection.toString().trim()) return
  return { selection, range }
}
const onSelectionChange = () => {
  const selected = getContentSelection()
  if (!selected) {
    selectionButton.value = null
    return
  }
  const rect = selected.range.getBoundingClientRect()
  selectionButton.value = {
    top: Math.max(rect.top - 44, 8),
    left: Math.min(
      Math.max(rect.left + rect.width / 2 - 36, 8),
      window.innerWidth - 80,
    ),
  }
}
const addSelectionBookmark = async () => {
  const selected = getContentSelection()
  if (!selected) return
  const { selection, range } = selected
  const start = range.startContainer
  const element = (
    start.nodeType === Node.ELEMENT_NODE ? start : start.parentElement
  ) as Element | null
  const chapterElement = element?.closest('[chapterindex]')
  if (!chapterElement) return
  const index = Number(chapterElement.getAttribute('chapterindex'))
  const chapterContent = findChapterContent(index)
  const pos = chapterContent?.positionOf(start, range.startOffset) ?? 0
  const bookText = selection.toString().trim()
  selectionButton.value = null
  const saved = await saveNewBookmark({
    bookName: store.readingBook.name,
    bookAuthor: store.readingBook.author ?? '',
    chapterIndex: index,
    chapterPos: pos,
    chapterName: catalog.value[index]?.title ?? '',
    bookText,
  })
  if (saved) window.getSelection()?.removeAllRanges()
}

// 给当前阅读位置加书签，只记录所在段落的第一句
const addCurrentBookmark = async () => {
  const index = chapterIndex.value
  const chapterContent = findChapterContent(index)
  const paragraph = chapterContent?.paragraphAt(chapterPos.value)
  if (!paragraph || !paragraph.text) {
    ElMessage.warning('没有找到当前阅读位置')
    return
  }
  bookmarkVisible.value = false
  await saveNewBookmark({
    bookName: store.readingBook.name,
    bookAuthor: store.readingBook.author ?? '',
    chapterIndex: index,
    chapterPos: paragraph.start,
    chapterName: catalog.value[index]?.title ?? '',
    bookText: firstSentence(paragraph.text),
  })
}

// 书签编辑、删除（面板和浮窗共用）
const bookmarkEditor = ref<InstanceType<typeof BookmarkEditor>>()
const editBookmark = (bookmark: Bookmark) => {
  bookmarkPopover.value = null
  bookmarkVisible.value = false
  bookmarkEditor.value?.edit(bookmark)
}
const removeBookmark = (bookmark: Bookmark) => {
  bookmarkPopover.value = null
  bookmarkEditor.value?.remove(bookmark)
}

// 单击正文书签文字显示浮窗；点关闭按钮、空白处或滚动页面时关闭
const bookmarkPopover = ref<{
  bookmarks: Bookmark[]
  x: number
  lineTop: number
  lineBottom: number
} | null>(null)
const onContentClick = (event: MouseEvent) => {
  const mark = (event.target as Element | null)?.closest?.('.bookmark-mark')
  if (!mark) return
  // 正在选中文字时不弹出，避免与「选中文字加书签」冲突
  const selection = window.getSelection()
  if (selection && !selection.isCollapsed && selection.toString().trim()) return
  const chapterElement = mark.closest('[chapterindex]')
  const caret = caretFromPoint(event.clientX, event.clientY)
  if (!chapterElement || !caret) return
  const index = Number(chapterElement.getAttribute('chapterindex'))
  const list: Bookmark[] =
    findChapterContent(index)?.bookmarksAt(caret.node, caret.offset) ?? []
  if (list.length === 0) return
  event.stopPropagation()
  // 书签跨行时取被点击的那一行
  const rects = Array.from(mark.getClientRects())
  const rect =
    rects.find(r => event.clientY >= r.top && event.clientY <= r.bottom) ??
    mark.getBoundingClientRect()
  bookmarkPopover.value = {
    bookmarks: list,
    x: event.clientX,
    lineTop: rect.top,
    lineBottom: rect.bottom,
  }
}
const onWrapperClick = () => {
  if (bookmarkPopover.value) {
    bookmarkPopover.value = null
    return
  }
  showToolBar.value = !showToolBar.value
}
const closeBookmarkPopover = () => {
  bookmarkPopover.value = null
}
const onEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeBookmarkPopover()
}

// 停止滚动 2 秒后保存进度；持续滚动时最多 10 秒保存一次
const saveBookProgressDebounced = useDebounceFn(
  () => store.saveBookProgress(),
  2000,
  { maxWait: 10000 },
)

/**
 * 用户是否在网页上操作过（滚动、按键、点击）。只有用户操作带来的进度变化才同步给手机；
 * 打开书籍、采用手机进度等程序定位前会重置，避免把网页的旧进度发给手机
 */
let userInput = false
const onUserInput = () => {
  userInput = true
}
const USER_INPUT_EVENTS = ['wheel', 'touchmove', 'keydown', 'mousedown'] as const

const onReadedLengthChange = (index: number, pos: number) => {
  saveReadingBookProgressToBrowser(index, pos)
  saveBookProgressDebounced()
}

// 文档标题
watchEffect(() => {
  document.title = catalog.value[chapterIndex.value]?.title || document.title
})

// 阅读记录保存浏览器
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  // 保存pinia
  chapterIndex.value = index
  chapterPos.value = pos
  if (userInput) store.markProgressDirty()
}

// 进度同步
// 返回导航变化 同步请求会在获取书架前完成

/**
 * VisibilityChange https://developer.mozilla.org/zh-CN/docs/Web/API/Document/visibilitychange_event
 * 监听关闭页面 切换tab 返回桌面 等操作
 * 注意不用监听点击链接导航变化 不对Safari<14.5兼容处理
 **/
const onVisibilityChange = () => {
  const _bookProgress = bookProgress.value
  if (document.visibilityState == 'hidden' && _bookProgress) {
    // 只在网页上有未发送的阅读进度时才会真正发送
    store.saveBookProgress()
  } else if (document.visibilityState == 'visible') {
    checkPhoneProgress()
  }
}

/** 从手机读取这本书的当前进度 */
const fetchPhoneProgress = async (bookUrl: string) => {
  const res = await API.getBookShelf()
  if (!res.data.isSuccess) return
  return res.data.data.find(book => book.bookUrl === bookUrl)
}

/** 采用手机进度：程序定位，不会再发回手机 */
const adoptPhoneProgress = (index: number, pos: number) => {
  userInput = false
  store.setContentLoading(true)
  getContent(index, true, pos)
  store.setSyncedProgress(store.readingBook.bookUrl, index, pos)
}

/**
 * 检查手机端进度：手机进度与上次同步的不一致，说明在手机上读过，提示是否跳转。
 * 不比较时间，避免电脑与手机时钟不一致导致误判
 */
let lastPhoneCheckTime = 0
/** 已经提示过的手机进度，关闭提示后同一进度不再重复提示 */
let promptedPhoneProgress = ''
let phoneProgressNotice: { close: () => void } | null = null
const checkPhoneProgress = async () => {
  const now = Date.now()
  if (now - lastPhoneCheckTime < 3000) return
  lastPhoneCheckTime = now
  const { bookUrl } = store.readingBook
  try {
    const phone = await fetchPhoneProgress(bookUrl)
    const synced = store.syncedProgress
    if (!phone || !synced || synced.bookUrl !== bookUrl) return
    if (
      phone.durChapterIndex === synced.chapterIndex &&
      phone.durChapterPos === synced.chapterPos
    ) {
      return
    }
    const key = `${phone.durChapterIndex}:${phone.durChapterPos}`
    if (key === promptedPhoneProgress) return
    if (typeof catalog.value[phone.durChapterIndex] === 'undefined') return
    promptedPhoneProgress = key
    phoneProgressNotice?.close()
    const title =
      phone.durChapterTitle || catalog.value[phone.durChapterIndex].title
    phoneProgressNotice = ElNotification({
      title: '手机端进度有更新',
      message: `手机读到了「${title}」，点击跳转`,
      position: 'top-right',
      duration: 0,
      customClass: 'phone-progress-notice',
      onClick: () => {
        phoneProgressNotice?.close()
        adoptPhoneProgress(phone.durChapterIndex, phone.durChapterPos)
      },
    })
  } catch (e) {
    console.warn('[BookChapter]: 获取手机端进度失败', e)
  }
}
const onWindowFocus = () => {
  if (document.visibilityState == 'visible') checkPhoneProgress()
}
// 页面保持可见时（如放在副屏），每 30 秒检查一次手机进度
let phoneProgressTimer = 0
// 定时同步

// 章节切换
const toNextChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value + 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: '下一章',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: '本章是最后一章',
      type: 'error',
    })
  }
}
const toPreChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value - 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: '上一章',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: '本章是第一章',
      type: 'error',
    })
  }
}

// 翻页：空格、上下键、PageUp/PageDown 翻一屏（保留约两行重叠），翻到章首章末继续翻会自动换章；左右键切换章节
/** 翻页时保留的重叠高度，方便衔接上一屏 */
const PAGE_OVERLAP = 100
type LineBox = { top: number; bottom: number; left: number; right: number }

/** 收集正文中当前视口附近的文字行（视口坐标），按从上到下排序 */
const collectLines = (): LineBox[] => {
  const viewHeight = document.documentElement.clientHeight
  const lines: LineBox[] = []
  const blocks = content.value?.querySelectorAll('.title, [data-chapterpos] p') ?? []
  const range = document.createRange()
  for (const block of blocks) {
    const blockRect = block.getBoundingClientRect()
    if (blockRect.bottom < -viewHeight || blockRect.top > viewHeight * 2) continue
    range.selectNodeContents(block)
    for (const rect of range.getClientRects()) {
      if (rect.width === 0 || rect.height === 0) continue
      const line = lines.find(
        l => Math.abs(l.top - rect.top) < rect.height / 2 && l.bottom > rect.top,
      )
      if (line) {
        line.top = Math.min(line.top, rect.top)
        line.bottom = Math.max(line.bottom, rect.bottom)
        line.left = Math.min(line.left, rect.left)
        line.right = Math.max(line.right, rect.right)
      } else {
        lines.push({ top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right })
      }
    }
  }
  return lines.sort((a, b) => a.top - b.top)
}

// 翻页定位标记：往后翻标出接下来要读的行（行首左侧，5 秒后淡出）；
// 往前翻标出读到哪一行就能接上后文（行尾右侧，保留到下次翻页）
const pageMarker = ref<{
  top: number
  left: number
  height: number
  fading: boolean
} | null>(null)
let pageMarkerTimers: number[] = []
const clearPageMarker = () => {
  pageMarkerTimers.forEach(clearTimeout)
  pageMarkerTimers = []
  pageMarker.value = null
}
/** @param line 视口坐标下的行，按当前滚动位置换算成页面坐标 */
const showPageMarker = (line: LineBox, side: 'left' | 'right') => {
  clearPageMarker()
  const height = (line.bottom - line.top) * 0.6
  pageMarker.value = {
    top: line.top + window.scrollY + (line.bottom - line.top - height) / 2,
    left: side === 'left' ? line.left + window.scrollX - 10 : line.right + window.scrollX + 6,
    height,
    fading: false,
  }
  if (side === 'left') {
    pageMarkerTimers.push(
      window.setTimeout(() => {
        if (pageMarker.value) pageMarker.value.fading = true
      }, 5000),
      window.setTimeout(clearPageMarker, 5800),
    )
  }
}

const isAtPageBottom = () => {
  const root = document.documentElement
  return root.clientHeight + root.scrollTop >= root.scrollHeight - 2
}
const isAtPageTop = () => document.documentElement.scrollTop <= 1

/** 往前翻进入上一章后停在章末 */
let pendingChapterEnd = false
/** 按切换章节的方式重新加载某一章（标题在顶部）；atEnd 为 true 时停在章末 */
const switchToChapter = (index: number, atEnd: boolean) => {
  ElMessage({ message: catalog.value[index].title, duration: 1500 })
  store.setContentLoading(true)
  pendingChapterEnd = atEnd
  getContent(index)
  store.saveBookProgress()
}
const autoSwitchChapter = (forward: boolean) => {
  const loaded = chapterData.value
  if (loaded.length === 0) return
  const index = forward ? loaded[loaded.length - 1].index + 1 : loaded[0].index - 1
  if (typeof catalog.value[index] === 'undefined') {
    ElMessage.warning(forward ? '已经是最后一章' : '已经是第一章')
    return
  }
  switchToChapter(index, !forward)
}

const scrollPage = (delta: number) => {
  // 按当前页面高度截住滚动距离：翻页动画中无限加载接上新章节时，不会滚进新章节
  const root = document.documentElement
  const maxScroll = root.scrollHeight - root.clientHeight
  delta = Math.max(-root.scrollTop, Math.min(delta, maxScroll - root.scrollTop))
  canJump = false
  jump(delta, {
    duration: store.config.jumpDuration,
    callback: () => (canJump = true),
  })
}

/**
 * 翻页。开启无限加载时章节首尾相接，跨章时按章节边界分页，保证新章节标题落在屏幕顶部：
 * - 往后翻：下一屏会出现新章节标题时，先停在本章末尾；本章已读完则把新标题滚到顶部
 * - 往前翻：先停在本章标题处；已在本章开头则退到上一章末尾
 */
const turnPage = (forward: boolean) => {
  const viewHeight = document.documentElement.clientHeight
  const pageStep = viewHeight - PAGE_OVERLAP
  /** 章节标题在屏幕上的位置，与刚加载一章时一致（顶部留白下方） */
  const titleTop = (top.value as HTMLElement | undefined)?.offsetHeight ?? 0
  const titleElements = Array.from(
    content.value?.querySelectorAll('.title') ?? [],
  ) as HTMLElement[]
  const titles = titleElements.map(el => el.getBoundingClientRect().top)
  if (forward && isAtPageBottom()) {
    // 已到页面底部：若屏幕上还有未置顶的新章节标题（无限加载接上的短章节），按切章方式加载它
    const pendingTitle = titleElements.find((_, i) => titles[i] > titleTop + 1)
    const pendingIndex = Number(
      pendingTitle?.closest('[chapterindex]')?.getAttribute('chapterindex'),
    )
    if (pendingTitle && !Number.isNaN(pendingIndex)) switchToChapter(pendingIndex, false)
    else autoSwitchChapter(true)
    return
  }
  const lines = collectLines()

  if (forward) {
    const lastFull = [...lines].reverse().find(l => l.top >= 0 && l.bottom <= viewHeight + 0.5)
    const next = lastFull ? lines.find(l => l.top >= lastFull.bottom - 1) : undefined
    const nextTitle = titles.find(t => t > titleTop + 1)
    if (nextTitle !== undefined && nextTitle <= viewHeight + pageStep) {
      // 本章还有没读到的行：完整位于新标题之上、且需要继续往下滚才能看到
      const unread =
        next !== undefined && next.bottom <= nextTitle + 2 && nextTitle - viewHeight >= 1
      if (unread) {
        // 先把本章剩余内容翻完，屏幕底部停在本章末尾
        showPageMarker(next, 'left')
        scrollPage(nextTitle - viewHeight)
      } else {
        clearPageMarker()
        const root = document.documentElement
        const maxScroll = root.scrollHeight - root.clientHeight
        if (root.scrollTop + nextTitle - titleTop > maxScroll + 1) {
          // 新章节太短，滚不到顶部：按切章方式加载它
          const index = Number(
            titleElements
              .find((_, i) => titles[i] === nextTitle)
              ?.closest('[chapterindex]')
              ?.getAttribute('chapterindex'),
          )
          if (!Number.isNaN(index)) {
            switchToChapter(index, false)
            return
          }
        }
        scrollPage(nextTitle - titleTop)
      }
      return
    }
    if (next) showPageMarker(next, 'left')
    else clearPageMarker()
    scrollPage(pageStep)
    return
  }

  const firstFull = lines.find(l => l.top >= -0.5 && l.bottom <= viewHeight)
  const prev = firstFull ? [...lines].reverse().find(l => l.bottom <= firstFull.top + 1) : undefined
  const currentIndex = titles.reduce((found, t, i) => (t <= titleTop + 1 ? i : found), -1)
  if (currentIndex >= 0) {
    const current = titles[currentIndex]
    if (current < titleTop - 1 && current > titleTop - pageStep) {
      // 本章开头在上一屏以内：先停在本章标题处
      if (prev) showPageMarker(prev, 'right')
      else clearPageMarker()
      scrollPage(current - titleTop)
      return
    }
    if (Math.abs(current - titleTop) <= 1 && currentIndex > 0) {
      // 已在本章开头，上一章已加载：退到上一章末尾，并标出其最后一行
      const lastOfPrevious = [...lines].reverse().find(l => l.bottom <= current + 1)
      if (lastOfPrevious) showPageMarker(lastOfPrevious, 'right')
      else clearPageMarker()
      scrollPage(current - viewHeight)
      return
    }
  }
  if (isAtPageTop()) {
    autoSwitchChapter(false)
    return
  }
  if (prev) showPageMarker(prev, 'right')
  else clearPageMarker()
  scrollPage(-pageStep)
}

/** 正在输入或有弹窗时不响应翻页按键 */
const shouldIgnoreKey = (event: KeyboardEvent) => {
  const target = event.target as HTMLElement | null
  if (target?.closest?.('input, textarea, select, [contenteditable="true"]')) return true
  return [...document.querySelectorAll('.el-overlay')].some(
    el => (el as HTMLElement).style.display !== 'none',
  )
}

let canJump = true
// 监听按键
const handleKeyPress = (event: KeyboardEvent) => {
  if (!canJump || shouldIgnoreKey(event)) return
  switch (event.key) {
    case 'ArrowLeft':
      event.stopPropagation()
      event.preventDefault()
      toPreChapter()
      break
    case 'ArrowRight':
      event.stopPropagation()
      event.preventDefault()
      toNextChapter()
      break
    case 'ArrowUp':
    case 'PageUp':
      event.stopPropagation()
      event.preventDefault()
      turnPage(false)
      break
    case 'ArrowDown':
    case 'PageDown':
      event.stopPropagation()
      event.preventDefault()
      turnPage(true)
      break
    case ' ':
      event.stopPropagation()
      event.preventDefault()
      turnPage(!event.shiftKey)
      break
  }
}

// 阻止浏览器默认的按键滚动，统一由 handleKeyPress 翻页
const PAGE_KEYS = ['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', ' ']
const ignoreKeyPress = (event: KeyboardEvent) => {
  if (PAGE_KEYS.includes(event.key) && !shouldIgnoreKey(event)) {
    event.preventDefault()
    event.stopPropagation()
  }
}

onMounted(async () => {
  await store.loadWebConfig()
  //获取书籍数据
  const bookUrl = sessionStorage.getItem('bookUrl')
  const name = sessionStorage.getItem('bookName')
  const author = sessionStorage.getItem('bookAuthor')
  let chapterIndex = Number(sessionStorage.getItem('chapterIndex') || 0)
  let chapterPos = Number(sessionStorage.getItem('chapterPos') || 0)
  const isSeachBook = sessionStorage.getItem('isSeachBook') === 'true'
  if (isNullOrBlank(bookUrl) || isNullOrBlank(name) || author === null) {
    ElMessage.warning('书籍信息为空，即将自动返回书架页面...')
    return setTimeout(toShelf, 500)
  }
  // 以手机端的当前进度为准（刷新页面、从「最近阅读」打开时，浏览器里存的可能是旧进度）
  try {
    const phone = await fetchPhoneProgress(bookUrl as string)
    if (phone) {
      chapterIndex = phone.durChapterIndex
      chapterPos = phone.durChapterPos
    }
  } catch (e) {
    console.warn('[BookChapter]: 获取手机端进度失败，使用浏览器记录的进度', e)
  }
  const book: typeof store.readingBook = {
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    bookUrl,
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    name,
    author,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  onResize()
  window.addEventListener('resize', onResize)
  loadingWrapper(
    store.loadWebCatalog(book).then(chapters => {
      if (typeof chapters[chapterIndex] === 'undefined') {
        chapterIndex = 0
        chapterPos = 0
        book.chapterIndex = 0
        book.chapterPos = 0
      }
      store.setReadingBook(book)
      store.setSyncedProgress(book.bookUrl, chapterIndex, chapterPos)
      getContent(chapterIndex, true, chapterPos)
      USER_INPUT_EVENTS.forEach(name =>
        window.addEventListener(name, onUserInput, { passive: true, capture: true }),
      )
      phoneProgressTimer = window.setInterval(() => {
        if (document.visibilityState == 'visible') checkPhoneProgress()
      }, 30000)
      loadBookmarks()
      document.addEventListener('selectionchange', onSelectionChange)
      window.addEventListener('scroll', closeBookmarkPopover, { passive: true })
      window.addEventListener('keydown', onEscape)
      window.addEventListener('keyup', handleKeyPress)
      window.addEventListener('keydown', ignoreKeyPress)
      window.addEventListener('focus', onWindowFocus)
      // 兼容Safari < 14
      document.addEventListener('visibilitychange', onVisibilityChange)
      //监听底部加载
      scrollObserver = new IntersectionObserver(onReachBottom, {
        rootMargin: '-100% 0% 20% 0%',
      })
      if (infiniteLoading.value === true) scrollObserver.observe(loading.value)
      //第二次点击同一本书 页面标题不会变化
      document.title = '...'
      document.title = (name as string) + ' | ' + chapters[chapterIndex].title
    }),
  )
})

onUnmounted(() => {
  window.removeEventListener('keyup', handleKeyPress)
  window.removeEventListener('keydown', ignoreKeyPress)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('focus', onWindowFocus)
  USER_INPUT_EVENTS.forEach(name =>
    window.removeEventListener(name, onUserInput, { capture: true }),
  )
  clearInterval(phoneProgressTimer)
  clearPageMarker()
  phoneProgressNotice?.close()
  // 兼容Safari < 14
  document.removeEventListener('visibilitychange', onVisibilityChange)
  document.removeEventListener('selectionchange', onSelectionChange)
  window.removeEventListener('scroll', closeBookmarkPopover)
  window.removeEventListener('keydown', onEscape)
  bookmarkVisible.value = false
  readSettingsVisible.value = false
  popCataVisible.value = false
  scrollObserver?.disconnect()
  scrollObserver = null
})

const addToBookShelfConfirm = async () => {
  const book = store.readingBook
  // 阅读的是搜索的书籍 并未在书架
  if (book.isSeachBook === true) {
    await ElMessageBox.confirm(`是否将《${book.name}》放入书架？`, '放入书架', {
      confirmButtonText: '确认',
      cancelButtonText: '否',
      type: 'info',
      /*
        ElMessageBox.confirm默认在触发hashChange事件时自动关闭
        按下物理返回键时触发hashChange事件
        使用router.push("/")则不会触发hashChange事件
        */
      closeOnHashChange: false,
    })
      .then(() => {
        //选择是，无动作
        isSeachBook.value = false
      })
      .catch(async () => {
        //选择否，删除书籍
        await API.deleteBook(book)
      })
      .finally(() => sessionStorage.removeItem('isSeachBook'))
  }
}
onBeforeRouteLeave(async (to, from, next) => {
  console.log('onBeforeRouteLeave')
  // 弹窗时停止响应按键翻页
  window.removeEventListener('keyup', handleKeyPress)
  await addToBookShelfConfirm()
  next()
})
</script>

<style lang="scss" scoped>
:deep(.pop-setting) {
  margin-left: 68px;
  top: 0;
}

:deep(.pop-cata) {
  margin-left: 10px;
}

.chapter-wrapper {
  padding: 0 4%;

  overflow-x: hidden;

  :deep(.no-point) {
    pointer-events: none;
  }

  .tool-bar {
    position: fixed;
    top: 0;
    left: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 58px;
        height: 48px;
        text-align: center;
        padding-top: 12px;
        cursor: pointer;
        outline: none;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }

        .icon-text {
          font-size: 12px;
        }
      }
    }
  }

  .read-bar {
    position: fixed;
    bottom: 0;
    right: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 42px;
        height: 31px;
        padding-top: 12px;
        text-align: center;
        align-items: center;
        cursor: pointer;
        outline: none;
        margin-top: -1px;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }
      }
    }
  }

  .page-marker {
    position: absolute;
    z-index: 50;
    width: 3px;
    border-radius: 2px;
    background: #ed4259;
    opacity: 0.7;
    pointer-events: none;
    transition: opacity 0.8s;

    &.fading {
      opacity: 0;
    }
  }

  .selection-bookmark {
    position: fixed;
    z-index: 200;
    padding: 6px 14px;
    border-radius: 16px;
    background: #ed4259;
    color: #fff;
    font-size: 13px;
    line-height: 20px;
    cursor: pointer;
    user-select: none;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .chapter {
    font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
      'Helvetica Neue Light', sans-serif;
    text-align: left;
    padding: 0 65px;
    min-height: 100vh;
    width: 670px;
    margin: 0 auto;

    .content {
      font-size: 18px;
      line-height: 1.8;
      font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
        'Helvetica Neue Light', sans-serif;

      .bottom-bar,
      .top-bar {
        height: 64px;
      }
    }
  }
}

.day {
  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.12),
      0 0 6px rgba(0, 0, 0, 0.04);
  }

  :deep(.tool-icon) {
    border: 1px solid rgba(0, 0, 0, 0.1);
    margin-top: -1px;
    color: #000;

    .icon-text {
      color: rgba(0, 0, 0, 0.4);
    }
  }

  :deep(.chapter) {
    border: 1px solid #d8d8d8;
    color: #262626;
  }
}

.night {
  :deep(.bookmark-popover) {
    color: #aaa;
  }

  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.48),
      0 0 6px rgba(0, 0, 0, 0.16);
  }

  :deep(.tool-icon) {
    border: 1px solid #444;
    margin-top: -1px;
    color: #666;

    .icon-text {
      color: #666;
    }
  }

  :deep(.chapter) {
    border: 1px solid #444;
    color: #666;
  }

  :deep(.popper__arrow) {
    background: #666;
  }
}

@media screen and (max-width: 776px) {
  .chapter-wrapper {
    padding: 0;

    .tool-bar {
      left: 0;
      width: 100vw;
      margin-left: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;

        .tool-icon {
          border: none;
        }
      }
    }

    .read-bar {
      right: 0;
      width: 100vw;
      margin-right: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;
        padding: 0 15px;

        .tool-icon {
          border: none;
          width: auto;

          .iconfont {
            display: inline-block;
          }
        }
      }
    }

    .chapter {
      width: 100vw !important;
      padding: 0 20px;
      box-sizing: border-box;
    }
  }
}
</style>
