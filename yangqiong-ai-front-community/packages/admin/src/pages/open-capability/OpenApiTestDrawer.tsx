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
import React, { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Collapse,
  Drawer,
  Input,
  Space,
  Spin,
  Tag,
  Upload,
  message,
} from 'antd';
import { CaretRightOutlined, UploadOutlined } from '@ant-design/icons';
import type { CapabilitySpec } from '@yangqiong/shared';
import CodeBlock from './CodeBlock';

/**
 * 单次接口调用结果
 */
interface ApiSendResult {
  httpStatus: number;
  durationMs: number;
  body: string;
}

/**
 * 请求方法标签（GET蓝/POST绿）
 * @param props
 * @return
 */
const MethodTag: React.FC<{ method: string }> = ({ method }) => (
  <Tag color={method === 'GET' ? 'geekblue' : 'green'} style={{ fontFamily: 'monospace' }}>
    {method}
  </Tag>
);

/**
 * 面板标题（方法 + 路径 + 文档锚点）
 * @param props
 * @return
 */
const PanelTitle: React.FC<{ no: number; method: string; path: string; doc: string }> = ({
  no,
  method,
  path,
  doc,
}) => (
  <Space size={8} wrap>
    <span style={{ fontWeight: 600 }}>{`${no}. ${doc}`}</span>
    <MethodTag method={method} />
    <code style={{ fontSize: 12 }}>{path}</code>
  </Space>
);

/**
 * 接口响应视图（HTTP状态/耗时 + 原始报文）
 * @param props
 * @return
 */
const ResultView: React.FC<{ result: ApiSendResult | null; loading: boolean }> = ({
  result,
  loading,
}) => {
  if (loading) {
    return (
      <div style={{ marginTop: 8 }}>
        <Spin size="small" /> <span style={{ fontSize: 12 }}>请求中…</span>
      </div>
    );
  }
  if (!result) {
    return null;
  }
  return (
    <div style={{ marginTop: 8 }}>
      <Space size={8} style={{ marginBottom: 4 }}>
        <Tag color={result.httpStatus === 200 ? 'success' : 'error'}>
          HTTP {result.httpStatus}
        </Tag>
        <span style={{ fontSize: 12 }}>{result.durationMs} ms</span>
      </Space>
      <CodeBlock value={result.body} rows={12} />
    </div>
  );
};

/**
 * 控制类事件的人类可读提示（澄清/审批/确认/预算）
 */
const CONTROL_KIND_NOTICES: Record<string, string> = {
  CLARIFICATION_REQUIRED:
    '模型请求用户澄清（入参缺少模型所需信息），本次流已结束且无正文输出，补全 arguments 后重试即可。',
  APPROVAL_REQUIRED: '模型请求工具审批（APPROVAL_REQUIRED），需要调用方响应审批后执行才会继续。',
  CONFIRM_REQUIRED: '引擎请求工具确认（CONFIRM_REQUIRED），需要批准后执行才会继续。',
  BUDGET_WARNING: '收到预算告警（BUDGET_WARNING），Token/成本超出阈值，请注意消耗。',
};

/**
 * 生成入参模板（依据入参Schema属性，字符串留空、数组给单元素示例）
 * @param spec
 * @return
 */
const buildArgumentsTemplate = (spec: CapabilitySpec | null): Record<string, unknown> => {
  if (!spec?.inputSchemaContent) {
    return {};
  }
  try {
    const schema = JSON.parse(spec.inputSchemaContent) as {
      properties?: Record<string, { type?: string }>;
    };
    const args: Record<string, unknown> = {};
    Object.entries(schema.properties ?? {}).forEach(([key, prop]) => {
      if (prop.type === 'number' || prop.type === 'integer') {
        args[key] = 0;
      } else if (prop.type === 'boolean') {
        args[key] = true;
      } else if (prop.type === 'array') {
        args[key] = [];
      } else {
        args[key] = '';
      }
    });
    return args;
  } catch {
    return {};
  }
};

/**
 * 开放接口对接测试抽屉
 * <p>
 * 面向接口对接人，按《能力开放平台调用方对接说明》逐个接口发起真实请求并查看原始报文，
 * 覆盖：能力列表/详情查询、同步/流式/异步调用、异步任务查询、数据上下文推送与查询、文件上传。
 * </p>
 * @param props
 * @return
 */
