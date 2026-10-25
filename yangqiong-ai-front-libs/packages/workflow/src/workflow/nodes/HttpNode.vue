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
</script>

<template>
  <div class="wf-node wf-node--http">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">🌐</span>
      <span class="wf-node__title">{{ data?.name || 'HTTP' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.method" class="wf-node__field">
        <span class="wf-node__label">方法:</span>
        <span class="wf-node__value" :class="'wf-node__method--' + data.config.method.toLowerCase()">{{ data.config.method }}</span>
      </div>
      <div v-if="data?.config?.url" class="wf-node__field">
        <span class="wf-node__label">URL:</span>
        <span class="wf-node__value wf-node__value--url">{{ data.config.url.length > 35 ? data.config.url.substring(0, 35) + '...' : data.config.url }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<style scoped>
.wf-node--http {
  --node-color: #06b6d4;
}
.wf-node__value--url {
  font-family: monospace;
  font-size: 10px;
  word-break: break-all;
}
.wf-node__method--get { color: #10b981; font-weight: 600; }
.wf-node__method--post { color: #6366f1; font-weight: 600; }
.wf-node__method--put { color: #f59e0b; font-weight: 600; }
.wf-node__method--delete { color: #ef4444; font-weight: 600; }
</style>
