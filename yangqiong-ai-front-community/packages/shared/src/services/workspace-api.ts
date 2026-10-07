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
import type { HttpRequest } from './http';

/**
 * 工作区类型（SERVER=云端服务器目录，CONNECTOR=本地连接器，BROWSER=浏览器直连）
 */
export type WorkspaceType = 'SERVER' | 'CONNECTOR' | 'BROWSER';

/**
 * 工作区信息（列表项附目录健康状态）
 */
export type WorkspaceItem = {
  /** 雪花ID超出JS安全整数范围，后端序列化为字符串，前端全程保持字符串避免精度丢失 */
  id: string;
  userId?: string;
  name: string;
  rootPath: string;
  description?: string;
  approvalMode: 'MANUAL' | 'AUTO' | 'FULL_ACCESS' | 'CUSTOM';
  status: number;
  createTime?: string;
  updateTime?: string;
  valid?: boolean;
  type?: WorkspaceType;
  /** 连接器/浏览器直连工作区在线状态（未返回视为在线） */
  online?: boolean;
};

/**
 * 回收站条目
 */
export type WorkspaceTrashItem = {
  path: string;
  name?: string;
  dir?: boolean;
  size?: number;
  deletedAt?: string;
};

/**
 * 工作区列表信封（云端模式部署下发 cloudEnabled 部署级开关，缺省视为未开启）
 */
export type WorkspaceListEnvelope = {
  list: WorkspaceItem[];
  cloudEnabled?: boolean;
};

/**
 * 归一化工作区列表结果（cloudModeExplicit 标记企业端信封形态，社区裸数组恒为 false）
 */
export type NormalizedWorkspaceList = {
  workspaces: WorkspaceItem[];
  cloudEnabled: boolean;
  cloudModeExplicit: boolean;
};

/**
 * 归一化工作区列表响应（兼容裸数组与信封两种后端形态）
 * @param data
 * @return
 */
export const normalizeWorkspaceList = (
  data: WorkspaceItem[] | WorkspaceListEnvelope | undefined,
): NormalizedWorkspaceList => {
  if (Array.isArray(data)) {
    return { workspaces: data, cloudEnabled: false, cloudModeExplicit: false };
  }
  return {
    workspaces: data?.list ?? [],
    cloudEnabled: data?.cloudEnabled === true,
    cloudModeExplicit: data != null,
  };
};

/**
 * 文件树节点（懒加载一级）
 */
export type WorkspaceTreeNode = {
  name: string;
  path: string;
  dir: boolean;
  size?: number;
};

/**
 * 工作簿sheet解析结果
 */
export type WorkspaceExcelSheet = {
  name: string;
  rowCount: number;
  header: (string | number | boolean | null)[];
  rows: (string | number | boolean | null)[][];
  truncated: boolean;
};

/**
 * Word文档段落
 */
export type WorkspaceDocxParagraph = {
  index: number;
  type: 'heading' | 'paragraph';
  text: string;
};

/**
 * Word文档表格（首行为表头）
 */
export type WorkspaceDocxTable = {
  index: number;
  header: string[];
  rows: string[][];
  truncated: boolean;
};

/**
 * zip压缩包条目
 */
export type WorkspaceZipEntry = {
  name: string;
  dir: boolean;
  size: number;
  compressedSize: number;
};

/**
 * 文件预览结果（text=文本内容，image=图片dataUrl，excel=工作簿解析，docx=Word结构化，
 * zip=条目清单，pdf=base64内联，binary=不支持预览的二进制）
 */
export type WorkspaceFilePreview = {
  name: string;
  path: string;
  size: number;
  type?: 'text' | 'image' | 'excel' | 'docx' | 'zip' | 'pdf' | 'binary';
  truncated?: boolean;
  content?: string;
  workbook?: { sheets: WorkspaceExcelSheet[] };
  document?: {
    paragraphs: WorkspaceDocxParagraph[];
    tables: WorkspaceDocxTable[];
    truncated: boolean;
  };
  entries?: WorkspaceZipEntry[];
};

