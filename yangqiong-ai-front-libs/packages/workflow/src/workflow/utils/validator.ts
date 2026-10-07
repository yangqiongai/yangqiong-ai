import type { WorkflowDefinition, WorkflowNode, WorkflowEdge } from '../types/workflow'

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

export function validateDefinition(def: WorkflowDefinition): ValidationResult {
  const errors: string[] = []

  if (!def.name || def.name.trim() === '') {
    errors.push('工作流名称不能为空')
  }

  if (!def.nodes || def.nodes.length === 0) {
    errors.push('工作流必须包含至少一个节点')
  } else {
    const nodeIds = new Set(def.nodes.map((n) => n.id))
    const duplicateIds = def.nodes
      .map((n) => n.id)
      .filter((id, index, arr) => arr.indexOf(id) !== index)
    if (duplicateIds.length > 0) {
      errors.push(`存在重复的节点ID: ${[...new Set(duplicateIds)].join(', ')}`)
    }

    def.nodes.forEach((node) => {
      const nodeErrors = validateNodeCompleteness(node)
      errors.push(...nodeErrors)
    })

    const connectivityErrors = validateConnectivity(def.nodes, def.edges)
    errors.push(...connectivityErrors)
  }

  if (def.edges && def.edges.length > 0) {
    const nodeIds = new Set(def.nodes.map((n) => n.id))
    def.edges.forEach((edge) => {
      if (!nodeIds.has(edge.sourceId)) {
        errors.push(`边 ${edge.id} 的源节点 ${edge.sourceId} 不存在`)
      }
      if (!nodeIds.has(edge.targetId)) {
        errors.push(`边 ${edge.id} 的目标节点 ${edge.targetId} 不存在`)
      }
    })
  }

  if (detectCycle(def.edges || [])) {
    errors.push('工作流中存在循环依赖，不允许形成环路')
  }

  return {
    valid: errors.length === 0,
    errors,
  }
}

export function detectCycle(edges: WorkflowEdge[]): boolean {
  if (!edges || edges.length === 0) return false

  const adjacency = new Map<string, string[]>()
  edges.forEach((edge) => {
    const neighbors = adjacency.get(edge.sourceId) || []
    neighbors.push(edge.targetId)
    adjacency.set(edge.sourceId, neighbors)
  })

  const visited = new Set<string>()
  const recursionStack = new Set<string>()

  function dfs(nodeId: string): boolean {
    visited.add(nodeId)
    recursionStack.add(nodeId)

    const neighbors = adjacency.get(nodeId) || []
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) {
        if (dfs(neighbor)) return true
      } else if (recursionStack.has(neighbor)) {
        return true
      }
    }

    recursionStack.delete(nodeId)
    return false
  }

  const allNodes = new Set<string>()
  edges.forEach((edge) => {
    allNodes.add(edge.sourceId)
    allNodes.add(edge.targetId)
  })

  for (const nodeId of allNodes) {
    if (!visited.has(nodeId)) {
      if (dfs(nodeId)) return true
    }
  }

  return false
}

