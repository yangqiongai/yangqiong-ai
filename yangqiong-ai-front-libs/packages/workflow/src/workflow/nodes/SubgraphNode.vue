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
  <div class="wf-node wf-node--subgraph">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">📦</span>
      <span class="wf-node__title">{{ data?.name || 'Subgraph' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.workflowName" class="wf-node__field">
        <span class="wf-node__label">引用工作流:</span>
        <span class="wf-node__value">{{ data.config.workflowName }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped>
.wf-node--subgraph {
  --node-color: #ec4899;
}
</style>
