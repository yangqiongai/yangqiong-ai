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
import React, { useEffect, useRef, useState } from 'react';
import { Button, Checkbox, Input, Modal, Select, Tabs, message } from 'antd';
import { DeleteOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons';

type FieldType = 'string' | 'number' | 'integer' | 'boolean' | 'array' | 'object';

const FIELD_TYPE_OPTIONS: { value: FieldType; label: string }[] = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'integer', label: '整数' },
  { value: 'boolean', label: '布尔' },
  { value: 'array', label: '数组' },
  { value: 'object', label: '对象' },
];

// 简单数组的元素类型（仅基本类型，对象元素走"对象数组"）
const ITEM_TYPE_OPTIONS = FIELD_TYPE_OPTIONS.filter((o) =>
  ['string', 'number', 'integer', 'boolean'].includes(o.value),
);

// 类型下拉选项（数组拆分为简单数组/对象数组两个入口，枚举为独立类型）
const UI_TYPE_OPTIONS = [
  ...ITEM_TYPE_OPTIONS,
  { value: 'enum', label: '枚举' },
  { value: 'array-simple', label: '简单数组' },
  { value: 'array-object', label: '对象数组' },
  { value: 'object', label: '对象' },
] as { value: string; label: string }[];

type UiType =
  | 'string'
  | 'number'
  | 'integer'
  | 'boolean'
  | 'enum'
  | 'array-simple'
  | 'array-object'
  | 'object';

/**
 * 由字段草稿推导类型下拉值（array按元素类型拆分，string带enum识别为枚举）
 * @param draft
 * @return
 */
const uiTypeOf = (draft: SchemaFieldDraft): UiType => {
  if (draft.type === 'array') {
    return draft.itemType === 'object' ? 'array-object' : 'array-simple';
  }
  return draft.type === 'string' && Array.isArray(draft.enumList)
    ? 'enum'
    : (draft.type as UiType);
};

/**
 * 应用类型下拉值到字段草稿（对象数组与对象互切保留子字段，枚举底层为string+enum）
 * @param draft
 * @param ui
 * @return
 */
const applyUiType = (draft: SchemaFieldDraft, ui: UiType): Partial<SchemaFieldDraft> => {
  if (ui === 'array-simple') {
    return { type: 'array', itemType: 'string', children: [], enumList: undefined };
  }
  if (ui === 'array-object') {
    return { type: 'array', itemType: 'object', enumList: undefined };
  }
  if (ui === 'object') {
    return { type: 'object', itemType: undefined, enumList: undefined };
  }
  if (ui === 'enum') {
    return { type: 'string', itemType: undefined, children: [], enumList: draft.enumList ?? [] };
  }
  return {
    type: ui,
    itemType: undefined,
    children: [],
    enumList: undefined,
  };
};

// 快捷编辑托管的Schema关键字，其余关键字（minLength等）在重建时原样保留
const MANAGED_KEYS = [
  'title',
  'type',
  'description',
  'enum',
  'pattern',
  'errorMessage',
  'items',
  'properties',
  'required',
];

/**
 * 快捷编辑字段草稿
 */
interface SchemaFieldDraft {
  key: string;

  /**
   * 字段名
   */
  name: string;

  /**
   * 字段类型
   */
  type: FieldType;

  /**
   * 字段说明
   */
  description?: string;

  /**
   * 是否必填
   */
  required: boolean;

  /**
   * string类型枚举值（标签式逐项输入）
   */
  enumList?: string[];

  /**
   * 正则格式校验（入参时后台校验）
   */
  pattern?: string;

  /**
   * 校验失败提示
   */
  errorMessage?: string;

  /**
   * 是否启用格式校验（勾选后展示正则与提示输入）
   */
  needValidate?: boolean;

  /**
   * array元素类型（object时子字段进items结构）
   */
  itemType?: FieldType;

  /**
   * object类型子字段（或array对象元素的子字段）
   */
  children: SchemaFieldDraft[];
}

let keySeed = 0;
const nextKey = () => `field-${++keySeed}`;

