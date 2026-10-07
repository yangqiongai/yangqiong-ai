<script setup lang="ts">
import { ref, computed, watch, markRaw, nextTick, onMounted, type Component } from 'vue'
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
import NotifyNode from './nodes/NotifyNode.vue'
import TimeControlNode from './nodes/TimeControlNode.vue'
import StartNode from './nodes/StartNode.vue'
import EndNode from './nodes/EndNode.vue'
import NormalEdge from './edges/NormalEdge.vue'
import ConditionEdge from './edges/ConditionEdge.vue'
import ParallelEdge from './edges/ParallelEdge.vue'
import NodeConfigPanel from './panels/NodeConfigPanel.vue'
import EdgeConfigPanel from './panels/EdgeConfigPanel.vue'
import WorkflowToolbar from './toolbar/WorkflowToolbar.vue'

import type { WorkflowDefinition, NodeType, EdgeType, NodeApprovalConfig, TimeControlConfig, WorkflowNodeExecStatus, WorkflowNodeExecData, WorkflowEditorExecutionApi } from './types/workflow'
import {
  toVueFlowNodes,
  toVueFlowEdges,
  toWorkflowDefinition,
  fromWorkflowDefinition,
  NODE_TYPE_MAP,
} from './utils/schema-adapter'
import { computeLayeredLayout } from './utils/layout'

const props = withDefaults(
  defineProps<{
    modelValue: WorkflowDefinition
    readonly?: boolean
    /** 流程显示名称（中文名），导出文件命名优先使用 */
    displayName?: string
    /** 是否启用企业版专属能力（审批/通知节点等），社区版传 false 时置灰 */
    enterpriseFeatures?: boolean
    agentOptions?: Array<{ code: string; name: string }>
    /** 集成渠道下拉选项，供通知节点配置选择 */
    channelOptions?: Array<{ id: string; name: string; type?: string }>
    /** 编辑器挂载后回传执行进度控制API，供宿主框架实时驱动画布 */
    registerApi?: (api: WorkflowEditorExecutionApi) => void
  }>(),
  {
    readonly: false,
    enterpriseFeatures: true,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: WorkflowDefinition): void
  (e: 'save', value: WorkflowDefinition): void
  (e: 'execute', value: WorkflowDefinition): void
  (e: 'pause'): void
  (e: 'resume'): void
  (e: 'cancel'): void
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
  notify: markRaw(NotifyNode) as unknown as Component,
  timeControl: markRaw(TimeControlNode) as unknown as Component,
  start: markRaw(StartNode) as unknown as Component,
  end: markRaw(EndNode) as unknown as Component,
}

const edgeTypes: EdgeTypesObject = {
  normal: markRaw(NormalEdge) as unknown as Component,
  conditional: markRaw(ConditionEdge) as unknown as Component,
  parallel: markRaw(ParallelEdge) as unknown as Component,
}

const initialFlowData = fromWorkflowDefinition(props.modelValue)

const {
  onNodeClick,
  onEdgeClick,
  onPaneClick,
  onConnect,
  onNodesChange,
  onEdgesChange,
  onNodeDragStop,
  onPaneReady,
  addNodes,
  addEdges,
  getNodes,
  getEdges,
  setNodes,
  setEdges,
  applyNodeChanges,
  applyEdgeChanges,
  removeNodes,
  removeEdges,
  findNode,
  findEdge,
  screenToFlowCoordinate,
  fitView,
  viewport,
} = useVueFlow({
  id: 'workflow-editor',
  nodes: initialFlowData.nodes,
  edges: initialFlowData.edges,
  nodesDraggable: !props.readonly,
  nodesConnectable: !props.readonly,
  // 只读模式禁用元素选择
  elementsSelectable: !props.readonly,
})

// 画布就绪后适应视口：限制最大缩放为1，避免节点较少时被放大到充满视口
onPaneReady((instance) => {
  instance.fitView({ padding: 0.3, maxZoom: 1, duration: 200 })
})

// 处理节点变化（拖动、删除等）
onNodesChange((changes) => {
  applyNodeChanges(changes)
  syncToModel()
})

