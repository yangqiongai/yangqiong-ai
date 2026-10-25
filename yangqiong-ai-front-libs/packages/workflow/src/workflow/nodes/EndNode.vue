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
  <div class="wf-node wf-node--end">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-end-content">
      <span class="wf-end-icon">⏹</span>
      <span class="wf-end-text">{{ data?.name || '结束' }}</span>
    </div>
  </div>
</template>

<style scoped>
.wf-node--end {
  --node-color: #ef4444;
  background: #fef2f2;
  border: 2px solid #ef4444;
  border-radius: 24px;
  min-width: unset;
  padding: 0;
  box-shadow: 0 1px 3px rgba(239, 68, 68, 0.2);
}
.wf-end-content {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
}
.wf-end-icon {
  color: #ef4444;
  font-size: 14px;
}
.wf-end-text {
  font-size: 13px;
  font-weight: 600;
  color: #991b1b;
}
</style>
