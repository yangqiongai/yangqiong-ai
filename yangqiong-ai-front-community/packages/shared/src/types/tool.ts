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
/**
 * 工具相关类型
 * 字段与后端实体对齐：ToolConfigInfo (ai-agent-tool)
 * McpServerConfigInfo / McpToolInfo / McpConnectionTestResult (ai-agent-mcp)
 */

/**
 * 工具配置信息
 * 对应后端 com.maizi.ai.agent.tool.model.ToolConfigInfo
 */
export interface ToolConfigInfo {
  id?: number;
  toolCode: string;
  toolName: string;
  toolDesc?: string;
  toolClass?: string;
  toolType?: string;
  toolCategory?: string;
  toolOrder?: number;
  toolConfig?: string;
  toolStatus?: number;
  remark?: string;
  /**
   * 所属分类编码（工具分类树节点code，空为未分类）
   */
  category?: string;
}

/**
 * 工具使用量统计
 * 对应后端 com.maizi.ai.agent.tool.model.ToolUsageInfo
 */
export interface ToolUsageInfo {
  toolCode: string;
  toolName?: string;
  toolDesc?: string;
  callCount?: number;
  successCount?: number;
  failCount?: number;
  lastCalledAt?: string;
}

/**
 * MCP 服务端配置信息
 * 对应后端 com.maizi.ai.agent.mcp.model.McpServerConfigInfo
 */
export interface McpServerConfigInfo {
  id?: number;
  serverCode: string;
  serverName: string;
  transportType: string;
  connectionConfig?: string;
  enabledTools?: string;
  disabledTools?: string;
  serverStatus?: number;
  remark?: string;
  offlineReason?: string;
  updateTime?: string;
  /**
   * 所属分类编码（MCP分类树节点code，空为未分类）
   */
  category?: string;
}

/**
 * MCP 服务端配置（运行时）
 * 对应后端 com.maizi.ai.agent.mcp.model.McpServerConfig
 */
export interface McpServerConfig {
  serverCode: string;
  serverName: string;
  transportType: string;
  connectionConfig?: Record<string, unknown>;
  enabledTools?: string[];
  disabledTools?: string[];
  serverStatus?: number;
}

/**
 * MCP 工具信息
 * 对应后端 com.maizi.ai.agent.mcp.model.McpToolInfo
 */
export interface McpToolInfo {
  name: string;
  description?: string;
  inputSchema?: string;
  serverCode?: string;
}

/**
 * MCP 连接测试结果
 * 对应后端 com.maizi.ai.agent.mcp.model.McpConnectionTestResult
 */
export interface McpConnectionTestResult {
  success: boolean;
  message?: string;
  toolCount: number;
  latencyMs: number;
  errorDetail?: string;
  rawToolCount?: number;
  filteredToolCount?: number;
}
