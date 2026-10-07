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
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  App,
  Breadcrumb,
  Button,
  Input,
  Select,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CopyOutlined,
  EyeOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  PaperClipOutlined,
  PlusOutlined,
  SendOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { McpAppRenderer } from '@yangqiong/shared';
import type { AgentChatRequest, ConversationSessionInfo, WorkspaceItem } from '@yangqiong/shared';
import {
  LOCAL_AGENT_CODE,
  MAX_CHAT_FILES,
  collapseFileRefs,
  copyToClipboard,
  diffProducedFiles,
  listAllFiles,
  restoreMessagesFromExport,
  selectDefaultWorkspace,
  truncateFileContent,
  useAgentChat,
  useApprovalRequests,
} from '@yangqiong/shared/chat';
import type { ChatFileRef, ChatMessage } from '@yangqiong/shared/chat';
import { api } from '@/services';
import { AgentIconView } from '@/components/AgentIcon';
import { useAuthStore } from '@/store/auth-store';
import { ApprovalCards } from '@/pages/chat/components/ApprovalCards';
import { ClarificationCards } from '@/pages/chat/components/ClarificationCards';
import { ConfirmCards } from '@/pages/chat/components/ConfirmCards';
import { MarkdownContent } from '@/pages/chat/components/ChatMarkdown';
import {
  readWorkspaceBinding,
  useWorkspaces,
  writeWorkspaceBinding,
} from '@/pages/chat/components/useWorkspaces';
import { WorkspaceSelector } from '@/pages/chat/components/WorkspaceSelector';
import { APPROVAL_MODE_META, WorkspaceManageModal } from '@/pages/chat/components/WorkspaceManageModal';
import { FileDocPanel } from './FileDocPanel';
import { FileTreePanel } from './FileTreePanel';
import { fileAccent } from './FileTypeIcon';

const { TextArea } = Input;
const { Text } = Typography;

/**
 * 本轮产生文件列表（≤3个全展示，超出折叠展开，点击预览）
 */
const ProducedFilesPanel: React.FC<{
  files: ChatFileRef[];
  onPreview: (path: string, all: string[]) => void;
}> = ({ files, onPreview }) => {
  const [expanded, setExpanded] = useState(false);
  const visible = expanded || files.length <= 3 ? files : files.slice(0, 3);
  const allPaths = files.map((f) => f.path);
  return (
    <div
      style={{
        marginTop: 8,
        paddingTop: 8,
        borderTop: '1px dashed var(--wb-border)',
      }}
    >
      <Text
        type="secondary"
        style={{ fontSize: 12, color: 'var(--wb-dim)', display: 'block', marginBottom: 6 }}
      >
        本轮产生文件（{files.length}）
      </Text>
      <Space size={[6, 6]} wrap>
        {visible.map((f) => {
          const accent = fileAccent(f.name);
          return (
            <Tag
              key={f.path}
              title={f.path}
              onClick={() => onPreview(f.path, allPaths)}
              className="wb-file-chip"
              style={{
                marginInlineEnd: 0,
                background: `${accent}14`,
                border: `1px solid ${accent}30`,
                color: accent,
              }}
            >
              <EyeOutlined style={{ marginInlineEnd: 4 }} />
              {f.name}
            </Tag>
          );
        })}
        {files.length > 3 && (
          <Button
            type="link"
            size="small"
            style={{ padding: 0, fontSize: 12 }}
            onClick={() => setExpanded(!expanded)}
          >
            {expanded ? '收起' : `展开全部（${files.length}）`}
          </Button>
        )}
      </Space>
    </div>
  );
};

/**
 * 本地工作台
 */