// 处理边变化（删除等）
onEdgesChange((changes) => {
  applyEdgeChanges(changes)
  syncToModel()
})

// 外部 modelValue 变化时同步（仅非自身触发时）
let skipSync = false
watch(
  () => props.modelValue,
  (newVal) => {
    if (skipSync) {
      skipSync = false
      return
    }
    const flowData = fromWorkflowDefinition(newVal)
    setNodes(flowData.nodes)
    setEdges(flowData.edges)
    // 外部整包替换视为新的编辑起点，重置历史
    resetHistory()
  },
  { deep: false },
)

// 撤销/重做历史栈（快照为定义JSON，防抖合并连续编辑，上限50条）
const history: string[] = []
const historyIndex = ref(-1)
let historyTimer: ReturnType<typeof setTimeout> | null = null

const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.length - 1)

function currentSnapshot(): string {
  return JSON.stringify(toWorkflowDefinition(getNodes.value, getEdges.value, props.modelValue))
}

function resetHistory() {
  history.length = 0
  history.push(currentSnapshot())
  historyIndex.value = 0
}

function pushHistory() {
  if (historyTimer) {
    clearTimeout(historyTimer)
    historyTimer = null
  }
  const snap = currentSnapshot()
  if (history[historyIndex.value] === snap) return
  history.splice(historyIndex.value + 1)
  history.push(snap)
  if (history.length > 50) {
    history.shift()
  }
  historyIndex.value = history.length - 1
}

function scheduleHistory() {
  if (historyTimer) clearTimeout(historyTimer)
  historyTimer = setTimeout(pushHistory, 300)
}

// 应用历史快照，直接重建画布并同步父级（不经防抖）
function applyHistory() {
  const value = JSON.parse(history[historyIndex.value]) as WorkflowDefinition
  const flowData = fromWorkflowDefinition(value)
  skipSync = true
  setNodes(flowData.nodes)
  setEdges(flowData.edges)
  emit('update:modelValue', value)
}

function handleUndo() {
  if (props.readonly || !canUndo.value) return
  historyIndex.value--
  applyHistory()
}

function handleRedo() {
  if (props.readonly || !canRedo.value) return
  historyIndex.value++
  applyHistory()
}

// 拖动结束立即落历史，避免与进行中的拖动快照混淆
onNodeDragStop(() => {
  if (!props.readonly) pushHistory()
})

const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const showPanel = ref(false)

const selectedNode = computed(() => {
  if (!selectedNodeId.value) return null
  return findNode(selectedNodeId.value)
})

const selectedEdge = computed(() => {
  if (!selectedEdgeId.value) return null
  return findEdge(selectedEdgeId.value)
})

// 条件分支可选目标节点（排除自身）
const branchTargetOptions = computed(() =>
  getNodes.value
    .filter((n) => n.id !== selectedNodeId.value)
    .map((n) => ({ id: n.id, name: n.data?.name || n.id })),
)

onNodeClick(({ node }) => {
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  showPanel.value = true
})

onEdgeClick(({ edge }) => {
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
  showPanel.value = true
})

onPaneClick(() => {
  selectedNodeId.value = null
  selectedEdgeId.value = null
  showPanel.value = false
})

onConnect((params) => {
  addEdges([
    {
      ...params,
      id: genId('e'),
      type: 'normal',
      data: { type: 'NORMAL' },
    },
  ])
  syncToModel()
})

// 由当前画布节点/边构建最新定义（面板试运行等场景需要实时定义，props.modelValue 挂载后不会回传更新）
function buildCurrentDefinition() {
  return toWorkflowDefinition(
    getNodes.value,
    getEdges.value,
    {
      name: props.modelValue.name,
      description: props.modelValue.description,
      stateConfig: props.modelValue.stateConfig,
      errorStrategy: props.modelValue.errorStrategy,
      maxRetries: props.modelValue.maxRetries,
      nodeTimeoutSeconds: props.modelValue.nodeTimeoutSeconds,
    },
  )
}

function syncToModel() {
  const definition = buildCurrentDefinition()
  skipSync = true
  emit('update:modelValue', definition)
  scheduleHistory()
}

