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
 * agentConfig 组装与解析（社区admin/企业admin/企业client三方单源）
 */

/**
 * 知识库配置
 */
export interface KnowledgeBaseConfig {
  kbCodes?: string[];

  /**
   * 知识库名称（与kbCodes对应，便于配置JSON直读）
   */
  kbNames?: string[];
}

/**
 * 推理模式配置
 */
export interface ReasoningConfig {
  enabled?: boolean;
  effort?: string;
  showThinking?: boolean;
}

/**
 * 图谱检索配置
 */
export interface GraphRetrievalConfig {
  enabled?: boolean;

  /**
   * 图谱库ID列表（知识库ID）
   */
  kbCodes?: string[];

  /**
   * 检索模式（AUTO=自动路由，LOCAL/GLOBAL/HYBRID/MULTI_HOP）
   */
  mode?: string;

  /**
   * 各类命中结果条数上限（默认10）
   */
  topK?: number;
}

/**
 * Text2SQL数据问答配置
 */
export interface Text2SqlConfig {
  enabled?: boolean;

  /**
   * 数据源编码（空=系统默认数据源）
   */
  datasourceCode?: string;
}

/**
 * 执行范式配置
 */
export interface ExecutionParadigmConfig {
  /**
   * 范式类型（EXECUTION_PARADIGM_OPTIONS 的 value 之一）
   */
  type: string;

  /**
   * 带工具的最大执行步数（默认10）
   */
  maxSteps?: number;

  /**
   * Reflexion 最大反思次数（默认2）
   */
  maxReflections?: number;

  /**
   * Self-Refine 最大修订次数（默认2）
   */
  maxRefinements?: number;
}

/**
 * 执行范式选项（type 与后端 ParadigmSpec 常量对齐，label 以中文名为主）
 */
export const EXECUTION_PARADIGM_OPTIONS = [
  { label: '推理行动（ReAct，默认）', value: 'react' },
  { label: '反思（Reflexion）', value: 'reflexion' },
  { label: '自我精炼（Self-Refine）', value: 'self-refine' },
  { label: '规划执行（Plan-and-Execute）', value: 'plan-execute' },
  { label: '并行规划（ReWoo）', value: 'rewoo' },
  { label: '问题分解（Self-Ask）', value: 'self-ask' },
  { label: '智能路由（Auto，实验）', value: 'auto' },
] as const;

/**
 * 执行范式选中后的中文说明（按 type 索引）
 */
export const EXECUTION_PARADIGM_DESC: Record<string, string> = {
  react: '默认执行方式：模型在"思考→行动→观察"循环中交替推理并调用工具，行为与未配置范式时一致',
  reflexion: '执行后由模型自评估，未通过则总结教训并从原始输入重新执行，可配置反思次数',
  'self-refine': '先生成初稿，再由模型批评，不满意时按批评意见迭代修订，可配置修订次数',
  'plan-execute': '先让模型制定分步计划，再逐步执行计划并汇总结果，适合多步骤任务',
  rewoo: '一次规划所有工具调用后并行执行，最后统一汇总作答，中间步骤不再调用模型',
  'self-ask': '将问题拆解为多个子问题，逐个独立回答后再汇总成最终答案',
  auto: '由模型根据问题自动选择合适的执行范式（实验功能，不保证稳定）',
};

/**
 * 反思类范式：需要展示反思/修订次数配置
 */
export const REFLECTION_PARADIGM_TYPES = ['reflexion', 'self-refine'];

/**
 * Token高消耗范式：存在评估/反思/子问题分解等额外多轮模型调用，选中时提示Token消耗
 * <p>
 * plan-execute（固定规划+汇总2次）、rewoo（固定2次且工具步零调用）、auto（仅+1次路由判定）不属明显增加
 * </p>
 */
export const TOKEN_HEAVY_PARADIGM_TYPES = ['reflexion', 'self-refine', 'self-ask'];

/**
 * Token高消耗范式的提示文案
 */
export const EXECUTION_PARADIGM_TOKEN_TIP = '该范式会触发多次额外模型调用（自评估、反思重试、问题分解等中间步骤），Token 消耗会明显高于默认 ReAct 模式，请留意用量与预算。';