/**
 * 从JSON Schema的properties解析字段草稿（object类型与array对象元素递归）
 * @param schema
 * @return
 */
const draftFromSchema = (schema: any): SchemaFieldDraft[] => {
  const props = schema?.properties ?? {};
  const required: string[] = Array.isArray(schema?.required) ? schema.required : [];
  return Object.entries(props)
    .filter(([, def]) => def && typeof def === 'object' && !Array.isArray(def))
    .map(([name, def]: [string, any]) => {
      const type: FieldType = FIELD_TYPE_OPTIONS.some((o) => o.value === def.type)
        ? def.type
        : 'string';
      const itemObj = type === 'array' && def.items?.type === 'object';
      return {
        key: nextKey(),
        name,
        type,
        description: typeof def.description === 'string' ? def.description : undefined,
        required: required.includes(name),
        enumList: Array.isArray(def.enum) ? def.enum.map(String) : undefined,
        pattern: typeof def.pattern === 'string' ? def.pattern : undefined,
        errorMessage: typeof def.errorMessage === 'string' ? def.errorMessage : undefined,
        needValidate: def.pattern != null || def.errorMessage != null,
        itemType:
          type === 'array'
            ? def.items && typeof def.items.type === 'string'
              ? (def.items.type as FieldType)
              : 'string'
            : undefined,
        children:
          type === 'object' ? draftFromSchema(def) : itemObj ? draftFromSchema(def.items) : [],
      };
    });
};

/**
 * 由字段草稿构建单个属性定义（保留旧定义中非托管关键字）
 * @param oldDef
 * @param draft
 * @return
 */
const buildPropertyDef = (oldDef: any, draft: SchemaFieldDraft): Record<string, any> => {
  const def: Record<string, any> = {};
  if (oldDef && typeof oldDef === 'object') {
    Object.keys(oldDef).forEach((k) => {
      if (!MANAGED_KEYS.includes(k)) {
        def[k] = oldDef[k];
      }
    });
  }
  if (draft.description?.trim()) {
    def.description = draft.description.trim();
  }
  if (draft.needValidate && draft.pattern?.trim()) {
    def.pattern = draft.pattern.trim();
  }
  if (draft.needValidate && draft.errorMessage?.trim()) {
    def.errorMessage = draft.errorMessage.trim();
  }
  if (draft.type === 'array') {
    def.type = 'array';
    if (draft.itemType === 'object') {
      const items = buildSchemaNode(oldDef?.items ?? {}, draft.children);
      def.items = { type: 'object', properties: items.properties, required: items.required };
    } else {
      def.items = { type: draft.itemType ?? 'string' };
    }
  } else if (draft.type === 'object') {
    def.type = 'object';
    def.properties = {};
    def.required = [];
  } else {
    def.type = draft.type;
    if (draft.type === 'string' && draft.enumList?.length) {
      def.enum = draft.enumList.map((s) => s.trim()).filter(Boolean);
    }
  }
  return def;
};

/**
 * 将字段草稿应用到Schema节点（仅重建properties/required，其余键保留）
 * @param node
 * @param drafts
 * @return
 */
const buildSchemaNode = (node: any, drafts: SchemaFieldDraft[]): Record<string, any> => {
  const next: Record<string, any> = { ...(node ?? {}) };
  const oldProps = (node?.properties ?? {}) as Record<string, any>;
  const properties: Record<string, any> = {};
  const required: string[] = [];
  drafts.forEach((d) => {
    const name = d.name.trim();
    if (!name) {
      return;
    }
    const def = buildPropertyDef(oldProps[name], d);
    if (d.type === 'object' && d.children.length) {
      const child = buildSchemaNode(oldProps[name] ?? {}, d.children);
      def.properties = child.properties;
      def.required = child.required;
    }
    if (d.required) {
      required.push(name);
    }
    properties[name] = def;
  });
  next.properties = properties;
  next.required = required;
  return next;
};

/**
 * 按key递归更新字段草稿
 * @param list
 * @param key
 * @param patch
 * @return
 */