function handleNodeUpdate(data: { name: string; config: Record<string, any>; inputMappings?: Record<string, string>; outputMappings?: Record<string, string>; approvalConfig?: any; timeControlConfig?: any; timeoutSeconds?: number; maxRetries?: number }) {
  if (!selectedNode.value || props.readonly) return
  selectedNode.value.data = {
    ...selectedNode.value.data,
    name: data.name,
    config: data.config,
    inputMappings: data.inputMappings,
    outputMappings: data.outputMappings,
    approvalConfig: data.approvalConfig,
    timeControlConfig: data.timeControlConfig,
    timeoutSeconds: data.timeoutSeconds,
    maxRetries: data.maxRetries,
  }
  syncToModel()
}

function handleEdgeUpdate(data: { type: EdgeType; conditionExpression?: string; conditionLabel?: string }) {
  if (!selectedEdge.value || props.readonly) return
  selectedEdge.value.data = {
    ...selectedEdge.value.data,
    type: data.type,
    conditionExpression: data.conditionExpression,
    conditionLabel: data.conditionLabel,
  }
  selectedEdge.value.type = data.type === 'CONDITIONAL' ? 'conditional' : data.type === 'PARALLEL' ? 'parallel' : 'normal'
  selectedEdge.value.label = data.conditionLabel || undefined
  syncToModel()
}

function deleteSelectedNode() {
  if (!selectedNodeId.value || props.readonly) return
  const node = findNode(selectedNodeId.value)
  if (node) {
    removeNodes([node])
    selectedNodeId.value = null
    showPanel.value = false
    syncToModel()
  }
}

function deleteSelectedEdge() {
  if (!selectedEdgeId.value || props.readonly) return
  const edge = findEdge(selectedEdgeId.value)
  if (edge) {
    removeEdges([edge])
    selectedEdgeId.value = null
    showPanel.value = false
    syncToModel()
  }
}

// 键盘删除
function onKeyDown(event: KeyboardEvent) {
  if (props.readonly) return
  // 撤销/重做快捷键（输入框聚焦时不拦截）
  const key = event.key.toLowerCase()
  if (event.ctrlKey || event.metaKey) {
    if (key === 'z' && !event.shiftKey) {
      event.preventDefault()
      handleUndo()
      return
    }
    if (key === 'y' || (key === 'z' && event.shiftKey)) {
      event.preventDefault()
      handleRedo()
      return
    }
  }
  if (event.key === 'Delete' || event.key === 'Backspace') {
    // 避免在输入框中删除
    const target = event.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') return
    if (selectedNodeId.value) {
      deleteSelectedNode()
    } else if (selectedEdgeId.value) {
      deleteSelectedEdge()
    }
  }
}

function handleSave() {
  emit('save', toWorkflowDefinition(getNodes.value, getEdges.value, props.modelValue))
}

// 生成不重复的元素 id（时间戳+随机后缀，避免同毫秒冲突）
function genId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

// 轻量非阻塞提示
const toastMessage = ref('')
const toastType = ref<'success' | 'error'>('success')
let toastTimer: ReturnType<typeof setTimeout> | null = null

function showToast(message: string, type: 'success' | 'error' = 'success') {
  toastMessage.value = message
  toastType.value = type
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 3000)
}

function handleLoad() {
  if (props.readonly) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'application/json,.json'
  input.onchange = () => {
    const file = input.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      try {
        const definition = JSON.parse(String(reader.result)) as WorkflowDefinition
        const flowData = fromWorkflowDefinition(definition)
        setNodes(flowData.nodes)
        setEdges(flowData.edges)
        syncToModel()
        showToast('工作流导入成功')
      } catch {
        showToast('导入失败：文件不是合法的工作流 JSON', 'error')
      }
    }
    reader.readAsText(file)
  }
  input.click()
}

function handleValidate(result: { valid: boolean; errors: string[] }) {
  if (!result.valid) {
    const suffix = result.errors.length > 1 ? ` 等 ${result.errors.length} 项问题` : ''
    showToast(`校验失败：${result.errors[0]}${suffix}`, 'error')
  } else {
    showToast('校验通过')
  }
}

