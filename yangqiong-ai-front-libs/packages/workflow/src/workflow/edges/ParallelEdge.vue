<script setup lang="ts">
import { computed } from 'vue'
import { getBezierPath, getSmoothStepPath } from '@vue-flow/core'
import { useEdgeStyle } from '../utils/edge-style'

const props = defineProps<{
  id: string
  sourceX: number
  sourceY: number
  targetX: number
  targetY: number
  sourcePosition: any
  targetPosition: any
  data?: Record<string, any>
  markerEnd?: string
  style?: Record<string, any>
}>()

// 响应式计算路径，节点拖动时连线实时跟随；样式按工具栏连线选项切换（默认平滑曲线）
const edgeStyle = useEdgeStyle()

const path = computed(() => {
  const params = {
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition,
  }
  return edgeStyle.value === 'rounded'
    ? getSmoothStepPath({ ...params, borderRadius: 16 })
    : getBezierPath(params)
})
</script>

<template>
  <g>
    <path
      :id="id"
      :d="path[0]"
      class="wf-edge wf-edge--parallel"
      :marker-end="markerEnd"
      :style="style"
      fill="none"
      stroke="#10b981"
      stroke-width="2"
      stroke-dasharray="10,5"
    />
  </g>
</template>

<style scoped>
.wf-edge--parallel:hover {
  stroke: #059669;
  stroke-width: 3;
}
</style>
