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
          <el-button size="small" text @click="openEdit(bookmark)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(bookmark)">
            删除
          </el-button>
        </div>
      </div>
    </div>
    <div class="empty" v-else>
      还没有书签。选中正文文字后点「加书签」，或点上方按钮给当前位置加书签。
    </div>

    <el-dialog
      v-model="editVisible"
      title="编辑书签"
      width="min(480px, 92vw)"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item label="书签原文">
          <el-input
            v-model="editing.bookText"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="editing.content"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import API from '@api'
import settings from '../config/themeConfig'
import type { Bookmark } from '@/book'

defineProps<{
  bookmarks: Bookmark[]
  currentChapterIndex: number
}>()
const emit = defineEmits<{
  jump: [bookmark: Bookmark]
  addCurrent: []
  changed: []
}>()

const store = useBookStore()
const popupTheme = computed(() => ({
  background: settings.themes[store.theme].popup,
}))

const formatTime = (time: number) => {
  const date = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const editVisible = ref(false)
const editing = ref<Bookmark>({} as Bookmark)
const openEdit = (bookmark: Bookmark) => {
  editing.value = { ...bookmark }
  editVisible.value = true
}
const saveEdit = async () => {
  const res = await API.saveBookmark(editing.value)
  if (!res.data.isSuccess) {
    ElMessage.error(res.data.errorMsg)
    return
  }
  editVisible.value = false
  ElMessage.success('已保存')
  emit('changed')
}

const remove = async (bookmark: Bookmark) => {
  try {
    await ElMessageBox.confirm('确定删除这个书签？', '删除书签', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  const res = await API.deleteBookmark(bookmark)
  if (!res.data.isSuccess) {
    ElMessage.error(res.data.errorMsg)
    return
  }
  ElMessage.success('已删除')
  emit('changed')
}
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