function handleExport() {
  const definition = toWorkflowDefinition(getNodes.value, getEdges.value, props.modelValue)
  const json = JSON.stringify(definition, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  // 文件名使用流程名称 + 版本号（如 发布通知_v6.json），名称优先用显示名，回退为定义编码
  const baseName = props.displayName || definition.name || 'workflow'
  const versionSuffix = props.modelValue.version != null ? `_v${props.modelValue.version}` : ''
  a.href = url
  a.download = `${baseName}${versionSuffix}.json`
  a.click()
  URL.revokeObjectURL(url)
}

function handleExecute() {
  emit('execute', toWorkflowDefinition(getNodes.value, getEdges.value, props.modelValue))
}

// ==================== 一键美化布局 ====================
const canvasEl = ref<HTMLDivElement | null>(null)

// 节点兜底尺寸（与画布样式 min-width 及常规内容高度对齐），实际尺寸优先从 DOM 读取
function fallbackNodeSize(type: string): { width: number; height: number } {
  if (type === 'start' || type === 'end') return { width: 76, height: 76 }
  return { width: 184, height: 96 }
}

// 从画布 DOM 读取节点实际尺寸，换算回画布坐标系（除以当前缩放）
function measureNodeSize(nodeId: string, type: string): { width: number; height: number } {
  const el = document.querySelector(`.vue-flow__node[data-id="${CSS.escape(nodeId)}"]`)
  const zoom = viewport.value?.zoom || 1
  if (!el || zoom <= 0) return fallbackNodeSize(type)
  const rect = el.getBoundingClientRect()
  return { width: rect.width / zoom, height: rect.height / zoom }
}

function handleBeautify() {
  if (props.readonly) return
  const nodes = getNodes.value
  if (nodes.length === 0) return
  const positions = computeLayeredLayout(
    nodes.map((n) => ({ id: n.id, ...measureNodeSize(n.id, n.type || '') })),
    getEdges.value.map((e) => ({ source: e.source, target: e.target })),
  )
  // 临时开启过渡动画获得平滑重排效果，动画结束后移除避免影响日常拖拽
  const canvas = canvasEl.value
  canvas?.classList.add('wf-editor__canvas--animating')
  for (const n of nodes) {
    const p = positions[n.id]
    if (p) n.position = p
  }
  nextTick(() => {
    fitView({ padding: 0.25, maxZoom: 1, duration: 400 })
    setTimeout(() => canvas?.classList.remove('wf-editor__canvas--animating'), 450)
  })
  syncToModel()
  pushHistory()
  showToast('布局已美化')
}

// ==================== 执行进度控制 ====================
const executing = ref(false)

// 执行暂停标志（宿主经registerApi.setPaused驱动，控制工具栏暂停/继续按钮切换）
const paused = ref(false)

// 节点本次执行的输入/输出详情（键为节点ID），供面板执行详情tab展示
const nodeExecData = ref<Record<string, WorkflowNodeExecData>>({})

function setNodeExecData(data: Record<string, WorkflowNodeExecData>) {
  nodeExecData.value = data
}

// 节点执行状态class前缀，resetExecution 时按前缀清理
const NODE_EXEC_CLASS_PREFIX = 'wf-node-exec--'
const EDGE_EXEC_CLASS_PREFIX = 'wf-edge-exec--'

function setNodeExecClass(nodeId: string, status: WorkflowNodeExecStatus | null) {
  const node = findNode(nodeId)
  if (!node) return
  const rest = String(node.class || '')
    .split(/\s+/)
    .filter((c) => c && !c.startsWith(NODE_EXEC_CLASS_PREFIX))
  if (status) rest.push(`${NODE_EXEC_CLASS_PREFIX}${status}`)
  node.class = rest.join(' ')
}

// 节点状态联动入边：进入节点的边视为"走过的路径"，skipped 用虚线弱化
function setIncomingEdgeClass(nodeId: string, status: WorkflowNodeExecStatus | null) {
  for (const edge of getEdges.value) {
    if (edge.target !== nodeId) continue
    const rest = String(edge.class || '')
      .split(/\s+/)
      .filter((c) => c && !c.startsWith(EDGE_EXEC_CLASS_PREFIX))
    if (status === 'skipped') {
      rest.push(`${EDGE_EXEC_CLASS_PREFIX}skipped`)
    } else if (status) {
      rest.push(`${EDGE_EXEC_CLASS_PREFIX}active`)
      if (status === 'running') rest.push(`${EDGE_EXEC_CLASS_PREFIX}animated`)
    }
    edge.class = rest.join(' ')
  }
}

function setNodeStatus(nodeId: string, status: WorkflowNodeExecStatus) {
  setNodeExecClass(nodeId, status)
  setIncomingEdgeClass(nodeId, status)
}

function resetExecution() {
  for (const node of getNodes.value) setNodeExecClass(node.id, null)
  for (const edge of getEdges.value) {
    edge.class = String(edge.class || '')
      .split(/\s+/)
      .filter((c) => c && !c.startsWith(EDGE_EXEC_CLASS_PREFIX))
      .join(' ')
  }
  executing.value = false
  paused.value = false
  nodeExecData.value = {}
}

// 挂载后回传执行进度控制API，宿主框架据此实时驱动画布节点与路径样式
onMounted(() => {
  props.registerApi?.({
    resetExecution,
    setNodeStatus,
    setExecuting: (value: boolean) => {
      executing.value = value
    },
    setPaused: (value: boolean) => {
      paused.value = value
    },
    setNodeExecData,
  })
})

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDrop(event: DragEvent) {
  if (props.readonly) return
  const type = event.dataTransfer?.getData('application/vueflow') as NodeType
  if (!type) return

  // 企业版专属节点在社区版不可用
  if (isNodeLocked(type)) {
    showToast(`${enterpriseNodeLabel(type)}为企业版专属功能，当前版本不可用`, 'error')
    return
  }

  // 开始/结束节点全流程仅允许一个
  if ((type === 'START' || type === 'END') && getNodes.value.some((n) => n.data?.type === type)) {
    showToast(`画布中已存在${defaultNodeName(type)}节点，全流程仅允许一个`, 'error')
    return
  }

  // 屏幕坐标换算为画布坐标，缩放/平移下仍落在鼠标位置
  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })

  addNodes([
    {
      id: genId(type.toLowerCase()),
      type: NODE_TYPE_MAP[type] || type.toLowerCase(),
      position,
      data: {
        name: defaultNodeName(type),
        type,
        config: defaultNodeConfig(type),
        approvalConfig: type === 'APPROVAL' ? defaultApprovalConfig() : undefined,
        timeControlConfig: type === 'TIME_CONTROL' ? defaultTimeControlConfig() : undefined,
      },
    },
  ])
  syncToModel()
}

