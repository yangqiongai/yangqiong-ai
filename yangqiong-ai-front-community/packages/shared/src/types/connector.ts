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
 * 连接器相关类型
 * 字段与后端对齐：ConnectorCredential / ConnectorInstance (platform-connector/entity)
 * ProviderCatalogItem / ConnectorDescriptor / ConnectorField / ConnectorToolDefinition (platform-connector)
 */

/**
 * 提供商字段声明（凭证/配置通用）
 */
export interface ConnectorField {
  name?: string;

  label?: string;

  required?: boolean;

  /**
   * 敏感字段（接口返回时脱敏，表单渲染为密码框）
   */
  secret?: boolean;

  placeholder?: string;
}

/**
 * 连接器工具定义
 */
export interface ConnectorToolDefinition {
  name?: string;

  description?: string;

  /**
   * 参数Schema（JSON Schema格式字符串）
   */
  parametersSchema?: string;
}

/**
 * 提供商描述
 */
export interface ConnectorDescriptor {
  displayName?: string;

  category?: string;

  icon?: string;

  description?: string;

  credentialFields?: ConnectorField[];

  configFields?: ConnectorField[];

  tools?: ConnectorToolDefinition[];

  /**
   * 是否支持入站消息
   */
  inboundSupported?: boolean;
}

/**
 * 提供商目录项
 */
export interface ProviderCatalogItem {
  providerCode?: string;

  descriptor?: ConnectorDescriptor;
}

/**
 * 连接器凭证
 * 对应后端 ConnectorCredential（表 ai_connector_credential，ID为字符串序列化）
 */
export interface ConnectorCredential {
  id?: string;

  providerCode?: string;

  name?: string;

  /**
   * 凭证明文JSON（仅保存请求携带，列表响应恒为空）
   */
  credentialJson?: string;

  /**
   * 敏感字段尾号掩码（JSON: 字段名->掩码值）
   */
  maskedJson?: string;

  status?: string;

  scopeId?: string;

  createUser?: string;

  createTime?: string;

  updateUser?: string;

  updateTime?: string;
}

/**
 * 连接器实例
 * 对应后端 ConnectorInstance（表 ai_connector_instance，ID为字符串序列化）
 */
export interface ConnectorInstance {
  id?: string;

  /**
   * 实例编码（入站回调路由键）
   */
  instanceCode?: string;

  providerCode?: string;

  name?: string;

  /**
   * 非敏感配置JSON
   */
  configJson?: string;

  /**
   * 关联凭证ID(可空=免凭证提供商)
   */
  credentialId?: string;

  /**
   * 入站消息目标Agent编码(可空=仅出站工具)
   */
  agentCode?: string;

  status?: string;

  /**
   * 乐观锁版本号
   */
  version?: number;

  scopeId?: string;

  createUser?: string;

  createTime?: string;

  updateUser?: string;

  updateTime?: string;
}
