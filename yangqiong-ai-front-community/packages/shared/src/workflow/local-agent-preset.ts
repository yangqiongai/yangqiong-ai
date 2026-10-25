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

import type { WorkflowAgentPreset } from './preset-types';

/** 工作区任务默认提示词模板（${input}引用上游输入） */
const LOCAL_TASK_PROMPT = `请在当前工作区内完成以下任务：
\${input}

执行要求：
- 仅在当前工作区目录内读写文件，不越界访问其他目录
- 每一步确认文件操作结果后再继续
- 完成后汇总操作结果与产出的关键信息`;

/**
 * 工作区节点预设模板
 * 生成 agentCode=localAgent 的固定Agent节点配置，执行时由 LocalAgentProcessor 在指定工作区内处理；
 * 工作区下拉选项经 workspaceOptions 注入（仅服务端存储型工作区），选中值写入 _workspaceId 经执行器透传为请求私有参数
 */
export const localAgentPreset: WorkflowAgentPreset = {
  code: 'localAgent',
  name: '工作区节点',
  description: '在工作区内读写文件、执行任务',
  icon: '📁',
  color: '#10b981',
  tag: '工作区任务',
  // Agent类型固定为本地Agent，配置面板锁定不可切换
  fixedAgent: true,
  // 配置面板渲染工作区选择下拉，选项由宿主注入
  workspaceSelect: true,
  defaults: {
    sysPrompt: LOCAL_TASK_PROMPT,
    maxIterations: 25,
  },
};