function onNodeDragStart(event: DragEvent, type: NodeType) {
  // 企业版专属节点在社区版禁止拖出
  if (isNodeLocked(type)) {
    showToast(`${enterpriseNodeLabel(type)}为企业版专属功能，当前版本不可用`, 'error')
    return
  }
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', type)
    event.dataTransfer.effectAllowed = 'move'
  }
}

function defaultNodeName(type: NodeType): string {
  const names: Record<NodeType, string> = {
    AGENT: 'Agent',
    CONDITION: '条件分支',
    PARALLEL: '并行网关',
    LOOP: '循环节点',
    SUBGRAPH: '子图节点',
    TRANSFORM: '数据变换',
    SCRIPT: '脚本',
    HTTP: 'HTTP请求',
    ASSIGN: '变量赋值',
    APPROVAL: '审批节点',
    NOTIFY: '通知节点',
    TIME_CONTROL: '时间控制',
    START: '开始',
    END: '结束',
  }
  return names[type] || type
}

function defaultNodeConfig(type: NodeType): Record<string, any> {
  switch (type) {
    case 'TRANSFORM':
      return { transformType: 'SUBSTRING' }
    case 'SCRIPT':
      return { scriptType: 'SPEL' }
    case 'HTTP':
      return { method: 'GET', timeout: 10000 }
    case 'LOOP':
      return { _loopMode: 'condition', maxIterations: 100 }
    case 'PARALLEL':
      return { joinType: 'ALL' }
    case 'ASSIGN':
      return { assignments: {} }
    case 'NOTIFY':
      return { level: 'INFO', async: false, ignoreFailure: true }
    default:
      return {}
  }
}

