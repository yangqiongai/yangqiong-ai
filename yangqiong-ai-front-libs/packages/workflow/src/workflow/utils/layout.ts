/**
 * 工作流画布自动布局：分层 DAG（自上而下），用于「一键美化」
 *
 * 算法分四步：
 * 1. 分层（rank）：Kahn 拓扑排序 + 最长路径法，主干自上而下逐层展开
 * 2. 层内排序：barycenter（重心法）交替迭代，最小化连线交叉
 * 3. 坐标计算：层内垂直居中 + 父节点居中对齐（主干笔直、分支对称）
 * 4. 防重叠：层内从左到右扫描，保证最小间距
 */

export interface LayoutNodeInput {
  id: string
  width: number
  height: number
}

export interface LayoutEdgeInput {
  source: string
  target: string
}

export interface LayoutPosition {
  x: number
  y: number
}

export interface LayoutOptions {
  /**
   * 层间距（垂直，像素）
   */
  rankGap?: number
  /**
   * 同层节点水平间距（像素）
   */
  nodeGap?: number
  /**
   * 画布边距（像素）
   */
  margin?: number
  /**
   * 交叉消除迭代轮数
   */
  iterations?: number
}

/**
 * 计算分层布局位置，键为节点 id
 * @param nodes
 * @param edges
 * @param options
 * @return
 */
export function computeLayeredLayout(
  nodes: LayoutNodeInput[],
  edges: LayoutEdgeInput[],
  options: LayoutOptions = {},
): Record<string, LayoutPosition> {
  const rankGap = options.rankGap ?? 96
  const nodeGap = options.nodeGap ?? 64
  const margin = options.margin ?? 48
  const iterations = options.iterations ?? 4

  const result: Record<string, LayoutPosition> = {}
  if (nodes.length === 0) return result

  const idSet = new Set(nodes.map((n) => n.id))
  const sizeOf = new Map(nodes.map((n) => [n.id, n]))
  // 过滤自环与无效端点的边
  const validEdges = edges.filter(
    (e) => e.source !== e.target && idSet.has(e.source) && idSet.has(e.target),
  )

  // 邻接表与前驱/后继映射
  const inDeg = new Map<string, number>()
  const succs = new Map<string, string[]>()
  const preds = new Map<string, string[]>()
  for (const n of nodes) {
    inDeg.set(n.id, 0)
    succs.set(n.id, [])
    preds.set(n.id, [])
  }
  for (const e of validEdges) {
    inDeg.set(e.target, inDeg.get(e.target)! + 1)
    succs.get(e.source)!.push(e.target)
    preds.get(e.target)!.push(e.source)
  }

  // 分层：Kahn 拓扑序 + 最长路径松弛，主干节点自然逐层下落
  const rank = new Map<string, number>()
  for (const n of nodes) rank.set(n.id, 0)
  const queue: string[] = nodes.filter((n) => inDeg.get(n.id) === 0).map((n) => n.id)
  const ordered = new Set<string>()
  while (queue.length > 0) {
    const id = queue.shift()!
    ordered.add(id)
    for (const t of succs.get(id)!) {
      rank.set(t, Math.max(rank.get(t)!, rank.get(id)! + 1))
      inDeg.set(t, inDeg.get(t)! - 1)
      if (inDeg.get(t) === 0) queue.push(t)
    }
  }
  // 环内剩余节点（防御性兜底）：挂在第一个前驱的下一层
  for (const n of nodes) {
    if (ordered.has(n.id)) continue
    let base = 0
    for (const e of validEdges) {
      if (e.target === n.id) base = Math.max(base, rank.get(e.source)! + 1)
    }
    rank.set(n.id, base)
  }

  // 按层分组
  const maxRank = Math.max(...nodes.map((n) => rank.get(n.id)!))
  const layers: string[][] = Array.from({ length: maxRank + 1 }, () => [])
  for (const n of nodes) layers[rank.get(n.id)!].push(n.id)

  // 层内位置索引（barycenter 排序依据）
  const pos = new Map<string, number>()
  for (const layer of layers) layer.forEach((id, i) => pos.set(id, i))

  const sortByBarycenter = (layer: string[], neighbors: Map<string, string[]>) => {
    const keyed = layer.map((id, i) => {
      const ns = neighbors.get(id)!.filter((t) => pos.has(t))
      const key = ns.length === 0 ? i : ns.reduce((s, t) => s + pos.get(t)!, 0) / ns.length
      return { id, key }
    })
    keyed.sort((a, b) => a.key - b.key)
    keyed.forEach((o, i) => {
      layer[i] = o.id
      pos.set(o.id, i)
    })
  }

  // 交叉消除：自上而下按前驱重心、自下而上按后继重心交替迭代
  for (let iter = 0; iter < iterations; iter++) {
    for (let r = 1; r < layers.length; r++) sortByBarycenter(layers[r], preds)
    for (let r = layers.length - 2; r >= 0; r--) sortByBarycenter(layers[r], succs)
  }

  // y 坐标：逐层累计层高（层内按最大节点高度垂直居中）
  const yOf = new Map<string, number>()
  let cursorY = margin
  for (const layer of layers) {
    const layerHeight = Math.max(...layer.map((id) => sizeOf.get(id)!.height))
    for (const id of layer) {
      yOf.set(id, cursorY + (layerHeight - sizeOf.get(id)!.height) / 2)
    }
    cursorY += layerHeight + rankGap
  }

  // x 坐标：初始按层内顺序依次排列
  const xOf = new Map<string, number>()
  for (const layer of layers) {
    let x = margin
    for (const id of layer) {
      xOf.set(id, x)
      x += sizeOf.get(id)!.width + nodeGap
    }
  }

  // 层内防重叠：按 x 从左到右扫描，保证最小间距与左边界
  const sweepLayer = (layer: string[]) => {
    const sorted = [...layer].sort((a, b) => xOf.get(a)! - xOf.get(b)!)
    let prevEnd = Number.NEGATIVE_INFINITY
    for (const id of sorted) {
      const x = Math.max(xOf.get(id)!, prevEnd + nodeGap, margin)
      xOf.set(id, x)
      prevEnd = x + sizeOf.get(id)!.width
    }
  }

  // 父节点居中对齐迭代：单链笔直、多分支对称展开在父节点两侧
  for (let iter = 0; iter < 3; iter++) {
    for (let r = 1; r < layers.length; r++) {
      for (const id of layers[r]) {
        const parents = preds.get(id)!.filter((p) => rank.get(p)! < r)
        if (parents.length === 0) continue
        const cx =
          parents.reduce((s, p) => s + xOf.get(p)! + sizeOf.get(p)!.width / 2, 0) / parents.length
        xOf.set(id, cx - sizeOf.get(id)!.width / 2)
      }
      sweepLayer(layers[r])
    }
  }

  for (const n of nodes) {
    result[n.id] = { x: Math.round(xOf.get(n.id)!), y: Math.round(yOf.get(n.id)!) }
  }
  return result
}
