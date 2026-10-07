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
import { Button, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, SafetyOutlined } from '@ant-design/icons';
import type { ConfirmRequestInfo } from '@yangqiong/shared';

const { Text } = Typography;

interface ConfirmCardsProps {
  confirms: ConfirmRequestInfo[];
  onDecide: (requestId: string, approved: boolean) => void;
  submitting: boolean;
}

/**
 * 工具参数摘要文本（JSON序列化并截断，防长参数撑爆卡片）
 * @param input
 * @return
 */
const summarizeInput = (input?: Record<string, unknown>): string => {
  if (!input || Object.keys(input).length === 0) {
    return '（无参数）';
  }
  const text = JSON.stringify(input);
  return text.length > 160 ? `${text.slice(0, 160)}…` : text;
};

/**
 * 对话流内联工具确认卡片（引擎审批暂停时实时渲染于输入框上方）
 */
export const ConfirmCards: React.FC<ConfirmCardsProps> = ({ confirms, onDecide, submitting }) => {

  /**
   * 提交中的决策动作（转圈只显示在对应按钮上）
   */
  const [pendingAction, setPendingAction] = useState<'approve' | 'reject' | null>(null);

  if (confirms.length === 0) {
    return null;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 8 }}>
      <style>{`
        @keyframes cfm-in { from { opacity: 0; transform: translateY(8px) scale(0.98); } to { opacity: 1; transform: none; } }
        @keyframes cfm-breathe { 0%, 100% { opacity: 0.35; } 50% { opacity: 1; } }
        @keyframes cfm-halo { 0%, 100% { box-shadow: 0 0 0 0 rgba(15, 165, 115, 0.3); } 50% { box-shadow: 0 0 0 6px rgba(15, 165, 115, 0); } }
        .cfm-btn { transition: all 0.18s ease; }
        .cfm-btn:hover { transform: translateY(-1px); }
        .cfm-approve:hover { box-shadow: 0 4px 12px rgba(15, 165, 115, 0.4); filter: brightness(1.06); }
        .cfm-reject:hover { box-shadow: 0 4px 12px rgba(214, 69, 69, 0.3); }
      `}</style>
      {confirms.map((confirm) => (
        <div
          key={confirm.requestId}
          style={{
            position: 'relative',
            overflow: 'hidden',
            borderRadius: 12,
            border: '1px solid #DCEFE6',
            background: 'linear-gradient(165deg, #FBFEFC 0%, #F5FAF8 100%)',
            boxShadow: '0 4px 14px rgba(31, 36, 48, 0.06)',
            padding: '12px 14px',
            animation: 'cfm-in 0.3s ease-out both',
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
              background: 'linear-gradient(90deg, transparent 0%, #0FA573 38%, #3FC48F 65%, transparent 100%)',
              opacity: 0.55,
              animation: 'cfm-breathe 3.2s ease-in-out infinite',
            }}
          />
          <div style={{ display: 'flex', gap: 12 }}>
            {/* 确认徽章（呼吸光环） */}
            <div
              style={{
                flexShrink: 0,
                width: 38,
                height: 38,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'linear-gradient(135deg, #3FC48F 0%, #0C8A60 100%)',
                color: '#FFFFFF',
                fontSize: 17,
                animation: 'cfm-halo 2.4s ease-in-out infinite',
              }}
            >
              <SafetyOutlined />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <Text strong style={{ fontSize: 12, letterSpacing: 2, color: '#0FA573' }}>
                  操作确认
                </Text>
                <Text strong style={{ fontSize: 14, color: '#1F2430' }}>
                  以下工具调用需要你批准
                </Text>
              </div>
              {/* 待确认工具清单 */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8 }}>
                {confirm.toolCalls.map((call) => (
                  <div
                    key={call.toolUseId}
                    style={{
                      padding: '6px 10px',
                      borderLeft: '2px solid #0FA573',
                      borderRadius: '0 6px 6px 0',
                      background: 'rgba(15, 165, 115, 0.06)',
                    }}
                  >
                    <Text strong style={{ fontSize: 12.5, color: '#1F2430', fontFamily: 'monospace' }}>
                      {call.toolName}
                    </Text>
                    <div
                      style={{
                        fontSize: 12,
                        lineHeight: 1.6,
                        color: '#5A6472',
                        fontFamily: 'monospace',
                        wordBreak: 'break-all',
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {summarizeInput(call.input)}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          {/* 批准与拒绝操作 */}
          <div
            style={{
              marginTop: 10,
              paddingTop: 10,
              borderTop: '1px dashed #DCEFE6',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            <Button
              className="cfm-btn cfm-reject"
              size="small"
              danger
              loading={submitting && pendingAction === 'reject'}
              disabled={submitting}
              icon={<CloseOutlined />}
              onClick={() => {
                setPendingAction('reject');
                onDecide(confirm.requestId, false);
              }}
            >
              拒绝
            </Button>
            <Button
              className="cfm-btn cfm-approve"
              size="small"
              type="primary"
              loading={submitting && pendingAction === 'approve'}
              icon={<CheckOutlined />}
              style={{
                border: 'none',
                background: 'linear-gradient(135deg, #3FC48F 0%, #0C8A60 100%)',
                color: '#FFFFFF',
              }}
              onClick={() => {
                setPendingAction('approve');
                onDecide(confirm.requestId, true);
              }}
            >
              批准执行
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
};