function defaultApprovalConfig(): NodeApprovalConfig {
  return {
    reason: '',
    options: [],
    inputFields: [],
    timeoutSeconds: 300,
    rejectBehavior: 'FAIL',
  }
}

function defaultTimeControlConfig(): TimeControlConfig {
  return {
    timeType: 'DELAY',
    delaySeconds: 10,
  }
}

const draggableNodeTypes: { type: NodeType; label: string; icon: string; group: string }[] = [
  { type: 'START', label: '开始', icon: '▶️', group: '基础' },
  { type: 'END', label: '结束', icon: '⏹️', group: '基础' },
  { type: 'AGENT', label: 'Agent', icon: '🤖', group: '智能' },
  { type: 'APPROVAL', label: '审批节点', icon: '✋', group: '智能' },
  { type: 'NOTIFY', label: '通知节点', icon: '📢', group: '智能' },
  { type: 'CONDITION', label: '条件分支', icon: '🔀', group: '控制' },
  { type: 'PARALLEL', label: '并行网关', icon: '⚡', group: '控制' },
  { type: 'LOOP', label: '循环节点', icon: '🔄', group: '控制' },
  { type: 'TIME_CONTROL', label: '时间控制', icon: '⏰', group: '控制' },
  { type: 'SUBGRAPH', label: '子图节点', icon: '📦', group: '控制' },
  { type: 'TRANSFORM', label: '数据变换', icon: '🔧', group: '数据' },
  { type: 'SCRIPT', label: '脚本/表达式', icon: '📜', group: '数据' },
  { type: 'HTTP', label: 'HTTP请求', icon: '🌐', group: '数据' },
  { type: 'ASSIGN', label: '变量赋值', icon: '📝', group: '数据' },
]

const nodeGroups = computed(() => {
  const groups: Record<string, typeof draggableNodeTypes> = {}
  for (const item of draggableNodeTypes) {
    if (!groups[item.group]) groups[item.group] = []
    groups[item.group].push(item)
  }
  return groups
})

// 非企业版时审批/通知节点锁定（企业版专属能力，社区版不可用）
const approvalLocked = computed(() => !props.enterpriseFeatures)

// 企业版专属节点类型
const ENTERPRISE_ONLY_TYPES: NodeType[] = ['APPROVAL', 'NOTIFY']

function isNodeLocked(type: NodeType): boolean {
  return ENTERPRISE_ONLY_TYPES.includes(type) && approvalLocked.value
}

// 企业版专属节点名称（用于提示文案）
function enterpriseNodeLabel(type: NodeType): string {
  return defaultNodeName(type)
}
</script>

