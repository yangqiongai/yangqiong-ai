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
import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { AgentTypeInfo } from '../types';
import { parseAgentConfig, stringifyAgentConfig, type AgentConfigStructured } from './agent-config';

/**
 * 挂载保存 API 最小结构（各端 api 实例的子集）
 */
export interface AgentMountApi {
  agent: {
    type: {
      update: (typeCode: string, data: Partial<AgentTypeInfo>) => Promise<unknown>;
    };
  };
}

/**
 * 能力挂载直显编辑逻辑（覆盖agentConfig单字段保存即生效，三方共用逻辑）
 * @param params.api
 * @param params.agentCode
 * @param params.agentConfig
 * @param params.onSaved
 * @param params.onError
 * @return
 */
export const useAgentMount = (params: {
  api: AgentMountApi;
  agentCode: string;
  agentConfig?: string;
  onSaved?: () => void;
  onError?: (message: string) => void;
}) => {
  const [mountConfig, setMountConfig] = useState<AgentConfigStructured>({});

  // 选中Agent变化时回填能力挂载配置
  useEffect(() => {
    setMountConfig(parseAgentConfig(params.agentConfig));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.agentCode, params.agentConfig]);

  const mountSaveMutation = useMutation({
    mutationFn: async () => {
      if (!params.agentCode) {
        throw new Error('请先选择智能体');
      }
      // 仅覆盖agentConfig单字段部分更新，保存即生效；重排序开关默认关闭，未配置时显式写入false
      await params.api.agent.type.update(params.agentCode, {
        agentConfig: stringifyAgentConfig({ ...mountConfig, rerankEnabled: mountConfig.rerankEnabled ?? false }),
      });
    },
    onSuccess: () => params.onSaved?.(),
    onError: (err) => params.onError?.(err instanceof Error ? err.message : '保存失败'),
  });

  return { mountConfig, setMountConfig, mountSaveMutation };
};
