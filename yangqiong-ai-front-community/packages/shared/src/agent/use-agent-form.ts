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
import { useState } from 'react';
import type { AgentTypeInfo } from '../types';
import { parseAgentConfig, stringifyAgentConfig, type AgentConfigStructured } from './agent-config';

/**
 * Agent 表单状态（四段式：基础属性/运行参数/能力挂载/备注，展示层无关）
 */
export interface AgentFormState {
  agentCode: string;
  agentName: string;
  description: string;
  icon: string;
  sortOrder: string;
  remark: string;
  status: boolean;
  category: string;
  sessionType: string;
  config: AgentConfigStructured;
}

/**
 * 空表单默认值
 */
export const EMPTY_AGENT_FORM: AgentFormState = {
  agentCode: '',
  agentName: '',
  description: '',
  icon: '',
  sortOrder: '',
  remark: '',
  status: true,
  category: '',
  sessionType: 'CHAT',
  config: {},
};

/**
 * 表单状态组装为 agent 保存 payload（typeCode/typeName 前缀映射，空值不写入）
 * @param form
 * @return
 */
export const buildAgentPayload = (form: AgentFormState): Partial<AgentTypeInfo> => ({
  typeCode: form.agentCode.trim(),
  typeName: form.agentName.trim(),
  typeDescription: form.description.trim() || undefined,
  typeIcon: form.icon.trim() || undefined,
  typeOrder: form.sortOrder.trim() === '' ? undefined : Number(form.sortOrder),
  remark: form.remark.trim() || undefined,
  typeStatus: form.status ? 1 : 0,
  typeCategory: form.category.trim() || undefined,
  sessionType: form.sessionType,
  // 重排序开关默认关闭，未配置时显式写入false
  agentConfig: stringifyAgentConfig({ ...form.config, rerankEnabled: form.config.rerankEnabled ?? false }),
});

/**
 * 校验表单必填项，非法时返回错误信息，合法返回空串
 * @param form
 * @return
 */
export const validateAgentForm = (form: AgentFormState): string => {
  if (!form.agentCode.trim() || !form.agentName.trim()) {
    return '请填写智能体编码与名称';
  }
  return '';
};

/**
 * Agent 创建/编辑表单状态逻辑（展示层无关，antd/shadcn 视图共用）
 * @return
 */
export const useAgentForm = () => {
  const [form, setForm] = useState<AgentFormState>(EMPTY_AGENT_FORM);

  /**
   * 打开新建（重置为空表单）
   */
  const openCreate = () => {
    setForm(EMPTY_AGENT_FORM);
  };

  /**
   * 打开编辑（AgentTypeInfo 回填，配置JSON解析失败置空）
   * @param agent
   */
  const openEdit = (agent: AgentTypeInfo) => {
    setForm({
      agentCode: agent.typeCode,
      agentName: agent.typeName,
      description: agent.typeDescription ?? '',
      icon: agent.typeIcon ?? '',
      sortOrder: agent.typeOrder != null ? String(agent.typeOrder) : '',
      remark: agent.remark ?? '',
      status: agent.typeStatus !== 0,
      category: agent.typeCategory ?? '',
      sessionType: agent.sessionType ?? 'CHAT',
      config: parseAgentConfig(agent.agentConfig),
    });
  };

  return { form, setForm, openCreate, openEdit };
};
