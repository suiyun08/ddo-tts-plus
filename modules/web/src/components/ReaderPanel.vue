<template>
  <transition name="reader-panel">
    <div
      v-if="visible"
      class="reader-panel-mask"
      @click.stop="emit('close')"
      @wheel.stop
      @touchmove.stop
    >
      <div
        class="reader-panel"
        :class="{ night: isNight }"
        :style="{ background, width }"
        @click.stop
      >
        <div class="reader-panel-header">
          <span class="reader-panel-title">{{ title }}</span>
          <div class="reader-panel-extra"><slot name="extra" /></div>
          <button class="reader-panel-close" title="关闭（Esc）" @click="emit('close')">
            ×
          </button>
        </div>
        <div class="reader-panel-body"><slot /></div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
/**
 * 阅读页的浮窗（目录、书签、设置）：屏幕居中的圆角卡片，背后有浅色遮罩。
 * 高度随内容伸缩，最高到窗口高度减去上下留白，超出时在卡片内滚动
 */
defineProps<{
  visible: boolean
  title: string
  background: string
  width: string
  isNight: boolean
}>()
const emit = defineEmits<{ close: [] }>()
</script>

<style lang="scss" scoped>
.reader-panel-mask {
  position: fixed;
  inset: 0;
  /* 低于左侧工具栏（100 以上），浮窗打开时仍可直接点其他按钮切换 */
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.18);
}

.reader-panel {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 80px);
  border-radius: 16px;
  overflow: hidden;
  color: #262626;
  box-shadow:
    0 18px 48px rgba(0, 0, 0, 0.22),
    0 4px 12px rgba(0, 0, 0, 0.1);

  &.night {
    color: #aaa;
    box-shadow:
      0 18px 48px rgba(0, 0, 0, 0.6),
      0 4px 12px rgba(0, 0, 0, 0.4);
  }
}

.reader-panel-header {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 16px 12px 24px;

  .reader-panel-title {
    font-size: 18px;
    font-weight: 600;
  }

  .reader-panel-extra {
    flex: 1;
    display: flex;
    justify-content: flex-end;
  }

  .reader-panel-close {
    width: 32px;
    height: 32px;
    border: none;
    border-radius: 50%;
    background: rgba(128, 128, 128, 0.12);
    color: inherit;
    font-size: 20px;
    line-height: 32px;
    cursor: pointer;

    &:hover {
      background: rgba(128, 128, 128, 0.22);
    }
  }
}

.reader-panel-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 24px 20px;
}

.reader-panel-enter-active,
.reader-panel-leave-active {
  transition: opacity 0.18s;

  .reader-panel {
    transition: transform 0.18s;
  }
}

.reader-panel-enter-from,
.reader-panel-leave-to {
  opacity: 0;

  .reader-panel {
    transform: scale(0.97);
  }
}
</style>
