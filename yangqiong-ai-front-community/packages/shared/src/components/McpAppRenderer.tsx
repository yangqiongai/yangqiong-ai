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
import { useMemo } from 'react';

/**
 * MCP Apps UI 声明
 * 字段与后端 McpAppsUiDeclaration 对齐：resourceUri/renderHint/csp
 */
export interface McpAppsUiDeclarationInfo {
  resourceUri?: string;

  renderHint?: string;

  csp?: string;
}

/**
 * MCP Apps UI 声明提取结果
 */
export interface McpAppsUiExtractResult {
  declaration: McpAppsUiDeclarationInfo;

  payload?: string;
}

/**
 * MCP Apps 渲染器属性
 */
export interface McpAppRendererProps {
  /**
   * 工具结果携带的 UI 声明（meta.ui）
   */
  declaration?: McpAppsUiDeclarationInfo | null;

  /**
   * 工具结果载荷文本
   */
  payload?: string;

  /**
   * 白名单 render_allowed（0/1），缺省视为已放行（声明本身仅对放行资源下发）
   */
  renderAllowed?: number | boolean;
}

/**
 * 表单模板类型
 */
const HINT_FORM = 'form';

/**
 * 图表模板类型
 */
const HINT_CHART = 'chart';

/**
 * 卡片模板类型
 */
const HINT_CARD = 'card';

/**
 * 默认内容安全策略（与后端 McpAppsUiContract.CSP_POLICY 对齐，拒绝外链与内联脚本）
 */
const DEFAULT_CSP = "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'";

/**
 * 合法渲染提示集合
 */
const VALID_HINTS = [HINT_FORM, HINT_CHART, HINT_CARD];

/**
 * HTML 转义防注入
 * @param text
 * @return
 */
const escapeHtml = (text?: string): string =>
  (text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

/**
 * 校验声明 CSP 是否与后端契约同等严格（default-src 'none' 且 script-src 不放行外链/内联）
 * @param csp
 * @return
 */
const isStrictCsp = (csp?: string): boolean => {
  if (!csp) {
    return false;
  }
  return (
    /default-src\s+'none'/.test(csp) &&
    /script-src/.test(csp) &&
    !/script-src[^;]*('unsafe-inline'|\*|https?:\/\/)/.test(csp)
  );
};

/**
 * 按渲染提示构建 MVP 模板 HTML（与后端 McpAppsUiContract.renderUiHtml 模板结构对齐）
 * @param hint
 * @param payload
 * @param csp
 * @return
 */
const buildAppHtml = (hint: string, payload: string, csp: string): string => {
  let body: string;
  if (hint === HINT_FORM) {
    body = '<form class="mcp-app-form"><input name="value" placeholder="请输入"/>'
      + '<button type="submit">提交</button></form>';
  } else if (hint === HINT_CHART) {
    body = `<div class="mcp-app-chart" data-points='${escapeHtml(payload)}'></div>`;
  } else {
    body = `<div class="mcp-app-card">${escapeHtml(payload)}</div>`;
  }
  return `<!DOCTYPE html><html><head><meta charset="utf-8"/>`
    + `<meta http-equiv="Content-Security-Policy" content="${escapeHtml(csp)}"/>`
    + `</head><body class="mcp-app mcp-app-${hint}" style="margin:0;font-family:inherit;">${body}</body></html>`;
};

/**
 * 从工具结果载荷中提取 MCP Apps UI 声明
 * 数据来源契约：后端 McpAppsUiContract 在工具 meta 携带 {ui:{resourceUri,renderHint,csp}}，
 * 平台对话流当前仅透传文本，约定工具结果文本内嵌 ```json {"ui":{...},"payload":"..."} 代码块作为声明载体
 * @param raw 工具结果原文（字符串/对象均可）
 * @return 声明与载荷，无声明时返回 null
 */
export const extractMcpAppsUiDeclaration = (raw?: unknown): McpAppsUiExtractResult | null => {
  if (!raw) {
    return null;
  }
  const candidates: string[] = [];
  if (typeof raw === 'string') {
    candidates.push(raw);
    const fencePattern = /```(?:json)?\s*([\s\S]*?)```/g;
    let match = fencePattern.exec(raw);
    while (match) {
      candidates.push(match[1]);
      match = fencePattern.exec(raw);
    }
  } else {
    try {
      candidates.push(JSON.stringify(raw));
    } catch {
      return null;
    }
  }
  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate) as { ui?: unknown; payload?: unknown };
      const ui = parsed?.ui;
      if (
        ui != null &&
        typeof ui === 'object' &&
        typeof (ui as McpAppsUiDeclarationInfo).resourceUri === 'string'
      ) {
        return {
          declaration: ui as McpAppsUiDeclarationInfo,
          payload: typeof parsed.payload === 'string' ? parsed.payload : undefined,
        };
      }
    } catch {
      // 非 JSON 候选内容跳过
    }
  }
  return null;
};

/**
 * MCP Apps 渲染器
 * 按声明 renderHint 渲染 form/chart/card 三类 MVP 模板；无声明或 render_allowed=0 不渲染（返回 null）；
 * iframe 沙箱不授予脚本权限，CSP 仅接受与后端契约同等严格的策略，否则回落默认策略，拒绝外链脚本
 */
export const McpAppRenderer = ({ declaration, payload, renderAllowed }: McpAppRendererProps) => {
  const srcDoc = useMemo(() => {
    if (!declaration || !declaration.resourceUri) {
      return null;
    }
    const allowed =
      renderAllowed === undefined || renderAllowed === null || renderAllowed === 1 || renderAllowed === true;
    if (!allowed) {
      return null;
    }
    const hint = VALID_HINTS.includes(declaration.renderHint ?? '')
      ? (declaration.renderHint as string)
      : HINT_CARD;
    const csp = isStrictCsp(declaration.csp) ? (declaration.csp as string) : DEFAULT_CSP;
    return buildAppHtml(hint, payload ?? '', csp);
  }, [declaration, payload, renderAllowed]);

  if (!srcDoc) {
    return null;
  }

  return (
    <iframe
      title={declaration?.resourceUri ?? 'mcp-app'}
      srcDoc={srcDoc}
      sandbox={declaration?.renderHint === HINT_FORM ? 'allow-forms' : undefined}
      style={{
        display: 'block',
        width: '100%',
        minHeight: 96,
        border: '1px solid #f0f0f0',
        borderRadius: 8,
        background: '#fff',
        marginTop: 8,
      }}
    />
  );
};

export default McpAppRenderer;