export function validateNodeCompleteness(node: WorkflowNode): string[] {
  const errors: string[] = []

  if (!node.id || node.id.trim() === '') {
    errors.push('节点ID不能为空')
  }

  if (!node.name || node.name.trim() === '') {
    errors.push(`节点 ${node.id || '(未知)'} 的名称不能为空`)
  }

  if (!node.type) {
    errors.push(`节点 ${node.id || '(未知)'} 的类型不能为空`)
  }

  const validTypes = ['AGENT', 'CONDITION', 'PARALLEL', 'LOOP', 'SUBGRAPH', 'TRANSFORM', 'SCRIPT', 'HTTP', 'ASSIGN', 'APPROVAL', 'NOTIFY', 'TIME_CONTROL', 'START', 'END']
  if (node.type && !validTypes.includes(node.type)) {
    errors.push(`节点 ${node.id} 的类型 ${node.type} 不合法`)
  }

  if (node.timeoutSeconds !== undefined && node.timeoutSeconds !== null) {
    if (!Number.isInteger(node.timeoutSeconds) || node.timeoutSeconds <= 0) {
      errors.push(`节点 ${node.id} 的超时秒数必须为正整数`)
    } else if (node.timeoutSeconds > 3600) {
      errors.push(`节点 ${node.id} 的超时秒数不能超过 3600 秒`)
    }
  }

  if (node.maxRetries !== undefined && node.maxRetries !== null) {
    if (!Number.isInteger(node.maxRetries) || node.maxRetries < 0) {
      errors.push(`节点 ${node.id} 的最大重试次数必须为非负整数`)
    } else if (node.maxRetries > 10) {
      errors.push(`节点 ${node.id} 的最大重试次数不能超过 10 次`)
    }
  }

  if (node.type === 'AGENT' && (!node.config || !node.config.agentCode)) {
    errors.push(`Agent 节点 ${node.id} 必须配置 agentCode`)
  }

  if (node.type === 'NOTIFY' && (!node.config || !node.config.channelId)) {
    errors.push(`通知节点 ${node.id} 必须选择集成渠道（channelId）`)
  }

  if (node.type === 'NOTIFY' && node.config && !node.config.content) {
    errors.push(`通知节点 ${node.id} 必须配置通知内容（content）`)
  }

  if (node.type === 'TRANSFORM' && (!node.config || !node.config.transformType)) {
    errors.push(`Transform 节点 ${node.id} 必须配置 transformType`)
  }

  if (node.type === 'SCRIPT' && (!node.config || !node.config.scriptType)) {
    errors.push(`Script 节点 ${node.id} 必须配置 scriptType`)
  }

  if (node.type === 'SCRIPT' && (!node.config || (!node.config.expression && !node.config.script))) {
    errors.push(`Script 节点 ${node.id} 必须配置 expression 或 script`)
  }

  if (node.type === 'HTTP' && (!node.config || !node.config.url)) {
    errors.push(`HTTP 节点 ${node.id} 必须配置 url`)
  }

  if (node.type === 'LOOP' && (!node.config || (!node.config.exitCondition && !node.config.iterateOver))) {
    errors.push(`Loop 节点 ${node.id} 必须配置退出条件 exitCondition 或遍历变量 iterateOver`)
  }

  if (node.type === 'SUBGRAPH' && (!node.config || !node.config.workflowName)) {
    errors.push(`Subgraph 节点 ${node.id} 必须配置引用的工作流名称 workflowName`)
  }

  return errors
}

export function validateConnectivity(
  nodes: WorkflowNode[],
  edges: WorkflowEdge[],
): string[] {
  const errors: string[] = []

  if (nodes.length <= 1) return errors

  const startCount = nodes.filter((n) => n.type === 'START').length
  if (startCount > 1) {
    errors.push(`START 节点只能有一个，当前存在 ${startCount} 个`)
  }

  const endCount = nodes.filter((n) => n.type === 'END').length
  if (endCount > 1) {
    errors.push(`END 节点只能有一个，当前存在 ${endCount} 个`)
  }

  const nodeIds = new Set(nodes.map((n) => n.id))
  const connectedNodes = new Set<string>()

  edges.forEach((edge) => {
    connectedNodes.add(edge.sourceId)
    connectedNodes.add(edge.targetId)
  })

  const isolatedNodes = nodes.filter((n) => !connectedNodes.has(n.id))

  isolatedNodes.forEach((node) => {
    if (node.type === 'START' || node.type === 'END') {
      const hasEdge = edges.some(
        (e) => e.sourceId === node.id || e.targetId === node.id,
      )
      if (!hasEdge) {
        errors.push(`节点 ${node.id} (${node.type}) 未连接到任何边`)
      }
    } else {
      errors.push(`节点 ${node.id} (${node.name}) 是孤立节点，未连接到任何边`)
    }
  })

  const startNodes = nodes.filter((n) => n.type === 'START')
  startNodes.forEach((node) => {
    const hasOutEdge = edges.some((e) => e.sourceId === node.id)
    if (!hasOutEdge) {
      errors.push(`START 节点 ${node.id} 必须有出边`)
    }
  })

  const endNodes = nodes.filter((n) => n.type === 'END')
  endNodes.forEach((node) => {
    const hasInEdge = edges.some((e) => e.targetId === node.id)
    if (!hasInEdge) {
      errors.push(`END 节点 ${node.id} 必须有入边`)
    }
  })

  return errors
}
