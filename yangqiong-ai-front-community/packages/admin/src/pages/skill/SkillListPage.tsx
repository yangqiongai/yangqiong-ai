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
import React, { useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Popover,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  TreeSelect,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { CategoryTreeNode, SkillDefinition, SkillDraft } from '@yangqiong/shared';
import { CategoryTreePanel } from '@/components/CategoryTreePanel';
import type { CategoryTreeService } from '@/components/CategoryTreePanel';
import { MarkdownContent } from '../chat/components/ChatMarkdown';

const QUERY_KEY = 'skills';
const VERSION_QUERY_KEY = 'skillVersions';
const VERSION_DETAIL_QUERY_KEY = 'skillVersionDetail';
const CATEGORY_TREE_QUERY_KEY = 'skill-category-tree';
const HISTORY_PAGE_SIZE = 5;
const TRUST_LEVELS = ['BUILTIN', 'TRUSTED', 'COMMUNITY', 'AGENT_CREATED'];

/**
 * 技能分类树数据服务
 */
const skillCategoryService: CategoryTreeService = {
  tree: () => api.skill.category.tree(),
  create: (data) => api.skill.category.create(data),
  update: (id, data) => api.skill.category.update(id, data),
  remove: (id) => api.skill.category.delete(id),
};

/**
 * 分类树节点转TreeSelect数据（value=分类code，与实体category字段对齐）
 * @param nodes
 * @return
 */
const toCategoryTreeData = (nodes?: CategoryTreeNode[]): any[] =>
  (nodes ?? []).map((node) => ({
    title: node.name,
    value: node.code,
    children: toCategoryTreeData(node.children),
  }));

/**
 * 技能类型中文标签
 */
const SKILL_TYPE_LABELS: Record<string, string> = {
  UPLOADED: '上传',
  GENERATED: 'AI生成',
  AGENT_CREATED: 'Agent创建',
  BUILT_IN: '内置',
  CUSTOM: '自定义',
};

/**
 * 技能类型标签颜色
 */
const SKILL_TYPE_COLORS: Record<string, string> = {
  UPLOADED: 'blue',
  GENERATED: 'purple',
  AGENT_CREATED: 'geekblue',
  BUILT_IN: 'gold',
  CUSTOM: 'cyan',
};

/**
 * 信任等级中文标签
 */
const TRUST_LEVEL_LABELS: Record<string, string> = {
  BUILTIN: '内置（随服务分发）',
  TRUSTED: '可信',
  COMMUNITY: '社区',
  AGENT_CREATED: 'Agent创建',
};

/**
 * 是否内置技能（BUILTIN信任等级随服务分发，不可删除、不可伪造）
 * @param record
 * @return
 */
const isBuiltinSkill = (record?: SkillDefinition | null) => record?.trustLevel === 'BUILTIN';

/**
 * 评测维度中文标签
 */
const DIMENSION_LABELS: Record<string, string> = {
  structure: '结构完整性',
  clarity: '指令清晰度',
  security: '安全合规',
  toolMatch: '工具匹配度',
};

/**
 * 质量分着色：≥80绿 / 60-79橙 / <60红
 * @param score
 * @return
 */
const qualityScoreColor = (score?: number) =>
  score == null ? undefined : score >= 80 ? 'success' : score >= 60 ? 'warning' : 'error';

/**
 * 渲染版本评分详情（分维度得分与改进建议）
 * @param dimensionsJson
 * @return
 */
const renderEvalDetail = (dimensionsJson?: string) => {
  let dims: Record<string, number> = {};
  let suggestions = '';
  try {
    const parsed = dimensionsJson ? JSON.parse(dimensionsJson) : null;
    dims = parsed?.dimensions ?? {};
    suggestions = parsed?.suggestions ?? '';
  } catch {
    dims = {};
  }
  return (
    <div style={{ maxWidth: 320 }}>
      {Object.entries(DIMENSION_LABELS).map(([key, label]) => (
        <div key={key}>
          {label}：{dims[key] ?? '-'}
        </div>
      ))}
      {suggestions ? (
        <div style={{ marginTop: 8 }}>
          <Typography.Text type="secondary">改进建议：{suggestions}</Typography.Text>
        </div>
      ) : null}
    </div>
  );
};

export interface SkillFormValues {
  skillId?: string;
  skillName: string;
  skillDescription?: string;
  skillContent?: string;
  boundTools?: string[];
  remark?: string;
  /**
   * 所属分类（分类树节点code，空为未分类）
   */
  category?: string;
}

export interface SkillDraftFormValues {
  skillName: string;
  skillDescription?: string;
  skillContent: string;
  boundTools?: string[];
}

/**
 * 技能列表
 */
export const SkillListPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'db' | 'builtin'>('db');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  /** 左树选中的过滤编码（分类code / __ungrouped__，undefined=全部，仅自定义技能生效） */
  const [categoryCode, setCategoryCode] = useState<string | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SkillDefinition | null>(null);
  const [form] = Form.useForm<SkillFormValues>();
  const [contentMode, setContentMode] = useState<'edit' | 'preview'>('edit');

  const [draftOpen, setDraftOpen] = useState(false);
  const [draftForm] = Form.useForm<SkillDraftFormValues>();

  const [draftResult, setDraftResult] = useState<SkillDraft | null>(null);
  const [draftResultOpen, setDraftResultOpen] = useState(false);

  const [trustModalOpen, setTrustModalOpen] = useState(false);
  const [trustTarget, setTrustTarget] = useState<SkillDefinition | null>(null);
  const [trustLevel, setTrustLevel] = useState<string | undefined>();

  const [viewOpen, setViewOpen] = useState(false);
  const [viewTarget, setViewTarget] = useState<SkillDefinition | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyTarget, setHistoryTarget] = useState<SkillDefinition | null>(null);
  const [historyPage, setHistoryPage] = useState(1);
  const [snapshotVersion, setSnapshotVersion] = useState<number | null>(null);

  // 管理列表无缓存：staleTime=0且gcTime=0，每次进入/翻页/搜索均实时请求后端
  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, activeTab, page, size, keyword, categoryCode],
    queryFn: () =>
      api.skill.definition.page({
        source: activeTab,
        keyword,
        page,
        size,
        // 分类过滤仅对自定义技能生效，内置技能随服务分发无分类
        categoryCode: activeTab === 'db' ? categoryCode : undefined,
      }),
    staleTime: 0,
    gcTime: 0,
  });

  // 分类树数据（与左侧分类树面板共享缓存，供表单分类下拉使用）
  const { data: categoryTree } = useQuery({
    queryKey: [CATEGORY_TREE_QUERY_KEY],
    queryFn: api.skill.category.tree,
  });

  const { data: versionData, isLoading: versionLoading } = useQuery({
    queryKey: [VERSION_QUERY_KEY, historyTarget?.skillId, historyPage],
    queryFn: () =>
      api.skill.version.list(historyTarget?.skillId as string, {
        page: historyPage,
        size: HISTORY_PAGE_SIZE,
      }),
    enabled: historyOpen && !!historyTarget,
  });

  const { data: snapshot, isLoading: snapshotLoading } = useQuery({
    queryKey: [VERSION_DETAIL_QUERY_KEY, historyTarget?.skillId, snapshotVersion],
    queryFn: () => api.skill.version.get(historyTarget?.skillId as string, snapshotVersion as number),
    enabled: snapshotVersion != null && !!historyTarget,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: SkillFormValues) => api.skill.definition.create(values),
    onSuccess: () => {
      message.success('创建成功');
      setModalOpen(false);
      invalidateList();
    },
    onError: (err) => {
      // 企业版审批门禁转审批(错误码30007)：非失败，info提示并关闭弹窗
      if ((err as Error & { code?: number }).code === 30007) {
        message.info('技能变更已提交审批，治理中心审批通过后自动生效');
        setModalOpen(false);
        form.resetFields();
        return;
      }
      message.error(err instanceof Error ? err.message : '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: SkillFormValues) => {
      // 后端为整条覆盖式保存，合并原记录字段避免遗漏属性被清空；版本号由后端管理，不随表单提交
      const payload: Partial<SkillDefinition> & { remark?: string } = {
        ...(editing as SkillDefinition),
        ...values,
      };
      delete payload.skillVersion;
      return api.skill.definition.update(editing?.skillId as string, payload);
    },
    onSuccess: () => {
      message.success('更新成功');
      setModalOpen(false);
      setEditing(null);
      invalidateList();
    },
    onError: (err) => {
      // 企业版审批门禁转审批(错误码30007)：非失败，info提示并关闭弹窗
      if ((err as Error & { code?: number }).code === 30007) {
        message.info('技能变更已提交审批，治理中心审批通过后自动生效');
        setModalOpen(false);
        setEditing(null);
        return;
      }
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (skillId: string) => api.skill.definition.delete(skillId),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const generateMutation = useMutation({
    mutationFn: (values: SkillDraftFormValues) => api.skill.definition.generate(values),
    onSuccess: (result) => {
      message.success('草稿已生成');
      setDraftOpen(false);
      setDraftResult(result);
      setDraftResultOpen(true);
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '生成失败');
    },
  });

  const draftMutation = useMutation({
    mutationFn: () => {
      if (!draftResult) throw new Error('草稿为空');
      return api.skill.definition.create(draftResult);
    },
    onSuccess: () => {
      message.success('已保存为技能');
      setDraftResultOpen(false);
      setDraftResult(null);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const trustMutation = useMutation({
    mutationFn: () =>
      api.skill.definition.updateTrustLevel(trustTarget?.skillId as string, trustLevel as string),
    onSuccess: () => {
      message.success('信任等级已更新');
      setTrustModalOpen(false);
      setTrustTarget(null);
      setTrustLevel(undefined);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '操作失败');
    },
  });

  const rollbackMutation = useMutation({
    mutationFn: (targetVersion: number) =>
      api.skill.version.rollback(historyTarget?.skillId as string, targetVersion),
    onSuccess: () => {
      message.success('已回滚并产生新版本');
      queryClient.invalidateQueries({ queryKey: [VERSION_QUERY_KEY, historyTarget?.skillId] });
      invalidateList();
      // 刷新技能当前版本号，保证抽屉内"当前"标记指向新版本
      if (historyTarget) {
        api.skill.definition
          .get(historyTarget.skillId)
          .then((latest) => {
            if (latest) setHistoryTarget(latest);
          })
          .catch(() => undefined);
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '回滚失败');
    },
  });

  const evaluateMutation = useMutation({
    mutationFn: (skillId: string) => api.skill.version.evaluate(skillId),
    onSuccess: (result) => {
      message.success(`评测完成，质量分 ${result?.qualityScore ?? '-'}`);
      invalidateList();
      if (historyTarget && result) {
        setHistoryTarget({ ...historyTarget, qualityScore: result.qualityScore });
      }
      queryClient.invalidateQueries({ queryKey: [VERSION_QUERY_KEY] });
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '评测失败');
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setContentMode('edit');
    setModalOpen(true);
  };

  const openEdit = (record: SkillDefinition) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      skillName: record.skillName,
      skillDescription: record.skillDescription,
      skillContent: record.skillContent,
      boundTools: record.boundTools,
      category: record.category,
    });
    setContentMode('edit');
    setModalOpen(true);
  };

  const openHistory = (record: SkillDefinition) => {
    setHistoryTarget(record);
    setHistoryPage(1);
    setSnapshotVersion(null);
    setHistoryOpen(true);
  };

  const closeHistory = () => {
    setHistoryOpen(false);
    setHistoryTarget(null);
    setHistoryPage(1);
    setSnapshotVersion(null);
  };

  const openDraft = () => {
    draftForm.resetFields();
    setDraftOpen(true);
  };

  const openTrust = (record: SkillDefinition) => {
    setTrustTarget(record);
    setTrustLevel(undefined);
    setTrustModalOpen(true);
  };

  const openView = (record: SkillDefinition) => {
    setViewTarget(record);
    setViewOpen(true);
  };

  const handleTabChange = (key: string) => {
    setActiveTab(key as 'db' | 'builtin');
    setPage(1);
    setSize(10);
    setKeyword('');
    setSearchKeyword('');
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  const handleGenerate = async () => {
    const values = await draftForm.validateFields();
    generateMutation.mutate(values);
  };

  const columns: ColumnsType<SkillDefinition> = [
    { title: '技能 Code', dataIndex: 'skillId', width: 160 },
    { title: '名称', dataIndex: 'skillName', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'skillType',
      width: 110,
      render: (v?: string) =>
        v ? (
          <Tag color={SKILL_TYPE_COLORS[v] ?? 'default'}>{SKILL_TYPE_LABELS[v] ?? v}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '版本',
      dataIndex: 'skillVersion',
      width: 90,
      render: (v?: number) => (v != null ? v : '-'),
    },
    { title: '分类', dataIndex: 'category', width: 110, ellipsis: true },
    {
      title: '质量分',
      dataIndex: 'qualityScore',
      width: 90,
      render: (v?: number) =>
        v != null ? <Tag color={qualityScoreColor(v)}>{v}</Tag> : <Tag>未评测</Tag>,
    },
    {
      title: '绑定工具',
      dataIndex: 'boundTools',
      width: 180,
      render: (v?: string[]) =>
        v && v.length ? v.map((t) => <Tag key={t}>{t}</Tag>) : '-',
    },
    {
      title: '操作',
      width: 360,
      fixed: 'right',
      render: (_: unknown, record: SkillDefinition) => {
        const builtin = isBuiltinSkill(record);
        return (
          <Space size="small" wrap>
            <Button type="link" size="small" onClick={() => openHistory(record)}>
              历史
            </Button>
            <Button
              type="link"
              size="small"
              loading={evaluateMutation.isPending && evaluateMutation.variables === record.skillId}
              onClick={() => evaluateMutation.mutate(record.skillId)}
            >
              评测
            </Button>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Tooltip title={builtin ? '内置技能信任等级固定为内置' : undefined}>
              <Button
                type="link"
                size="small"
                disabled={builtin}
                onClick={() => openTrust(record)}
              >
                信任等级
              </Button>
            </Tooltip>
            <Popconfirm
              title="确认删除该技能？"
              onConfirm={() => deleteMutation.mutate(record.skillId)}
              disabled={builtin}
            >
              <Tooltip title={builtin ? '内置技能随服务分发，不允许删除' : undefined}>
                <Button type="link" size="small" danger disabled={builtin}>
                  删除
                </Button>
              </Tooltip>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  /**
   * 内置技能列（只读，仅支持查看）
   */
  const builtinColumns: ColumnsType<SkillDefinition> = [
    { title: '技能 Code', dataIndex: 'skillId', width: 200 },
    { title: '名称', dataIndex: 'skillName', ellipsis: true },
    {
      title: '描述',
      dataIndex: 'skillDescription',
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '版本',
      dataIndex: 'skillVersion',
      width: 90,
      render: (v?: number) => (v != null ? v : '-'),
    },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_: unknown, record: SkillDefinition) => (
        <Button type="link" size="small" onClick={() => openView(record)}>
          查看
        </Button>
      ),
    },
  ];

  const handleSearch = () => {
    setKeyword(searchKeyword);
    setPage(1);
  };

  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
      <div style={{ width: 260, flexShrink: 0 }}>
        <CategoryTreePanel
          title="技能分类"
          queryKey={CATEGORY_TREE_QUERY_KEY}
          service={skillCategoryService}
          codeExtra="创建后不可修改，技能列表按该编码过滤"
          value={categoryCode}
          onChange={(code) => {
            setCategoryCode(code);
            setPage(1);
          }}
        />
      </div>
      <Card
        title="技能管理"
        style={{ flex: 1, minWidth: 0 }}
        extra={
        activeTab === 'db' ? (
          <Space>
            <Button onClick={openDraft}>生成草稿</Button>
            <Button type="primary" onClick={openCreate}>
              新增技能
            </Button>
          </Space>
        ) : (
          <Typography.Text type="secondary">内置技能随服务分发，仅支持查看</Typography.Text>
        )
      }
    >
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={[
          { key: 'db', label: '自定义技能' },
          { key: 'builtin', label: '内置技能' },
        ]}
      />

      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="编码/名称"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          allowClear
          style={{ width: 220 }}
          onPressEnter={handleSearch}
        />
        <Button type="primary" onClick={handleSearch}>
          查询
        </Button>
        <Button
          onClick={() => {
            setSearchKeyword('');
            setKeyword('');
            setPage(1);
          }}
        >
          重置
        </Button>
      </Space>

      <Table<SkillDefinition>
        rowKey="skillId"
        columns={activeTab === 'db' ? columns : builtinColumns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={activeTab === 'db' ? { x: 1300 } : { x: 800 }}
        pagination={{
          current: page,
          pageSize: size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Modal
        title={
          <Space>
            <span>{editing ? '编辑技能' : '新增技能'}</span>
            {editing && <Tag color="geekblue">v{editing.skillVersion ?? '-'}</Tag>}
          </Space>
        }
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
        width={960}
        styles={{ body: { paddingTop: 8 } }}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="skillId"
                label="技能 Code"
                rules={[
                  { required: true, message: '请输入技能Code' },
                  { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和连字符' },
                ]}
              >
                <Input placeholder="技能唯一标识，如 my-skill" maxLength={128} disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="skillName"
                label="名称"
                rules={[{ required: true, message: '请输入名称' }]}
              >
                <Input placeholder="请输入技能名称" maxLength={128} showCount />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="boundTools" label="绑定工具">
                <Select mode="tags" placeholder="输入工具编码后回车" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="category" label="分类" extra="从分类树中选择，可清空为未分类">
            <TreeSelect
              placeholder="不选择则为未分类"
              allowClear
              showSearch
              treeNodeFilterProp="title"
              treeData={toCategoryTreeData(categoryTree?.nodes)}
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="skillDescription" label="描述">
            <Input.TextArea rows={2} placeholder="一句话说明该技能的用途" />
          </Form.Item>
          <Form.Item
            name="skillContent"
            label={
              <Space size="middle">
                <span>内容（支持 Markdown）</span>
                <Segmented
                  size="small"
                  value={contentMode}
                  onChange={(v) => setContentMode(v as 'edit' | 'preview')}
                  options={[
                    { label: '编辑', value: 'edit' },
                    { label: '预览', value: 'preview' },
                  ]}
                />
              </Space>
            }
            rules={[{ required: true, message: '请输入内容' }]}
            style={{ marginBottom: 8 }}
          >
            {contentMode === 'edit' ? (
              <Input.TextArea
                rows={16}
                placeholder={'# 技能说明\n\n使用 Markdown 编写技能内容，支持标题、列表、代码块等'}
                style={{ fontFamily: 'SFMono-Regular, Consolas, monospace', fontSize: 13 }}
              />
            ) : (
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => {
                  const content = getFieldValue('skillContent') as string | undefined;
                  return (
                    <div
                      style={{
                        border: '1px solid #d9d9d9',
                        borderRadius: 6,
                        padding: '12px 16px',
                        minHeight: 320,
                        maxHeight: 480,
                        overflow: 'auto',
                        background: '#fafafa',
                      }}
                    >
                      {content ? (
                        <MarkdownContent content={content} />
                      ) : (
                        <Typography.Text type="secondary">暂无内容可预览</Typography.Text>
                      )}
                    </div>
                  );
                }}
              </Form.Item>
            )}
          </Form.Item>
          {editing && (
            <Form.Item name="remark" label="变更说明">
              <Input.TextArea rows={2} maxLength={200} placeholder="留空将自动记录变更说明" />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title="生成技能草稿"
        open={draftOpen}
        onOk={handleGenerate}
        onCancel={() => setDraftOpen(false)}
        confirmLoading={generateMutation.isPending}
        destroyOnClose
        width={860}
      >
        <Form form={draftForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="skillName"
                label="名称"
                rules={[{ required: true, message: '请输入名称' }]}
              >
                <Input placeholder="请输入技能名称" maxLength={128} showCount />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="boundTools" label="绑定工具">
                <Select mode="tags" placeholder="输入工具编码后回车" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="skillDescription" label="描述">
            <Input.TextArea rows={2} placeholder="一句话说明该技能的用途" />
          </Form.Item>
          <Form.Item
            name="skillContent"
            label="内容提示"
            rules={[{ required: true, message: '请输入内容' }]}
          >
            <Input.TextArea rows={10} placeholder="用于生成技能的内容提示" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="草稿结果"
        open={draftResultOpen}
        onCancel={() => {
          setDraftResultOpen(false);
          setDraftResult(null);
        }}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setDraftResultOpen(false);
              setDraftResult(null);
            }}
          >
            取消
          </Button>,
          <Button
            key="save"
            type="primary"
            loading={draftMutation.isPending}
            onClick={() => draftMutation.mutate()}
          >
            保存为技能
          </Button>,
        ]}
        width={860}
      >
        {draftResult && (
          <div>
            <Row gutter={16}>
              <Col span={12}>
                <Typography.Paragraph>
                  <Typography.Text type="secondary">技能 ID：</Typography.Text>
                  {draftResult.skillId ?? '-'}
                </Typography.Paragraph>
              </Col>
              <Col span={12}>
                <Typography.Paragraph>
                  <Typography.Text type="secondary">名称：</Typography.Text>
                  {draftResult.skillName}
                </Typography.Paragraph>
              </Col>
            </Row>
            <Typography.Paragraph>
              <Typography.Text type="secondary">描述：</Typography.Text>
              {draftResult.skillDescription || '-'}
            </Typography.Paragraph>
            <Typography.Text type="secondary">内容预览：</Typography.Text>
            <div
              style={{
                border: '1px solid #d9d9d9',
                borderRadius: 6,
                padding: '12px 16px',
                marginTop: 8,
                maxHeight: 400,
                overflow: 'auto',
                background: '#fafafa',
              }}
            >
              <MarkdownContent content={draftResult.skillContent} />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title="设置信任等级"
        open={trustModalOpen}
        onOk={() => trustMutation.mutate()}
        onCancel={() => {
          setTrustModalOpen(false);
          setTrustTarget(null);
          setTrustLevel(undefined);
        }}
        confirmLoading={trustMutation.isPending}
        okButtonProps={{ disabled: !trustLevel }}
        destroyOnClose
      >
        <p style={{ marginBottom: 12 }}>
          技能：<strong>{trustTarget?.skillName}</strong>
          {trustTarget && isBuiltinSkill(trustTarget) && (
            <Tag color="gold" style={{ marginLeft: 8 }}>
              内置
            </Tag>
          )}
        </p>
        <Select
          style={{ width: '100%' }}
          placeholder="请选择信任等级"
          value={trustLevel}
          onChange={setTrustLevel}
          options={TRUST_LEVELS.filter((l) => isBuiltinSkill(trustTarget) || l !== 'BUILTIN').map(
            (l) => ({ label: TRUST_LEVEL_LABELS[l] ?? l, value: l })
          )}
        />
        {trustTarget && !isBuiltinSkill(trustTarget) && (
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
            内置信任等级为随服务分发的内置技能专属，普通技能不可选择
          </Typography.Text>
        )}
      </Modal>

      <Modal
        title={
          <Space>
            <span>内置技能 - {viewTarget?.skillName ?? ''}</span>
            <Tag color="gold">内置（随服务分发）</Tag>
          </Space>
        }
        open={viewOpen}
        onCancel={() => {
          setViewOpen(false);
          setViewTarget(null);
        }}
        footer={null}
        width={860}
      >
        <Row gutter={16}>
          <Col span={12}>
            <Typography.Paragraph>
              <Typography.Text type="secondary">技能 ID：</Typography.Text>
              {viewTarget?.skillId ?? '-'}
            </Typography.Paragraph>
          </Col>
          <Col span={12}>
            <Typography.Paragraph>
              <Typography.Text type="secondary">版本：</Typography.Text>
              v{viewTarget?.skillVersion ?? '-'}
            </Typography.Paragraph>
          </Col>
        </Row>
        <Typography.Paragraph>
          <Typography.Text type="secondary">描述：</Typography.Text>
          {viewTarget?.skillDescription || '-'}
        </Typography.Paragraph>
        <Typography.Paragraph>
          <Typography.Text type="secondary">绑定工具：</Typography.Text>
          {viewTarget?.boundTools?.length
            ? viewTarget.boundTools.map((t) => <Tag key={t}>{t}</Tag>)
            : '-'}
        </Typography.Paragraph>
        <Typography.Text type="secondary">内容：</Typography.Text>
        <div
          style={{
            border: '1px solid #d9d9d9',
            borderRadius: 6,
            padding: '12px 16px',
            marginTop: 8,
            maxHeight: 420,
            overflow: 'auto',
            background: '#fafafa',
          }}
        >
          {viewTarget?.skillContent ? (
            <MarkdownContent content={viewTarget.skillContent} />
          ) : (
            <Typography.Text type="secondary">暂无内容</Typography.Text>
          )}
        </div>
      </Modal>

      <Drawer
        title={`版本历史 - ${historyTarget?.skillName ?? ''}`}
        width={520}
        open={historyOpen}
        onClose={closeHistory}
        destroyOnClose
      >
        <Spin spinning={versionLoading}>
          {versionData?.list?.length ? (
            <Timeline
              items={versionData.list.map((item) => {
                const isCurrent = item.version === historyTarget?.skillVersion;
                return {
                  children: (
                    <div>
                      <Space size={4} wrap>
                        <Tag color="geekblue">v{item.version}</Tag>
                        {isCurrent && <Tag color="green">当前</Tag>}
                        {item.qualityScore != null && (
                          <Tag color={qualityScoreColor(item.qualityScore)}>
                            质量分 {item.qualityScore}
                          </Tag>
                        )}
                      </Space>
                      <div style={{ margin: '4px 0' }}>{item.changeLog || '（无变更说明）'}</div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.createTime ?? '-'} · {item.createUser ?? '-'} · 指纹{' '}
                        {item.fingerprint ?? '-'}
                      </Typography.Text>
                      <div style={{ marginTop: 4 }}>
                        <Space size="small">
                          <Button
                            type="link"
                            size="small"
                            style={{ paddingLeft: 0 }}
                            onClick={() => setSnapshotVersion(item.version)}
                          >
                            查看快照
                          </Button>
                          {item.qualityScore != null && item.evalDimensions && (
                            <Popover
                              trigger="click"
                              title={`v${item.version} 评分详情`}
                              content={renderEvalDetail(item.evalDimensions)}
                            >
                              <Button type="link" size="small">
                                评分详情
                              </Button>
                            </Popover>
                          )}
                          <Popconfirm
                            title="将产生新版本，历史保留"
                            description={`确认回滚到 v${item.version}？`}
                            onConfirm={() => rollbackMutation.mutate(item.version)}
                            disabled={isCurrent || rollbackMutation.isPending}
                          >
                            <Button
                              type="link"
                              size="small"
                              disabled={isCurrent || rollbackMutation.isPending}
                            >
                              回滚到此版本
                            </Button>
                          </Popconfirm>
                        </Space>
                      </div>
                    </div>
                  ),
                };
              })}
            />
          ) : (
            <Empty description="暂无版本记录" />
          )}
          {(versionData?.total ?? 0) > HISTORY_PAGE_SIZE && (
            <Pagination
              size="small"
              current={historyPage}
              pageSize={HISTORY_PAGE_SIZE}
              total={versionData?.total ?? 0}
              onChange={setHistoryPage}
              style={{ marginTop: 16, textAlign: 'right' }}
            />
          )}
        </Spin>
      </Drawer>

      <Modal
        title={`版本快照 v${snapshotVersion ?? ''}`}
        open={snapshotVersion != null}
        onCancel={() => setSnapshotVersion(null)}
        footer={null}
        width={860}
      >
        <Spin spinning={snapshotLoading}>
          <div
            style={{
              border: '1px solid #d9d9d9',
              borderRadius: 6,
              padding: '12px 16px',
              maxHeight: 480,
              overflow: 'auto',
              background: '#fafafa',
            }}
          >
            <MarkdownContent content={snapshot?.skillContent ?? ''} />
          </div>
        </Spin>
      </Modal>
      </Card>
    </div>
  );
};
