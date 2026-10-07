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
import type {
  WorkspaceItem,
  WorkspaceTrashItem,
  WorkspaceTreeNode,
  WorkspaceType,
} from '../services/workspace-api';
import type { ChatFileRef } from './chat-message';

/**
 * 本地工作台固定使用的本地文件Agent编码
 */
export const LOCAL_AGENT_CODE = 'localAgent';

/**
 * 一次对话最多引用的文件数
 */
export const MAX_CHAT_FILES = 5;

/**
 * 单文件注入消息的内容字符上限
 */
export const MAX_FILE_CONTENT_CHARS = 12000;

/**
 * 文件树行节点（自研受控树数据结构，懒加载子级）
 */
export interface TreeRow {
  path: string;

  name: string;

  isLeaf: boolean;

  children?: TreeRow[];
}

/**
 * 回收站固定目录名（服务端软删除落点，正常文件树中不展示）
 */
export const TRASH_DIR_NAME = '.trash';

/**
 * 工作区文件树接口最小结构（由各端 api 实例适配）
 */
export interface WorkspaceFileApi {
  tree(workspaceId: string, userId: string, dir?: string): Promise<WorkspaceTreeNode[]>;
  upload(workspaceId: string, dir: string | undefined, files: File[]): Promise<unknown>;
  download(workspaceId: string, path: string): Promise<Blob>;
  exportZip(workspaceId: string, path?: string): Promise<Blob>;
  listTrash(workspaceId: string): Promise<WorkspaceTrashItem[]>;
  restore(workspaceId: string, path: string): Promise<void>;
}

/**
 * 单文件内容超限时截断，避免消息体过大
 * @param content
 * @return
 */
export const truncateFileContent = (content: string): string =>
  content.length > MAX_FILE_CONTENT_CHARS
    ? content.slice(0, MAX_FILE_CONTENT_CHARS) + '\n...（内容过长已截断）'
    : content;

/**
 * 取父目录路径（无分隔符时表示根目录）
 * @param path
 * @return
 */
export const parentOf = (path: string): string => {
  const norm = path.replace(/\\/g, '/');
  const idx = norm.lastIndexOf('/');
  return idx === -1 ? '' : norm.slice(0, idx);
};

/**
 * 字节数格式化（预览头部与文件清单展示）
 * @param size
 * @return
 */