const OpenApiTestDrawer: React.FC<{
  open: boolean;
  spec: CapabilitySpec | null;
  onClose: () => void;
}> = ({ open, spec, onClose }) => {
  const [results, setResults] = useState<Record<string, ApiSendResult | null>>({});
  const [loadings, setLoadings] = useState<Record<string, boolean>>({});
  const [detailCode, setDetailCode] = useState<string>('');
  const [runBody, setRunBody] = useState<string>('');
  const [asyncBody, setAsyncBody] = useState<string>('');
  const [streamBody, setStreamBody] = useState<string>('');
  const [taskId, setTaskId] = useState<string>('');
  const [contextBody, setContextBody] = useState<string>('');
  const [contextRef, setContextRef] = useState<string>('');
  const [streamEvents, setStreamEvents] = useState<string[]>([]);
  const [streamError, setStreamError] = useState<string>('');

  /**
   * 流结束信息（耗时/是否手动中断），null=未开始或进行中
   */
  const [streamEnd, setStreamEnd] = useState<{ durationMs: number; aborted: boolean } | null>(null);
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const capabilityCode = spec?.code ?? '';

  const runBodyTemplate = useMemo(
    () =>
      JSON.stringify(
        {
          capability: capabilityCode,
          arguments: buildArgumentsTemplate(spec),
          dedupKey: `debug-${Date.now()}`,
          dataContextRefs: [],
          caller: 'api-tester',
          scopeId: 'default',
        },
        null,
        2,
      ),
    [capabilityCode, spec],
  );

  const contextBodyTemplate = useMemo(
    () =>
      JSON.stringify(
        { type: 'demo-note', name: '演示数据', content: '在此填写推送给能力的上下文内容…', ttlSeconds: 1800 },
        null,
        2,
      ),
    [],
  );

  /**
   * 打开抽屉时初始化各请求体预填值
   */
  const initDefaults = () => {
    setDetailCode(capabilityCode);
    setRunBody(runBodyTemplate);
    setAsyncBody(runBodyTemplate);
    setStreamBody(runBodyTemplate);
    setContextBody(contextBodyTemplate);
    setResults({});
    setLoadings({});
    setTaskId('');
    setContextRef('');
    setStreamEvents([]);
    setStreamError('');
    setStreamEnd(null);
  };

  /**
   * 解析事件流：拼接正文增量并统计各类型事件
   */
  const streamAnalysis = useMemo(() => {
    let text = '';
    let thinking = 0;
    let tool = 0;
    let controlKind = '';
    streamEvents.forEach((line) => {
      try {
        const event = JSON.parse(line) as { kind?: string; payload?: string };
        if (event.kind === 'TEXT_DELTA') {
          text += event.payload ?? '';
        } else if (event.kind === 'THINKING_DELTA') {
          thinking += 1;
        } else if (event.kind === 'TOOL_CALL_DELTA') {
          tool += 1;
        } else if (event.kind) {
          controlKind = event.kind;
        }
      } catch {
        // 非JSON事件行不参与统计
      }
    });
    return { text, thinking, tool, controlKind };
  }, [streamEvents]);

  /**
   * 发送JSON请求并记录原始响应报文
   * @param key 面板标识
   * @param method
   * @param path
   * @param body 请求体对象（GET时为undefined）
   */
  const sendJson = async (key: string, method: string, path: string, body?: unknown) => {
    setLoadings((prev) => ({ ...prev, [key]: true }));
    const startedAt = Date.now();
    try {
      const resp = await fetch(`/open/v1${path}`, {
        method,
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await resp.text();
      let pretty = text;
      try {
        pretty = JSON.stringify(JSON.parse(text), null, 2);
      } catch {
        // 非JSON响应原样展示
      }
      setResults((prev) => ({
        ...prev,
        [key]: { httpStatus: resp.status, durationMs: Date.now() - startedAt, body: pretty },
      }));
      return text;
    } catch (err) {
      setResults((prev) => ({
        ...prev,
        [key]: {
          httpStatus: 0,
          durationMs: Date.now() - startedAt,
          body: `请求失败: ${err instanceof Error ? err.message : String(err)}`,
        },
      }));
      return null;
    } finally {
      setLoadings((prev) => ({ ...prev, [key]: false }));
    }
  };

  /**
   * 流式调用：逐块读取SSE事件并追加展示，错误常驻显示、结束后给出统计
   */
  const sendStream = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(streamBody);
    } catch (e) {
      setStreamEvents([]);
      setStreamError(`请求体不是有效的JSON：${e instanceof Error ? e.message : String(e)}`);
      return;
    }
    setStreaming(true);
    setStreamEvents([]);
    setStreamError('');
    setStreamEnd(null);
    const startedAt = Date.now();
    abortRef.current = new AbortController();
    try {
      const resp = await fetch('/open/v1/run/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: JSON.stringify(parsed),
        signal: abortRef.current.signal,
      });
      if (!resp.ok || !resp.body) {
        const text = await resp.text().catch(() => '');
        setResults((prev) => ({
          ...prev,
          stream: { httpStatus: resp.status, durationMs: 0, body: text || `HTTP ${resp.status}` },
        }));
        return;
      }
      // 前置校验失败等场景会返回JSON错误体（HTTP 200但非SSE），直接解析展示错误原因
      const contentType = resp.headers.get('content-type') ?? '';
      if (!contentType.includes('text/event-stream')) {
        const text = await resp.text().catch(() => '');
        let reason = text;
        try {
          reason = (JSON.parse(text) as { message?: string }).message ?? text;
        } catch {
          // 非JSON响应原样展示
        }
        setStreamError(`服务端返回非SSE响应（${contentType || '未知类型'}）：${reason}`);
        return;
      }
      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        lines.forEach((line) => {
          const trimmed = line.trim();
          if (trimmed.startsWith('data:')) {
            setStreamEvents((prev) => [...prev, trimmed.slice(5).trim()]);
          }
        });
      }
      setStreamEnd({ durationMs: Date.now() - startedAt, aborted: false });
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        setStreamEnd({ durationMs: Date.now() - startedAt, aborted: true });
      } else {
        setStreamError(err instanceof Error ? err.message : '流式调用失败');
      }
    } finally {
      setStreaming(false);
    }
  };

  /**
   * 上传文件到能力知识库
   * @param file
   */
  const uploadFile = async (file: File) => {
    setLoadings((prev) => ({ ...prev, files: true }));
    const startedAt = Date.now();
    try {
      const form = new FormData();
      form.append('capability', capabilityCode);
      form.append('file', file);
      const resp = await fetch('/open/v1/files', { method: 'POST', body: form });
      const text = await resp.text();
      let pretty = text;
      try {
        pretty = JSON.stringify(JSON.parse(text), null, 2);
      } catch {
        // 非JSON响应原样展示
      }
      setResults((prev) => ({
        ...prev,
        files: { httpStatus: resp.status, durationMs: Date.now() - startedAt, body: pretty },
      }));
    } finally {
      setLoadings((prev) => ({ ...prev, files: false }));
    }
    return false;
  };

  const resultOf = (key: string) => results[key] ?? null;
  const loadingOf = (key: string) => loadings[key] ?? false;

  /**
   * 通用GET面板动作
   */
  const runGet = (key: string, path: string) => sendJson(key, 'GET', path);

  /**
   * 同步调用并联动任务查询面板提示
   */
  const runSync = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(runBody);
    } catch {
      message.error('请求体不是有效的JSON');
      return;
    }
    await sendJson('run', 'POST', '/run', parsed);
  };

  /**
   * 异步调用并把返回taskId联动到任务查询面板
   */
  const runAsync = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(asyncBody);
    } catch {
      message.error('请求体不是有效的JSON');
      return;
    }
    const text = await sendJson('async', 'POST', '/run/async', parsed);
    if (text) {
      try {
        const ref = JSON.parse(text) as { data?: { taskId?: string } };
        if (ref.data?.taskId) {
          setTaskId(ref.data.taskId);
        }
      } catch {
        // 响应非JSON时不联动
      }
    }
  };

  /**
   * 推送数据上下文并把返回ref联动到查询面板
   */
  const pushContext = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(contextBody);
    } catch {
      message.error('请求体不是有效的JSON');
      return;
    }
    const text = await sendJson('contextPush', 'POST', '/context', parsed);
    if (text) {
      try {
        const ref = JSON.parse(text) as { data?: { ref?: string } };
        if (ref.data?.ref) {
          setContextRef(ref.data.ref);
        }
      } catch {
        // 响应非JSON时不联动
      }
    }
  };

  /**
   * 流结束后的统计与提示（null=未开始或进行中）
   */
  const streamEndAlert =
    !streaming && streamEnd ? (
      <Alert
        type={
          streamEnd.aborted || streamAnalysis.controlKind
            ? 'warning'
            : streamEvents.length === 0
              ? 'info'
              : 'success'
        }
        showIcon
        style={{ marginTop: 8 }}
        message={
          streamEnd.aborted
            ? `已手动中断：共收到 ${streamEvents.length} 条事件，耗时 ${streamEnd.durationMs} ms`
            : streamEvents.length === 0
              ? `连接已结束，未收到任何SSE事件，耗时 ${streamEnd.durationMs} ms`
              : `流已结束：共 ${streamEvents.length} 条事件（思考增量 ${streamAnalysis.thinking}、工具调用增量 ${streamAnalysis.tool}），正文 ${streamAnalysis.text.length} 字，耗时 ${streamEnd.durationMs} ms`
        }
        description={CONTROL_KIND_NOTICES[streamAnalysis.controlKind]}
      />
    ) : null;

  const collapseItems = [
    {
      key: 'list',
      label: <PanelTitle no={1} method="GET" path="/open/v1/capabilities" doc="查询能力列表" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="获取可用能力清单，调用方据此选择要调用的能力编码。"
          />
          <Button
            type="primary"
            size="small"
            icon={<CaretRightOutlined />}
            loading={loadingOf('list')}
            onClick={() => runGet('list', '/capabilities')}
          >
            发送
          </Button>
          <ResultView result={resultOf('list')} loading={loadingOf('list')} />
        </div>
      ),
    },
    {
      key: 'detail',
      label: <PanelTitle no={2} method="GET" path="/open/v1/capabilities/{code}" doc="查询能力详情" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="返回能力的 argumentHints（参数名/必填/说明），是组装 arguments 的唯一依据。"
          />
          <Space.Compact style={{ width: 320, marginBottom: 8 }}>
            <Input
              value={detailCode}
              onChange={(e) => setDetailCode(e.target.value)}
              placeholder="能力编码"
            />
            <Button
              type="primary"
              loading={loadingOf('detail')}
              onClick={() => runGet('detail', `/capabilities/${encodeURIComponent(detailCode)}`)}
            >
              发送
            </Button>
          </Space.Compact>
          <ResultView result={resultOf('detail')} loading={loadingOf('detail')} />
        </div>
      ),
    },
    {
      key: 'run',
      label: <PanelTitle no={3} method="POST" path="/open/v1/run" doc="同步调用（主流方式）" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="同步等待能力执行结果；dedupKey 用于幂等去重（相同键+相同入参指纹在有效期内直接返回缓存）。"
          />
          <Input.TextArea
            value={runBody}
            onChange={(e) => setRunBody(e.target.value)}
            rows={10}
            style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 8 }}
          />
          <Button
            type="primary"
            icon={<CaretRightOutlined />}
            loading={loadingOf('run')}
            onClick={runSync}
          >
            发送
          </Button>
          <ResultView result={resultOf('run')} loading={loadingOf('run')} />
        </div>
      ),
    },
    {
      key: 'stream',
      label: <PanelTitle no={4} method="POST" path="/open/v1/run/stream" doc="流式调用（SSE）" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="SSE事件流：TEXT_DELTA 文本增量 / THINKING_DELTA 思考增量 / TOOL_CALL_DELTA 工具调用增量等，逐条展示并自动拼接正文；真实AI调用通常需要数十秒，请耐心等待。"
          />
          <Input.TextArea
            value={streamBody}
            onChange={(e) => setStreamBody(e.target.value)}
            rows={10}
            style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 8 }}
          />
          <Space>
            <Button
              type="primary"
              icon={<CaretRightOutlined />}
              loading={streaming}
              onClick={sendStream}
            >
              发送
            </Button>
            {streaming && (
              <Button
                danger
                onClick={() => {
                  abortRef.current?.abort();
                }}
              >
                中断
              </Button>
            )}
          </Space>
          {streamError && (
            <Alert type="error" showIcon closable style={{ marginTop: 8 }} message={streamError} />
          )}
          {streaming && streamEvents.length === 0 && !streamError && (
            <div style={{ marginTop: 8 }}>
              <Spin size="small" /> <span style={{ fontSize: 12 }}>已连接，等待事件…</span>
            </div>
          )}
          {streamAnalysis.text && (
            <div style={{ marginTop: 8 }}>
              <div style={{ fontSize: 12, marginBottom: 4 }}>拼接正文（TEXT_DELTA 增量合并）</div>
              <CodeBlock value={streamAnalysis.text} rows={8} />
            </div>
          )}
          {streamEvents.length > 0 && (
            <div style={{ marginTop: 8 }}>
              <div style={{ fontSize: 12, marginBottom: 4 }}>
                原始事件流（共 {streamEvents.length} 条）
              </div>
              <CodeBlock
                value={streamEvents.map((e, i) => `[${i + 1}] ${e}`).join('\n')}
                rows={12}
              />
            </div>
          )}
          {streamEndAlert}
          <ResultView result={resultOf('stream')} loading={loadingOf('stream')} />
        </div>
      ),
    },
    {
      key: 'async',
      label: <PanelTitle no={5} method="POST" path="/open/v1/run/async" doc="异步调用" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="提交异步任务返回 taskId，适用于长耗时能力；返回后自动填充到下一步的任务查询。"
          />
          <Input.TextArea
            value={asyncBody}
            onChange={(e) => setAsyncBody(e.target.value)}
            rows={10}
            style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 8 }}
          />
          <Button
            type="primary"
            icon={<CaretRightOutlined />}
            loading={loadingOf('async')}
            onClick={runAsync}
          >
            发送
          </Button>
          <ResultView result={resultOf('async')} loading={loadingOf('async')} />
        </div>
      ),
    },
    {
      key: 'task',
      label: <PanelTitle no={6} method="GET" path="/open/v1/tasks/{taskId}" doc="查询异步任务" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="执行中返回 success=false（以 errorMessage 区分未完成与失败），完成后返回最终结果。"
          />
          <Space.Compact style={{ width: 480, marginBottom: 8 }}>
            <Input
              value={taskId}
              onChange={(e) => setTaskId(e.target.value)}
              placeholder="异步调用返回的 taskId"
            />
            <Button
              type="primary"
              loading={loadingOf('task')}
              onClick={() => runGet('task', `/tasks/${encodeURIComponent(taskId)}`)}
            >
              查询
            </Button>
          </Space.Compact>
          <ResultView result={resultOf('task')} loading={loadingOf('task')} />
        </div>
      ),
    },
    {
      key: 'contextPush',
      label: <PanelTitle no={7} method="POST" path="/open/v1/context" doc="推送数据上下文" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="大入参先推送上下文换取 ref，再在 run 的 dataContextRefs 中引用；返回后自动填充到下一步查询。"
          />
          <Input.TextArea
            value={contextBody}
            onChange={(e) => setContextBody(e.target.value)}
            rows={8}
            style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 8 }}
          />
          <Button
            type="primary"
            icon={<CaretRightOutlined />}
            loading={loadingOf('contextPush')}
            onClick={pushContext}
          >
            发送
          </Button>
          <ResultView result={resultOf('contextPush')} loading={loadingOf('contextPush')} />
        </div>
      ),
    },
    {
      key: 'contextGet',
      label: <PanelTitle no={8} method="GET" path="/open/v1/context/{ref}" doc="查询数据上下文" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="按 ref 查询已推送的上下文内容，用于核对推送是否生效。"
          />
          <Space.Compact style={{ width: 480, marginBottom: 8 }}>
            <Input
              value={contextRef}
              onChange={(e) => setContextRef(e.target.value)}
              placeholder="推送上下文返回的 ref"
            />
            <Button
              type="primary"
              loading={loadingOf('contextGet')}
              onClick={() => runGet('contextGet', `/context/${encodeURIComponent(contextRef)}`)}
            >
              查询
            </Button>
          </Space.Compact>
          <ResultView result={resultOf('contextGet')} loading={loadingOf('contextGet')} />
        </div>
      ),
    },
    {
      key: 'files',
      label: <PanelTitle no={9} method="POST" path="/open/v1/files" doc="上传文件" />,
      children: (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 8 }}
            message="上传文件到能力配置的知识库（调用方不能自选知识库），解析完成后即可被该能力的检索引用。"
          />
          <Upload beforeUpload={uploadFile} showUploadList={false} maxCount={1}>
            <Button icon={<UploadOutlined />} loading={loadingOf('files')}>
              选择文件上传
            </Button>
          </Upload>
          <ResultView result={resultOf('files')} loading={loadingOf('files')} />
        </div>
      ),
    },
  ];

  return (
    <Drawer
      title={spec ? `开放接口对接测试：${spec.name || spec.code}` : '开放接口对接测试'}
      width={760}
      open={open}
      onClose={onClose}
      destroyOnClose
      afterOpenChange={(visible) => {
        if (visible) {
          initDefaults();
        }
      }}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="以下面板对应《能力开放平台调用方对接说明》中的各接口，可直接发送真实请求查看原始报文，按顺序操作即可走通完整对接流程。"
        description={
          <span style={{ fontSize: 12 }}>
            Base URL：<code>/open/v1</code>（社区版默认免凭证；企业版需携带 X-API-Key）
          </span>
        }
      />
      <Collapse
        items={collapseItems}
        defaultActiveKey={['list']}
        style={{ backgroundColor: 'transparent' }}
      />
    </Drawer>
  );
};

export default OpenApiTestDrawer;
