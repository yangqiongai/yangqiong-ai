<!--
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { computed, inject, type Ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { WorkflowAgentPreset } from '../types/workflow'

const props = defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
  }
}>()

// 编辑器provide的预设模板索引，按agentCode命中后渲染专属图标/主题色/标签
const presetMap = inject<Ref<Record<string, WorkflowAgentPreset>>>('wfAgentPresets')

const matchedPreset = computed(() => {
  const code = props.data?.config?.agentCode as string | undefined
  if (!code || !presetMap?.value) return undefined
  return presetMap.value[code]
})

const nodeIcon = computed(() => matchedPreset.value?.icon || '🤖')
const presetTag = computed(() => matchedPreset.value?.tag || matchedPreset.value?.name)
</script>

<template>
  <div class="wf-node wf-node--agent" :style="matchedPreset?.color ? { borderLeftColor: matchedPreset.color } : undefined">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">{{ nodeIcon }}</span>
      <span class="wf-node__title">{{ data?.name || 'Agent' }}</span>
      <span
        v-if="presetTag"
        class="wf-node__preset-tag"
        :style="matchedPreset?.color ? { color: matchedPreset.color, borderColor: matchedPreset.color } : undefined"
      >{{ presetTag }}</span>
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
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<style scoped>
.wf-node--agent {
  --node-color: #6366f1;
}
.wf-node__preset-tag {
  margin-left: auto;
  padding: 1px 6px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1.4;
  white-space: nowrap;
}
</style>
