<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: {
    name: string
    type: string
    config: Record<string, any>
    timeControlConfig?: {
      timeType?: 'DELAY' | 'COUNTDOWN' | 'SPECIFIC' | 'PERIODIC' | 'CRON'
      delaySeconds?: number
      countdownMinutes?: number
      countdownSeconds?: number
      specificTime?: string
      cronExpression?: string
      periodType?: 'DAILY' | 'WEEKLY' | 'MONTHLY'
      periodTime?: string
      periodWeekdays?: number[]
      periodDayOfMonth?: number
      workdayOnly?: boolean
    }
  }
}>()

const typeLabels: Record<string, string> = {
  DELAY: '延迟执行',
  COUNTDOWN: '倒计时',
  SPECIFIC: '具体时间',
  PERIODIC: '周期性时间',
  CRON: 'Cron 表达式',
}

const weekdayNames: Record<number, string> = {
  1: '一',
  2: '二',
  3: '三',
  4: '四',
  5: '五',
  6: '六',
  7: '日',
}

const modeLabel = computed(() => typeLabels[props.data?.timeControlConfig?.timeType || 'DELAY'] || '延迟执行')

const detailText = computed(() => {
  const cfg = props.data?.timeControlConfig
  if (!cfg) return ''
  switch (cfg.timeType) {
    case 'COUNTDOWN': {
      const minutes = cfg.countdownMinutes || 0
      const seconds = cfg.countdownSeconds || 0
      return `${minutes}分${seconds}秒`
    }
    case 'SPECIFIC':
      return cfg.specificTime || '未设置'
    case 'CRON':
      return cfg.cronExpression || '未设置'
    case 'PERIODIC': {
      const periodLabels: Record<string, string> = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }
      let text = periodLabels[cfg.periodType || 'DAILY'] || '每天'
      if (cfg.periodType === 'WEEKLY' && cfg.periodWeekdays?.length) {
        text += cfg.periodWeekdays.slice().sort().map((d) => weekdayNames[d] || d).join('')
      }
      if (cfg.periodType === 'MONTHLY' && cfg.periodDayOfMonth) {
        text += `${cfg.periodDayOfMonth}日`
      }
      text += ` ${cfg.periodTime || '00:00'}`
      if (cfg.workdayOnly) text += '（仅工作日）'
      return text
    }
    default:
      return `${cfg.delaySeconds || 0}秒`
  }
})
</script>

<template>
  <div class="wf-node wf-node--time-control">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node__header">
      <span class="wf-node__icon">⏰</span>
      <span class="wf-node__title">{{ data?.name || '时间控制' }}</span>
    </div>
    <div class="wf-node__body">
      <div class="wf-node__field">
        <span class="wf-node__label">模式:</span>
        <span class="wf-node__value">{{ modeLabel }}</span>
      </div>
      <div class="wf-node__field">
        <span class="wf-node__label">等待:</span>
        <span class="wf-node__value">{{ detailText || '未设置' }}</span>
      </div>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped>
.wf-node--time-control {
  --node-color: #8b5cf6;
}
</style>