export const WorkbenchPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const userId = user?.id;

  // 雪花ID超出Number安全范围，选中工作区id全程保持字符串避免精度丢失
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string>();
  const [input, setInput] = useState<string>('');

  /**
   * 澄清回答提交中
   */
  const [clarificationSubmitting, setClarificationSubmitting] = useState<boolean>(false);

  /**
   * 引擎确认提交中
   */
  const [confirmSubmitting, setConfirmSubmitting] = useState<boolean>(false);

  // 对话消息状态与会话调度（共享hook：消息、会话ID、流式回调去重、MCP UI提取、失败兜底）
  const {
    messages,
    setMessages,
    sessionId,
    setSessionId,
    sending,
    clarifications,
    setClarifications,
    confirms,
    setConfirms,
    ensureSessionId,
    streamCallbacks,
    runAssistantStream,
    sendMessage,
    reset: resetChat,
  } = useAgentChat(api.agent, {
    onError: (msg) => message.error(msg),
    errorPrefix: '请求失败：',
  });

  const [manageOpen, setManageOpen] = useState(false);
  const [manageInitialAction, setManageInitialAction] = useState<'default-create'>();
  const [treeCollapsed, setTreeCollapsed] = useState(false);
  const [treeWidth, setTreeWidth] = useState(280);

  /**
   * 文件树刷新信号（审批处理完成、消息流结束后自增触发自动刷新）
   */
  const [treeRefreshKey, setTreeRefreshKey] = useState(0);

  /**
   * 审批层级切换保存中
   */
  const [savingMode, setSavingMode] = useState(false);

  /**
   * 待发送的引用文件列表（输入框上方 Tag 展示）
   */
  const [chatFiles, setChatFiles] = useState<ChatFileRef[]>([]);

  /**
   * 右侧激活tab（chat=对话区，doc=文档编辑查看区）
   */
  const [activeTab, setActiveTab] = useState<'chat' | 'doc'>('chat');

  /**
   * 文档区当前文件路径（文件树/本轮产出文件点击后进入文档区）
   */
  const [docPath, setDocPath] = useState<string | undefined>(undefined);

  /**
   * 文档区可切换文件列表（来自本轮产出文件，支持上一个/下一个）
   */
  const [docFiles, setDocFiles] = useState<string[] | undefined>(undefined);

  const scrollRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);

  const { workspaces, loading: workspacesLoading, refresh } = useWorkspaces(userId);

  // 当前工作区列表变化时校正选中项（首选第一个，被删除后回退）
  useEffect(() => {
    if (workspaces.length === 0) {
      if (selectedWorkspaceId != null) {
        setSelectedWorkspaceId(undefined);
      }
      return;
    }
    if (selectedWorkspaceId == null || !workspaces.some((w) => w.id === selectedWorkspaceId)) {
      setSelectedWorkspaceId(selectDefaultWorkspace(workspaces)?.id);
    }
  }, [workspaces, selectedWorkspaceId]);

  const currentWorkspace = workspaces.find((w) => w.id === selectedWorkspaceId);

  // 真正切换工作区后文件引用与文档区内容已失效：清空引用列表并回到对话tab（用ref比较避免初始化/重挂载时误重置）
  const prevWorkspaceIdRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (prevWorkspaceIdRef.current !== undefined && prevWorkspaceIdRef.current !== selectedWorkspaceId) {
      setChatFiles([]);
      setDocPath(undefined);
      setDocFiles(undefined);
      setActiveTab('chat');
    }
    prevWorkspaceIdRef.current = selectedWorkspaceId;
  }, [selectedWorkspaceId]);

  // 会话列表：仅展示本地文件Agent的会话
  const { data: sessionData, isLoading: sessionsLoading } = useQuery({
    queryKey: ['workbench-sessions', userId],
    queryFn: () =>
      api.conversation.session.list({ userId: userId ?? '', page: 1, size: 50 }),
    enabled: Boolean(userId),
  });

  const sessions = useMemo<ConversationSessionInfo[]>(
    () =>
      (sessionData?.list ?? [])
        .filter((s) => s.agentCode === LOCAL_AGENT_CODE)
        .slice()
        .sort((a, b) => (b.updateTime ?? '').localeCompare(a.updateTime ?? '')),
    [sessionData],
  );

  /**
   * 会话展示名：优先会话标题，缺失时回退会话ID前缀，避免界面直接暴露原始UUID
   * @param s
   * @return
   */
  const sessionLabelOf = (s: ConversationSessionInfo) =>
    s.sessionTitle?.trim() || `会话 ${s.sessionId.slice(0, 8)}`;

  /**
   * 当前会话展示名：优先会话列表标题，其次当前消息首条用户输入，最后回退会话ID前缀
   * @return
   */
  const currentSessionLabel = useMemo(() => {
    if (!sessionId) {
      return '';
    }
    const hit = sessions.find((s) => s.sessionId === sessionId);
    if (hit?.sessionTitle?.trim()) {
      return hit.sessionTitle.trim();
    }
    const firstUser = messages.find((m) => m.role === 'user' && m.content.trim());
    if (firstUser?.content.trim()) {
      const text = firstUser.content.trim().replace(/\s+/g, ' ').slice(0, 20);
      return text.length >= 20 ? `${text}...` : text;
    }
    return `会话 ${sessionId.slice(0, 8)}`;
  }, [sessionId, sessions, messages]);

  // 审批事件订阅（SSE + 存量拉取），卡片渲染在输入框上方
  const { requests: approvalRequests, removeRequest: removeApprovalRequest } =
    useApprovalRequests(api, sessionId || undefined);

  // 新消息或切回对话tab时滚动到底部（文档tab期间消息区被卸载，切回时重新定位）
  useEffect(() => {
    if (activeTab !== 'chat') {
      return;
    }
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, activeTab]);

  /**
   * 左栏分隔条拖拽调宽（mousedown 后监听 window 事件，越界收拢到区间内）
   * @param e
   */
  const startDrag = (e: React.MouseEvent) => {
    e.preventDefault();
    const onMove = (ev: MouseEvent) => {
      if (!bodyRef.current) {
        return;
      }
      const next = ev.clientX - bodyRef.current.getBoundingClientRect().left;
      setTreeWidth(Math.min(560, Math.max(200, next)));
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  };

  /**
   * 恢复历史会话消息，并还原会话绑定的workspaceId
   * @param session
   * @return
   */
  const restoreSession = async (session: ConversationSessionInfo) => {
    if (sending) {
      message.warning('正在对话中，请等待回复完成后再切换会话');
      return;
    }
    try {
      const json = await api.conversation.session.export(session.sessionId, 'json');
      setMessages(restoreMessagesFromExport(json));
      setSessionId(session.sessionId);
      const bound = readWorkspaceBinding(session.sessionId);
      if (bound && workspaces.some((w) => w.id === bound)) {
        setSelectedWorkspaceId(bound);
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '恢复会话失败');
    }
  };

  const handleReset = () => {
    resetChat();
    setInput('');
    setChatFiles([]);
  };

  /**
   * 文件树点击文件（在右侧文档区打开）
   * @param path
   */
  const handleTreePreview = (path: string) => {
    setDocFiles(undefined);
    setDocPath(path);
    setActiveTab('doc');
  };

  /**
   * 文件树右键加入对话（按路径去重并限制数量）
   * @param file
   */
  const handleAddToChat = (file: ChatFileRef) => {
    if (chatFiles.some((f) => f.path === file.path)) {
      message.info('该文件已在对话引用列表中');
      return;
    }
    if (chatFiles.length >= MAX_CHAT_FILES) {
      message.warning(`一次对话最多引用 ${MAX_CHAT_FILES} 个文件`);
      return;
    }
    setChatFiles((prev) => [...prev, file]);
  };

  /**
   * 读取引用文件内容并组装为注入消息的 file 块（文本用内容、excel 用工作簿JSON摘要）
   * @param workspaceId
   * @param refs
   * @return
   */
  const buildFileBlocks = async (workspaceId: string, refs: ChatFileRef[]): Promise<string[]> => {
    const blocks: string[] = [];
    for (const ref of refs) {
      try {
        const data = await api.workspace.preview(workspaceId, userId as string, ref.path);
        let fileContent = '';
        if (data.type === 'excel' && data.workbook) {
          // excel 以工作簿JSON摘要参与对话
          fileContent = JSON.stringify({
            sheets: (data.workbook.sheets ?? []).map((s) => ({
              name: s.name,
              rowCount: s.rowCount,
              truncated: s.truncated,
              header: s.header,
              rows: s.rows,
            })),
          });
        } else {
          fileContent = data.content ?? '';
          if (data.truncated) {
            fileContent += '\n...（文件超 200KB 已截断）';
          }
        }
        // 单文件内容超限时截断，避免消息体过大
        fileContent = truncateFileContent(fileContent);
        blocks.push(`<file path="${ref.path}">\n${fileContent}\n</file>`);
      } catch {
        blocks.push(`<file path="${ref.path}">\n（文件读取失败）\n</file>`);
      }
    }
    return blocks;
  };

  const handleSend = async () => {
    const content = input.trim();
    if (!content || sending) {
      return;
    }
    if (!currentWorkspace || !userId) {
      message.warning('请先选择工作区');
      return;
    }

    // 首次发送时本地生成 sessionId 并绑定当前工作区
    const activeSessionId = ensureSessionId();
    if (!sessionId) {
      writeWorkspaceBinding(activeSessionId, currentWorkspace.id);
    }

    // 本轮产生文件检测：发送前记录文件快照，回复结束后对比新增
    const beforeFiles = await listAllFiles(api.workspace, currentWorkspace.id, userId);
    const refs = chatFiles;
    // 引用文件内容拼入消息（单个文件读取失败不阻塞发送）
    const fileBlocks = refs.length ? await buildFileBlocks(currentWorkspace.id, refs) : [];
    const fileNote = refs.length ? `\n\n（引用文件：${refs.map((f) => f.path).join('、')}）` : '';
    const payload = [content, ...fileBlocks].join('\n\n');

    // 工作区ID置于body，映射后端AgentRequest.body由LocalAgentProcessor读取
    const req: AgentChatRequest = {
      agentCode: LOCAL_AGENT_CODE,
      input: [payload],
      sessionId: activeSessionId,
      userId,
      body: { _workspaceId: currentWorkspace.id },
    };

    setInput('');
    setChatFiles([]);
    const assistantId = await sendMessage({
      req,
      userContent: content + fileNote,
      clearPending: true,
    });
    // 对比快照识别本轮新增文件，挂到助手消息下供点击预览
    if (assistantId) {
      const produced = await diffProducedFiles(api.workspace, currentWorkspace.id, userId, beforeFiles);
      if (produced.length > 0) {
        setMessages((prev) =>
          prev.map((m) => (m.id === assistantId ? { ...m, producedFiles: produced } : m)),
        );
      }
    }
    queryClient.invalidateQueries({ queryKey: ['workbench-sessions'] });
    // 消息流结束：本轮对话中Agent可能已新增/修改/删除文件，自动刷新文件树
    setTreeRefreshKey((k) => k + 1);
  };

  /**
   * 提交澄清回答：恢复流增量追加为新的assistant消息，流中可再次收到澄清提问（多轮澄清）
   * @param toolCallId
   * @param answer
   * @return
   */
  const handleSubmitClarification = async (toolCallId: string, answer: string) => {
    if (!sessionId || clarificationSubmitting) {
      return;
    }
    setClarificationSubmitting(true);
    await runAssistantStream(true, (assistantId) => {
      const callbacks = streamCallbacks(assistantId, true);
      return api.agent.chatClarificationStream(
        { sessionId, toolCallId, answer },
        callbacks.onContent,
        callbacks.onThinking,
        callbacks.onClarification,
        callbacks.onConfirm,
      );
    });
    setClarificationSubmitting(false);
    // 已回答的提问从卡片列表移除
    setClarifications((prev) => prev.filter((c) => c.toolCallId !== toolCallId));
    queryClient.invalidateQueries({ queryKey: ['workbench-sessions'] });
    // 恢复流中Agent可能已新增/修改/删除文件，自动刷新文件树
    setTreeRefreshKey((k) => k + 1);
  };

  /**
   * 提交引擎确认决策：恢复流增量追加为新的assistant消息，流中可再次收到确认请求（连续审批）
   * @param requestId
   * @param approved
   * @return
   */
  const handleConfirmDecide = async (requestId: string, approved: boolean) => {
    if (!sessionId || confirmSubmitting) {
      return;
    }
    setConfirmSubmitting(true);
    await runAssistantStream(true, (assistantId) => {
      const callbacks = streamCallbacks(assistantId, true);
      return api.agent.chatConfirmStream(
        { sessionId, approved, operator: userId ?? undefined },
        callbacks.onContent,
        callbacks.onThinking,
        callbacks.onClarification,
        callbacks.onConfirm,
      );
    });
    setConfirmSubmitting(false);
    // 已处理的确认从卡片列表移除
    setConfirms((prev) => prev.filter((c) => c.requestId !== requestId));
    queryClient.invalidateQueries({ queryKey: ['workbench-sessions'] });
    // 恢复流中Agent可能已新增/修改/删除文件，自动刷新文件树
    setTreeRefreshKey((k) => k + 1);
  };

  /**
   * 复制消息原始内容
   * @param msg
   */
  const handleCopy = async (msg: ChatMessage) => {
    try {
      await copyToClipboard(msg.content);
      message.success('已复制');
    } catch {
      message.error('复制失败，请检查浏览器剪贴板权限');
    }
  };

  const openManage = (initialAction?: 'default-create') => {
    setManageInitialAction(initialAction);
    setManageOpen(true);
  };

  /**
   * 切换当前工作区审批层级并实时保存
   * @param mode
   * @return
   */
  const handleApprovalModeChange = async (mode: WorkspaceItem['approvalMode']) => {
    if (!currentWorkspace || !userId || savingMode) {
      return;
    }
    setSavingMode(true);
    try {
      await api.workspace.update(currentWorkspace.id, { userId, approvalMode: mode });
      await refresh();
      message.success(`审批层级已切换为「${APPROVAL_MODE_META[mode].text}」`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存审批层级失败');
    } finally {
      setSavingMode(false);
    }
  };

  const pathSegments = useMemo(
    () => (currentWorkspace?.rootPath ?? '').split(/[\\/]+/).filter(Boolean),
    [currentWorkspace?.rootPath],
  );

  const modeMeta = currentWorkspace
    ? APPROVAL_MODE_META[currentWorkspace.approvalMode]
    : undefined;

  return (
    <div
      className="wb-root"
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* 全局亮色主题样式（晨雾白工作纸 × 蓝紫主色 × 青/粉/琥珀多彩点缀） */}
      <style>{`
        .wb-root {
          --wb-primary: #4F6BFF;
          --wb-primary-bright: #7C8CFF;
          --wb-primary-soft: rgba(79, 107, 255, 0.09);
          --wb-violet: #8B5CF6;
          --wb-cyan: #22D3EE;
          --wb-pink: #F472B6;
          --wb-amber: #F59E0B;
          --wb-amber-soft: rgba(245, 158, 11, 0.12);
          --wb-bg0: #F6F7FA;
          --wb-bg1: #FFFFFF;
          --wb-bg2: #F2F4F8;
          --wb-border: #E8EBF3;
          --wb-text: #232838;
          --wb-dim: #6B7385;
          background: linear-gradient(180deg, #F7F8FE 0%, #F2F4FB 100%);
          color: var(--wb-text);
          animation: wb-fade-in 0.4s ease-out;
        }
        /* 顶部彩色流动光带（蓝→青→紫→粉→琥珀循环） */
        .wb-root::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 3px;
          background: linear-gradient(90deg, #4F6BFF, #22D3EE, #8B5CF6, #F472B6, #F59E0B, #4F6BFF);
          background-size: 300% 100%;
          opacity: 0.85;
          animation: wb-flow 8s linear infinite;
          z-index: 6;
          pointer-events: none;
        }
        @keyframes wb-flow { 0% { background-position: 0% 0; } 100% { background-position: 300% 0; } }
        /* 背景漂浮彩色光斑 */
        .wb-orbs { position: absolute; inset: 0; overflow: hidden; pointer-events: none; z-index: 0; }
        .wb-orbs i { position: absolute; border-radius: 50%; filter: blur(70px); }
        .wb-orbs i:nth-child(1) { width: 460px; height: 460px; top: -140px; right: 6%; background: radial-gradient(circle, rgba(99, 102, 241, 0.3), transparent 70%); animation: wb-float-a 18s ease-in-out infinite; }
        .wb-orbs i:nth-child(2) { width: 380px; height: 380px; bottom: -120px; left: 3%; background: radial-gradient(circle, rgba(244, 114, 182, 0.2), transparent 70%); animation: wb-float-b 22s ease-in-out infinite; }
        .wb-orbs i:nth-child(3) { width: 320px; height: 320px; top: 38%; left: 44%; background: radial-gradient(circle, rgba(34, 211, 238, 0.16), transparent 70%); animation: wb-float-c 26s ease-in-out infinite; }
        @keyframes wb-float-a { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(-60px, 40px); } }
        @keyframes wb-float-b { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(50px, -50px); } }
        @keyframes wb-float-c { 0%, 100% { transform: translate(0, 0) scale(1); } 50% { transform: translate(-40px, -30px) scale(1.12); } }
        @keyframes wb-fade-in {
          from { opacity: 0; transform: translateY(6px); }
          to { opacity: 1; transform: none; }
        }
        @keyframes wb-msg-in {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: none; }
        }
        .wb-msg-in { animation: wb-msg-in 0.28s ease-out both; }
        /* 顶部工具栏：玻璃拟态 */
        .wb-toolbar {
          height: 52px;
          flex-shrink: 0;
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 0 16px;
          background: rgba(255, 255, 255, 0.72);
          backdrop-filter: blur(14px);
          border-bottom: 1px solid rgba(232, 235, 243, 0.9);
          position: relative;
          z-index: 4;
        }
        .wb-toolbar::after {
          content: '';
          position: absolute;
          left: 0; right: 0; bottom: -1px;
          height: 1px;
          background: linear-gradient(90deg, transparent 0%, rgba(79, 107, 255, 0.32) 30%, rgba(34, 211, 238, 0.32) 55%, rgba(139, 92, 246, 0.28) 80%, transparent 100%);
          pointer-events: none;
        }
        .wb-root .ant-btn-text { color: var(--wb-dim); }
        .wb-root .ant-btn-text:hover { color: var(--wb-primary) !important; background: var(--wb-primary-soft) !important; }
        .wb-root .ant-btn-default {
          background: #FFFFFF;
          border-color: var(--wb-border);
          color: var(--wb-text);
        }
        .wb-root .ant-btn-default:hover { border-color: var(--wb-primary) !important; color: var(--wb-primary) !important; }
        .wb-root .ant-breadcrumb a, .wb-root .ant-breadcrumb li { color: var(--wb-dim); }
        .wb-root .ant-breadcrumb li:last-child { color: var(--wb-text); font-weight: 500; }
        .wb-root .ant-breadcrumb .anticon-folder-open { color: #F5A623; font-size: 15px; }
        /* Select / Tag 亮色化 */
        .wb-root .ant-select .ant-select-selector {
          background: #FFFFFF !important;
          border-color: var(--wb-border) !important;
          color: var(--wb-text) !important;
        }
        .wb-root .ant-select:hover .ant-select-selector { border-color: var(--wb-primary) !important; }
        .wb-root .ant-select-focused .ant-select-selector { border-color: var(--wb-primary) !important; box-shadow: 0 0 0 2px var(--wb-primary-soft) !important; }
        .wb-root .ant-select-arrow { color: var(--wb-dim); }
        .wb-light-popup {
          background: #FFFFFF !important;
          border: 1px solid var(--wb-border);
          box-shadow: 0 10px 32px rgba(31, 36, 48, 0.1);
        }
        .wb-light-popup .ant-select-item { color: var(--wb-text); }
        .wb-light-popup .ant-select-item-option-active { background: var(--wb-primary-soft) !important; }
        .wb-light-popup .ant-select-item-option-selected { background: var(--wb-primary-soft) !important; color: var(--wb-primary) !important; }
        /* 文件树亮色 */
        .wb-root .ant-tree { background: transparent; color: var(--wb-text); }
        .wb-root .ant-tree-node-content-wrapper { color: var(--wb-text); border-radius: 8px; transition: all 0.18s; }
        .wb-root .ant-tree-node-content-wrapper:hover { background: var(--wb-primary-soft) !important; }
        .wb-root .ant-tree-node-content-wrapper.ant-tree-node-selected {
          background: linear-gradient(90deg, rgba(79, 107, 255, 0.13), rgba(139, 92, 246, 0.13)) !important;
          color: var(--wb-primary);
          font-weight: 600;
        }
        .wb-root .ant-tree-indent-unit { border-color: #DDE1EA; }
        .wb-root .ant-tree-switcher { color: var(--wb-dim); }
        .wb-root .ant-tree-treenode { padding: 1px 0; }
        .wb-root .ant-tree-iconEle { color: var(--wb-dim); }
        .wb-divider { width: 4px; flex-shrink: 0; cursor: col-resize; background: transparent; transition: background-color 0.2s; position: relative; }
        .wb-divider:hover { background: rgba(79, 107, 255, 0.3); }
        .wb-crumb .ant-breadcrumb ol { flex-wrap: nowrap; }
        /* 消息流背景：暖白底 + 蓝/粉光斑 + 极淡点阵 */
        .wb-stream {
          flex: 1;
          min-height: 0;
          overflow-y: auto;
          padding: 20px 20px 10px;
          background:
            radial-gradient(ellipse 640px 320px at 88% -8%, rgba(79, 107, 255, 0.06), transparent),
            radial-gradient(ellipse 540px 340px at 8% 104%, rgba(244, 114, 182, 0.05), transparent),
            radial-gradient(rgba(35, 40, 56, 0.045) 1px, transparent 1px);
          background-size: auto, auto, 26px 26px;
        }
        .wb-user-row { position: relative; }
        .wb-user-row .wb-copy-btn { opacity: 0; transition: opacity 0.15s; }
        .wb-user-row:hover .wb-copy-btn { opacity: 1; }
        .wb-bubble-user {
          background: linear-gradient(135deg, #4F6BFF 0%, #7C5CFF 100%);
          border: 1px solid rgba(124, 92, 255, 0.4);
          box-shadow: 0 6px 18px rgba(79, 107, 255, 0.3);
        }
        .wb-bubble-ai {
          background: rgba(255, 255, 255, 0.92);
          border: 1px solid var(--wb-border);
          box-shadow: 0 2px 12px rgba(35, 40, 56, 0.05);
          backdrop-filter: blur(6px);
        }
        /* 输入卡：白卡浮起 + 聚焦时顶部多彩渐变线与靛青光晕 */
        .wb-input-card {
          border: 1px solid var(--wb-border);
          border-radius: 18px;
          background: rgba(255, 255, 255, 0.92);
          padding: 12px 16px 8px;
          box-shadow: 0 2px 14px rgba(35, 40, 56, 0.06);
          transition: border-color 0.2s, box-shadow 0.2s;
          position: relative;
          overflow: hidden;
        }
        .wb-input-card::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 2px;
          background: linear-gradient(90deg, #4F6BFF, #22D3EE, #8B5CF6, #F472B6);
          opacity: 0;
          transition: opacity 0.25s;
          pointer-events: none;
        }
        .wb-input-card:focus-within::before { opacity: 1; }
        .wb-input-card:focus-within {
          border-color: rgba(79, 107, 255, 0.45);
          box-shadow: 0 0 0 3px rgba(79, 107, 255, 0.12), 0 8px 24px rgba(79, 107, 255, 0.14);
        }
        .wb-root .ant-input { color: var(--wb-text) !important; }
        .wb-root .ant-input::placeholder { color: rgba(107, 115, 133, 0.55) !important; }
        .wb-send-btn {
          background: linear-gradient(135deg, #4F6BFF 0%, #8B5CF6 100%) !important;
          border: none !important;
          box-shadow: 0 4px 14px rgba(79, 107, 255, 0.4);
          transition: box-shadow 0.2s, transform 0.15s;
        }
        .wb-send-btn:hover:not(:disabled) {
          box-shadow: 0 6px 20px rgba(79, 107, 255, 0.55) !important;
          transform: translateY(-1px) scale(1.05);
        }
        .wb-send-btn:disabled { background: rgba(35, 40, 56, 0.1) !important; box-shadow: none; }
        /* 渐变通用按钮（保存/一键创建等主行动点） */
        .wb-gradient-btn {
          background: linear-gradient(135deg, #4F6BFF 0%, #8B5CF6 100%) !important;
          border: none !important;
          box-shadow: 0 4px 14px rgba(79, 107, 255, 0.35);
          transition: box-shadow 0.2s, transform 0.15s;
        }
        .wb-gradient-btn:hover:not(:disabled) { box-shadow: 0 6px 20px rgba(79, 107, 255, 0.5) !important; transform: translateY(-1px); }
        .wb-gradient-btn:disabled { background: rgba(35, 40, 56, 0.08) !important; box-shadow: none !important; color: rgba(35, 40, 56, 0.25) !important; }
        /* 文件chip（引用/产出文件）：按类型着色 + 悬浮上浮 */
        .wb-file-chip { cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; }
        .wb-file-chip:hover { transform: translateY(-1px); box-shadow: 0 4px 10px rgba(35, 40, 56, 0.14); }
        /* 空态hero：旋转彩虹渐变光环 */
        @keyframes wb-spin { to { transform: rotate(360deg); } }
        .wb-empty-hero { animation: wb-fade-in 0.5s ease-out 0.05s both; }
        .wb-hero-ring { position: relative; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
        .wb-hero-ring::before {
          content: '';
          position: absolute; inset: 0; border-radius: 50%;
          background: conic-gradient(from 0deg, #4F6BFF, #22D3EE, #8B5CF6, #F472B6, #F59E0B, #4F6BFF);
          animation: wb-spin 7s linear infinite;
          opacity: 0.9;
        }
        .wb-hero-ring::after {
          content: '';
          position: absolute; inset: 3px; border-radius: 50%;
          background: #FDFDFF;
          box-shadow: inset 0 0 18px rgba(79, 107, 255, 0.08);
        }
        .wb-hero-ring > * { position: relative; z-index: 1; }
        .wb-hero-title {
          font-size: 17px;
          font-weight: 700;
          letter-spacing: 0.5px;
          background: linear-gradient(90deg, #3D4FC4 0%, #7C3AED 60%, #DB2777 100%);
          -webkit-background-clip: text;
          background-clip: text;
          color: transparent;
        }
        .wb-hero-sub { color: var(--wb-dim); font-size: 12.5px; margin-top: 8px; }
        /* 空状态引导卡 */
        .wb-empty-card {
          text-align: center;
          padding: 44px 56px;
          border: 1px solid rgba(79, 107, 255, 0.14);
          border-radius: 20px;
          background: linear-gradient(180deg, #FFFFFF 0%, #FBFCFF 100%);
          box-shadow: 0 14px 44px rgba(79, 107, 255, 0.1);
          animation: wb-fade-in 0.5s ease-out 0.1s both;
          position: relative;
          overflow: hidden;
        }
        .wb-empty-card::before {
          content: '';
          position: absolute;
          top: -80px; left: 50%;
          transform: translateX(-50%);
          width: 320px; height: 160px;
          background: radial-gradient(ellipse at center, rgba(79, 107, 255, 0.12), rgba(139, 92, 246, 0.08) 45%, transparent 72%);
          pointer-events: none;
        }
        /* 滚动条亮色 */
        .wb-root ::-webkit-scrollbar { width: 8px; height: 8px; }
        .wb-root ::-webkit-scrollbar-thumb { background: rgba(35, 40, 56, 0.14); border-radius: 4px; }
        .wb-root ::-webkit-scrollbar-thumb:hover { background: rgba(79, 107, 255, 0.4); }
        .wb-root ::-webkit-scrollbar-track { background: transparent; }
        /* 思考中动画 */
        @keyframes wb-dot-anim { 0%, 100% { opacity: 0.2; } 50% { opacity: 1; } }
        .wb-dot-anim { display: inline-block; animation: wb-dot-anim 1.2s ease-in-out infinite; }
        /* 亮色浮层（拒绝审批 Popconfirm / 各下拉） */
        .wb-light-popover .ant-popover-inner { background: #FFFFFF; border: 1px solid var(--wb-border); box-shadow: 0 10px 32px rgba(31, 36, 48, 0.1); }
        .wb-light-popover .ant-popover-title { color: var(--wb-text); border-bottom: 1px solid var(--wb-border); }
        .wb-light-popover .ant-popover-inner-content { color: #454C5E; }
        .wb-light-popover .ant-popover-arrow::before { background: #FFFFFF; }
        .wb-light-popup .ant-typography { color: var(--wb-dim) !important; }
        /* 文件预览抽屉亮色（Portal 到 body，需独立类） */
        .wb-preview-drawer .ant-drawer-content { background: #FFFFFF; }
        .wb-preview-drawer .ant-drawer-header { background: transparent; border-bottom: 1px solid var(--wb-border); }
        .wb-preview-drawer .ant-drawer-title { color: var(--wb-text); }
        .wb-preview-drawer .ant-drawer-close { color: var(--wb-dim); }
        /* 右侧双tab（对话/文档）：胶囊样式，激活项蓝紫渐变填充；仅激活pane撑满高度，隐藏pane保持antd默认display:none避免内容叠加 */
        .wb-tabs { flex: 1; min-height: 0; display: flex; flex-direction: column; }
        .wb-tabs > .ant-tabs-nav { margin: 0; padding: 6px 16px; background: rgba(255, 255, 255, 0.68); backdrop-filter: blur(10px); border-bottom: 1px solid var(--wb-border); flex-shrink: 0; }
        .wb-tabs > .ant-tabs-nav::before { border-bottom: none !important; }
        .wb-tabs .ant-tabs-nav-list { gap: 4px; }
        .wb-tabs .ant-tabs-tab {
          padding: 5px 16px !important;
          margin: 0 !important;
          border-radius: 999px !important;
          border: 1px solid transparent !important;
          background: transparent !important;
          transition: all 0.2s;
        }
        .wb-tabs .ant-tabs-tab .ant-tabs-tab-btn { color: var(--wb-dim) !important; }
        .wb-tabs .ant-tabs-tab:hover { background: var(--wb-primary-soft) !important; }
        .wb-tabs .ant-tabs-tab:hover .ant-tabs-tab-btn { color: var(--wb-primary) !important; }
        .wb-tabs .ant-tabs-tab-active {
          background: linear-gradient(135deg, #4F6BFF 0%, #8B5CF6 100%) !important;
          box-shadow: 0 4px 14px rgba(79, 107, 255, 0.35);
        }
        .wb-tabs .ant-tabs-tab-active .ant-tabs-tab-btn,
        .wb-tabs .ant-tabs-tab-active .ant-tabs-tab-btn .anticon { color: #FFFFFF !important; }
        .wb-tabs .wb-tab-ic-chat { color: #4F6BFF; margin-inline-end: 6px; }
        .wb-tabs .wb-tab-ic-doc { color: #8B5CF6; margin-inline-end: 6px; }
        .wb-tabs .ant-tabs-ink-bar { display: none; }
        .wb-tabs .ant-tabs-content-holder { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
        .wb-tabs .ant-tabs-content { height: 100%; }
        .wb-tabs .ant-tabs-tabpane-active { height: 100%; overflow: hidden; display: flex; flex-direction: column; }
      `}</style>
      {/* 背景漂浮彩色光斑层 */}
      <div className="wb-orbs" aria-hidden="true">
        <i />
        <i />
        <i />
      </div>
      {/* 顶部工具栏 */}
      <div className="wb-toolbar">
        <Button
          type="text"
          size="small"
          icon={treeCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setTreeCollapsed((v) => !v)}
          title={treeCollapsed ? '展开文件树' : '收起文件树'}
        />
        <WorkspaceSelector
          value={selectedWorkspaceId}
          onChange={setSelectedWorkspaceId}
          workspaces={workspaces}
          loading={workspacesLoading}
          onManage={() => openManage()}
          dropdownClassName="wb-light-popup"
        />
        <div className="wb-crumb" style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
          {currentWorkspace && (
            <Tooltip title={currentWorkspace.rootPath}>
              <Breadcrumb
                items={[
                  { title: <FolderOpenOutlined /> },
                  ...pathSegments.map((seg) => ({ title: seg })),
                ]}
              />
            </Tooltip>
          )}
        </div>
        {modeMeta && currentWorkspace && (
          <Space size={8} style={{ flexShrink: 0 }}>
            {currentWorkspace.valid === false && <Tag color="red">失效</Tag>}
            <Tag color={modeMeta.color}>{modeMeta.text}</Tag>
          </Space>
        )}
      </div>

      {/* 主体三段式：文件树 | 分隔条 | 对话区 */}
      <div
        ref={bodyRef}
        style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          overflow: 'hidden',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div
          style={{
            width: treeCollapsed ? 0 : treeWidth,
            flexShrink: 0,
            overflow: 'hidden',
            borderRight: treeCollapsed ? 'none' : '1px solid var(--wb-border)',
            background: 'rgba(252, 253, 255, 0.72)',
            backdropFilter: 'blur(12px)',
          }}
        >
          <FileTreePanel
            workspaceId={selectedWorkspaceId}
            userId={userId}
            valid={currentWorkspace?.valid}
            refreshKey={treeRefreshKey}
            onPreview={handleTreePreview}
            onAddToChat={handleAddToChat}
          />
        </div>
        {!treeCollapsed && (
          <div className="wb-divider" onMouseDown={startDrag} />
        )}
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          {workspaces.length === 0 ? (
            <div
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 24,
              }}
            >
              <div
                style={{
                  textAlign: 'center',
                }}
                className="wb-empty-card"
              >
                <div className="wb-hero-ring" style={{ width: 78, height: 78, margin: '0 auto' }}>
                  <FolderOpenOutlined style={{ fontSize: 30, color: '#4F6BFF' }} />
                </div>
                <div className="wb-hero-title" style={{ marginTop: 16, fontSize: 17 }}>
                  创建你的第一个工作区开始使用
                </div>
                <div className="wb-hero-sub">
                  工作区是 AI 读写本地文件的目录边界，创建后即可在对话中操作其中的文件
                </div>
                <Button
                  icon={<ThunderboltOutlined />}
                  className="wb-gradient-btn"
                  type="primary"
                  style={{
                    marginTop: 20,
                    color: '#FFFFFF',
                    fontWeight: 600,
                  }}
                  onClick={() => openManage('default-create')}
                >
                  一键创建默认目录
                </Button>
              </div>
            </div>
          ) : (
            <Tabs
              className="wb-tabs"
              activeKey={activeTab}
              onChange={(k) => setActiveTab(k as 'chat' | 'doc')}
              items={[
                {
                  key: 'chat',
                  label: (
                    <span>
                      <MessageOutlined className="wb-tab-ic-chat" /> 对话
                    </span>
                  ),
                  children: (
                    <>
              {/* 会话栏 */}
              <div
                style={{
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 16px',
                  borderBottom: '1px solid var(--wb-border)',
                  background: 'rgba(255, 255, 255, 0.6)',
                }}
              >
                <Text type="secondary" style={{ fontSize: 12, color: 'var(--wb-dim)' }}>
                  历史会话
                </Text>
                <Select<string>
                  size="small"
                  style={{ minWidth: 220, maxWidth: 360 }}
                  placeholder="选择会话续聊"
                  value={sessionId || undefined}
                  loading={sessionsLoading}
                  dropdownClassName="wb-light-popup"
                  onChange={(v) => {
                    const target = sessions.find((s) => s.sessionId === v);
                    if (target) {
                      void restoreSession(target);
                    }
                  }}
                  options={(() => {
                    const list = sessions.map((s) => ({
                      value: s.sessionId,
                      label: sessionLabelOf(s),
                    }));
                    // 当前会话尚未进入列表（刚发起新对话未落库/列表未刷新）时补一条可读选项，避免选择框直接展示原始UUID
                    if (sessionId && !sessions.some((s) => s.sessionId === sessionId) && currentSessionLabel) {
                      list.unshift({ value: sessionId, label: currentSessionLabel });
                    }
                    return list;
                  })()}
                  notFoundContent="暂无历史会话"
                  allowClear={false}
                />
                {sessionId && (
                  <Tag
                    style={{
                      marginInlineEnd: 0,
                      borderRadius: 999,
                      background: 'rgba(245, 158, 11, 0.12)',
                      border: 'none',
                      color: '#B45309',
                      maxWidth: 220,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {currentSessionLabel}
                  </Tag>
                )}
                <Button size="small" icon={<PlusOutlined />} onClick={handleReset}>
                  新对话
                </Button>
              </div>

              {/* 消息流 */}
              <div ref={scrollRef} className="wb-stream">
                {messages.length === 0 ? (
                  <div className="wb-empty-hero" style={{ textAlign: 'center', marginTop: 110 }}>
                    <div className="wb-hero-ring" style={{ width: 88, height: 88, margin: '0 auto 18px' }}>
                      <AgentIconView size={46} />
                    </div>
                    <div className="wb-hero-title">开始与工作区对话</div>
                    <div className="wb-hero-sub">
                      AI 将在工作区目录边界内读写文件，写删操作受审批层级控制
                    </div>
                  </div>
                ) : (
                  messages.map((m) => (
                    <div
                      key={m.id}
                      className={`${m.role === 'user' ? 'wb-user-row' : ''} wb-msg-in`}
                      style={{
                        display: 'flex',
                        justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
                        marginBottom: 14,
                        gap: 10,
                      }}
                    >
                      {m.role === 'assistant' && <AgentIconView size={30} />}
                      <div
                        className={m.role === 'user' ? 'wb-bubble-user' : 'wb-bubble-ai'}
                        style={{
                          maxWidth: '76%',
                          padding: '10px 14px',
                          borderRadius: m.role === 'user' ? '14px 14px 4px 14px' : '14px 14px 14px 4px',
                          color: m.role === 'user' ? '#F2F5FA' : 'inherit',
                          wordBreak: 'break-word',
                        }}
                      >
                        {m.role === 'assistant' && m.thinking && (
                          <details style={{ marginBottom: 6 }}>
                            <summary
                              style={{ cursor: 'pointer', userSelect: 'none', fontSize: 12, color: 'var(--wb-dim)' }}
                            >
                              思考过程
                            </summary>
                            <div
                              style={{
                                marginTop: 6,
                                maxHeight: 240,
                                overflowY: 'auto',
                                whiteSpace: 'pre-wrap',
                                background: '#F7F8FB',
                                border: '1px solid var(--wb-border)',
                                borderRadius: 8,
                                padding: 10,
                                fontSize: 12,
                                color: '#5A6272',
                              }}
                            >
                              {m.thinking}
                            </div>
                          </details>
                        )}
                        {m.content ? (
                          m.role === 'assistant' ? (
                            <MarkdownContent content={m.content} />
                          ) : (
                            <div style={{ whiteSpace: 'pre-wrap' }}>{collapseFileRefs(m.content)}</div>
                          )
                        ) : (
                          m.streaming && (
                            <span style={{ color: 'var(--wb-dim)', fontSize: 13 }}>
                              思考中<span className="wb-dot-anim">…</span>
                            </span>
                          )
                        )}
                        {m.role === 'assistant' && m.content && !m.streaming && (
                          <>
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              onClick={() => void handleCopy(m)}
                              style={{ marginTop: 4, marginLeft: -10, fontSize: 12, color: 'var(--wb-dim)' }}
                            >
                              复制
                            </Button>
                            {m.mcpApp && (
                              <McpAppRenderer
                                declaration={m.mcpApp.declaration}
                                payload={m.mcpApp.payload}
                              />
                            )}
                            {m.producedFiles && m.producedFiles.length > 0 && (
                              <ProducedFilesPanel
                                files={m.producedFiles}
                                onPreview={(p, all) => {
                                  setDocFiles(all);
                                  setDocPath(p);
                                  setActiveTab('doc');
                                }}
                              />
                            )}
                          </>
                        )}
                      </div>
                      {m.role === 'user' && m.content && (
                        <Button
                          type="text"
                          size="small"
                          className="wb-copy-btn"
                          icon={<CopyOutlined />}
                          onClick={() => void handleCopy(m)}
                          title="复制"
                          style={{
                            position: 'absolute',
                            top: '100%',
                            right: 0,
                            marginTop: 2,
                            fontSize: 12,
                            color: 'var(--wb-dim)',
                            background: 'transparent',
                            zIndex: 1,
                          }}
                        />
                      )}
                      {m.role === 'user' && (
                        <div
                          style={{
                            width: 30,
                            height: 30,
                            flexShrink: 0,
                            marginTop: 2,
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'linear-gradient(135deg, #4F6BFF, #8B5CF6)',
                            boxShadow: '0 4px 10px rgba(79, 107, 255, 0.35)',
                          }}
                        >
                          <UserOutlined style={{ color: '#FFFFFF', fontSize: 15 }} />
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>

              {/* 审批卡片 + 输入区 */}
              <div style={{ padding: '12px 16px 16px', flexShrink: 0 }}>
                <ApprovalCards
                  requests={approvalRequests}
                  onHandled={(requestId) => {
                    removeApprovalRequest(requestId);
                    // 审批处理完成（批准/拒绝）：文件可能已写入或删除，自动刷新文件树
                    setTreeRefreshKey((k) => k + 1);
                  }}
                  approvedBy={userId}
                />
                <ClarificationCards
                  questions={clarifications}
                  onSubmit={handleSubmitClarification}
                  submitting={clarificationSubmitting}
                />
                <ConfirmCards
                  confirms={confirms}
                  onDecide={handleConfirmDecide}
                  submitting={confirmSubmitting}
                />
                <div className="wb-input-card">
                  {chatFiles.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 8 }}>
                      {chatFiles.map((f) => {
                        const accent = fileAccent(f.name);
                        return (
                          <Tag
                            key={f.path}
                            closable
                            icon={<PaperClipOutlined />}
                            onClose={() => setChatFiles((prev) => prev.filter((x) => x.path !== f.path))}
                            title={f.path}
                            className="wb-file-chip"
                            style={{
                              marginInlineEnd: 0,
                              background: `${accent}14`,
                              border: `1px solid ${accent}30`,
                              color: accent,
                            }}
                          >
                            {f.name}
                          </Tag>
                        );
                      })}
                    </div>
                  )}
                  <TextArea
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder={
                      currentWorkspace
                        ? `在「${currentWorkspace.name}」中输入指令，Enter 发送，Shift + Enter 换行`
                        : '请先选择工作区'
                    }
                    autoSize={{ minRows: 1, maxRows: 6 }}
                    variant="borderless"
                    disabled={sending}
                    style={{ fontSize: 14, padding: 0, resize: 'none' }}
                    onPressEnter={(e) => {
                      if (!e.shiftKey) {
                        e.preventDefault();
                        void handleSend();
                      }
                    }}
                  />
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginTop: 6,
                    }}
                  >
                    <Space size={6}>
                      <AgentIconView size={18} />
                      <Select
                        size="small"
                        variant="borderless"
                        style={{ minWidth: 96 }}
                        dropdownClassName="wb-light-popup"
                        value={currentWorkspace?.approvalMode ?? 'MANUAL'}
                        disabled={!currentWorkspace || savingMode}
                        loading={savingMode}
                        options={Object.entries(APPROVAL_MODE_META).map(([value, meta]) => ({
                          value,
                          label: meta.text,
                        }))}
                        onChange={handleApprovalModeChange}
                      />
                      <Text type="secondary" style={{ fontSize: 12, color: 'var(--wb-dim)' }}>
                        当前工作区：{currentWorkspace?.name ?? '未选择'}
                      </Text>
                    </Space>
                    <Button
                      type="primary"
                      shape="circle"
                      className="wb-send-btn"
                      icon={<SendOutlined />}
                      onClick={handleSend}
                      loading={sending}
                      disabled={!input.trim()}
                    />
                  </div>
                </div>
              </div>
                    </>
                  ),
                },
                {
                  key: 'doc',
                  label: (
                    <span>
                      <FileTextOutlined className="wb-tab-ic-doc" /> {docPath ? docPath.split(/[\\/]/).pop() : '文档'}
                    </span>
                  ),
                  children: (
                    <FileDocPanel
                      workspaceId={selectedWorkspaceId}
                      userId={userId}
                      path={docPath}
                      files={docFiles}
                      onPathChange={setDocPath}
                      onClose={() => setActiveTab('chat')}
                    />
                  ),
                },
              ]}
            />
          )}
        </div>
      </div>

      <WorkspaceManageModal
        open={manageOpen}
        userId={userId}
        workspaces={workspaces}
        loading={workspacesLoading}
        initialAction={manageInitialAction}
        onClose={() => {
          setManageOpen(false);
          setManageInitialAction(undefined);
        }}
        onChanged={() => {
          void refresh();
          queryClient.invalidateQueries({ queryKey: ['workspaces'] });
        }}
      />
    </div>
  );
};

export default WorkbenchPage;
