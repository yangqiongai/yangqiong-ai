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
  label?: string
}>()

// 响应式计算路径与标签，节点拖动/标签编辑时实时跟随；样式按工具栏连线选项切换（默认平滑曲线）
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

const edgeLabel = computed(() => props.data?.conditionLabel || props.label || '')
</script>

<template>
  <g>
    <path
      :id="id"
      :d="path[0]"
      class="wf-edge wf-edge--conditional"
      :marker-end="markerEnd"
      :style="style"
      fill="none"
      stroke="#f59e0b"
      stroke-width="2"
      stroke-dasharray="5,5"
    />
    <text v-if="edgeLabel">
      <textPath
        :href="`#${id}`"
        :startOffset="'50%'"
        text-anchor="middle"
        class="wf-edge__label"
      >
        {{ edgeLabel }}
      </textPath>
    </text>
  </g>
</template>

<style scoped>
.wf-edge--conditional:hover {
  stroke: #d97706;
  stroke-width: 3;
}
.wf-edge__label {
  font-size: 12px;
  fill: #f59e0b;
  background: white;
}
</style>
