/*
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
 */

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
