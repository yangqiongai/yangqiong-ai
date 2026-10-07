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
import React, { useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Progress,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/services';
import { useRagRetrieve } from '@yangqiong/shared/knowledge';
import type {
  KbDocument,
  KnowledgeBase,
  RecallResult,
} from '@yangqiong/shared';

const { Text, Paragraph } = Typography;

interface KbOption {
  label: string;
  value: string;
}

interface RetrieveFormValues {
  kbIds: string[];
  query: string;
  topK?: number;
}

interface RecallFormValues {
  kbIds: string[];
  query: string;
  topK?: number;
}

interface ExpandFormValues {
  kbIds: string[];
  docId: string;
  query: string;
}

/**
 * 命中切片结构（后端 ChunkCandidate）
 */
interface HitSliceLike {
  text?: string;
  docId?: string;
  sliceId?: string;
  score?: number;
}

/**
 * RAG 检索验证
 */
export const RagRetrievePanel: React.FC = () => {
  const { data: kbList } = useQuery({
    queryKey: ['kb-list-all'],
    queryFn: () => api.knowledge.kb.list(),
  });

  const kbOptions = useMemo(
    () =>
      (kbList ?? []).map((k: KnowledgeBase) => ({
        label: `${k.kbName}（${k.kbId}）`,
        value: k.kbId,
      })),
    [kbList],
  );

  return (
    <Card title="RAG 检索验证">
      <Tabs
        items={[
          {
            key: 'retrieve',
            label: '基础检索',
            children: <BasicRetrieveTab kbOptions={kbOptions} />,
          },
          {
            key: 'recall-verify',
            label: '召回验证',
            children: <RecallVerifyTab kbOptions={kbOptions} />,
          },
          {
            key: 'context-expand',
            label: '上下文扩展',
            children: <ContextExpandTab kbOptions={kbOptions} />,
          },
        ]}
      />
    </Card>
  );
};

/**
 * 基础检索
 */
const BasicRetrieveTab = ({ kbOptions }: { kbOptions: KbOption[] }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<RetrieveFormValues>();
  const { results, searching, retrieve, reset } = useRagRetrieve(api.knowledge.rag);

  const handleSubmit = async () => {
    const values = await form.validateFields();
    reset();
    const outcome = await retrieve(values);
    if (!outcome.ok) {
      message.error(outcome.error);
    } else if (outcome.rows.length === 0) {
      message.info('未检索到相关切片');
    }
  };

  return (
    <>
      <Form<RetrieveFormValues>
        form={form}
        layout="vertical"
        initialValues={{ topK: 5 }}
      >
        <Form.Item
          name="kbIds"
          label="知识库"
          rules={[{ required: true, message: '请选择知识库' }]}
        >
          <Select
            mode="multiple"
            options={kbOptions}
            placeholder="可选择多个知识库"
          />
        </Form.Item>
        <Form.Item
          name="query"
          label="查询问题"
          rules={[{ required: true, message: '请输入查询问题' }]}
        >
          <Input.TextArea rows={3} placeholder="请输入要检索的问题" />
        </Form.Item>
        <Space size="large" wrap>
          <Form.Item name="topK" label="Top K">
            <InputNumber min={1} max={50} />
          </Form.Item>
        </Space>
        <Form.Item>
          <Button
            type="primary"
            loading={searching}
            onClick={handleSubmit}
          >
            检索
          </Button>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 16 }}>
        <Text strong>检索结果（{results.length} 条）</Text>
        {results.length === 0 ? (
          <Empty
            style={{ marginTop: 24 }}
            description={searching ? '检索中...' : '暂无结果'}
          />
        ) : (
          <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
            {results.map((r, idx) => (
              <Card
                key={`${r.sliceId}-${idx}`}
                size="small"
                title={
                  <Space>
                    <Tag color="blue">#{idx + 1}</Tag>
                    <Text>分数：{r.score.toFixed(4)}</Text>
                    {r.sourceDocName && (
                      <Text type="secondary">{r.sourceDocName}</Text>
                    )}
                  </Space>
                }
              >
                <Paragraph style={{ marginBottom: 0 }}>{r.content}</Paragraph>
              </Card>
            ))}
          </Space>
        )}
      </div>
    </>
  );
};