export const formatSize = (size?: number): string => {
  if (size == null) {
    return '-';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  return size >= 1024 ? `${(size / 1024).toFixed(1)} KB` : `${size} B`;
};

/**
 * 文件/目录名称合法性校验（禁止路径分隔符与Windows保留字符）
 * @param name
 * @return
 */
export const isValidFileName = (name: string): boolean => !/[/\\:*?"<>|]/.test(name);

/**
 * 路径是否位于回收站目录内（正常文件树不展示 .trash）
 * @param path
 * @return
 */
export const isTrashEntry = (path: string): boolean =>
  path.split(/[\\/]/).includes(TRASH_DIR_NAME);

/**
 * 后端树节点转为前端树行（过滤回收站目录）
 * @param nodes
 * @return
 */
export const toTreeRows = (nodes: WorkspaceTreeNode[]): TreeRow[] =>
  (nodes ?? [])
    .filter((node) => !isTrashEntry(node.path))
    .map((node) => ({
      path: node.path,
      name: node.name,
      isLeaf: !node.dir,
    }));

/**
 * 递归写入指定目录的子节点（保留其他分支的已加载状态）
 * @param rows
 * @param parentPath
 * @param children
 * @return
 */
export const updateTreeChildren = (
  rows: TreeRow[],
  parentPath: string,
  children: TreeRow[],
): TreeRow[] =>
  rows.map((row) => {
    if (row.path === parentPath) {
      return { ...row, children: children.length ? children : undefined };
    }
    return row.children
      ? { ...row, children: updateTreeChildren(row.children, parentPath, children) }
      : row;
  });

/**
 * 递归拉取工作区全部文件清单（path -> size），用于本轮产生文件检测
 * @param api
 * @param workspaceId
 * @param userId
 * @return
 */
export const listAllFiles = async (
  api: WorkspaceFileApi,
  workspaceId: string,
  userId: string,
): Promise<Map<string, number>> => {
  const files = new Map<string, number>();
  const walk = async (dir?: string): Promise<void> => {
    const nodes = await api.tree(workspaceId, userId, dir);
    await Promise.all(
      (nodes ?? []).map(async (node) => {
        // 回收站目录不参与文件清单与产物对比
        if (isTrashEntry(node.path)) {
          return;
        }
        if (node.dir) {
          await walk(node.path);
        } else {
          files.set(node.path, node.size ?? 0);
        }
      }),
    );
  };
  await walk();
  return files;
};

/**
 * 对比回复前后的文件快照，返回本轮新增文件
 * @param api
 * @param workspaceId
 * @param userId
 * @param before
 * @return
 */
export const diffProducedFiles = async (
  api: WorkspaceFileApi,
  workspaceId: string,
  userId: string,
  before: Map<string, number>,
): Promise<ChatFileRef[]> => {
  const after = await listAllFiles(api, workspaceId, userId);
  const produced: ChatFileRef[] = [];
  after.forEach((_size, path) => {
    if (!before.has(path)) {
      produced.push({ path, name: path.split(/[\\/]/).pop() ?? path });
    }
  });
  return produced;
};

/**
 * 取路径最后一段名称（下载命名与回收站条目展示）
 * @param path
 * @return
 */
export const baseNameOf = (path: string): string => {
  const norm = path.replace(/\\/g, '/');
  const idx = norm.lastIndexOf('/');
  return idx === -1 ? norm : norm.slice(idx + 1);
};

/**
 * 工作区类型展示文案（各端按自身 UI 组件渲染标识）
 */
export const WORKSPACE_TYPE_META: Record<WorkspaceType, { text: string }> = {
  SERVER: { text: '云端' },
  CONNECTOR: { text: '本地连接' },
  BROWSER: { text: '浏览器直连' },
};

/**
 * 工作区默认选中策略：在线本地连接 > 浏览器直连 > 云端（连接器离线时回落下一优先级；
 * 在线状态字段后端具备前视为在线，仅 SERVER 数据时行为与现状选中首个一致）
 * @param workspaces
 * @return
 */
export const selectDefaultWorkspace = (
  workspaces: WorkspaceItem[],
): WorkspaceItem | undefined => {
  if (!workspaces.length) {
    return undefined;
  }
  const onlineConnector = workspaces.find((w) => w.type === 'CONNECTOR' && w.online !== false);
  if (onlineConnector) {
    return onlineConnector;
  }
  const browser = workspaces.find((w) => w.type === 'BROWSER');
  if (browser) {
    return browser;
  }
  const server = workspaces.find((w) => !w.type || w.type === 'SERVER');
  if (server) {
    return server;
  }
  return workspaces.find((w) => w.type === 'CONNECTOR') ?? workspaces[0];
};

/**
 * 工作区选择器候选过滤：企业端信封形态明确下发云端模式关闭时，云端工作区不出现
 * （社区裸数组 cloudModeExplicit=false，行为保持现状不变）
 * @param workspaces
 * @param cloudEnabled
 * @param cloudModeExplicit
 * @return
 */
export const filterWorkspacesForSelector = (
  workspaces: WorkspaceItem[],
  cloudEnabled: boolean,
  cloudModeExplicit: boolean,
): WorkspaceItem[] => {
  if (!cloudModeExplicit || cloudEnabled) {
    return workspaces;
  }
  return workspaces.filter((w) => w.type !== 'SERVER');
};

/**
 * 连接器离线时的云端回落目标（改用云端工作区继续：仅切换当前对话绑定，不动对话历史）
 * @param workspaces
 * @return
 */
export const selectCloudFallbackWorkspace = (
  workspaces: WorkspaceItem[],
): WorkspaceItem | undefined => workspaces.find((w) => !w.type || w.type === 'SERVER');

/**
 * 识别连接器设备离线类报错（对齐后端 ConnectorWorkspaceGateway 离线文案）
 * @param message
 * @return
 */
export const isConnectorOfflineError = (message?: string): boolean =>
  Boolean(message && message.includes('设备离线'));

/**
 * 待上传条目（relativePath 保留拖拽/文件夹选择的相对结构）
 */
export interface WorkspaceUploadItem {
  file: File;

  relativePath?: string;
}

/**
 * 递归收集拖拽目录条目（前缀拼接父级相对路径）
 * @param entry
 * @param prefix
 * @param items
 */
const collectFileSystemEntry = async (
  entry: FileSystemEntry,
  prefix: string,
  items: WorkspaceUploadItem[],
): Promise<void> => {
  if (entry.isFile) {
    const fileEntry = entry as FileSystemFileEntry;
    const file = await new Promise<File | null>((resolve) => {
      fileEntry.file(resolve, () => resolve(null));
    });
    if (file) {
      items.push({ file, relativePath: prefix ? `${prefix}/${file.name}` : file.name });
    }
    return;
  }
  if (entry.isDirectory) {
    const dirEntry = entry as FileSystemDirectoryEntry;
    const reader = dirEntry.createReader();
    const nextPrefix = prefix ? `${prefix}/${dirEntry.name}` : dirEntry.name;
    const readBatch = (): Promise<FileSystemEntry[]> =>
      new Promise((resolve, reject) => {
        // readEntries 单次只返回一批条目，需循环读取直到空批
        reader.readEntries(resolve, () => reject(new Error(`读取目录「${dirEntry.name}」失败`)));
      });
    let batch = await readBatch();
    while (batch.length > 0) {
      for (const child of batch) {
        await collectFileSystemEntry(child, nextPrefix, items);
      }
      batch = await readBatch();
    }
  }
};

/**
 * 从拖拽事件收集待上传条目（保留相对目录结构，浏览器不支持条目 API 时退化为普通文件）
 * @param dataTransfer
 * @return
 */
export const collectDraggedFiles = async (
  dataTransfer: DataTransfer,
): Promise<WorkspaceUploadItem[]> => {
  const items: WorkspaceUploadItem[] = [];
  // webkitGetAsEntry 必须在事件同步阶段集中调用，事件结束后条目即失效
  const entries = Array.from(dataTransfer.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.webkitGetAsEntry())
    .filter((entry): entry is FileSystemEntry => Boolean(entry));
  if (entries.length > 0) {
    for (const entry of entries) {
      await collectFileSystemEntry(entry, '', items);
    }
    return items;
  }
  return Array.from(dataTransfer.files ?? []).map((file) => ({ file }));
};

/**
 * 按相对目录分组上传（保留文件夹结构），返回上传文件总数
 * @param api
 * @param workspaceId
 * @param targetDir 目标目录（空表示根目录）
 * @param items
 * @return
 */
export const uploadWorkspaceEntries = async (
  api: WorkspaceFileApi,
  workspaceId: string,
  targetDir: string | undefined,
  items: WorkspaceUploadItem[],
): Promise<number> => {
  const groups = new Map<string, File[]>();
  for (const item of items) {
    const parent = parentOf((item.relativePath ?? '').replace(/\\/g, '/'));
    const dir = [targetDir ?? '', parent].filter(Boolean).join('/');
    const bucket = groups.get(dir);
    if (bucket) {
      bucket.push(item.file);
    } else {
      groups.set(dir, [item.file]);
    }
  }
  let count = 0;
  for (const [dir, files] of groups) {
    await api.upload(workspaceId, dir || undefined, files);
    count += files.length;
  }
  return count;
};

/**
 * 触发浏览器保存 Blob 文件（下载与导出 zip 共用）
 * @param blob
 * @param fileName
 */
export const saveBlobFile = (blob: Blob, fileName: string): void => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};