/**
 * agentConfig 结构化配置（正向单源）
 */
export interface AgentConfigStructured {
  model?: string;

  /**
   * 处理器编码（缺省走默认路由：agentConfig.processor > agentCode匹配 > default）
   */
  processor?: string;

  systemPrompt?: string;
  maxIterations?: number;
  temperature?: number;
  bindingMode?: string;
  tools?: string[];
  mcpServers?: string[];
  skills?: string[];
  knowledgeBase?: KnowledgeBaseConfig;

  /**
   * 知识检索topK（顶层字段，默认5）
   */
  topK?: number;

  /**
   * 知识检索重排序开关（顶层字段，默认false关闭；全局总开关关闭时不生效）
   */
  rerankEnabled?: boolean;

  /**
   * 重排序模型编码（顶层字段，空=与主模型一致）
   */
  rerankModelCode?: string;

  /**
   * 执行范式（缺省/type=react 为默认 ReAct 循环）
   */
  executionParadigm?: ExecutionParadigmConfig;
  reasoning?: ReasoningConfig;

  /**
   * 图谱检索配置（默认关闭；开启后检索图谱证据与知识上下文一同注入）
   */
  graphRetrieval?: GraphRetrievalConfig;

  /**
   * Text2SQL数据问答配置（默认关闭；关闭时该智能体不响应数据问答）
   */
  text2sql?: Text2SqlConfig;
  streamOutput?: boolean;
}

/**
 * 图谱检索模式选项（与后端 GraphRetrievalMode 对齐，AUTO走服务端自动路由）
 */
export const GRAPH_RETRIEVAL_MODE_OPTIONS = [
  { label: 'AUTO（自动路由，默认）', value: 'AUTO' },
  { label: 'LOCAL（本地：实体邻域）', value: 'LOCAL' },
  { label: 'GLOBAL（全局：社区报告）', value: 'GLOBAL' },
  { label: 'HYBRID（混合：本地+全局）', value: 'HYBRID' },
  { label: 'MULTI_HOP（多跳路径）', value: 'MULTI_HOP' },
] as const;

/**
 * 绑定模式选项
 */
export const BINDING_MODE_OPTIONS = [
  { label: 'append（请求级覆盖与清单合并取并集）', value: 'append' },
  { label: 'replace（请求级覆盖替换非内置清单）', value: 'replace' },
];

/**
 * 能力挂载语义提示
 */
export const MOUNT_HINT =
  '未配置/清空 = 仅内置工具（BUILTIN 分类）与内置技能（trustLevel=BUILTIN）自动加载，MCP 不挂载；勾选清单 = 精确挂载非内置能力。';

/**
 * 校验JSON并格式化，非法时返回null
 * @param text
 * @return
 */
export const tryPrettyJson = (text?: string): string | null => {
  if (!text) return null;
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return null;
  }
};

/**
 * JSON格式化预览（非法时原样返回）
 * @param text
 * @return
 */
export const prettyJson = (text?: string): string => {
  if (!text) return '{}';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
};

/**
 * 归一化knowledgeBase载入形态：新格式数组[{kbCode,kbName}]与旧格式对象{kbCode|kbCodes,topK}
 * 统一转为内部表单形态{kbCodes,kbNames}，旧格式的topK提升到顶层
 * @param cfg
 * @return
 */
const normalizeKnowledgeBase = (cfg: AgentConfigStructured): void => {
  const kb = cfg.knowledgeBase as unknown;
  if (Array.isArray(kb)) {
    const items = kb.filter(
      (item): item is { kbCode: string; kbName?: string } =>
        Boolean(item) && typeof item === 'object' && Boolean((item as { kbCode?: unknown }).kbCode),
    );
    cfg.knowledgeBase = {
      kbCodes: items.map((item) => item.kbCode),
      kbNames: items.map((item) => (typeof item.kbName === 'string' ? item.kbName : '')),
    };
  } else if (kb && typeof kb === 'object') {
    const kbObj = kb as KnowledgeBaseConfig & {
      kbCode?: string;
      topK?: number;
      rerankEnabled?: boolean;
      rerankModelCode?: string;
    };
    if (cfg.topK == null && kbObj.topK != null) {
      cfg.topK = kbObj.topK;
    }
    // 旧格式对象内的重排序字段提升到顶层
    if (cfg.rerankEnabled == null && kbObj.rerankEnabled != null) {
      cfg.rerankEnabled = kbObj.rerankEnabled;
    }
    if (!cfg.rerankModelCode && kbObj.rerankModelCode) {
      cfg.rerankModelCode = kbObj.rerankModelCode;
    }
    cfg.knowledgeBase = {
      kbCodes: kbObj.kbCodes ?? (kbObj.kbCode ? [kbObj.kbCode] : []),
      kbNames: kbObj.kbNames,
    };
  }
};

