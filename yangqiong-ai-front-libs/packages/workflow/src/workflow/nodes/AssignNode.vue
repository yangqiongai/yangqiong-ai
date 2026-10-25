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
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
  }
}>()

function assignmentCount(config: Record<string, any>): number {
  return config?.assignments ? Object.keys(config.assignments).length : 0
}
</script>

<template>
  <div class="wf-node wf-node--assign">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">📝</span>
      <span class="wf-node__title">{{ data?.name || 'Assign' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.assignments" class="wf-node__field">
        <span class="wf-node__label">赋值:</span>
        <span class="wf-node__value">{{ assignmentCount(data.config) }} 个变量</span>
      </div>
      <div v-if="data?.config?.assignments" class="wf-node__assignments">
        <div v-for="(value, key) in data.config.assignments" :key="key" class="wf-node__assign-item">
          <span class="wf-node__assign-key">{{ key }}</span>
          <span class="wf-node__assign-arrow">=</span>
          <span class="wf-node__assign-value">{{ String(value).length > 20 ? String(value).substring(0, 20) + '...' : value }}</span>
        </div>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<style scoped>
.wf-node--assign {
  --node-color: #84cc16;
}
.wf-node__assignments {
  margin-top: 4px;
}
.wf-node__assign-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  margin-bottom: 2px;
}
.wf-node__assign-key {
  color: #6366f1;
  font-weight: 500;
}
.wf-node__assign-arrow {
  color: #94a3b8;
}
.wf-node__assign-value {
  color: #475569;
  font-family: monospace;
}
</style>
