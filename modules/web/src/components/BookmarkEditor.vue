<template>
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
</template>

<script setup lang="ts">
import API from '@api'
import type { Bookmark } from '@/book'

/** 书签编辑和删除，供书签面板和书签浮窗共用 */
const emit = defineEmits<{
  changed: []
}>()

const editVisible = ref(false)
const editing = ref<Bookmark>({} as Bookmark)

const edit = (bookmark: Bookmark) => {
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

defineExpose({ edit, remove })
</script>
