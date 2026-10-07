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
import { Button, Input, Typography } from 'antd';
import { QuestionCircleOutlined, SendOutlined } from '@ant-design/icons';
import type { ClarificationRequestInfo } from '@yangqiong/shared';

const { Text } = Typography;

interface ClarificationCardsProps {
  questions: ClarificationRequestInfo[];
  onSubmit: (toolCallId: string, answer: string) => void;
  submitting: boolean;
}

/**
 * 解析后的选项（字母编号与内容）
 */
interface ParsedOption {
  label: string;
  content: string;
}

/**
 * 从问题文本解析选项列表（A. xxx B. xxx格式，字母编号+点/顿号/括号，至少2个有效选项才视为选择题）
 * @param text
 * @return
 */
const parseOptions = (text: string): ParsedOption[] => {
  const marks = [...text.matchAll(/(^|\s)([A-Z])[.、)）]\s*/g)];
  if (marks.length < 2) {
    return [];
  }
  const options: ParsedOption[] = [];
  for (let i = 0; i < marks.length; i++) {
    const start = (marks[i].index ?? 0) + marks[i][0].length;
    const end = i + 1 < marks.length
      ? (marks[i + 1].index ?? text.length) + marks[i + 1][1].length
      : text.length;
    const content = text.slice(start, end).trim().replace(/[，,。；;、\s]+$/, '');
    if (content) {
      options.push({ label: marks[i][2], content });
    }
  }
  return options.length >= 2 ? options : [];
};

/**
 * 对话流内联澄清提问卡片（AI向用户提问实时渲染于输入框上方，问题含选项时渲染可点击选择按钮）
 */
