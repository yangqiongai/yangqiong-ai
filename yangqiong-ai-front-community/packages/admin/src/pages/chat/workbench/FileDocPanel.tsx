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
import { Alert, App, Button, Empty, Input, Space, Spin, Table, Tabs, Tag, Typography } from 'antd';
import {
  CloseOutlined,
  FileOutlined,
  FileTextOutlined,
  FolderOutlined,
  LeftOutlined,
  RightOutlined,
  SaveOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import { renderAsync } from 'docx-preview';
import { api } from '@/services';
import { FileTypeIcon } from './FileTypeIcon';
import type {
  WorkspaceDocxParagraph,
  WorkspaceDocxTable,
  WorkspaceExcelSheet,
  WorkspaceFilePreview,
  WorkspaceZipEntry,
} from '@yangqiong/shared';

const { Text } = Typography;

/**
 * 字节数格式化（文档区头部展示）
 * @param size
 * @return
 */
const formatSize = (size?: number): string => {
  if (size == null) {
    return '-';
  }
  return size >= 1024 ? `${(size / 1024).toFixed(1)} KB` : `${size} B`;
};

/**
 * 工作簿sheet表格渲染（antd Table，前100行截断与后端约定一致）
 * @param sheet
 * @return
 */
const ExcelSheetTable: React.FC<{ sheet: WorkspaceExcelSheet }> = ({ sheet }) => {
  const columns = useMemo(
    () =>
      (sheet.header ?? []).map((title, index) => ({
        title: title == null || title === '' ? `列${index + 1}` : String(title),
        dataIndex: index,
        key: index,
        ellipsis: true,
        render: (value: string | number | boolean | null) =>
          value == null || value === '' ? '-' : String(value),
      })),
    [sheet.header],
  );
  const dataSource = useMemo(
    () => (sheet.rows ?? []).map((row, rowIndex) => ({ ...row, key: rowIndex })),
    [sheet.rows],
  );
  return (
    <Table
      size="small"
      columns={columns}
      dataSource={dataSource}
      pagination={dataSource.length > 20 ? { pageSize: 20, size: 'small' } : false}
      scroll={{ x: 'max-content', y: 'min(380px, calc(100vh - 620px))' }}
      bordered
    />
  );
};

/**
 * Word文档条目（按body内下标排序还原段落与表格混排顺序）
 */
type DocxItem =
  | { key: string; index: number; kind: 'paragraph'; paragraph: WorkspaceDocxParagraph }
  | { key: string; index: number; kind: 'table'; table: WorkspaceDocxTable };

/**
 * Word文档表格渲染
 * @param table
 * @return
 */
const DocxTableView: React.FC<{ table: WorkspaceDocxTable }> = ({ table }) => {
  const columns = (table.header ?? []).map((title, index) => ({
    title: title === '' ? `列${index + 1}` : title,
    dataIndex: String(index),
    key: index,
    ellipsis: true,
    render: (value: string) => (value === '' ? '-' : value),
  }));
  const dataSource = (table.rows ?? []).map((row, rowIndex) => {
    const record: Record<string, string> = { key: String(rowIndex) };
    row.forEach((value, colIndex) => {
      record[String(colIndex)] = value;
    });
    return record;
  });
  return (
    <Table
      size="small"
      columns={columns}
      dataSource={dataSource}
      pagination={false}
      scroll={{ x: 'max-content' }}
      bordered
      style={{ margin: '4px 0 12px' }}
    />
  );
};

/**
 * Word文档内容渲染（标题加粗、段落正文、表格，按文档原始顺序混排）
 * @param doc
 * @return
 */
const DocxView: React.FC<{ doc: NonNullable<WorkspaceFilePreview['document']> }> = ({ doc }) => {
  const items = useMemo<DocxItem[]>(
    () => [
      ...(doc.paragraphs ?? []).map((paragraph) => ({
        key: `p-${paragraph.index}`,
        index: paragraph.index,
        kind: 'paragraph' as const,
        paragraph,
      })),
      ...(doc.tables ?? []).map((table) => ({
        key: `t-${table.index}`,
        index: table.index,
        kind: 'table' as const,
        table,
      })),
    ].sort((a, b) => a.index - b.index),
    [doc],
  );
  return (
    <div>
      {items.map((item) =>
        item.kind === 'paragraph' ? (
          item.paragraph.type === 'heading' ? (
            <div
              key={item.key}
              style={{ fontWeight: 600, fontSize: 15, color: 'var(--wb-text)', margin: '12px 0 6px' }}
            >
              {item.paragraph.text}
            </div>
          ) : (
            <p key={item.key} style={{ margin: '0 0 8px', fontSize: 13, lineHeight: 1.8, color: '#1F2430' }}>
              {item.paragraph.text}
            </p>
          )
        ) : (
          <DocxTableView key={item.key} table={item.table} />
        ),
      )}
      {doc.truncated && (
        <Alert type="warning" showIcon message="文档内容超限已截断" style={{ marginTop: 8, border: 'none' }} />
      )}
    </div>
  );
};

/**
 * Word文档高保真渲染（docx-preview还原原始排版，失败或无dataUrl时降级结构化DocxView）
 * @param dataUrl
 * @param doc
 * @param name
 * @return
 */
const DocxPreviewView: React.FC<{
  dataUrl?: string;
  doc?: NonNullable<WorkspaceFilePreview['document']>;
  name: string;
}> = ({ dataUrl, doc, name }) => {
  const hostRef = useRef<HTMLDivElement>(null);
  const [renderFailed, setRenderFailed] = useState(false);
  const buffer = useMemo(() => {
    if (!dataUrl || !dataUrl.includes(',')) {
      return undefined;
    }
    try {
      const binary = atob(dataUrl.slice(dataUrl.indexOf(',') + 1));
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      return bytes.buffer;
    } catch {
      return undefined;
    }
  }, [dataUrl]);
  useEffect(() => {
    setRenderFailed(false);
    const host = hostRef.current;
    if (!host || !buffer) {
      return;
    }
    let cancelled = false;
    // renderAsync 会先清空传入容器：渲染到游离节点，完成后一次性挂载，规避 StrictMode 双执行竞态
    const styleBox = document.createElement('div');
    const bodyBox = document.createElement('div');
    renderAsync(buffer, bodyBox, styleBox)
      .then(() => {
        if (cancelled) {
          return;
        }
        host.innerHTML = '';
        host.appendChild(styleBox);
        host.appendChild(bodyBox);
      })
      .catch(() => {
        if (!cancelled) {
          setRenderFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [buffer]);
  if (!buffer || renderFailed) {
    return doc ? (
      <DocxView doc={doc} />
    ) : (
      <Empty description="Word文档渲染失败" style={{ padding: 48 }} />
    );
  }
  return (
    <div
      ref={hostRef}
      data-docx-name={name}
      style={{ background: '#F7F8FB', border: '1px solid var(--wb-border)', borderRadius: 8, padding: 8 }}
    />
  );
};

/**
 * zip条目清单渲染
 * @param entries
 * @param truncated
 * @return
 */
const ZipView: React.FC<{ entries: WorkspaceZipEntry[]; truncated?: boolean }> = ({ entries, truncated }) => {
  const dataSource = (entries ?? []).map((entry, index) => ({ ...entry, key: index }));
  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (value: string, record: WorkspaceZipEntry) => (
        <span>
          {record.dir ? (
            <FolderOutlined style={{ marginRight: 6, color: 'var(--wb-primary)' }} />
          ) : (
            <FileOutlined style={{ marginRight: 6, opacity: 0.55 }} />
          )}
          {value}
        </span>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 110,
      render: (value: number, record: WorkspaceZipEntry) => (record.dir ? '-' : formatSize(value)),
    },
    {
      title: '压缩后',
      dataIndex: 'compressedSize',
      key: 'compressedSize',
      width: 110,
      render: (value: number, record: WorkspaceZipEntry) => (record.dir ? '-' : formatSize(value)),
    },
  ];
  return (
    <>
      {truncated && (
        <Alert type="warning" showIcon message="条目超 500 个仅展示前 500 条" style={{ marginBottom: 8, border: 'none' }} />
      )}
      <Table
        size="small"
        columns={columns}
        dataSource={dataSource}
        pagination={dataSource.length > 20 ? { pageSize: 20, size: 'small' } : false}
        scroll={{ x: 'max-content', y: 'min(380px, calc(100vh - 620px))' }}
        bordered
      />
    </>
  );
};

/**
 * PDF渲染（base64转Blob URL走浏览器原生查看器，卸载时释放）
 * @param dataUrl
 * @param name
 * @return
 */
const PdfView: React.FC<{ dataUrl?: string; name: string }> = ({ dataUrl, name }) => {
  const blobUrl = useMemo(() => {
    if (!dataUrl || !dataUrl.includes(',')) {
      return undefined;
    }
    try {
      const binary = atob(dataUrl.slice(dataUrl.indexOf(',') + 1));
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      return URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
    } catch {
      return undefined;
    }
  }, [dataUrl]);
  useEffect(
    () => () => {
      if (blobUrl) {
        URL.revokeObjectURL(blobUrl);
      }
    },
    [blobUrl],
  );
  if (!blobUrl) {
    return <Empty description="PDF内容加载失败" style={{ padding: 48 }} />;
  }
  return (
    <iframe
      title={name}
      src={blobUrl}
      style={{
        width: '100%',
        height: 'calc(100vh - 330px)',
        border: '1px solid var(--wb-border)',
        borderRadius: 8,
        background: '#525659',
      }}
    />
  );
};

interface FileDocPanelProps {
  /** 雪花ID后端序列化为字符串，全程保持字符串避免精度丢失 */
  workspaceId?: string;
  userId?: string;

  /**
   * 当前文档区文件相对路径（空展示占位）
   */
  path?: string;

  /**
   * 可切换的文件路径列表（长度>1时显示上一个/下一个导航）
   */
  files?: string[];

  /**
   * 切换文档文件回调（导航点击时触发）
   */
  onPathChange?: (path: string) => void;

  /**
   * 关闭文档区回调（切回对话tab）
   */
  onClose: () => void;

  /**
   * 保存成功回调（外层刷新文件树等）
   */
  onSaved?: () => void;
}

/**
 * 工作区文档编辑查看面板（文本可直接编辑保存，图片/Excel/Word/zip/PDF只读预览，多文件可切换）
 */
export const FileDocPanel: React.FC<FileDocPanelProps> = ({
  workspaceId,
  userId,
  path,
  files,
  onPathChange,
  onClose,
  onSaved,
}) => {
  const { message } = App.useApp();
  const [preview, setPreview] = useState<WorkspaceFilePreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /**
   * 文本编辑内容（进入编辑时以预览内容初始化）
   */
  const [draft, setDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const index = files && path ? files.indexOf(path) : -1;
  const hasPrev = index > 0;
  const hasNext = files ? index >= 0 && index < files.length - 1 : false;

  useEffect(() => {
    setDraft(null);
    if (!path || !workspaceId || !userId) {
      setPreview(null);
      setError(null);
      return;
    }
    setLoading(true);
    setPreview(null);
    setError(null);
    let cancelled = false;
    api.workspace
      .preview(workspaceId, userId, path)
      .then((data) => {
        if (!cancelled) {
          setPreview(data);
          // 文本文件加载后直接进入可编辑状态（草稿=原文，未修改时保存禁用）
          setDraft(data.type === 'text' ? (data.content ?? '') : null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          const text = err instanceof Error ? err.message : '读取文件失败';
          setError(text);
          message.error(text);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, workspaceId, userId]);

  /**
   * 导航切换到相邻文件
   * @param offset
   */
  const navigate = (offset: number) => {
    if (!files || index < 0) {
      return;
    }
    const next = files[index + offset];
    if (next) {
      onPathChange?.(next);
    }
  };

  /**
   * 文本是否已修改（草稿与原文对比）
   */
  const editing = draft != null && preview?.type === 'text' && draft !== preview.content;

  /**
   * 保存文本编辑内容（后端校验文本类型与200KB上限）
   */
  const handleSave = async () => {
    if (!editing || !workspaceId || !userId || !path || saving) {
      return;
    }
    setSaving(true);
    try {
      await api.workspace.saveContent(workspaceId, { userId, path, content: draft });
      message.success('已保存');
      onSaved?.();
      // 重新加载预览以刷新大小等元信息（草稿同步为已保存内容）
      const data = await api.workspace.preview(workspaceId, userId, path);
      setPreview(data);
      setDraft(data.type === 'text' ? (data.content ?? '') : null);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (!path) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="wb-empty-hero" style={{ textAlign: 'center' }}>
          <div className="wb-hero-ring" style={{ width: 78, height: 78, margin: '0 auto 16px' }}>
            <FileTextOutlined style={{ fontSize: 28, color: '#8B5CF6' }} />
          </div>
          <div className="wb-hero-title" style={{ fontSize: 15 }}>尚未打开文档</div>
          <div className="wb-hero-sub">点击左侧文件树或对话中的文件，在此查看与编辑</div>
        </div>
      </div>
    );
  }

  const renderBody = () => {
    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: 64 }}>
          <Spin />
        </div>
      );
    }
    if (!preview) {
      return <Empty description={error ?? '暂无预览内容'} style={{ padding: 48 }} />;
    }
    if (preview.type === 'text') {
      if (preview.truncated) {
        return (
          <>
            <Alert
              type="warning"
              showIcon
              message="文件超 200KB 已截断，仅支持查看"
              style={{ marginBottom: 12, border: 'none' }}
            />
            <pre
              style={{
                margin: 0,
                padding: 12,
                background: '#F7F8FB',
                border: '1px solid var(--wb-border)',
                color: '#1F2430',
                borderRadius: 8,
                fontFamily: 'SFMono-Regular, Consolas, monospace',
                fontSize: 12,
                lineHeight: 1.6,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                maxHeight: 'calc(100vh - 340px)',
                overflowY: 'auto',
              }}
            >
              {preview.content}
            </pre>
          </>
        );
      }
      return (
        <Input.TextArea
          value={draft ?? ''}
          onChange={(e) => setDraft(e.target.value)}
          spellCheck={false}
          style={{
            height: 'calc(100vh - 360px)',
            minHeight: 320,
            resize: 'none',
            fontFamily: 'SFMono-Regular, Consolas, monospace',
            fontSize: 12,
            lineHeight: 1.6,
            borderColor: 'rgba(79, 107, 255, 0.4)',
          }}
        />
      );
    }
    return (
      <>
        <Text
          type="secondary"
          style={{ fontSize: 12, color: 'var(--wb-dim)', display: 'block', marginBottom: 8 }}
        >
          {preview.path}
        </Text>
        {preview.type === 'image' ? (
          <div
            style={{
              height: 'calc(100vh - 360px)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: '#F7F8FB',
              border: '1px solid var(--wb-border)',
              borderRadius: 8,
              padding: 12,
            }}
          >
            <img
              src={preview.content}
              alt={preview.name}
              style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
            />
          </div>
        ) : preview.type === 'excel' && preview.workbook ? (
          <Tabs
            size="small"
            items={(preview.workbook.sheets ?? []).map((sheet) => ({
              key: sheet.name,
              label: (
                <span>
                  {sheet.name}
                  <Tag style={{ marginLeft: 6 }}>{sheet.rowCount}行</Tag>
                </span>
              ),
              children: (
                <>
                  {sheet.truncated && (
                    <Alert
                      type="warning"
                      showIcon
                      message="数据行超 100 行仅展示前 100 行"
                      style={{ marginBottom: 8, border: 'none' }}
                    />
                  )}
                  <ExcelSheetTable sheet={sheet} />
                </>
              ),
            }))}
          />
        ) : preview.type === 'docx' ? (
          <DocxPreviewView dataUrl={preview.content} doc={preview.document} name={preview.name} />
        ) : preview.type === 'zip' && preview.entries ? (
          <ZipView entries={preview.entries} truncated={preview.truncated} />
        ) : preview.type === 'pdf' ? (
          <PdfView dataUrl={preview.content} name={preview.name} />
        ) : (
          <Empty
            description="该格式暂不支持在线预览（如 doc/rar），可在对话中让 AI 读取内容"
            style={{ padding: 48 }}
          />
        )}
      </>
    );
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      {/* 头部：文件名 + 导航 + 关闭 */}
      <div
        style={{
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '8px 16px',
          borderBottom: '1px solid var(--wb-border)',
          background: 'rgba(255, 255, 255, 0.6)',
        }}
      >
        <Space size={8} style={{ minWidth: 0 }}>
          <FileTypeIcon name={preview ? preview.name : path} />
          <span
            style={{
              fontWeight: 600,
              color: 'var(--wb-text)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {preview ? preview.name : path}
          </span>
          {preview && (
            <Tag
              style={{
                marginInlineEnd: 0,
                borderRadius: 999,
                background: 'rgba(35, 40, 56, 0.05)',
                border: 'none',
                color: 'var(--wb-dim)',
              }}
            >
              {formatSize(preview.size)}
            </Tag>
          )}
          {preview?.type === 'text' && (
            <Tag
              style={{
                marginInlineEnd: 0,
                borderRadius: 999,
                border: 'none',
                background: editing ? 'rgba(245, 158, 11, 0.14)' : 'rgba(16, 185, 129, 0.12)',
                color: editing ? '#B45309' : '#059669',
              }}
            >
              {editing ? '编辑中' : '可编辑'}
            </Tag>
          )}
        </Space>
        <Space size={4} style={{ flexShrink: 0 }}>
          {files && files.length > 1 && (
            <>
              <Button type="text" size="small" icon={<LeftOutlined />} disabled={!hasPrev} onClick={() => navigate(-1)}>
                上一个
              </Button>
              <Text type="secondary" style={{ fontSize: 12, color: 'var(--wb-dim)' }}>
                {index + 1}/{files.length}
              </Text>
              <Button type="text" size="small" disabled={!hasNext} onClick={() => navigate(1)}>
                下一个 <RightOutlined />
              </Button>
            </>
          )}
          <Button type="text" size="small" icon={<CloseOutlined />} onClick={onClose} title="关闭" />
        </Space>
      </div>

      {/* 内容区 */}
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: 16 }}>{renderBody()}</div>

      {/* 文本编辑操作条 */}
      {preview?.type === 'text' && !preview.truncated && (
        <div
          style={{
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '8px 16px',
            borderTop: '1px solid var(--wb-border)',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12, color: 'var(--wb-dim)' }}>
            {editing ? '内容已修改，保存后写入工作区文件' : '文本文件支持直接编辑与保存'}
          </Text>
          <Space size={8}>
            {editing && (
              <Button
                size="small"
                icon={<UndoOutlined />}
                disabled={saving}
                onClick={() => preview && setDraft(preview.content ?? '')}
              >
                还原
              </Button>
            )}
            <Button
              type="primary"
              size="small"
              className="wb-gradient-btn"
              icon={<SaveOutlined />}
              loading={saving}
              disabled={!editing}
              onClick={() => void handleSave()}
            >
              保存
            </Button>
          </Space>
        </div>
      )}
    </div>
  );
};

export default FileDocPanel;