const updateDrafts = (
  list: SchemaFieldDraft[],
  key: string,
  patch: Partial<SchemaFieldDraft>,
): SchemaFieldDraft[] =>
  list.map((d) =>
    d.key === key
      ? { ...d, ...patch }
      : { ...d, children: updateDrafts(d.children, key, patch) },
  );

/**
 * 按key递归删除字段草稿
 * @param list
 * @param key
 * @return
 */
const removeDrafts = (list: SchemaFieldDraft[], key: string): SchemaFieldDraft[] =>
  list
    .filter((d) => d.key !== key)
    .map((d) => ({ ...d, children: removeDrafts(d.children, key) }));

/**
 * 同级列表内拖拽移动字段草稿
 * @param list
 * @param dragKey
 * @param overKey
 * @param pos
 * @return
 */
const moveDraft = (
  list: SchemaFieldDraft[],
  dragKey: string,
  overKey: string,
  pos: 'before' | 'after',
): SchemaFieldDraft[] => {
  const from = list.findIndex((d) => d.key === dragKey);
  if (from < 0) {
    return list;
  }
  const dragged = list[from];
  const without = list.filter((d) => d.key !== dragKey);
  let to = without.findIndex((d) => d.key === overKey);
  if (to < 0) {
    return list;
  }
  if (pos === 'after') {
    to += 1;
  }
  const next = [...without];
  next.splice(to, 0, dragged);
  return next;
};