/**
 * 召回验证指标
 */
const RateMetric = ({ label, value }: { label: string; value?: number }) => (
  <div style={{ width: 160 }}>
    <Text type="secondary">{label}</Text>
    <Progress percent={Math.round((value ?? 0) * 100)} size="small" />
  </div>
);

/**
 * 召回验证
 */
const RecallVerifyTab = ({ kbOptions }: { kbOptions: KbOption[] }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<RecallFormValues>();
  const [results, setResults] = useState<RecallResult[]>([]);

  const recallMutation = useMutation({
    mutationFn: (values: RecallFormValues) => {
      // 多行文本按行拆分为批量查询
      const queries = values.query
        .split('\n')
        .map((q) => q.trim())
        .filter(Boolean);
      return api.knowledge.rag.verifyRecall({
        queries,
        kbIds: values.kbIds,
        topK: values.topK ?? 5,
        minScore: 0,
      });
    },
    onSuccess: (data) => {
      setResults(data ?? []);
      if (!data || data.length === 0) {
        message.info('未召回任何切片');
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '召回验证失败');
    },
  });

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setResults([]);
    recallMutation.mutate(values);
  };

  return (
    <>
      <Form<RecallFormValues>
        form={form}
        layout="vertical"
        initialValues={{ topK: 5 }}
      >
        <Form.Item
          name="kbIds"
          label="知识库"
          rules={[{ required: true, message: '请选择知识库' }]}
        >
          <Select
            mode="multiple"
            options={kbOptions}
            placeholder="可选择多个知识库"
          />
        </Form.Item>
        <Form.Item
          name="query"
          label="查询问题"
          rules={[{ required: true, message: '请输入查询问题' }]}
        >
          <Input.TextArea
            rows={3}
            placeholder="请输入要验证的问题，每行一个可批量验证"
          />
        </Form.Item>
        <Space size="large" wrap>
          <Form.Item name="topK" label="Top K">
            <InputNumber min={1} max={50} />
          </Form.Item>
        </Space>
        <Form.Item>
          <Button
            type="primary"
            loading={recallMutation.isPending}
            onClick={handleSubmit}
          >
            验证
          </Button>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 16 }}>
        <Text strong>验证结果（{results.length} 条）</Text>
        {results.length === 0 ? (
          <Empty
            style={{ marginTop: 24 }}
            description={recallMutation.isPending ? '验证中...' : '暂无结果'}
          />
        ) : (
          <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
            {results.map((r, idx) => (
              <Card
                key={`${r.query}-${idx}`}
                size="small"
                title={
                  <Space wrap>
                    <Tag color="blue">#{idx + 1}</Tag>
                    <Text>{r.query}</Text>
                  </Space>
                }
              >
                <Space size="large" wrap align="center">
                  <Tag color="geekblue">命中 {r.hitCount} 条切片</Tag>
                  <RateMetric label="召回率" value={r.recallRate} />
                  <RateMetric label="覆盖度" value={r.coverageRate} />
                  <RateMetric label="MRR" value={r.mrr} />
                </Space>

                {(r.hitSlices ?? []).length > 0 && (
                  <>
                    <Text strong style={{ display: 'block', marginTop: 12 }}>
                      命中切片（展示前 3 条 / 共 {(r.hitSlices ?? []).length} 条）
                    </Text>
                    <Space
                      direction="vertical"
                      style={{ width: '100%', marginTop: 8 }}
                    >
                      {(r.hitSlices ?? []).slice(0, 3).map((slice, i) => {
                        const s = slice as HitSliceLike;
                        return (
                          <div
                            key={s.sliceId ?? `slice-${i}`}
                            style={{
                              background: '#fafafa',
                              padding: 8,
                              borderRadius: 4,
                            }}
                          >
                            <Space wrap>
                              <Tag>#{i + 1}</Tag>
                              {typeof s.score === 'number' && (
                                <Text>分数：{s.score.toFixed(4)}</Text>
                              )}
                              {s.docId && (
                                <Text type="secondary">文档：{s.docId}</Text>
                              )}
                            </Space>
                            <Paragraph
                              style={{ marginBottom: 0, marginTop: 4 }}
                              ellipsis={{
                                rows: 2,
                                expandable: true,
                                symbol: '展开',
                              }}
                            >
                              {s.text}
                            </Paragraph>
                          </div>
                        );
                      })}
                    </Space>
                  </>
                )}
              </Card>
            ))}
          </Space>
        )}
      </div>
    </>
  );
};

