<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
  }
}>()

const transformTypeLabels: Record<string, string> = {
  SUBSTRING: '截取子串',
  REVERSE: '逆序',
  UPPER: '转大写',
  LOWER: '转小写',
  TRIM: '去空白',
  REPLACE: '替换',
  CONCAT: '拼接',
  TEMPLATE: '模板',
  LENGTH: '取长度',
  MATH: '数学运算',
}
</script>

<template>
  <div class="wf-node wf-node--transform">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">🔧</span>
      <span class="wf-node__title">{{ data?.name || 'Transform' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.transformType" class="wf-node__field">
        <span class="wf-node__label">变换:</span>
        <span class="wf-node__value">{{ transformTypeLabels[data.config.transformType] || data.config.transformType }}</span>
      </div>
      <div v-if="data?.config?.outputVar" class="wf-node__field">
        <span class="wf-node__label">输出变量:</span>
        <span class="wf-node__value">{{ data.config.outputVar }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped>
.wf-node--transform {
  --node-color: #f97316;
}
</style>
