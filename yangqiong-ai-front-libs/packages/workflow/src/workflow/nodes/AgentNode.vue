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
  <div class="wf-node wf-node--agent">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">🤖</span>
      <span class="wf-node__title">{{ data?.name || 'Agent' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.agentCode" class="wf-node__field">
        <span class="wf-node__label">Agent:</span>
        <span class="wf-node__value">{{ data.config.agentCode }}</span>
      </div>
      <div v-if="data?.config?.maxIterations" class="wf-node__field">
        <span class="wf-node__label">最大迭代:</span>
        <span class="wf-node__value">{{ data.config.maxIterations }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped>
.wf-node--agent {
  --node-color: #6366f1;
}
</style>
