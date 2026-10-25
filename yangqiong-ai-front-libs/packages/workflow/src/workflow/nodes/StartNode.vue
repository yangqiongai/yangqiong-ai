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
  <div class="wf-node wf-node--start">
    <Handle type="source" :position="Position.Bottom" />
    <Handle type="source" :position="Position.Right" id="right" />
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
