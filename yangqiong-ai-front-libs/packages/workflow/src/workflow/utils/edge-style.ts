import { ref } from 'vue'

/**
 * 连线样式：curve 平滑曲线（默认）、rounded 圆角折线
 */
export type EdgeStyle = 'curve' | 'rounded'

// 编辑器会话内共享的连线样式偏好（展示偏好，不持久化到流程定义，默认保持原曲线样式）
const edgeStyle = ref<EdgeStyle>('curve')

/**
 * 连线样式偏好（编辑器内全局共享）
 * @return
 */
export function useEdgeStyle() {
  return edgeStyle
}
