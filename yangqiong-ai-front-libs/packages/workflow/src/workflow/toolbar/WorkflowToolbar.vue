<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowDefinition } from '../types/workflow'
import { validateDefinition } from '../utils/validator'
import { useEdgeStyle } from '../utils/edge-style'

const props = defineProps<{
  definition: WorkflowDefinition
  canUndo?: boolean
  canRedo?: boolean
  executing?: boolean
  /** 执行已暂停（控制暂停/继续按钮切换） */
  paused?: boolean
  /** 是否启用企业版专属能力（暂停/继续），社区版传 false 时置灰 */
  enterpriseFeatures?: boolean
}>()

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'load'): void
  (e: 'validate', result: { valid: boolean; errors: string[] }): void
  (e: 'export'): void
  (e: 'execute'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'beautify'): void
  (e: 'pause'): void
  (e: 'resume'): void
  (e: 'cancel'): void
}>()

function handleSave() {
  emit('save')
}

function handleLoad() {
  emit('load')
}

function handleValidate() {
  const result = validateDefinition(props.definition)
  emit('validate', result)
}

function handleExport() {
  emit('export')
}

function handleExecute() {
  emit('execute')
}

// 连线样式偏好（编辑器内共享，默认保持平滑曲线）
const edgeStyle = useEdgeStyle()

// 暂停/继续为企业版专属能力，社区版置灰不可用
const pauseEnabled = computed(() => props.enterpriseFeatures !== false)
</script>

<template>
  <div class="wf-toolbar">
    <div class="wf-toolbar__group">
      <button class="wf-toolbar__btn" :disabled="!canUndo" title="撤销 (Ctrl+Z)" @click="emit('undo')">
        <span class="wf-toolbar__icon">↶</span>
        <span class="wf-toolbar__text">撤销</span>
      </button>
      <button class="wf-toolbar__btn" :disabled="!canRedo" title="重做 (Ctrl+Y)" @click="emit('redo')">
        <span class="wf-toolbar__icon">↷</span>
        <span class="wf-toolbar__text">重做</span>
      </button>
      <button class="wf-toolbar__btn" title="加载工作流" @click="handleLoad">
        <span class="wf-toolbar__icon">📂</span>
        <span class="wf-toolbar__text">加载</span>
      </button>
      <button class="wf-toolbar__btn wf-toolbar__btn--primary" title="保存工作流" @click="handleSave">
        <span class="wf-toolbar__icon">💾</span>
        <span class="wf-toolbar__text">保存</span>
      </button>
    </div>
    <div class="wf-toolbar__group">
      <div class="wf-toolbar__seg" title="连线样式">
        <span class="wf-toolbar__seg-label">连线</span>
        <button
          class="wf-toolbar__seg-btn"
          :class="{ 'wf-toolbar__seg-btn--active': edgeStyle === 'curve' }"
          @click="edgeStyle = 'curve'"
        >曲线</button>
        <button
          class="wf-toolbar__seg-btn"
          :class="{ 'wf-toolbar__seg-btn--active': edgeStyle === 'rounded' }"
          @click="edgeStyle = 'rounded'"
        >折线</button>
      </div>
      <button
        class="wf-toolbar__btn wf-toolbar__btn--beautify"
        title="一键美化：自动排列节点布局并优化连线走向"
        @click="emit('beautify')"
      >
        <span class="wf-toolbar__icon">✨</span>
        <span class="wf-toolbar__text">美化</span>
      </button>
      <button class="wf-toolbar__btn" title="校验工作流" @click="handleValidate">
        <span class="wf-toolbar__icon">✅</span>
        <span class="wf-toolbar__text">校验</span>
      </button>
      <button class="wf-toolbar__btn" title="导出 JSON" @click="handleExport">
        <span class="wf-toolbar__icon">📤</span>
        <span class="wf-toolbar__text">导出</span>
      </button>
      <button
        v-if="!props.executing"
        class="wf-toolbar__btn wf-toolbar__btn--execute"
        title="执行工作流"
        @click="handleExecute"
      >
        <span class="wf-toolbar__icon">▶️</span>
        <span class="wf-toolbar__text">执行</span>
      </button>
      <template v-else>
        <button
          v-if="!props.paused"
          class="wf-toolbar__btn wf-toolbar__btn--pause"
          :disabled="!pauseEnabled"
          :title="pauseEnabled ? '在当前节点执行完后暂停' : '暂停为企业版专属功能，当前版本不可用'"
          @click="emit('pause')"
        >
          <span class="wf-toolbar__icon">⏸</span>
          <span class="wf-toolbar__text">暂停</span>
        </button>
        <button
          v-else
          class="wf-toolbar__btn wf-toolbar__btn--pause"
          :disabled="!pauseEnabled"
          :title="pauseEnabled ? '从暂停位置恢复执行' : '继续为企业版专属功能，当前版本不可用'"
          @click="emit('resume')"
        >
          <span class="wf-toolbar__icon">▶</span>
          <span class="wf-toolbar__text">继续</span>
        </button>
        <button
          class="wf-toolbar__btn wf-toolbar__btn--cancel"
          title="终止执行：当前节点执行完后停止，保留已执行节点结果"
          @click="emit('cancel')"
        >
          <span class="wf-toolbar__icon">⏹</span>
          <span class="wf-toolbar__text">终止</span>
        </button>
      </template>
    </div>
    <!-- 工具栏右侧扩展插槽，供上层注入自定义按钮 -->
    <slot name="toolbar-extra"></slot>
  </div>
</template>

<style scoped>
.wf-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  height: 48px;
  box-sizing: border-box;
}
.wf-toolbar__group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wf-toolbar__btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.wf-toolbar__btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}
.wf-toolbar__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.wf-toolbar__btn:disabled:hover {
  background: #fff;
  border-color: #e2e8f0;
}
.wf-toolbar__btn--primary {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}
.wf-toolbar__btn--primary:hover {
  background: #4f46e5;
  border-color: #4f46e5;
}
.wf-toolbar__btn--execute {
  background: #10b981;
  color: #fff;
  border-color: #10b981;
}
.wf-toolbar__btn--execute:hover {
  background: #059669;
  border-color: #059669;
}
.wf-toolbar__btn--pause {
  background: #f59e0b;
  color: #fff;
  border-color: #f59e0b;
}
.wf-toolbar__btn--pause:hover {
  background: #d97706;
  border-color: #d97706;
}
.wf-toolbar__btn--pause:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.wf-toolbar__btn--pause:disabled:hover {
  background: #f59e0b;
  border-color: #f59e0b;
}
.wf-toolbar__btn--cancel {
  background: #ef4444;
  color: #fff;
  border-color: #ef4444;
}
.wf-toolbar__btn--cancel:hover {
  background: #dc2626;
  border-color: #dc2626;
}
.wf-toolbar__btn--beautify {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  color: #fff;
  border-color: #8b5cf6;
}
.wf-toolbar__btn--beautify:hover {
  background: linear-gradient(135deg, #7c3aed, #4f46e5);
  border-color: #7c3aed;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.35);
}
.wf-toolbar__seg {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
.wf-toolbar__seg-label {
  font-size: 12px;
  color: #94a3b8;
  padding: 0 6px;
}
.wf-toolbar__seg-btn {
  padding: 3px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.wf-toolbar__seg-btn:hover {
  color: #1e293b;
}
.wf-toolbar__seg-btn--active {
  background: #fff;
  color: #4f46e5;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}
.wf-toolbar__icon {
  font-size: 14px;
}
.wf-toolbar__text {
  font-size: 13px;
}
</style>
