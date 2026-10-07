<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, markRaw, type Component } from 'vue'
import { VueFlow, useVueFlow, type NodeTypesObject, type EdgeTypesObject } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

import AgentNode from './nodes/AgentNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import ParallelNode from './nodes/ParallelNode.vue'
import LoopNode from './nodes/LoopNode.vue'
import SubgraphNode from './nodes/SubgraphNode.vue'
import TransformNode from './nodes/TransformNode.vue'
import ScriptNode from './nodes/ScriptNode.vue'
import HttpNode from './nodes/HttpNode.vue'
import AssignNode from './nodes/AssignNode.vue'
import ApprovalNode from './nodes/ApprovalNode.vue'
import StartNode from './nodes/StartNode.vue'
import EndNode from './nodes/EndNode.vue'
import NormalEdge from './edges/NormalEdge.vue'
import ConditionEdge from './edges/ConditionEdge.vue'
import ParallelEdge from './edges/ParallelEdge.vue'

import type { WorkflowDefinition, WorkflowNodeTraceItem } from './types/workflow'
import { fromWorkflowDefinition } from './utils/schema-adapter'

const props = defineProps<{
  definition: WorkflowDefinition
  trace: WorkflowNodeTraceItem[]
}>()

const nodeTypes: NodeTypesObject = {
  agent: markRaw(AgentNode) as unknown as Component,
  condition: markRaw(ConditionNode) as unknown as Component,
  parallel: markRaw(ParallelNode) as unknown as Component,
  loop: markRaw(LoopNode) as unknown as Component,
  subgraph: markRaw(SubgraphNode) as unknown as Component,
  transform: markRaw(TransformNode) as unknown as Component,
  script: markRaw(ScriptNode) as unknown as Component,
  http: markRaw(HttpNode) as unknown as Component,
  assign: markRaw(AssignNode) as unknown as Component,
  approval: markRaw(ApprovalNode) as unknown as Component,
  start: markRaw(StartNode) as unknown as Component,
  end: markRaw(EndNode) as unknown as Component,
}

const edgeTypes: EdgeTypesObject = {
  normal: markRaw(NormalEdge) as unknown as Component,
  conditional: markRaw(ConditionEdge) as unknown as Component,
  parallel: markRaw(ParallelEdge) as unknown as Component,
}

const initialFlowData = fromWorkflowDefinition(props.definition)

const { onNodeClick, setNodes, setEdges, getNodes, getEdges, fitView } = useVueFlow({
  id: 'workflow-trace-viewer',
  nodes: initialFlowData.nodes,
  edges: initialFlowData.edges,
  nodesDraggable: false,
  nodesConnectable: false,
  elementsSelectable: true,
})

// 轨迹步骤：同节点覆盖写语义去重，按执行顺序升序
const steps = computed<WorkflowNodeTraceItem[]>(() => {
  const byNode = new Map<string, WorkflowNodeTraceItem>()
  for (const item of props.trace || []) {
    byNode.set(item.nodeId, item)
  }
  return [...byNode.values()].sort(
    (a, b) => (a.executionOrder ?? Number.MAX_SAFE_INTEGER) - (b.executionOrder ?? Number.MAX_SAFE_INTEGER),
  )
})

// 已点亮的步骤数，默认全部展示（终态）
const revealedCount = ref(steps.value.length)
const playing = ref(false)
let playTimer: ReturnType<typeof setInterval> | null = null

const selectedTrace = ref<WorkflowNodeTraceItem | null>(null)

const selectedNodeName = computed(() => {
  if (!selectedTrace.value) return ''
  const node = getNodes.value.find((n) => n.id === selectedTrace.value?.nodeId)
  return selectedTrace.value.nodeName || node?.data?.name || selectedTrace.value.nodeId
})

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = null
  }
}

function startPlay() {
  if (playing.value) return
  // 播放到终点后再次播放，从头开始
  if (revealedCount.value >= steps.value.length) {
    revealedCount.value = 0
  }
  playing.value = true
  playTimer = setInterval(() => {
    if (revealedCount.value >= steps.value.length) {
      stopPlay()
      return
    }
    revealedCount.value++
  }, 800)
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
  } else {
    startPlay()
  }
}

function stepPrev() {
  stopPlay()
  if (revealedCount.value > 0) revealedCount.value--
}

function stepNext() {
  stopPlay()
  if (revealedCount.value < steps.value.length) revealedCount.value++
}

