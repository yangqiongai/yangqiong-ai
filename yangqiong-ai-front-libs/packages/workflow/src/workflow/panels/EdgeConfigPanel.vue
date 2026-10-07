<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { EdgeType } from '../types/workflow'

const props = defineProps<{
  edgeId: string
  edgeType: EdgeType
  sourceId: string
  targetId: string
  conditionExpression?: string
  conditionLabel?: string
  readonly: boolean
}>()

const emit = defineEmits<{
  (e: 'update', data: { type: EdgeType; conditionExpression?: string; conditionLabel?: string }): void
  (e: 'delete'): void
}>()

const formData = reactive({
  type: props.edgeType || 'NORMAL' as EdgeType,
  conditionExpression: props.conditionExpression || '',
  conditionLabel: props.conditionLabel || '',
})

watch(
  () => [props.edgeId, props.edgeType, props.conditionExpression, props.conditionLabel],
  () => {
    formData.type = props.edgeType || 'NORMAL'
    formData.conditionExpression = props.conditionExpression || ''
    formData.conditionLabel = props.conditionLabel || ''
  },
)

function handleUpdate() {
  emit('update', {
    type: formData.type,
    conditionExpression: formData.type === 'CONDITIONAL' ? formData.conditionExpression : undefined,
    conditionLabel: formData.type === 'CONDITIONAL' ? formData.conditionLabel : undefined,
  })
}
</script>

<template>
  <div class="wf-panel wf-panel--edge">
    <div class="wf-panel__header">
      <h3>边配置</h3>
      <button v-if="!readonly" class="wf-panel__delete-btn" title="删除连接线" @click="emit('delete')">🗑️</button>
    </div>
    <div class="wf-panel__body">
      <div class="wf-form-group">
        <label class="wf-form-label">源节点</label>
        <input
          :value="sourceId"
          class="wf-form-input"
          disabled
        />
      </div>
      <div class="wf-form-group">
        <label class="wf-form-label">目标节点</label>
        <input
          :value="targetId"
          class="wf-form-input"
          disabled
        />
      </div>
      <div class="wf-form-group">
        <label class="wf-form-label">边类型</label>
        <select
          v-model="formData.type"
          class="wf-form-select"
          :disabled="readonly"
          @change="handleUpdate"
        >
          <option value="NORMAL">普通边</option>
          <option value="CONDITIONAL">条件边</option>
          <option value="PARALLEL">并行边</option>
        </select>
      </div>
      <template v-if="formData.type === 'CONDITIONAL'">
        <div class="wf-form-group">
          <label class="wf-form-label">条件标签</label>
          <input
            v-model="formData.conditionLabel"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="请输入条件标签"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">条件表达式</label>
          <textarea
            v-model="formData.conditionExpression"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder="请输入条件表达式"
            rows="3"
            @change="handleUpdate"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.wf-panel {
  background: #fff;
  border-left: 1px solid #e2e8f0;
  height: 100%;
  overflow-y: auto;
}
.wf-panel__header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wf-panel__header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.wf-panel__delete-btn {
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  cursor: pointer;
  font-size: 14px;
  padding: 4px 8px;
  line-height: 1;
}
.wf-panel__delete-btn:hover {
  background: #fee2e2;
}
.wf-panel__body {
  padding: 16px;
}
.wf-form-group {
  margin-bottom: 16px;
}
.wf-form-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  margin-bottom: 4px;
}
.wf-form-input,
.wf-form-select,
.wf-form-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  color: #1e293b;
  background: #fff;
  box-sizing: border-box;
}
.wf-form-input:disabled,
.wf-form-select:disabled,
.wf-form-textarea:disabled {
  background: #f8fafc;
  color: #94a3b8;
}
.wf-form-textarea {
  resize: vertical;
  font-family: monospace;
}
</style>
