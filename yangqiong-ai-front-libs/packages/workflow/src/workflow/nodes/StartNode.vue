<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
  }
}>()
</script>

<template>
  <div class="wf-node wf-node--start">
    <Handle type="source" :position="Position.Bottom" />
    <div class="wf-start-content">
      <span class="wf-start-icon">▶</span>
      <span class="wf-start-text">{{ data?.name || '开始' }}</span>
    </div>
  </div>
</template>

<style scoped>
.wf-node--start {
  --node-color: #10b981;
  background: #ecfdf5;
  border: 2px solid #10b981;
  border-radius: 24px;
  min-width: unset;
  padding: 0;
  box-shadow: 0 1px 3px rgba(16, 185, 129, 0.2);
}
.wf-start-content {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
}
.wf-start-icon {
  color: #10b981;
  font-size: 14px;
}
.wf-start-text {
  font-size: 13px;
  font-weight: 600;
  color: #065f46;
}
</style>