const FieldRows: React.FC<{
  drafts: SchemaFieldDraft[];
  onChange: (next: SchemaFieldDraft[]) => void;
  depth?: number;
  prefix?: string;
}> = ({ drafts, onChange, depth = 0, prefix = '' }) => {
  // 拖拽状态：armedKey=按下手柄待拖、dragKey=拖拽中、over=悬停目标及相对位置
  const [armedKey, setArmedKey] = useState<string | null>(null);
  const [dragKey, setDragKey] = useState<string | null>(null);
  const [over, setOver] = useState<{ key: string; pos: 'before' | 'after' } | null>(null);

  const endDrag = () => {
    setArmedKey(null);
    setDragKey(null);
    setOver(null);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {drafts.map((d, idx) => {
        const num = prefix ? `${prefix}.${idx + 1}` : `${idx + 1}`;
        const hasChildren = d.type === 'object' || (d.type === 'array' && d.itemType === 'object');
        const showTop = over?.key === d.key && over.pos === 'before';
        const showBottom = over?.key === d.key && over.pos === 'after';
        return (
          <div
            key={d.key}
            draggable={armedKey === d.key}
            onDragStart={(e) => {
              setDragKey(d.key);
              e.dataTransfer.effectAllowed = 'move';
              e.dataTransfer.setData('text/plain', d.key);
            }}
            onDragEnd={endDrag}
            onDragOver={(e) => {
              if (!dragKey || dragKey === d.key) {
                return;
              }
              e.preventDefault();
              const rect = e.currentTarget.getBoundingClientRect();
              const pos = e.clientY < rect.top + rect.height / 2 ? 'before' : 'after';
              setOver((prev) =>
                prev?.key === d.key && prev.pos === pos ? prev : { key: d.key, pos },
              );
            }}
            onDrop={(e) => {
              e.preventDefault();
              if (!dragKey || dragKey === d.key) {
                return;
              }
              const pos = over?.key === d.key ? over.pos : 'before';
              onChange(moveDraft(drafts, dragKey, d.key, pos));
              endDrag();
            }}
            style={{
              ...(depth > 0 ? { borderLeft: '2px solid #f0f0f0', paddingLeft: 12 } : {}),
              boxShadow: showTop
                ? 'inset 0 2px 0 #1677ff'
                : showBottom
                  ? 'inset 0 -2px 0 #1677ff'
                  : undefined,
              opacity: dragKey === d.key ? 0.5 : undefined,
              borderRadius: 4,
            }}
          >
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span
                style={{
                  minWidth: 34,
                  textAlign: 'right',
                  color: '#1677ff',
                  fontWeight: 600,
                  fontSize: 12,
                  flexShrink: 0,
                }}
              >
                {num}
              </span>
              <span
                title="拖动排序"
                style={{ cursor: 'grab', color: '#999', display: 'flex', flexShrink: 0 }}
                onMouseDown={() => setArmedKey(d.key)}
                onMouseUp={() => setArmedKey((prev) => (prev === d.key ? null : prev))}
              >
                <HolderOutlined />
              </span>
              <Input
                placeholder="字段名"
                value={d.name}
                style={{ width: 150 }}
                onChange={(e) => onChange(updateDrafts(drafts, d.key, { name: e.target.value }))}
              />
              <Select
                value={uiTypeOf(d)}
                options={UI_TYPE_OPTIONS}
                style={{ width: 96 }}
                onChange={(value) => onChange(updateDrafts(drafts, d.key, applyUiType(d, value)))}
              />
              <Input
                placeholder="说明"
                value={d.description}
                style={{ flex: 1 }}
                onChange={(e) =>
                  onChange(updateDrafts(drafts, d.key, { description: e.target.value }))
                }
              />
              <Checkbox
                checked={d.required}
                onChange={(e) =>
                  onChange(updateDrafts(drafts, d.key, { required: e.target.checked }))
                }
              >
                必填
              </Checkbox>
              <Checkbox
                checked={!!d.needValidate}
                onChange={(e) =>
                  onChange(updateDrafts(drafts, d.key, { needValidate: e.target.checked }))
                }
              >
                校验
              </Checkbox>
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => onChange(removeDrafts(drafts, d.key))}
              />
            </div>
            {d.type === 'array' && d.itemType !== 'object' && (
              <div
                style={{
                  marginTop: 6,
                  marginLeft: 66,
                  width: 'calc(100% - 66px)',
                  display: 'flex',
                  gap: 8,
                  alignItems: 'center',
                }}
              >
                <span style={{ color: '#999', flexShrink: 0 }}>元素类型</span>
                <Select
                  value={d.itemType ?? 'string'}
                  options={ITEM_TYPE_OPTIONS}
                  style={{ width: 96 }}
                  onChange={(value: FieldType) =>
                    onChange(updateDrafts(drafts, d.key, { itemType: value }))
                  }
                />
              </div>
            )}
            {d.needValidate && (['string', 'number', 'integer', 'boolean'].includes(d.type) ||
              (d.type === 'string' && Array.isArray(d.enumList))) && (
              <div
                style={{
                  marginTop: 6,
                  marginLeft: 66,
                  width: 'calc(100% - 66px)',
                  display: 'flex',
                  gap: 8,
                }}
              >
                <Input
                  addonBefore="正则"
                  placeholder="格式校验正则（可选，如 ^PRJ-\d{4}$）"
                  value={d.pattern}
                  onChange={(e) => onChange(updateDrafts(drafts, d.key, { pattern: e.target.value }))}
                />
                <Input
                  addonBefore="提示"
                  placeholder="校验失败提示（可选，如：项目编码必须为PRJ-开头的4位数字）"
                  value={d.errorMessage}
                  onChange={(e) =>
                    onChange(updateDrafts(drafts, d.key, { errorMessage: e.target.value }))
                  }
                />
              </div>
            )}
            {(d.type === 'string' && Array.isArray(d.enumList)) && (
              <Select
                mode="tags"
                open={false}
                value={d.enumList ?? []}
                placeholder="枚举值：输入后回车逐项添加，可点标签×删除"
                style={{ marginTop: 6, marginLeft: 66, width: 'calc(100% - 66px)' }}
                onChange={(values: string[]) =>
                  onChange(updateDrafts(drafts, d.key, { enumList: values }))
                }
              />
            )}
            {hasChildren && (
              <div style={{ marginTop: 8, marginLeft: 24 }}>
                <FieldRows
                  drafts={d.children}
                  depth={depth + 1}
                  prefix={num}
                  onChange={(children) => onChange(updateDrafts(drafts, d.key, { children }))}
                />
              </div>
            )}
          </div>
        );
      })}
      <Button
        type="dashed"
        block
        size="small"
        icon={<PlusOutlined />}
        onClick={() =>
          onChange([
            ...drafts,
            { key: nextKey(), name: '', type: 'string', required: false, children: [] },
          ])
        }
      >
        添加字段
      </Button>
    </div>
  );
};

