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

const levelLabels: Record<string, string> = {
  INFO: '提示',
  WARN: '警告',
  ERROR: '严重',
}
</script>

<template>
  <div class="wf-node wf-node--notify">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">📢</span>
      <span class="wf-node__title">{{ data?.name || '通知' }}</span>
    </div>
    <div class="wf-node__body">
      <div v-if="data?.config?.channelId" class="wf-node__field">
        <span class="wf-node__label">渠道:</span>
        <span class="wf-node__value">#{{ data.config.channelId }}<template v-if="data?.config?.channelType"> · {{ data.config.channelType }}</template></span>
      </div>
      <div v-if="data?.config?.content" class="wf-node__field">
        <span class="wf-node__label">内容:</span>
        <span class="wf-node__value">{{ data.config.content.length > 18 ? data.config.content.substring(0, 18) + '...' : data.config.content }}</span>
      </div>
      <div class="wf-node__field">
        <span class="wf-node__label">模式:</span>
        <span class="wf-node__value">{{ data?.config?.async ? '异步' : '同步' }}</span>
      </div>
      <div class="wf-node__field">
        <span class="wf-node__label">级别:</span>
        <span class="wf-node__value">{{ levelLabels[data?.config?.level] || '提示' }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<style scoped>
.wf-node--notify {
  --node-color: #06b6d4;
}
</style>