export type WorkspaceFileSaveParams = {
  userId: string;
  /** 文件路径（相对工作区根） */
  path: string;
  /** 文本内容（UTF-8） */
  content: string;
};

export type WorkspaceSaveParams = {
  userId: string;
  name: string;
  /** 服务器目录绝对路径（SERVER 类型必填；CONNECTOR 类型不传，服务端只存不透明ID） */
  rootPath?: string;
  description?: string;
  approvalMode?: WorkspaceItem['approvalMode'];
  /** 目录不存在时自动创建（默认目录一键创建用） */
  autoCreate?: boolean;
  /** 工作区类型（缺省视为SERVER） */
  type?: WorkspaceType;
  /** 连接器设备ID（CONNECTOR 类型登记时绑定，工具请求固定路由到该设备） */
  deviceId?: string;
};

export type WorkspaceUpdateParams = {
  userId: string;
  name?: string;
  description?: string;
  approvalMode?: WorkspaceItem['approvalMode'];
  status?: number;
};

export type WorkspaceDirectoryItem = {
  path: string;
  name: string;
};

export const createWorkspaceApi = (http: HttpRequest) => ({
  list: (userId: string) =>
    http.get<WorkspaceItem[] | WorkspaceListEnvelope>('/api/workspace', { params: { userId } }),
  browseDirs: (path?: string) =>
    http.get<WorkspaceDirectoryItem[]>('/api/workspace/browse-dirs', { params: { path } }),
  create: (data: WorkspaceSaveParams) => http.post<WorkspaceItem>('/api/workspace', data),
  update: (id: string, data: WorkspaceUpdateParams) => http.put<void>(`/api/workspace/${id}`, data),
  remove: (id: string, userId: string) =>
    http.delete<void>(`/api/workspace/${id}`, { params: { userId } }),
  tree: (id: string, userId: string, path?: string) =>
    http.get<WorkspaceTreeNode[]>(`/api/workspace/${id}/tree`, { params: { userId, path } }),
  preview: (id: string, userId: string, path: string) =>
    http.get<WorkspaceFilePreview>(`/api/workspace/${id}/file`, { params: { userId, path } }),
  saveContent: (id: string, data: WorkspaceFileSaveParams) =>
    http.put<{ path: string; size: number }>(`/api/workspace/${id}/file/content`, data),
  copyFile: (id: string, data: { userId: string; path: string; targetDir?: string }) =>
    http.post<{ path: string }>(`/api/workspace/${id}/file/copy`, data),
  createDirectory: (id: string, data: { userId: string; path?: string; name: string }) =>
    http.post<{ path: string }>(`/api/workspace/${id}/file/mkdir`, data),
  moveFile: (id: string, data: { userId: string; path: string; targetDir?: string }) =>
    http.post<{ path: string }>(`/api/workspace/${id}/file/move`, data),
  renameFile: (id: string, data: { userId: string; path: string; newName: string }) =>
    http.post<void>(`/api/workspace/${id}/file/rename`, data),
  deleteFile: (id: string, userId: string, path: string) =>
    http.delete<void>(`/api/workspace/${id}/file`, { params: { userId, path } }),
  upload: (id: string, dir: string | undefined, files: File[]) => {
    const formData = new FormData();
    if (dir) {
      formData.append('dir', dir);
    }
    files.forEach((file) => formData.append('file', file));
    // 文件传输端点不套用全局超时，避免大文件上传被中断
    return http.post<unknown>(`/api/workspace/${id}/file/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0,
    });
  },
  download: (id: string, path: string) =>
    http.get<Blob>(`/api/workspace/${id}/file/download`, {
      params: { path },
      responseType: 'blob',
      timeout: 0,
    }),
  exportZip: (id: string, path?: string) =>
    http.post<Blob>(`/api/workspace/${id}/export`, { path }, { responseType: 'blob', timeout: 0 }),
  listTrash: (id: string) => http.get<WorkspaceTrashItem[]>(`/api/workspace/${id}/trash`),
  restore: (id: string, path: string) =>
    http.post<void>(`/api/workspace/${id}/trash/restore`, { path }),
});