function resetReplay() {
  stopPlay()
  revealedCount.value = 0
}

function revealAll() {
  stopPlay()
  revealedCount.value = steps.value.length
}

const traceByNodeId = computed(() => {
  const map = new Map<string, WorkflowNodeTraceItem>()
  for (const item of steps.value.slice(0, revealedCount.value)) {
    map.set(item.nodeId, item)
  }
  return map
})

function statusClass(status?: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'wf-trace-node--completed'
    case 'FAILED':
      return 'wf-trace-node--failed'
    case 'RUNNING':
    case 'PAUSED':
      return 'wf-trace-node--running'
    case 'CANCELLED':
      return 'wf-trace-node--cancelled'
    default:
      return 'wf-trace-node--pending'
  }
}

function statusLabel(status?: string): string {
  switch (status) {
    case 'COMPLETED':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '运行中'
    case 'PAUSED':
      return '已暂停'
    case 'CANCELLED':
      return '已取消'
    default:
      return '未执行'
  }
}

function formatDuration(ms?: number | null): string {
  if (ms == null) return '-'
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

function formatTime(ts?: number | null): string {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

function formatJson(value?: Record<string, any> | null): string {
  if (value == null) return '-'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

// 叠加回放层：节点状态着色/角标 + 边高亮/置灰
function applyTraceStyles() {
  const reached = traceByNodeId.value
  const reachedIds = new Set(reached.keys())

  const nodes = getNodes.value.map((node) => {
    const t = reached.get(node.id)
    const cls = ['wf-trace-node']
    let style: Record<string, any> | undefined
    if (t) {
      cls.push(statusClass(t.status))
      const badgeParts: string[] = []
      if (t.durationMs != null) badgeParts.push(`⏱${formatDuration(t.durationMs)}`)
      if ((t.retryCount ?? 0) > 0) badgeParts.push(`↻${t.retryCount}次`)
      if ((t.iterationCount ?? 0) > 0) badgeParts.push(`⟳${t.iterationCount}次`)
      if (badgeParts.length) {
        style = { '--wf-trace-badge': `"${badgeParts.join(' ')}"` }
      }
    } else {
      cls.push('wf-trace-node--pending')
    }
    return { ...node, class: cls.join(' '), style, data: { ...node.data } }
  })

  const edges = getEdges.value.map((edge) => {
    const sourceReached = reachedIds.has(edge.source)
    const targetReached = reachedIds.has(edge.target)
    const sourceTrace = reached.get(edge.source)
    const edgeLabel = edge.data?.conditionLabel
    const isConditionalBranch = sourceTrace?.nodeType === 'CONDITION' && edge.data?.type === 'CONDITIONAL'

    let cls = 'wf-trace-edge'
    let style: Record<string, any> | undefined
    if (isConditionalBranch && sourceReached && sourceTrace?.branchTaken != null) {
      // 条件节点已定分支：命中分支高亮，未命中分支置灰
      if (edgeLabel === sourceTrace.branchTaken) {
        cls += ' wf-trace-edge--walked'
        style = { stroke: '#10b981', strokeWidth: 3 }
      } else {
        cls += ' wf-trace-edge--dimmed'
      }
    } else if (sourceReached && targetReached) {
      cls += ' wf-trace-edge--walked'
      style = { stroke: '#10b981', strokeWidth: 3 }
    }
    return { ...edge, class: cls, style, data: { ...edge.data } }
  })

  setNodes(nodes)
  setEdges(edges)
}

onNodeClick(({ node }) => {
  // 详情按全量轨迹展示（包含尚未回放到的步骤）
  selectedTrace.value = steps.value.find((t) => t.nodeId === node.id) || null
})

function closeDetail() {
  selectedTrace.value = null
}

// 定义或轨迹变化时重建画布并恢复终态展示
watch(
  () => [props.definition, props.trace] as const,
  () => {
    const flowData = fromWorkflowDefinition(props.definition)
    setNodes(flowData.nodes)
    setEdges(flowData.edges)
    revealedCount.value = steps.value.length
    selectedTrace.value = null
    // revealedCount 未变化时 watch 不触发，需直接着色一次
    applyTraceStyles()
  },
  { deep: false },
)

watch(revealedCount, applyTraceStyles)

// 初始着色
applyTraceStyles()

onBeforeUnmount(stopPlay)
</script>

<template>
  <div class="wf-trace-viewer">
    <div class="wf-trace-viewer__canvas">
      <VueFlow
        :node-types="nodeTypes"
        :edge-types="edgeTypes"
        :default-viewport="{ zoom: 1, x: 0, y: 0 }"
        :min-zoom="0.2"
        :max-zoom="2"
        fit-view-on-init
      >
        <Background />
        <Controls />
        <MiniMap position="bottom-right" pannable zoomable :width="180" :height="120" />
      </VueFlow>

      <!-- 状态图例 -->
      <div class="wf-trace-viewer__legend">
        <span class="wf-trace-viewer__legend-item wf-trace-node--completed"><i />成功</span>
        <span class="wf-trace-viewer__legend-item wf-trace-node--failed"><i />失败</span>
        <span class="wf-trace-viewer__legend-item wf-trace-node--running"><i />运行中</span>
        <span class="wf-trace-viewer__legend-item wf-trace-node--pending"><i />未执行</span>
      </div>

      <!-- 节点轨迹详情抽屉 -->
      <div v-if="selectedTrace" class="wf-trace-viewer__detail">
        <div class="wf-trace-viewer__detail-header">
          <span class="wf-trace-viewer__detail-title">{{ selectedNodeName }}</span>
          <button class="wf-trace-viewer__detail-close" @click="closeDetail">×</button>
        </div>
        <div class="wf-trace-viewer__detail-body">
          <div class="wf-trace-viewer__meta">
            <div class="wf-trace-viewer__meta-row">
              <span class="wf-trace-viewer__meta-label">状态</span>
              <span :class="statusClass(selectedTrace.status)" class="wf-trace-viewer__meta-value">
                {{ statusLabel(selectedTrace.status) }}
              </span>
            </div>
            <div class="wf-trace-viewer__meta-row">
              <span class="wf-trace-viewer__meta-label">耗时</span>
              <span class="wf-trace-viewer__meta-value">{{ formatDuration(selectedTrace.durationMs) }}</span>
            </div>
            <div class="wf-trace-viewer__meta-row">
              <span class="wf-trace-viewer__meta-label">重试次数</span>
              <span class="wf-trace-viewer__meta-value">{{ selectedTrace.retryCount ?? 0 }}</span>
            </div>
            <div class="wf-trace-viewer__meta-row" v-if="selectedTrace.iterationCount != null">
              <span class="wf-trace-viewer__meta-label">迭代次数</span>
              <span class="wf-trace-viewer__meta-value">{{ selectedTrace.iterationCount }}</span>
            </div>
            <div class="wf-trace-viewer__meta-row" v-if="selectedTrace.nodeType === 'CONDITION'">
              <span class="wf-trace-viewer__meta-label">命中分支</span>
              <span class="wf-trace-viewer__meta-value">{{ selectedTrace.branchTaken ?? '-' }}</span>
            </div>
            <div class="wf-trace-viewer__meta-row">
              <span class="wf-trace-viewer__meta-label">开始时间</span>
              <span class="wf-trace-viewer__meta-value">{{ formatTime(selectedTrace.startTime) }}</span>
            </div>
            <div class="wf-trace-viewer__meta-row">
              <span class="wf-trace-viewer__meta-label">结束时间</span>
              <span class="wf-trace-viewer__meta-value">{{ formatTime(selectedTrace.endTime) }}</span>
            </div>
          </div>
          <div v-if="selectedTrace.errorMessage" class="wf-trace-viewer__error">
            {{ selectedTrace.errorMessage }}
          </div>
          <div class="wf-trace-viewer__section">
            <div class="wf-trace-viewer__section-title">输入</div>
            <pre class="wf-trace-viewer__json">{{ formatJson(selectedTrace.inputData) }}</pre>
          </div>
          <div class="wf-trace-viewer__section">
            <div class="wf-trace-viewer__section-title">输出</div>
            <pre class="wf-trace-viewer__json">{{ formatJson(selectedTrace.outputData) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部时间轴控制条 -->
    <div class="wf-trace-viewer__timeline">
      <button
        class="wf-trace-viewer__btn"
        title="重置"
        :disabled="steps.length === 0"
        @click="resetReplay"
      >⏮</button>
      <button
        class="wf-trace-viewer__btn"
        title="上一步"
        :disabled="revealedCount <= 0"
        @click="stepPrev"
      >◀</button>
      <button
        class="wf-trace-viewer__btn wf-trace-viewer__btn--primary"
        :title="playing ? '暂停' : '播放'"
        :disabled="steps.length === 0"
        @click="togglePlay"
      >{{ playing ? '⏸' : '▶' }}</button>
      <button
        class="wf-trace-viewer__btn"
        title="下一步"
        :disabled="revealedCount >= steps.length"
        @click="stepNext"
      >▶|</button>
      <button
        class="wf-trace-viewer__btn"
        title="跳到末尾"
        :disabled="revealedCount >= steps.length"
        @click="revealAll"
      >⏭</button>
      <input
        v-model.number="revealedCount"
        class="wf-trace-viewer__slider"
        type="range"
        min="0"
        :max="steps.length"
        step="1"
      />
      <span class="wf-trace-viewer__progress">{{ revealedCount }} / {{ steps.length }}</span>
    </div>
  </div>
</template>

<style>
/* 节点/边渲染在 VueFlow 内部树中，需使用全局样式（以 wf-trace-viewer 为前缀避免泄漏） */
.wf-trace-viewer .wf-node {
  position: relative;
}
.wf-trace-viewer .wf-node::after {
  content: var(--wf-trace-badge, '');
  position: absolute;
  top: -10px;
  right: -6px;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 10px;
  line-height: 14px;
  white-space: nowrap;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}
.wf-trace-viewer .wf-trace-node--completed .wf-node {
  border-color: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.35);
}
.wf-trace-viewer .wf-trace-node--failed .wf-node {
  border-color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.35);
}
.wf-trace-viewer .wf-trace-node--running .wf-node {
  border-color: #f59e0b;
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.35);
}
.wf-trace-viewer .wf-trace-node--cancelled .wf-node {
  border-color: #94a3b8;
  box-shadow: 0 0 0 2px rgba(148, 163, 184, 0.3);
}
.wf-trace-viewer .wf-trace-node--pending .wf-node {
  opacity: 0.45;
  filter: grayscale(0.6);
}
.wf-trace-viewer .vue-flow__edge.wf-trace-edge--dimmed {
  opacity: 0.25;
}
.wf-trace-viewer .vue-flow__edge-path {
  transition: stroke 0.2s ease, stroke-width 0.2s ease;
}
</style>

<style scoped>
.wf-trace-viewer {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}
.wf-trace-viewer__canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
}
.wf-trace-viewer__legend {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  display: flex;
  gap: 12px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 11px;
  color: #475569;
}
.wf-trace-viewer__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.wf-trace-viewer__legend-item i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: #e2e8f0;
}
.wf-trace-viewer__legend-item.wf-trace-node--completed i {
  background: #10b981;
}
.wf-trace-viewer__legend-item.wf-trace-node--failed i {
  background: #ef4444;
}
.wf-trace-viewer__legend-item.wf-trace-node--running i {
  background: #f59e0b;
}
.wf-trace-viewer__legend-item.wf-trace-node--pending i {
  background: #cbd5e1;
}
.wf-trace-viewer__detail {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  width: 320px;
  max-height: calc(100% - 24px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.wf-trace-viewer__detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.wf-trace-viewer__detail-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-trace-viewer__detail-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #94a3b8;
  cursor: pointer;
  line-height: 1;
  padding: 2px 4px;
}
.wf-trace-viewer__detail-close:hover {
  color: #475569;
}
.wf-trace-viewer__detail-body {
  padding: 12px;
  overflow-y: auto;
}
.wf-trace-viewer__meta-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
}
.wf-trace-viewer__meta-label {
  color: #94a3b8;
}
.wf-trace-viewer__meta-value {
  color: #475569;
}
.wf-trace-viewer__error {
  margin: 8px 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.wf-trace-viewer__section {
  margin-top: 10px;
}
.wf-trace-viewer__section-title {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 4px;
}
.wf-trace-viewer__json {
  margin: 0;
  padding: 8px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.wf-trace-viewer__timeline {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.wf-trace-viewer__btn {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}
.wf-trace-viewer__btn:hover:not(:disabled) {
  background: #f1f5f9;
}
.wf-trace-viewer__btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.wf-trace-viewer__btn--primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}
.wf-trace-viewer__btn--primary:hover:not(:disabled) {
  background: #4f46e5;
}
.wf-trace-viewer__slider {
  flex: 1;
  accent-color: #6366f1;
}
.wf-trace-viewer__progress {
  font-size: 12px;
  color: #64748b;
  min-width: 48px;
  text-align: right;
}
</style>