<template>
  <div
    class="wf-editor"
    :class="{ 'wf-editor--readonly': readonly }"
    tabindex="0"
    @keydown="onKeyDown"
  >
    <WorkflowToolbar
      :definition="modelValue"
      :can-undo="canUndo"
      :can-redo="canRedo"
      :executing="executing"
      :paused="paused"
      :enterprise-features="enterpriseFeatures"
      @save="handleSave"
      @load="handleLoad"
      @validate="handleValidate"
      @export="handleExport"
      @execute="handleExecute"
      @undo="handleUndo"
      @redo="handleRedo"
      @beautify="handleBeautify"
      @pause="emit('pause')"
      @resume="emit('resume')"
      @cancel="emit('cancel')"
    >
      <!-- 透传工具栏扩展插槽，供上层（如企业版）注入右侧扩展按钮 -->
      <slot name="toolbar-extra" />
    </WorkflowToolbar>
    <div class="wf-editor__main">
      <!-- 左侧节点面板 -->
      <div v-if="!readonly" class="wf-editor__sidebar">
        <div v-for="(items, group) in nodeGroups" :key="group">
          <div class="wf-sidebar__title">{{ group }}</div>
          <div
            v-for="item in items"
            :key="item.type"
            class="wf-sidebar__item"
            :class="{ 'wf-sidebar__item--disabled': isNodeLocked(item.type) }"
            :draggable="!isNodeLocked(item.type)"
            :title="isNodeLocked(item.type) ? `${item.label}为企业版专属功能，当前版本不可用` : ''"
            @dragstart="onNodeDragStart($event, item.type)"
          >
            <span class="wf-sidebar__icon">{{ item.icon }}</span>
            <span class="wf-sidebar__label">{{ item.label }}<span
              v-if="isNodeLocked(item.type)"
              class="wf-sidebar__lock"
              title="该节点为企业版能力，请升级至企业版使用"
            ><svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor" aria-hidden="true"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z" /></svg></span></span>
          </div>
        </div>
      </div>

      <!-- 画布区域 -->
      <div ref="canvasEl" class="wf-editor__canvas" @dragover="onDragOver" @drop="onDrop">
        <VueFlow
          :node-types="nodeTypes"
          :edge-types="edgeTypes"
          :default-viewport="{ zoom: 0.85, x: 0, y: 0 }"
          :min-zoom="0.2"
          :max-zoom="2"
          :nodes-draggable="!readonly"
          :nodes-connectable="!readonly"
          :edges-deletable="!readonly"
          :elements-deletable="!readonly"
          :delete-key-code="readonly ? null : 'Delete'"
        >
          <Background />
          <Controls />
          <MiniMap position="bottom-right" pannable zoomable :width="180" :height="120" />
        </VueFlow>
        <!-- 非阻塞操作反馈 -->
        <Transition name="wf-toast">
          <div v-if="toastMessage" class="wf-toast" :class="`wf-toast--${toastType}`">
            {{ toastMessage }}
          </div>
        </Transition>
      </div>

      <!-- 右侧配置面板 -->
      <div v-if="showPanel" class="wf-editor__panel">
        <NodeConfigPanel
          v-if="selectedNode"
          :node-id="selectedNode.id"
          :node-name="selectedNode.data?.name || ''"
          :node-type="selectedNode.data?.type || 'AGENT'"
          :config="selectedNode.data?.config || {}"
          :input-mappings="selectedNode.data?.inputMappings"
          :output-mappings="selectedNode.data?.outputMappings"
          :approval-config="selectedNode.data?.approvalConfig"
          :time-control-config="selectedNode.data?.timeControlConfig"
          :timeout-seconds="selectedNode.data?.timeoutSeconds"
          :max-retries="selectedNode.data?.maxRetries"
          :node-options="branchTargetOptions"
          :agent-options="agentOptions"
          :channel-options="channelOptions"
          :definition="modelValue"
          :get-definition="buildCurrentDefinition"
          :exec-data="selectedNode ? nodeExecData[selectedNode.id] : undefined"
          :enterprise-features="enterpriseFeatures"
          :readonly="readonly"
          @update="handleNodeUpdate"
          @delete="deleteSelectedNode"
        />
        <EdgeConfigPanel
          v-else-if="selectedEdge"
          :edge-id="selectedEdge.id"
          :edge-type="selectedEdge.data?.type || 'NORMAL'"
          :source-id="selectedEdge.source"
          :target-id="selectedEdge.target"
          :condition-expression="selectedEdge.data?.conditionExpression"
          :condition-label="selectedEdge.data?.conditionLabel"
          :readonly="readonly"
          @update="handleEdgeUpdate"
          @delete="deleteSelectedEdge"
        />
      </div>
    </div>
  </div>
</template>

