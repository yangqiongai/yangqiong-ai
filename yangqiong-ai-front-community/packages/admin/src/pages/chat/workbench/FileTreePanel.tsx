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
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Alert, App, Button, Input, Modal, Space, Spin, Tooltip, Tree, Typography } from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderAddOutlined,
  PaperClipOutlined,
  ReloadOutlined,
  ShrinkOutlined,
  SnippetsOutlined,
} from '@ant-design/icons';
import type { DataNode, EventDataNode } from 'antd/es/tree';
import { api } from '@/services';
import type { WorkspaceTreeNode } from '@yangqiong/shared';
import { FileTypeIcon } from './FileTypeIcon';

const { Text } = Typography;

/**
 * 文件树节点（含相对路径供懒加载子级与预览使用）
 */
interface FileTreeNode extends DataNode {
  isLeaf?: boolean;
  path: string;
  name: string;
}

/**
 * 取父目录路径（无分隔符时表示根目录）
 * @param path
 * @returns
 */
const parentOf = (path: string) => {
  const norm = path.replace(/\\/g, '/');
  const idx = norm.lastIndexOf('/');
  return idx === -1 ? '' : norm.slice(0, idx);
};

/**
 * 按路径在树中查找节点（右键命中反查用）
 * @param nodes
 * @param path
 * @returns
 */
const findNodeByPath = (nodes: FileTreeNode[], path: string): FileTreeNode | null => {
  for (const node of nodes) {
    if (node.path === path) {
      return node;
    }
    if (node.children) {
      const found = findNodeByPath(node.children as FileTreeNode[], path);
      if (found) {
        return found;
      }
    }
  }
  return null;
};

/**
 * 剪贴板内容（仅支持复制，不随粘贴清空）
 */
interface ClipboardState {
  path: string;
  name: string;
}

interface ContextMenuState {
  x: number;
  y: number;

  /**
   * 右键目标节点，null表示根目录空白区域
   */
  node: FileTreeNode | null;
}

interface FileTreePanelProps {
  /** 雪花ID后端序列化为字符串，全程保持字符串避免精度丢失 */
  workspaceId?: string;
  userId?: string;

  /**
   * 目录健康状态（false=目录失效，整栏置顶提示且不再加载）
   */
  valid?: boolean;

  /**
   * 刷新信号：值变化时自动重载根目录（审批处理完成、消息流结束后触发）
   */
  refreshKey?: number;

  /**
   * 点击文件节点在右侧文档区打开
   */
  onPreview: (path: string) => void;

  /**
   * 右键菜单「加入对话」回调（仅文件节点触发）
   */
  onAddToChat?: (file: { path: string; name: string }) => void;
}

/**
 * 工作区文件树面板（懒加载 + 右键复制/粘贴/重命名/删除/加入对话）
 */