interface SchemaEditorModalProps {
  open: boolean;

  /**
   * 弹窗标题
   */
  title: string;

  /**
   * 打开时的初始JSON文本
   */
  initialValue?: string;

  /**
   * 打开时的初始JSON描述（字段联动与检查等补充控制信息）
   */
  initialDescription?: string;

  /**
   * 保存回调（已通过合法性校验；description为空串表示未填写）
   */
  onSave: (text: string, description: string) => void;

  /**
   * 取消回调
   */
  onCancel: () => void;
}

/**
 * Schema大屏编辑弹窗（快捷编辑/JSON编辑/JSON描述三Tab，托管properties与required的表单化维护）
 */
export const SchemaEditorModal: React.FC<SchemaEditorModalProps> = ({
  open,
  title,
  initialValue,
  initialDescription,
  onSave,
  onCancel,
}) => {
  const [activeTab, setActiveTab] = useState<'quick' | 'json' | 'desc'>('json');
  const [quickAvailable, setQuickAvailable] = useState(false);
  const [text, setText] = useState('');
  const [descText, setDescText] = useState('');
  const [drafts, setDrafts] = useState<SchemaFieldDraft[]>([]);
  const [rootTitle, setRootTitle] = useState('');
  const [rootDescription, setRootDescription] = useState('');
  // 进入快捷编辑时解析出的根对象（保留$schema等未托管键）
  const baseRootRef = useRef<any>({});

  useEffect(() => {
    if (!open) {
      return;
    }
    try {
      const parsed = initialValue?.trim() ? JSON.parse(initialValue) : {};
      const isObj = parsed && typeof parsed === 'object' && !Array.isArray(parsed);
      if (isObj) {
        baseRootRef.current = parsed;
        setRootTitle(typeof parsed.title === 'string' ? parsed.title : '');
        setRootDescription(typeof parsed.description === 'string' ? parsed.description : '');
        setDrafts(draftFromSchema(parsed));
        setQuickAvailable(true);
        setActiveTab('quick');
        setText(initialValue ?? '');
        setDescText(initialDescription ?? '');
      } else {
        // 根节点非对象时仅支持JSON编辑
        setQuickAvailable(false);
        setActiveTab('json');
        setText(
          initialValue?.trim()
            ? JSON.stringify(parsed, null, 2)
            : (initialValue ?? ''),
        );
        setDescText(initialDescription ?? '');
      }
    } catch {
      setQuickAvailable(false);
      setActiveTab('json');
      setText(initialValue ?? '');
      setDescText(initialDescription ?? '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialValue]);

  /**
   * 以快捷编辑草稿序列化JSON文本
   * @return
   */
  const serializeQuick = (): string => {
    const root = buildSchemaNode(baseRootRef.current ?? {}, drafts);
    if (rootTitle.trim()) {
      root.title = rootTitle.trim();
    } else {
      delete root.title;
    }
    if (rootDescription.trim()) {
      root.description = rootDescription.trim();
    } else {
      delete root.description;
    }
    return JSON.stringify(root, null, 2);
  };

  const handleTabChange = (key: string) => {
    if (key === activeTab) {
      return;
    }
    // 离开快捷编辑前同步草稿到JSON文本，保证其他Tab保存时内容完整
    if (activeTab === 'quick') {
      setText(serializeQuick());
    }
    if (key === 'quick') {
      try {
        const parsed = text.trim() ? JSON.parse(text) : {};
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          throw new Error('根节点必须是对象');
        }
        baseRootRef.current = parsed;
        setRootTitle(typeof parsed.title === 'string' ? parsed.title : '');
        setRootDescription(typeof parsed.description === 'string' ? parsed.description : '');
        setDrafts(draftFromSchema(parsed));
        setActiveTab('quick');
      } catch {
        message.error('JSON 格式不正确，无法切换到快捷编辑');
      }
      return;
    }
    setActiveTab(key as 'json' | 'desc');
  };

  const handleFormat = () => {
    const source = activeTab === 'desc' ? descText : text;
    if (!source.trim()) {
      return;
    }
    try {
      const formatted = JSON.stringify(JSON.parse(source), null, 2);
      if (activeTab === 'desc') {
        setDescText(formatted);
      } else {
        setText(formatted);
      }
    } catch {
      message.error('JSON 格式不正确，无法格式化');
    }
  };

  const handleSave = () => {
    let json = text;
    if (activeTab === 'quick') {
      json = serializeQuick();
      setText(json);
    } else if (json.trim()) {
      try {
        JSON.parse(json);
      } catch {
        message.error('JSON 格式不正确，请修正后再保存');
        return;
      }
    }
    if (descText.trim()) {
      try {
        JSON.parse(descText);
      } catch {
        message.error('JSON 描述格式不正确，请修正后再保存');
        return;
      }
    }
    onSave(json, descText.trim() ? descText : '');
  };

  return (
    <Modal
      title={title}
      open={open}
      width={1080}
      centered
      styles={{ body: { minHeight: 800, maxHeight: 'calc(100vh - 230px)', overflow: 'auto' } }}
      onCancel={onCancel}
      footer={[
        ...(activeTab !== 'quick'
          ? [
              <Button key="format" onClick={handleFormat}>
                格式化
              </Button>,
            ]
          : []),
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="save" type="primary" onClick={handleSave}>
          确定
        </Button>,
      ]}
    >
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={[
          {
            key: 'quick',
            label: '快捷编辑',
            disabled: !quickAvailable,
            children: (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Input
                    addonBefore="标题"
                    placeholder="Schema标题（可选）"
                    value={rootTitle}
                    style={{ width: 340 }}
                    onChange={(e) => setRootTitle(e.target.value)}
                  />
                  <Input
                    addonBefore="描述"
                    placeholder="Schema描述（可选）"
                    value={rootDescription}
                    style={{ flex: 1 }}
                    onChange={(e) => setRootDescription(e.target.value)}
                  />
                </div>
                <div style={{ color: '#999', fontSize: 12 }}>
                  字段定义：勾选必填后字段进入 required；选择“枚举”类型录入候选值；对象、对象数组可添加子字段；拖动手柄排序，序号自动更新；未托管关键字（pattern 等）在JSON编辑Tab中维护
                </div>
                <div style={{ maxHeight: 'calc(100vh - 440px)', minHeight: 200, overflowY: 'auto', paddingRight: 4 }}>
                  <FieldRows drafts={drafts} onChange={setDrafts} />
                </div>
              </div>
            ),
          },
          {
            key: 'json',
            label: 'JSON编辑',
            children: (
              <Input.TextArea
                rows={24}
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder='{"type":"object","properties":{}}'
                style={{ fontFamily: 'JetBrains Mono, Consolas, monospace', fontSize: 13, maxHeight: 'calc(100vh - 330px)' }}
              />
            ),
          },
          {
            key: 'desc',
            label: '补充信息',
            children: (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ color: '#999', fontSize: 12 }}>
                  补充控制信息（可选）：以JSON描述字段间联动关系、检查规则等额外要求；留空则不追加
                </div>
                <Input.TextArea
                  rows={18}
                  value={descText}
                  onChange={(e) => setDescText(e.target.value)}
                  placeholder={'{\n  "字段联动": ["type=A 时 size 需为 1~10 的整数"],\n  "检查": ["size 必须大于0"]\n}'}
                  style={{ fontFamily: 'JetBrains Mono, Consolas, monospace', fontSize: 13, maxHeight: 'calc(100vh - 380px)' }}
                />
              </div>
            ),
          },
        ]}
      />
    </Modal>
  );
};