<style>
.wf-node {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border-left: 4px solid var(--node-color, #64748b);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
.wf-node--start,
.wf-node--end {
  border-left: none;
  border-radius: 50%;
  min-width: unset;
  text-align: center;
}
.wf-node__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.wf-node__icon {
  font-size: 14px;
}
.wf-node__title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-node__body {
  padding: 8px 12px;
}
.wf-node__field {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
.wf-node__field:last-child {
  margin-bottom: 0;
}
.wf-node__label {
  font-size: 11px;
  color: #94a3b8;
}
.wf-node__value {
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 选中节点高亮 */
.vue-flow__node.selected .wf-node {
  box-shadow: 0 0 0 2px #6366f1, 0 4px 12px rgba(99, 102, 241, 0.3);
}
.vue-flow__edge.selected .wf-edge--normal,
.vue-flow__edge.selected .wf-edge--conditional,
.vue-flow__edge.selected .wf-edge--parallel {
  stroke: #6366f1;
  stroke-width: 3;
}

/* 执行进度节点状态 */
.vue-flow__node.wf-node-exec--running .wf-node {
  animation: wf-node-pulse 1.2s ease-in-out infinite;
}
.vue-flow__node.wf-node-exec--completed .wf-node {
  border-color: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.35);
}
.vue-flow__node.wf-node-exec--failed .wf-node {
  border-color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.4);
}
.vue-flow__node.wf-node-exec--skipped .wf-node {
  opacity: 0.5;
}
.vue-flow__node.wf-node-exec--skipped .wf-node__title {
  text-decoration: line-through;
}
.vue-flow__node.wf-node-exec--paused .wf-node {
  border-color: #f59e0b;
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.4);
}
.vue-flow__node.wf-node-exec--paused .wf-node__title {
  color: #b45309;
}
@keyframes wf-node-pulse {
  0%,
  100% {
    border-color: #f59e0b;
    box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.45);
  }
  50% {
    border-color: #fbbf24;
    box-shadow: 0 0 0 7px rgba(245, 158, 11, 0.12);
  }
}

/* 执行路径边样式：走过高亮、执行中流动虚线、跳过灰虚线 */
.vue-flow__edge.wf-edge-exec--active path {
  stroke: #10b981;
  stroke-width: 2.5;
}
.vue-flow__edge.wf-edge-exec--skipped path {
  stroke: #cbd5e1;
  stroke-width: 1.5;
  stroke-dasharray: 4 4;
}
.vue-flow__edge.wf-edge-exec--animated path {
  stroke-dasharray: 7 5;
  animation: wf-edge-dash 0.5s linear infinite;
}
@keyframes wf-edge-dash {
  to {
    stroke-dashoffset: -12;
  }
}
</style>

<style scoped>
.wf-editor {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
  outline: none;
}
.wf-editor__main {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.wf-editor__sidebar {
  width: 160px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}
.wf-sidebar__title {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  margin-top: 12px;
}
.wf-sidebar__title:first-child {
  margin-top: 0;
}
.wf-sidebar__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: grab;
  transition: all 0.15s ease;
  font-size: 13px;
  color: #475569;
}
.wf-sidebar__item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}
.wf-sidebar__item--disabled,
.wf-sidebar__item--disabled:hover {
  opacity: 0.45;
  background: #f8fafc;
  border-color: #e2e8f0;
  cursor: not-allowed;
  color: #94a3b8;
}
.wf-sidebar__item:active {
  cursor: grabbing;
}
.wf-sidebar__icon {
  font-size: 16px;
}
.wf-sidebar__label {
  font-size: 13px;
}
.wf-sidebar__lock {
  display: inline-flex;
  align-items: center;
  vertical-align: -2px;
  margin-left: 4px;
  color: #d48806;
}
.wf-editor__canvas {
  flex: 1;
  height: 100%;
}
/* 美化重排时节点位移平滑过渡（仅美化瞬间挂载，不影响日常拖拽） */
.wf-editor__canvas--animating .vue-flow__node {
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.wf-editor__panel {
  width: 320px;
  background: #fff;
  border-left: 1px solid #e2e8f0;
  overflow-y: auto;
  flex-shrink: 0;
}
.wf-editor--readonly .wf-sidebar__item {
  cursor: not-allowed;
  opacity: 0.5;
}
.wf-toast {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  pointer-events: none;
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-toast--success {
  background: rgba(16, 185, 129, 0.95);
}
.wf-toast--error {
  background: rgba(239, 68, 68, 0.95);
}
.wf-toast-enter-active,
.wf-toast-leave-active {
  transition: all 0.25s ease;
}
.wf-toast-enter-from,
.wf-toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
