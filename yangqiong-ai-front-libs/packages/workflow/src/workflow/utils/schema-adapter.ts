import type { Node, Edge } from '@vue-flow/core'
import type {
  WorkflowNode,
  WorkflowEdge,
  WorkflowDefinition,
  NodeType,
  EdgeType,
} from '../types/workflow'

export const NODE_TYPE_MAP: Record<NodeType, string> = {
  AGENT: 'agent',
  CONDITION: 'condition',
  PARALLEL: 'parallel',
  LOOP: 'loop',
  SUBGRAPH: 'subgraph',
  TRANSFORM: 'transform',
  SCRIPT: 'script',
  HTTP: 'http',
  ASSIGN: 'assign',
  APPROVAL: 'approval',
  NOTIFY: 'notify',
  TIME_CONTROL: 'timeControl',
  START: 'start',
  END: 'end',
}

const VUE_FLOW_TYPE_MAP: Record<string, NodeType> = {
  agent: 'AGENT',
  condition: 'CONDITION',
  parallel: 'PARALLEL',
  loop: 'LOOP',
  subgraph: 'SUBGRAPH',
  transform: 'TRANSFORM',
  script: 'SCRIPT',
  http: 'HTTP',
  assign: 'ASSIGN',
  approval: 'APPROVAL',
  notify: 'NOTIFY',
  timeControl: 'TIME_CONTROL',
  start: 'START',
  end: 'END',
}

const EDGE_TYPE_MAP: Record<EdgeType, string> = {
  NORMAL: 'normal',
  CONDITIONAL: 'conditional',
  PARALLEL: 'parallel',
}

const VUE_FLOW_EDGE_TYPE_MAP: Record<string, EdgeType> = {
  normal: 'NORMAL',
  conditional: 'CONDITIONAL',
  parallel: 'PARALLEL',
}

export function toVueFlowNodes(nodes: WorkflowNode[]): Node[] {
  return nodes.map((node) => ({
    id: node.id,
    type: NODE_TYPE_MAP[node.type] || 'agent',
    position: node.position || { x: 0, y: 0 },
    data: {
      name: node.name,
      type: node.type,
      config: node.config,
      inputMappings: node.inputMappings,
      outputMappings: node.outputMappings,
      approvalConfig: node.approvalConfig,
      timeControlConfig: node.timeControlConfig,
      timeoutSeconds: node.timeoutSeconds,
      maxRetries: node.maxRetries,
    },
  }))
}

export function toVueFlowEdges(edges: WorkflowEdge[]): Edge[] {
  return edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceId,
    target: edge.targetId,
    type: EDGE_TYPE_MAP[edge.type] || 'normal',
    data: {
      type: edge.type,
      conditionExpression: edge.conditionExpression,
      conditionLabel: edge.conditionLabel,
    },
    label: edge.conditionLabel || undefined,
  }))
}

export function fromVueFlowNodes(nodes: Node[]): WorkflowNode[] {
  return nodes.map((node) => ({
    id: node.id,
    name: node.data?.name || '',
    type: VUE_FLOW_TYPE_MAP[node.type || ''] || (node.data?.type as NodeType) || 'AGENT',
    config: node.data?.config || {},
    inputMappings: node.data?.inputMappings,
    outputMappings: node.data?.outputMappings,
    approvalConfig: node.data?.approvalConfig,
    timeControlConfig: node.data?.timeControlConfig,
    timeoutSeconds: node.data?.timeoutSeconds,
    maxRetries: node.data?.maxRetries,
    position: { x: node.position.x, y: node.position.y },
  }))
}

export function fromVueFlowEdges(edges: Edge[]): WorkflowEdge[] {
  return edges.map((edge) => ({
    id: edge.id,
    sourceId: edge.source,
    targetId: edge.target,
    type: VUE_FLOW_EDGE_TYPE_MAP[edge.type || ''] || (edge.data?.type as EdgeType) || 'NORMAL',
    conditionExpression: edge.data?.conditionExpression,
    conditionLabel: edge.data?.conditionLabel,
  }))
}

export function toWorkflowDefinition(
  nodes: Node[],
  edges: Edge[],
  meta: Partial<WorkflowDefinition>,
): WorkflowDefinition {
  return {
    name: meta.name || '',
    description: meta.description,
    nodes: fromVueFlowNodes(nodes),
    edges: fromVueFlowEdges(edges),
    stateConfig: meta.stateConfig,
    errorStrategy: meta.errorStrategy,
    maxRetries: meta.maxRetries,
    nodeTimeoutSeconds: meta.nodeTimeoutSeconds,
  }
}

export function fromWorkflowDefinition(def: WorkflowDefinition): {
  nodes: Node[]
  edges: Edge[]
} {
  return {
    nodes: toVueFlowNodes(def.nodes),
    edges: toVueFlowEdges(def.edges),
  }
}