/**
 * 上下文扩展
 */
const ContextExpandTab = ({ kbOptions }: { kbOptions: KbOption[] }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<ExpandFormValues>();
  const [expanded, setExpanded] = useState('');
  const [sourceDocName, setSourceDocName] = useState('');

  const selectedKbIds = (Form.useWatch('kbIds', form) as string[] | undefined) ?? [];
  const kbKey = selectedKbIds.join(',');

  // 根据选中知识库加载文档列表
  const { data: docs, isFetching: docsLoading } = useQuery({
    queryKey: ['kb-docs-for-expand', kbKey],
    queryFn: () => {
      const kbIds = kbKey.split(',').filter(Boolean);
      return Promise.all(kbIds.map((kbId) => api.knowledge.documents.list(kbId)))
        .then((lists) => lists.flat());
    },
    enabled: selectedKbIds.length > 0,
  });

  const docOptions = useMemo(
    () =>
      (docs ?? []).map((d: KbDocument) => ({
        label: `${d.docName}（${d.docId}）`,
        value: d.docId,
      })),
    [docs],
  );

  const expandMutation = useMutation({
    mutationFn: (values: ExpandFormValues) =>
      api.knowledge.rag.expandContext(values.docId, values.query),
    onSuccess: (data) => {
      setExpanded(typeof data === 'string' ? data : '');
      if (!data) {
        message.info('未获取到扩展上下文');
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '上下文扩展失败');
    },
  });

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const doc = (docs ?? []).find((d: KbDocument) => d.docId === values.docId);
    setSourceDocName(doc?.docName ?? values.docId);
    setExpanded('');
    expandMutation.mutate(values);
  };

  // 按换行拆分为段落展示
  const paragraphs = expanded
    .split(/\n+/)
    .map((p) => p.trim())
    .filter(Boolean);

  return (
    <>
      <Form<ExpandFormValues> form={form} layout="vertical">
        <Form.Item
          name="kbIds"
          label="知识库"
          rules={[{ required: true, message: '请选择知识库' }]}
        >
          <Select
            mode="multiple"
            options={kbOptions}
            placeholder="可选择多个知识库"
            onChange={() => form.setFieldValue('docId', undefined)}
          />
        </Form.Item>
        <Form.Item
          name="docId"
          label="文档"
          rules={[{ required: true, message: '请选择文档' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={docOptions}
            loading={docsLoading}
            disabled={selectedKbIds.length === 0}
            placeholder={
              selectedKbIds.length === 0
                ? '请先选择知识库'
                : '选择需要扩展上下文的文档'
            }
          />
        </Form.Item>
        <Form.Item
          name="query"
          label="查询问题"
          rules={[{ required: true, message: '请输入查询问题' }]}
        >
          <Input.TextArea rows={3} placeholder="请输入用于定位扩展位置的问题" />
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            loading={expandMutation.isPending}
            onClick={handleSubmit}
          >
            扩展上下文
          </Button>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 16 }}>
        {paragraphs.length === 0 ? (
          <Empty
            style={{ marginTop: 24 }}
            description={expandMutation.isPending ? '扩展中...' : '暂无结果'}
          />
        ) : (
          <>
            <Space wrap>
              {sourceDocName && <Tag color="purple">来源：{sourceDocName}</Tag>}
              <Text type="secondary">共 {paragraphs.length} 段</Text>
            </Space>
            <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
              {paragraphs.map((p, i) => (
                <Card
                  key={`paragraph-${i}`}
                  size="small"
                  title={<Text type="secondary">段落 {i + 1}</Text>}
                >
                  <Paragraph style={{ marginBottom: 0 }}>{p}</Paragraph>
                </Card>
              ))}
            </Space>
          </>
        )}
      </div>
    </>
  );
};
