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
import { App, Button, Input, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { api } from '@/services';
import type { PendingRequestInfo } from '@yangqiong/shared';

const { Text } = Typography;

interface ApprovalCardsProps {
  requests: PendingRequestInfo[];
  onHandled: (requestId: string) => void;
  approvedBy?: string;

  /**
   * 深色主题（本地工作台等深色页面传入）
   */
  dark?: boolean;
}

/**
 * 对话流内联审批卡片（待审批事件实时渲染于输入框上方）
 */
export const ApprovalCards: React.FC<ApprovalCardsProps> = ({ requests, onHandled, approvedBy, dark }) => {
  const { message } = App.useApp();
  const [handling, setHandling] = useState<string>('');

  /**
   * 提交中的决策动作（转圈只显示在对应按钮上）
   */
  const [handlingAction, setHandlingAction] = useState<'approve' | 'reject'>('approve');
  const [rejectReasons, setRejectReasons] = useState<Record<string, string>>({});

  /**
   * 当前展开拒绝原因输入的请求ID
   */
  const [rejectingId, setRejectingId] = useState<string>('');

  if (requests.length === 0) {
    return null;
  }

  /**
   * 提交审批决策并移除对应卡片
   * @param request
   * @param approved
   */
  const handleDecision = async (request: PendingRequestInfo, approved: boolean) => {
    setHandling(request.requestId);
    setHandlingAction(approved ? 'approve' : 'reject');
    try {
      await api.approval.handle({
        requestId: request.requestId,
        approved,
        rejectReason: approved ? undefined : rejectReasons[request.requestId],
        approvedBy,
      });
      message.success(approved ? '已批准' : '已拒绝');
      setRejectingId('');
      onHandled(request.requestId);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '审批提交失败');
    } finally {
      setHandling('');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 8 }}>
      <style>{`
        @keyframes appr-in { from { opacity: 0; transform: translateY(8px) scale(0.98); } to { opacity: 1; transform: none; } }
        @keyframes appr-breathe { 0%, 100% { opacity: 0.35; } 50% { opacity: 1; } }
        @keyframes appr-halo { 0%, 100% { box-shadow: 0 0 0 0 rgba(64, 86, 214, 0.3); } 50% { box-shadow: 0 0 0 6px rgba(64, 86, 214, 0); } }
        .appr-collapse { display: grid; grid-template-rows: 0fr; opacity: 0; transition: grid-template-rows 0.25s ease, opacity 0.2s ease; }
        .appr-collapse.open { grid-template-rows: 1fr; opacity: 1; }
        .appr-collapse > div { overflow: hidden; }
        .appr-btn { transition: all 0.18s ease; }
        .appr-btn:hover { transform: translateY(-1px); }
        .appr-approve:hover { box-shadow: 0 4px 12px rgba(64, 86, 214, 0.4); filter: brightness(1.06); }
        .appr-input::placeholder { color: ${dark ? '#6B7385' : '#B8BFCF'}; }
      `}</style>
      {requests.map((request) => {
        const isHandling = handling === request.requestId;
        const rejecting = rejectingId === request.requestId;
        return (
          <div
            key={request.requestId}
            style={{
              position: 'relative',
              overflow: 'hidden',
              borderRadius: 12,
              border: dark ? '1px solid rgba(130, 150, 245, 0.35)' : '1px solid #E3E7F2',
              background: dark
                ? 'linear-gradient(160deg, rgba(40, 48, 66, 0.94) 0%, rgba(31, 38, 54, 0.96) 100%)'
                : 'linear-gradient(165deg, #FFFEFB 0%, #F8F9FD 100%)',
              boxShadow: dark ? '0 6px 20px rgba(0, 0, 0, 0.28)' : '0 4px 14px rgba(31, 36, 48, 0.06)',
              padding: '12px 14px',
              animation: 'appr-in 0.3s ease-out both',
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
                animation: 'appr-breathe 3.2s ease-in-out infinite',
              }}
            />
            <div style={{ display: 'flex', gap: 12 }}>
              {/* 审批徽章（呼吸光环） */}
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
                  animation: 'appr-halo 2.4s ease-in-out infinite',
                }}
              >
                <SafetyCertificateOutlined />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <Text strong style={{ fontSize: 12, letterSpacing: 2, color: dark ? '#8296F5' : '#4056D6' }}>
                    审批请求
                  </Text>
                  <Text strong style={{ fontSize: 14, color: dark ? '#E7ECF5' : '#1F2430' }}>
                    {request.targetName ?? request.resourceType ?? '工具调用'}
                  </Text>
                </div>
                {request.reason && (
                  <div
                    style={{
                      marginTop: 8,
                      padding: '6px 10px',
                      borderLeft: `2px solid ${dark ? '#8296F5' : '#4056D6'}`,
                      borderRadius: '0 6px 6px 0',
                      background: dark ? 'rgba(130, 150, 245, 0.08)' : 'rgba(64, 86, 214, 0.06)',
                      fontSize: 12.5,
                      lineHeight: 1.6,
                      color: dark ? '#C9D3E4' : '#4A5265',
                      wordBreak: 'break-word',
                    }}
                  >
                    {request.reason}
                  </div>
                )}
                <div
                  style={{
                    marginTop: 8,
                    fontSize: 12,
                    color: dark ? '#93A0B5' : '#8A92A6',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  请求 ID：
                  <Text
                    copyable
                    style={{
                      fontFamily: 'SFMono-Regular, Consolas, Menlo, monospace',
                      fontSize: 11.5,
                      color: dark ? '#C9D3E4' : '#6B7385',
                    }}
                  >
                    {request.requestId}
                  </Text>
                </div>
              </div>
              {/* 待审批呼吸徽标 */}
              <span
                style={{
                  flexShrink: 0,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  height: 22,
                  padding: '0 10px',
                  borderRadius: 999,
                  border: `1px solid ${dark ? 'rgba(130, 150, 245, 0.4)' : 'rgba(64, 86, 214, 0.3)'}`,
                  background: dark ? 'rgba(130, 150, 245, 0.1)' : 'rgba(64, 86, 214, 0.06)',
                  fontSize: 12,
                  color: dark ? '#8296F5' : '#4056D6',
                }}
              >
                <span
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: dark ? '#8296F5' : '#4056D6',
                    animation: 'appr-breathe 1.6s ease-in-out infinite',
                  }}
                />
                待审批
              </span>
            </div>
            {/* 操作条 */}
            <div
              style={{
                marginTop: 12,
                paddingTop: 10,
                borderTop: `1px dashed ${dark ? 'rgba(255, 255, 255, 0.14)' : '#E7EAF1'}`,
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
              }}
            >
              <Button
                className="appr-btn"
                size="small"
                danger
                disabled={isHandling}
                icon={<CloseOutlined />}
                onClick={() => setRejectingId(rejecting ? '' : request.requestId)}
              >
                拒绝
              </Button>
              <Button
                className="appr-btn appr-approve"
                size="small"
                type="primary"
                loading={isHandling && handlingAction === 'approve'}
                icon={<CheckOutlined />}
                style={{
                  border: 'none',
                  background: 'linear-gradient(135deg, #6B82F0 0%, #3D53CE 100%)',
                  color: '#FFFFFF',
                }}
                onClick={() => void handleDecision(request, true)}
              >
                批准
              </Button>
            </div>
            {/* 拒绝原因展开区 */}
            <div className={`appr-collapse${rejecting ? ' open' : ''}`}>
              <div>
                <div
                  style={{
                    marginTop: 10,
                    padding: '10px 12px',
                    borderRadius: 8,
                    border: `1px dashed ${dark ? 'rgba(255, 107, 107, 0.4)' : '#F5C2C0'}`,
                    background: dark ? 'rgba(255, 107, 107, 0.08)' : 'rgba(255, 77, 79, 0.05)',
                  }}
                >
                  <Input.TextArea
                    className="appr-input"
                    rows={2}
                    maxLength={200}
                    placeholder="拒绝原因（可选）"
                    value={rejectReasons[request.requestId] ?? ''}
                    onChange={(e) =>
                      setRejectReasons((prev) => ({ ...prev, [request.requestId]: e.target.value }))
                    }
                    style={{
                      background: dark ? 'rgba(255, 255, 255, 0.06)' : '#FFFFFF',
                      border: `1px solid ${dark ? 'rgba(130, 150, 245, 0.3)' : '#E3E7F2'}`,
                      color: dark ? '#E7ECF5' : undefined,
                      width: '100%',
                    }}
                  />
                  <div style={{ marginTop: 8, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Button size="small" onClick={() => setRejectingId('')}>
                      取消
                    </Button>
                    <Button
                      size="small"
                      danger
                      loading={isHandling && handlingAction === 'reject'}
                      disabled={isHandling}
                      onClick={() => void handleDecision(request, false)}
                    >
                      确认拒绝
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};