/**
 * 归一化executionParadigm载入形态：字符串简写"reflexion"统一转为对象{type}，
 * 非法对象（无type）置为undefined走默认ReAct
 * @param cfg
 * @return
 */
const normalizeExecutionParadigm = (cfg: AgentConfigStructured): void => {
  const paradigm = cfg.executionParadigm as unknown;
  if (typeof paradigm === 'string' && paradigm) {
    cfg.executionParadigm = { type: paradigm };
  } else if (paradigm && typeof paradigm === 'object' && !Array.isArray(paradigm)) {
    const p = paradigm as ExecutionParadigmConfig;
    cfg.executionParadigm = p.type ? { type: p.type, maxSteps: p.maxSteps, maxReflections: p.maxReflections, maxRefinements: p.maxRefinements } : undefined;
  } else {
    cfg.executionParadigm = undefined;
  }
};

/**
 * 解析 agentConfig JSON（容错，非法或空返回空对象）
 * @param text
 * @return
 */
export const parseAgentConfig = (text?: string): AgentConfigStructured => {
  if (!text) return {};
  try {
    const obj = JSON.parse(text);
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      const cfg = obj as AgentConfigStructured;
      normalizeKnowledgeBase(cfg);
      normalizeExecutionParadigm(cfg);
      return cfg;
    }
    return {};
  } catch {
    return {};
  }
};

/**
 * 结构化配置序列化为 agentConfig JSON（两态语义：空值/空数组键不写入）
 * @param cfg
 * @param pretty
 * @return
 */