export const ClarificationCards: React.FC<ClarificationCardsProps> = ({ questions, onSubmit, submitting }) => {
  /**
   * 各问题的答案草稿（toolCallId -> 答案文本）
   */
  const [answers, setAnswers] = useState<Record<string, string>>({});

  if (questions.length === 0) {
    return null;
  }

  /**
   * 提交单个问题的回答并移除对应卡片
   * @param question
   */
  const handleSubmit = (question: ClarificationRequestInfo) => {
    const answer = (answers[question.toolCallId] ?? '').trim();
    if (!answer || submitting) {
      return;
    }
    setAnswers((prev) => ({ ...prev, [question.toolCallId]: '' }));
    onSubmit(question.toolCallId, answer);
  };

  /**
   * 点击选项按钮填入答案（再次点击已选中项取消选择，支持在答案基础上手动补充修改）
   * @param question
   * @param option
   */
  const handleSelectOption = (question: ClarificationRequestInfo, option: ParsedOption) => {
    const active = (answers[question.toolCallId] ?? '') === option.content;
    setAnswers((prev) => ({ ...prev, [question.toolCallId]: active ? '' : option.content }));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 8 }}>
      <style>{`
        @keyframes cla-in { from { opacity: 0; transform: translateY(8px) scale(0.98); } to { opacity: 1; transform: none; } }
        @keyframes cla-breathe { 0%, 100% { opacity: 0.35; } 50% { opacity: 1; } }
        @keyframes cla-halo { 0%, 100% { box-shadow: 0 0 0 0 rgba(64, 86, 214, 0.3); } 50% { box-shadow: 0 0 0 6px rgba(64, 86, 214, 0); } }
        .cla-btn { transition: all 0.18s ease; }
        .cla-btn:hover { transform: translateY(-1px); }
        .cla-submit:hover { box-shadow: 0 4px 12px rgba(64, 86, 214, 0.4); filter: brightness(1.06); }
        .cla-input::placeholder { color: #B8BFCF; }
        .cla-opt { transition: all 0.18s ease; }
        .cla-opt:hover { border-color: #3D53CE !important; color: #3D53CE !important; }
      `}</style>
      {questions.map((question) => {
        // 结构化选项优先（引擎ask_user透传），无结构化选项时回退解析问题文本中的A. xxx格式
        const options = (question.options && question.options.length >= 2
          ? question.options.map((content, idx) => ({
              label: String.fromCharCode(65 + idx),
              content,
            }))
          : parseOptions(question.question));
        const answer = answers[question.toolCallId] ?? '';
        return (
          <div
            key={question.toolCallId}
            style={{
              position: 'relative',
              overflow: 'hidden',
              borderRadius: 12,
              border: '1px solid #E3E7F2',
              background: 'linear-gradient(165deg, #FFFEFB 0%, #F8F9FD 100%)',
              boxShadow: '0 4px 14px rgba(31, 36, 48, 0.06)',
              padding: '12px 14px',
              animation: 'cla-in 0.3s ease-out both',
            }}
          >
            {/* 顶部呼吸光带 */}
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: 2,
                background: 'linear-gradient(90deg, transparent 0%, #4056D6 38%, #5B72E8 65%, transparent 100%)',
                opacity: 0.55,
                animation: 'cla-breathe 3.2s ease-in-out infinite',
              }}
            />
            <div style={{ display: 'flex', gap: 12 }}>
              {/* 提问徽章（呼吸光环） */}
              <div
                style={{
                  flexShrink: 0,
                  width: 38,
                  height: 38,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: 'linear-gradient(135deg, #6B82F0 0%, #3D53CE 100%)',
                  color: '#FFFFFF',
                  fontSize: 17,
                  animation: 'cla-halo 2.4s ease-in-out infinite',
                }}
              >
                <QuestionCircleOutlined />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <Text strong style={{ fontSize: 12, letterSpacing: 2, color: '#4056D6' }}>
                    AI 提问
                  </Text>
                  <Text strong style={{ fontSize: 14, color: '#1F2430' }}>
                    需要你补充信息
                  </Text>
                </div>
                {/* 问题正文（引用块样式） */}
                <div
                  style={{
                    marginTop: 8,
                    padding: '6px 10px',
                    borderLeft: '2px solid #4056D6',
                    borderRadius: '0 6px 6px 0',
                    background: 'rgba(64, 86, 214, 0.06)',
                    fontSize: 12.5,
                    lineHeight: 1.6,
                    color: '#4A5265',
                    wordBreak: 'break-word',
                  }}
                >
                  {question.question}
                </div>
                {/* 候选选项按钮（单选语义：点击选中填入答案，再次点击取消） */}
                {options.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                    {options.map((option) => {
                      const active = answer === option.content;
                      return (
                        <button
                          key={option.label}
                          type="button"
                          className="cla-opt"
                          onClick={() => handleSelectOption(question, option)}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            maxWidth: '100%',
                            padding: '3px 10px',
                            borderRadius: 999,
                            border: active ? '1px solid #3D53CE' : '1px solid #D9DEF0',
                            background: active ? 'rgba(64, 86, 214, 0.1)' : '#FFFFFF',
                            color: active ? '#3D53CE' : '#4A5265',
                            fontSize: 12,
                            lineHeight: 1.6,
                            cursor: 'pointer',
                            fontWeight: active ? 500 : 400,
                          }}
                        >
                          <span style={{ fontWeight: 600 }}>{option.label}</span>
                          <span style={{ maxWidth: 240, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {option.content}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
            {/* 答案输入与提交 */}
            <Input.TextArea
              className="cla-input"
              rows={2}
              maxLength={500}
              placeholder={options.length > 0 ? '点击上方选项或输入你的回答…' : '输入你的回答…'}
              value={answer}
              onChange={(e) => setAnswers((prev) => ({ ...prev, [question.toolCallId]: e.target.value }))}
              style={{
                marginTop: 10,
                background: '#FFFFFF',
                border: '1px solid #E3E7F2',
                width: '100%',
              }}
            />
            <div
              style={{
                marginTop: 10,
                paddingTop: 10,
                borderTop: '1px dashed #E7EAF1',
                display: 'flex',
                justifyContent: 'flex-end',
              }}
            >
              <Button
                className="cla-btn cla-submit"
                size="small"
                type="primary"
                loading={submitting}
                icon={<SendOutlined />}
                style={{
                  border: 'none',
                  background: 'linear-gradient(135deg, #6B82F0 0%, #3D53CE 100%)',
                  color: '#FFFFFF',
                }}
                onClick={() => handleSubmit(question)}
              >
                发送回答
              </Button>
            </div>
          </div>
        );
      })}
    </div>
  );
};