export const FileTreePanel: React.FC<FileTreePanelProps> = ({ workspaceId, userId, valid, refreshKey, onPreview, onAddToChat }) => {
  const { message, modal } = App.useApp();
  const [treeData, setTreeData] = useState<FileTreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [clipboard, setClipboard] = useState<ClipboardState | null>(null);
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [renameTarget, setRenameTarget] = useState<FileTreeNode | null>(null);
  const [renameValue, setRenameValue] = useState('');

  /**
   * 新建文件夹目标父目录（title空表示根目录）
   */
  const [mkdirTarget, setMkdirTarget] = useState<{ path?: string; title?: string } | null>(null);
  const [mkdirValue, setMkdirValue] = useState('新建文件夹');

  /**
   * 拖拽中的节点路径（空白区落点兜底用）
   */
  const draggingPathRef = useRef<string | null>(null);

  // 右键菜单打开期间：点击/滚动/窗口变化时关闭（捕获阶段先于 React 处理，不影响新菜单的右键打开）
  useEffect(() => {
    if (!contextMenu) {
      return;
    }
    const close = () => setContextMenu(null);
    window.addEventListener('click', close);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    window.addEventListener('contextmenu', close, true);
    return () => {
      window.removeEventListener('click', close);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
      window.removeEventListener('contextmenu', close, true);
    };
  }, [contextMenu]);

  const disabled = !workspaceId || !userId || valid === false;

  const toNodes = useCallback(
    (nodes: WorkspaceTreeNode[]): FileTreeNode[] =>
      (nodes ?? []).map((node) => ({
        key: node.path,
        title: <span data-wb-path={node.path}>{node.name}</span>,
        isLeaf: !node.dir,
        path: node.path,
        name: node.name,
        icon: <FileTypeIcon name={node.name} dir={node.dir} />,
      })),
    [],
  );

  const loadRoot = useCallback(async () => {
    if (disabled) {
      setTreeData([]);
      return;
    }
    setLoading(true);
    try {
      const nodes = await api.workspace.tree(workspaceId, userId as string);
      setTreeData(toNodes(nodes ?? []));
      setExpandedKeys([]);
    } catch (err) {
      setTreeData([]);
      message.error(err instanceof Error ? err.message : '加载文件树失败');
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId, userId, disabled, toNodes]);

  // 工作区切换时重载根目录
  useEffect(() => {
    void loadRoot();
  }, [loadRoot]);

  // 审批处理完成、消息流结束后自动刷新（跳过首次，避免与上方工作区切换重复加载）
  const firstRenderRef = useRef(true);
  useEffect(() => {
    if (firstRenderRef.current) {
      firstRenderRef.current = false;
      return;
    }
    void loadRoot();
  }, [refreshKey, loadRoot]);

  /**
   * 递归写入指定目录的子节点
   */
  const updateChildren = (
    nodes: FileTreeNode[],
    parentPath: string,
    children: FileTreeNode[],
  ): FileTreeNode[] =>
    nodes.map((node) => {
      if (node.path === parentPath) {
        return { ...node, children: children.length ? children : undefined };
      }
      return node.children
        ? { ...node, children: updateChildren(node.children as FileTreeNode[], parentPath, children) }
        : node;
    });

  const handleLoadData = async (node: EventDataNode<FileTreeNode>) => {
    if (node.isLeaf || disabled) {
      return;
    }
    try {
      const nodes = await api.workspace.tree(
        workspaceId,
        userId as string,
        node.path as string,
      );
      setTreeData((prev) => updateChildren(prev, node.path as string, toNodes(nodes ?? [])));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载子目录失败');
    }
  };

  /**
   * 变更后刷新树（保留已展开层级，extraKeys为需强制刷新的目录路径，按路径深度浅先深后串行重拉）
   * @param extraKeys
   */
  const refreshAfterMutation = async (extraKeys: string[] = []) => {
    if (disabled) {
      return;
    }
    try {
      const roots = await api.workspace.tree(workspaceId, userId as string);
      let next = toNodes(roots ?? []);
      const keys = Array.from(new Set<string>([...expandedKeys.map(String), ...extraKeys])).sort(
        (a, b) => a.split(/[\\/]/).length - b.split(/[\\/]/).length,
      );
      for (const key of keys) {
        try {
          const children = await api.workspace.tree(workspaceId, userId as string, key);
          next = updateChildren(next, key, toNodes(children ?? []));
        } catch {
          // 子目录可能已被删除，跳过该层级
        }
      }
      setTreeData(next);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '刷新文件树失败');
    }
  };

  /**
   * 点击文件节点（在右侧文档区打开）
   * @param node
   */
  const handlePreview = (node: FileTreeNode) => {
    if (!node.isLeaf || disabled) {
      return;
    }
    onPreview(node.path);
  };

  /**
   * 复制节点到剪贴板（仅记录引用，粘贴时由后端校验存在性）
   * @param node
   */
  const handleCopy = (node: FileTreeNode) => {
    setClipboard({ path: node.path, name: node.name });
    message.success(node.isLeaf ? '已复制文件，请在目标目录右键粘贴' : '已复制目录，请在目标目录右键粘贴');
    setContextMenu(null);
  };

  /**
   * 粘贴剪贴板内容到目标目录（空表示根目录）
   * @param targetDir
   */
  const handlePaste = async (targetDir?: string) => {
    if (!clipboard || !workspaceId) {
      return;
    }
    try {
      await api.workspace.copyFile(workspaceId, {
        userId: userId as string,
        path: clipboard.path,
        targetDir,
      });
      message.success('已粘贴');
      await refreshAfterMutation(targetDir ? [targetDir] : []);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '粘贴失败');
    }
    setContextMenu(null);
  };

  /**
   * 打开重命名弹窗并回填当前名称
   * @param node
   */
  const openRename = (node: FileTreeNode) => {
    setRenameTarget(node);
    setRenameValue(node.name);
    setContextMenu(null);
  };

  /**
   * 提交重命名（前端先做字符校验，后端兜底）
   */
  const submitRename = async () => {
    if (!renameTarget || !workspaceId) {
      return;
    }
    const name = renameValue.trim();
    if (!name) {
      message.warning('名称不能为空');
      return;
    }
    if (/[/\\:*?"<>|]/.test(name)) {
      message.error('名称不能包含 \\ / : * ? " < > | 字符');
      return;
    }
    try {
      await api.workspace.renameFile(workspaceId, {
        userId: userId as string,
        path: renameTarget.path,
        newName: name,
      });
      message.success('已重命名');
      // 复制源被重命名后引用失效，清空剪贴板避免粘贴报错
      if (clipboard && clipboard.path === renameTarget.path) {
        setClipboard(null);
      }
      setRenameTarget(null);
      await refreshAfterMutation();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '重命名失败');
    }
  };

  /**
   * 提交新建文件夹（前端先做字符校验，后端兜底）
   */
  const submitMkdir = async () => {
    if (!mkdirTarget || !workspaceId) {
      return;
    }
    const name = mkdirValue.trim();
    if (!name) {
      message.warning('名称不能为空');
      return;
    }
    if (/[/\\:*?"<>|]/.test(name)) {
      message.error('名称不能包含 \\ / : * ? " < > | 字符');
      return;
    }
    try {
      await api.workspace.createDirectory(workspaceId, {
        userId: userId as string,
        path: mkdirTarget.path,
        name,
      });
      message.success('文件夹已创建');
      setMkdirTarget(null);
      await refreshAfterMutation(mkdirTarget.path ? [mkdirTarget.path] : []);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '创建文件夹失败');
    }
  };

  /**
   * 执行移动（targetDir为空表示根目录，同名冲突与越界由后端校验）
   * @param sourcePath
   * @param targetDir
   */
  const performMove = async (sourcePath: string, targetDir: string) => {
    if (!workspaceId) {
      return;
    }
    try {
      await api.workspace.moveFile(workspaceId, {
        userId: userId as string,
        path: sourcePath,
        targetDir: targetDir || undefined,
      });
      message.success('已移动');
      // 原位置子路径的展开状态失效，一并清理
      setExpandedKeys((prev) => prev.filter((key) => {
        const text = String(key);
        return text !== sourcePath && !text.startsWith(`${sourcePath}\\`) && !text.startsWith(`${sourcePath}/`);
      }));
      // 展开目标目录并重拉其子级，让移动结果立即可见
      if (targetDir) {
        setExpandedKeys((prev) => (prev.includes(targetDir) ? prev : [...prev, targetDir]));
      }
      await refreshAfterMutation(targetDir ? [targetDir] : []);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '移动失败');
    }
  };

  /**
   * 拖拽落点解析目标目录：目录节点=移入该目录，文件节点或间隙落点=其父目录（根级节点的父目录为根）
   * @param info
   */
  const handleDrop = (info: {
    node: EventDataNode<FileTreeNode>;
    dragNode: EventDataNode<FileTreeNode>;
    dropToGap: boolean;
  }) => {
    if (disabled) {
      return;
    }
    const dragPath = String((info.dragNode as FileTreeNode)?.path ?? '').replace(/\\/g, '/');
    const dropPath = String((info.node as FileTreeNode)?.path ?? '').replace(/\\/g, '/');
    if (!dragPath || !dropPath || dragPath === dropPath) {
      return;
    }
    const intoDir = !info.dropToGap && !info.node.isLeaf;
    const targetDir = intoDir ? dropPath : parentOf(dropPath);
    if (intoDir && dropPath.startsWith(`${dragPath}/`)) {
      message.warning('不能移动到自身内部');
      return;
    }
    // 已在目标目录内，位置不变
    if (parentOf(dragPath) === targetDir) {
      return;
    }
    void performMove(dragPath, targetDir);
  };

  /**
   * 树空白区落点兜底：rc-tree仅在节点上触发onDrop，拖出目录后落到空白区视为移动到根目录
   * @param event
   */
  const handleTreeContainerDrop = (event: React.DragEvent) => {
    event.preventDefault();
    const sourcePath = draggingPathRef.current;
    draggingPathRef.current = null;
    if (disabled || !sourcePath) {
      return;
    }
    // 节点区域交给rc-tree的onDrop处理，避免重复移动
    if ((event.target as HTMLElement).closest?.('.ant-tree-treenode')) {
      return;
    }
    const normPath = sourcePath.replace(/\\/g, '/');
    if (parentOf(normPath) === '') {
      return;
    }
    void performMove(normPath, '');
  };

  /**
   * 删除节点（目录二次确认递归删除）
   * @param node
   */
  const handleDelete = (node: FileTreeNode) => {
    setContextMenu(null);
    const isDir = !node.isLeaf;
    modal.confirm({
      title: isDir ? `删除目录「${node.name}」？` : `删除文件「${node.name}」？`,
      content: isDir ? '目录内全部内容将被一并删除，操作不可恢复' : '删除后不可恢复',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        if (!workspaceId) {
          return;
        }
        try {
          await api.workspace.deleteFile(workspaceId, userId as string, node.path);
          message.success('已删除');
          // 被删路径下的展开状态与剪贴板引用一并清理
          setExpandedKeys((prev) => prev.filter((key) => {
            const text = String(key);
            return text !== node.path && !text.startsWith(`${node.path}\\`) && !text.startsWith(`${node.path}/`);
          }));
          if (clipboard && (clipboard.path === node.path
            || clipboard.path.startsWith(`${node.path}\\`)
            || clipboard.path.startsWith(`${node.path}/`))) {
            setClipboard(null);
          }
          await refreshAfterMutation();
        } catch (err) {
          message.error(err instanceof Error ? err.message : '删除失败');
        }
      },
    });
  };

  /**
   * 按右键目标生成菜单项（文件=加入对话，目录/根=粘贴，全部=复制/重命名/删除）
   */
  const menuItems: { key: string; icon: React.ReactNode; label: string; danger?: boolean; onClick: () => void }[] = [];
  const menuNode = contextMenu?.node ?? null;
  if (contextMenu) {
    if (menuNode && menuNode.isLeaf) {
      menuItems.push({
        key: 'add-to-chat',
        icon: <PaperClipOutlined />,
        label: '加入对话',
        onClick: () => {
          onAddToChat?.({ path: menuNode.path, name: menuNode.name });
          setContextMenu(null);
        },
      });
    }
    // 目录与根目录空白区支持新建文件夹
    if (!menuNode || !menuNode.isLeaf) {
      menuItems.push({
        key: 'mkdir',
        icon: <FolderAddOutlined />,
        label: menuNode ? `在「${menuNode.name}」内新建文件夹` : '新建文件夹',
        onClick: () => {
          setMkdirTarget({ path: menuNode?.path, title: menuNode ? menuNode.name : undefined });
          setMkdirValue('新建文件夹');
          setContextMenu(null);
        },
      });
    }
    if (menuNode) {
      menuItems.push({ key: 'copy', icon: <CopyOutlined />, label: '复制', onClick: () => handleCopy(menuNode) });
    }
    if (clipboard && (!menuNode || !menuNode.isLeaf)) {
      menuItems.push({
        key: 'paste',
        icon: <SnippetsOutlined />,
        label: menuNode ? `粘贴到「${menuNode.name}」` : '粘贴到根目录',
        onClick: () => void handlePaste(menuNode ? menuNode.path : undefined),
      });
    }
    if (menuNode) {
      menuItems.push({ key: 'rename', icon: <EditOutlined />, label: '重命名', onClick: () => openRename(menuNode) });
      menuItems.push({
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        onClick: () => handleDelete(menuNode),
      });
    }
  }
  // 菜单防视口溢出
  const menuLeft = contextMenu ? Math.min(contextMenu.x, window.innerWidth - 160) : 0;
  const menuTop = contextMenu ? Math.min(contextMenu.y, window.innerHeight - menuItems.length * 34 - 24) : 0;

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'transparent',
        minWidth: 0,
      }}
      onContextMenu={(event) => {
        if (disabled) {
          return;
        }
        // 树行可拖拽时右键事件target可能落在整行或子元素而非标题，先向上定位树行容器，
        // 再向下查标题span取路径（data-wb-path在title子级上，closest无法向上命中）
        event.preventDefault();
        const row = (event.target as HTMLElement).closest?.('.ant-tree-treenode') as HTMLElement | null;
        const hit = row?.querySelector('[data-wb-path]') as HTMLElement | null;
        const path = hit?.dataset?.wbPath;
        const node = path ? findNodeByPath(treeData, path) : null;
        setContextMenu({ x: event.clientX, y: event.clientY, node });
      }}
    >
      <div
        style={{
          flexShrink: 0,
          padding: '8px 10px',
          borderBottom: '1px solid var(--wb-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Text strong style={{ fontSize: 13, color: 'var(--wb-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              width: 4,
              height: 14,
              borderRadius: 2,
              background: 'linear-gradient(180deg, #4F6BFF 0%, #8B5CF6 100%)',
              display: 'inline-block',
            }}
          />
          文件树
        </Text>
        <Space size={4}>
          <Tooltip title="刷新">
            <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => void loadRoot()} />
          </Tooltip>
          <Tooltip title="折叠全部">
            <Button
              type="text"
              size="small"
              icon={<ShrinkOutlined />}
              onClick={() => setExpandedKeys([])}
            />
          </Tooltip>
        </Space>
      </div>
      {valid === false && (
        <Alert
          type="error"
          showIcon
          message="工作区目录已失效，请检查磁盘路径或重新创建目录"
          style={{ borderRadius: 0, border: 'none' }}
        />
      )}
      <div
        style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '6px 4px' }}
        onDragOver={(event) => event.preventDefault()}
        onDrop={handleTreeContainerDrop}
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin size="small" />
          </div>
        ) : treeData.length === 0 ? (
          <Text
            type="secondary"
            style={{ fontSize: 12, color: 'var(--wb-dim)', padding: '8px 10px', display: 'block' }}
          >
            {disabled ? '当前工作区不可用' : '目录为空'}
          </Text>
        ) : (
          <Tree<FileTreeNode>
            showIcon
            blockNode
            treeData={treeData}
            loadData={(node) => handleLoadData(node as EventDataNode<FileTreeNode>)}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys)}
            draggable={{ icon: false }}
            onDragStart={({ node }) => {
              draggingPathRef.current = String((node as FileTreeNode).path);
            }}
            onDragEnd={() => {
              draggingPathRef.current = null;
            }}
            onDrop={(info) => handleDrop(info)}
            onSelect={(_, { node }) => handlePreview(node as FileTreeNode)}
          />
        )}
      </div>
      {contextMenu && menuItems.length > 0 && createPortal(
        <div
          className="wb-ctx-menu"
          style={{
            position: 'fixed',
            left: menuLeft,
            top: menuTop,
            // 菜单渲染到body顶层，避免被左栏容器的低层叠上下文（backdrop-filter）遮挡
            zIndex: 1050,
            background: '#FFFFFF',
            border: '1px solid var(--wb-border)',
            borderRadius: 8,
            boxShadow: '0 10px 32px rgba(31, 36, 48, 0.12)',
            padding: 4,
            minWidth: 140,
          }}
        >
          {menuItems.map((item) => (
            <div
              key={item.key}
              className="wb-ctx-item"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 10px',
                borderRadius: 6,
                fontSize: 13,
                color: item.danger ? '#D4380D' : 'var(--wb-text)',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
              onClick={item.onClick}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = item.danger ? '#FFF1EC' : 'var(--wb-primary-soft)';
                e.currentTarget.style.color = item.danger ? '#D4380D' : 'var(--wb-primary)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = item.danger ? '#D4380D' : 'var(--wb-text)';
              }}
            >
              {item.icon}
              {item.label}
            </div>
          ))}
        </div>,
        document.body,
      )}
      <Modal
        title={`重命名「${renameTarget?.name ?? ''}」`}
        open={Boolean(renameTarget)}
        onOk={() => void submitRename()}
        onCancel={() => setRenameTarget(null)}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
        width={420}
      >
        <Input
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          placeholder="输入新名称"
          maxLength={255}
          onPressEnter={() => void submitRename()}
          autoFocus
        />
      </Modal>
      <Modal
        title={mkdirTarget?.title ? `在「${mkdirTarget.title}」内新建文件夹` : '新建文件夹'}
        open={Boolean(mkdirTarget)}
        onOk={() => void submitMkdir()}
        onCancel={() => setMkdirTarget(null)}
        okText="创建"
        cancelText="取消"
        destroyOnHidden
        width={420}
      >
        <Input
          value={mkdirValue}
          onChange={(e) => setMkdirValue(e.target.value)}
          placeholder="输入文件夹名称"
          maxLength={255}
          onPressEnter={() => void submitMkdir()}
          autoFocus
        />
      </Modal>
    </div>
  );
};