export const stringifyAgentConfig = (cfg: AgentConfigStructured, pretty = false): string => {
  const cleaned: Record<string, unknown> = {};
  if (cfg.model) cleaned.model = cfg.model;
  if (cfg.processor) cleaned.processor = cfg.processor;
  if (cfg.systemPrompt) cleaned.systemPrompt = cfg.systemPrompt;
  if (cfg.maxIterations != null) cleaned.maxIterations = cfg.maxIterations;
  if (cfg.temperature != null) cleaned.temperature = cfg.temperature;
  if (cfg.bindingMode) cleaned.bindingMode = cfg.bindingMode;
  if (cfg.tools?.length) cleaned.tools = cfg.tools;
  if (cfg.mcpServers?.length) cleaned.mcpServers = cfg.mcpServers;
  if (cfg.skills?.length) cleaned.skills = cfg.skills;
  const kb = cfg.knowledgeBase;
  if (kb?.kbCodes?.length) {
    // 数组对象形式：kbCode与kbName成对输出，便于配置JSON直读
    cleaned.knowledgeBase = kb.kbCodes.map((code, i) => {
      const name = kb.kbNames?.[i];
      return name ? { kbCode: code, kbName: name } : { kbCode: code };
    });
  }
  if (cfg.topK != null) cleaned.topK = cfg.topK;
  // 重排序开关为显式布尔（默认关闭也写入），重排模型空=与主模型一致不写入
  if (typeof cfg.rerankEnabled === 'boolean') cleaned.rerankEnabled = cfg.rerankEnabled;
  if (cfg.rerankModelCode) cleaned.rerankModelCode = cfg.rerankModelCode;
  // 两态语义：执行范式仅配置非react类型时写入，缺省=默认ReAct不写
  const paradigm = cfg.executionParadigm;
  if (paradigm?.type && paradigm.type !== 'react') {
    const paradigmOut: Record<string, unknown> = { type: paradigm.type };
    if (paradigm.maxSteps != null) paradigmOut.maxSteps = paradigm.maxSteps;
    if (paradigm.maxReflections != null) paradigmOut.maxReflections = paradigm.maxReflections;
    if (paradigm.maxRefinements != null) paradigmOut.maxRefinements = paradigm.maxRefinements;
    cleaned.executionParadigm = paradigmOut;
  }
  // 两态语义：推理开启或显式隐藏思维链时写入，默认态不写
  const reasoningCfg = cfg.reasoning;
  if (reasoningCfg?.enabled || reasoningCfg?.showThinking === false) {
    const reasoningOut: Record<string, unknown> = {};
    if (reasoningCfg.enabled) reasoningOut.enabled = true;
    if (reasoningCfg.effort) reasoningOut.effort = reasoningCfg.effort;
    if (reasoningCfg.showThinking === false) reasoningOut.showThinking = false;
    cleaned.reasoning = reasoningOut;
  }
  if (cfg.streamOutput === false) cleaned.streamOutput = false;
  // 两态语义：图谱检索开启时写入配置块，默认关闭不写
  const graph = cfg.graphRetrieval;
  if (graph?.enabled) {
    const graphOut: Record<string, unknown> = { enabled: true };
    if (graph.kbCodes?.length) graphOut.kbCodes = graph.kbCodes;
    if (graph.mode) graphOut.mode = graph.mode;
    if (graph.topK != null) graphOut.topK = graph.topK;
    cleaned.graphRetrieval = graphOut;
  }
  // 两态语义：Text2SQL开启时写入配置块，默认关闭不写
  const text2sql = cfg.text2sql;
  if (text2sql?.enabled) {
    const t2sOut: Record<string, unknown> = { enabled: true };
    if (text2sql.datasourceCode) t2sOut.datasourceCode = text2sql.datasourceCode;
    cleaned.text2sql = t2sOut;
  }
  return JSON.stringify(cleaned, null, pretty ? 2 : 0);
};

/**
 * 能力挂载四域摘要
 * @param cfg
 * @return
 */
export const configSummary = (cfg: AgentConfigStructured): string => {
  const parts: string[] = [];
  if (cfg.model) parts.push(`模型 ${cfg.model}`);
  if (cfg.temperature != null) parts.push(`温度 ${cfg.temperature}`);
  if (cfg.maxIterations != null) parts.push(`最大迭代 ${cfg.maxIterations}`);
  if (cfg.executionParadigm?.type && cfg.executionParadigm.type !== 'react') {
    const matched = EXECUTION_PARADIGM_OPTIONS.find((opt) => opt.value === cfg.executionParadigm?.type);
    parts.push(`范式 ${matched ? matched.label.split('（')[0] : cfg.executionParadigm.type}`);
  }
  if (cfg.reasoning?.enabled) parts.push(`推理模式 ${cfg.reasoning.effort ?? 'medium'}`);
  if (cfg.rerankEnabled) parts.push(`重排序 ${cfg.rerankModelCode || '主模型'}`);
  if (cfg.graphRetrieval?.enabled) {
    parts.push(
      `图谱 ${cfg.graphRetrieval.kbCodes?.length ?? 0}库${cfg.graphRetrieval.mode ? ` ${cfg.graphRetrieval.mode}` : ''}`,
    );
  }
  if (cfg.processor) {
    parts.push(`处理器 ${cfg.processor}`);
  }
  if (cfg.text2sql?.enabled) {
    parts.push(`Text2SQL ${cfg.text2sql.datasourceCode ?? '默认数据源'}`);
  }
  if (cfg.reasoning?.showThinking === false) parts.push('隐藏思维链');
  if (cfg.streamOutput === false) parts.push('非流式输出');
  if (cfg.bindingMode) parts.push(`绑定 ${cfg.bindingMode}`);
  parts.push(
    `工具 ${cfg.tools?.length ?? 0} / MCP ${cfg.mcpServers?.length ?? 0} / 技能 ${
      cfg.skills?.length ?? 0
    } / 知识库 ${cfg.knowledgeBase?.kbCodes?.length ?? 0}`,
  );
  return parts.join(' · ');
};
