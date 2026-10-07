<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
    approvalConfig?: {
      reason?: string
      options?: string[]
      inputFields?: string[]
      timeoutSeconds?: number
      rejectBehavior?: string
    }
  }
}>()

function rejectLabel(behavior?: string): string {
  const labels: Record<string, string> = {
    FAIL: '失败',
    SKIP: '跳过',
    RETRY: '重试',
  }
  return labels[behavior || 'FAIL'] || '失败'
}
</script>

<template>
  <div class="wf-node wf-node--approval">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">✋</span>
      <span class="wf-node__title">{{ data?.name || '审批' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.approvalConfig?.reason" class="wf-node__field">
        <span class="wf-node__label">原因:</span>
        <span class="wf-node__value">{{ data.approvalConfig.reason.length > 18 ? data.approvalConfig.reason.substring(0, 18) + '...' : data.approvalConfig.reason }}</span>
      </div>
      <div v-if="data?.approvalConfig?.options?.length" class="wf-node__field">
        <span class="wf-node__label">选项:</span>
        <span class="wf-node__value">{{ data.approvalConfig.options.length }} 个</span>
      </div>
      <div v-if="data?.approvalConfig?.inputFields?.length" class="wf-node__field">
        <span class="wf-node__label">字段:</span>
        <span class="wf-node__value">{{ data.approvalConfig.inputFields.length }} 个</span>
      </div>
      <div v-if="data?.approvalConfig" class="wf-node__field">
        <span class="wf-node__label">超时:</span>
        <span class="wf-node__value">{{ data.approvalConfig.timeoutSeconds || 300 }}s</span>
      </div>
      <div v-if="data?.approvalConfig" class="wf-node__field">
        <span class="wf-node__label">拒绝:</span>
        <span class="wf-node__value">{{ rejectLabel(data.approvalConfig.rejectBehavior) }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped>
.wf-node--approval {
  --node-color: #f59e0b;
}
</style>
