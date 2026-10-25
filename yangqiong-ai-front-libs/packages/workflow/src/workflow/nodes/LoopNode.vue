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
  <div class="wf-node wf-node--loop">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">🔄</span>
      <span class="wf-node__title">{{ data?.name || 'Loop' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.exitCondition" class="wf-node__field">
        <span class="wf-node__label">退出条件:</span>
        <span class="wf-node__value">{{ data.config.exitCondition }}</span>
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
.wf-node--loop {
  --node-color: #8b5cf6;
}
</style>
