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
import React, { useMemo } from 'react';
import { Empty, Spin, Tag } from 'antd';
import {
  ArrowRightOutlined,
  DatabaseOutlined,
  MessageOutlined,
  PlusOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/services';
import { useAuthStore } from '@/store/auth-store';

const HERO_BG = 'linear-gradient(135deg, #0a1a33 0%, #123055 55%, #1b4470 100%)';
const GOLD = '#c9a86a';
const INK = '#0a1a33';

const fadeUp = `
@keyframes dash-fade-up {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}
.dash-reveal {
  opacity: 0;
  animation: dash-fade-up 0.65s cubic-bezier(0.22, 0.68, 0.35, 1) forwards;
}
@keyframes dash-glow {
  0%, 100% { opacity: 0.55; transform: scale(1); }
  50% { opacity: 0.9; transform: scale(1.06); }
}
@keyframes dash-hero-shift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}
@keyframes dash-float {
  0%, 100% { transform: translateY(0) translateX(0); opacity: 0.35; }
  50% { transform: translateY(-22px) translateX(10px); opacity: 0.95; }
}
@keyframes dash-shimmer {
  0% { transform: translateX(-140%) skewX(-18deg); opacity: 0; }
  18% { opacity: 0.6; }
  55% { opacity: 0.3; }
  70%, 100% { transform: translateX(300%) skewX(-18deg); opacity: 0; }
}
@keyframes dash-orbit {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes dash-breathe {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
@keyframes dash-pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 10px rgba(201,168,106,0.9); }
  50% { transform: scale(1.5); box-shadow: 0 0 22px rgba(201,168,106,1); }
}
`;

const HERO_SPARKS = [
  { top: '16%', left: '44%', size: 5, dur: '7s', delay: '0.4s', color: 'rgba(236,217,176,0.85)' },
  { top: '34%', left: '58%', size: 3, dur: '9s', delay: '1.8s', color: 'rgba(255,255,255,0.75)' },
  { top: '12%', left: '70%', size: 4, dur: '8s', delay: '3.1s', color: 'rgba(201,168,106,0.8)' },
  { top: '62%', left: '78%', size: 3, dur: '10s', delay: '2.3s', color: 'rgba(236,217,176,0.6)' },
  { top: '70%', left: '36%', size: 4, dur: '8.5s', delay: '4.2s', color: 'rgba(255,255,255,0.5)' },
  { top: '48%', left: '50%', size: 2.5, dur: '6.5s', delay: '5.4s', color: 'rgba(236,217,176,0.55)' },
];

const greeting = () => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
};

const todayText = () =>
  new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date());

const formatNumber = (v?: number) => {
  if (v == null) return '--';
  return v >= 10000 ? `${(v / 10000).toFixed(1)} 万` : `${v}`;
};

/**
 * 工作台
 */
export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  const userId = user?.id ?? '';
  const displayName = platformAdmin ? user?.name || '平台管理员' : user?.name || userId;

  const agentsQuery = useQuery({
    queryKey: ['dashboard-agents'],
    queryFn: () => api.agent.type.list({ page: 1, size: 100 }),
  });

  const modelsQuery = useQuery({
    queryKey: ['dashboard-models'],
    queryFn: () => api.model.list({ page: 1, size: 100 }),
  });

  const kbQuery = useQuery({
    queryKey: ['dashboard-kb'],
    // 平台看板统计全量知识库(不按userId过滤，KB可能未归属创建人)
    queryFn: () => api.knowledge.kb.list(),
    enabled: !!userId,
  });

  const statsQuery = useQuery({
    queryKey: ['dashboard-stats', userId],
    queryFn: () => api.conversation.stats(userId),
    enabled: !!userId,
    retry: false,
  });

  const agents = useMemo(
    () => (agentsQuery.data?.list ?? []).filter((a) => a.typeStatus === 1),
    [agentsQuery.data],
  );

  // 工作区最多展示3行12个，超出部分通过“全部管理”入口查看
  const visibleAgents = useMemo(() => agents.slice(0, 12), [agents]);

  const metrics = [
    {
      label: '活跃会话',
      value: formatNumber(statsQuery.data?.activeSessions),
      hint: `累计 ${formatNumber(statsQuery.data?.totalSessions)} 个会话`,
      icon: <MessageOutlined />,
      accent: '#2f6fb2',
      to: '/chat',
    },
    {
      label: '智能体',
      value: String(agents.length),
      hint: '已启用的 AI 智能体',
      icon: <RobotOutlined />,
      accent: '#0f7a5c',
      to: '/agents',
    },
    {
      label: '模型',
      value: String(modelsQuery.data?.total ?? 0),
      hint: '已接入的大语言模型',
      icon: <ThunderboltOutlined />,
      accent: '#a5701c',
      to: '/models',
    },
    {
      label: '知识库',
      value: String(kbQuery.data?.length ?? 0),
      hint: '支撑检索增强的知识资产',
      icon: <DatabaseOutlined />,
      accent: '#7a4d9f',
      to: '/knowledge',
    },
  ];

  const tokensUsed = statsQuery.data?.todayTokensUsed ?? 0;

  const loadingCore = agentsQuery.isLoading || modelsQuery.isLoading;

  return (
    <div style={{ minHeight: '100%', background: '#f2f4f8' }}>
      <style>{fadeUp}</style>

      {/* 顶部欢迎横幅 */}
      <div
        style={{
          position: 'relative',
          overflow: 'hidden',
          background: '#0d2140',
          padding: '44px 48px 52px',
        }}
      >
        {/* 装饰：流动渐变背景 */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: HERO_BG,
            backgroundSize: '180% 180%',
            animation: 'dash-hero-shift 16s ease-in-out infinite',
          }}
        />
        {/* 装饰：网格纹理 */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage:
              'linear-gradient(rgba(255,255,255,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.045) 1px, transparent 1px)',
            backgroundSize: '44px 44px',
          }}
        />
        {/* 装饰：金色流光扫过 */}
        <div
          style={{
            position: 'absolute',
            top: '-60%',
            left: 0,
            width: '36%',
            height: '220%',
            background:
              'linear-gradient(90deg, transparent, rgba(201,168,106,0.14), rgba(255,255,255,0.10), rgba(201,168,106,0.14), transparent)',
            animation: 'dash-shimmer 7s ease-in-out infinite',
          }}
        />
        {/* 装饰：漂浮光点 */}
        {HERO_SPARKS.map((s, i) => (
          <span
            key={i}
            style={{
              position: 'absolute',
              top: s.top,
              left: s.left,
              width: s.size,
              height: s.size,
              borderRadius: '50%',
              background: s.color,
              boxShadow: `0 0 ${s.size * 3}px ${s.color}`,
              filter: 'blur(0.5px)',
              animation: `dash-float ${s.dur} ease-in-out ${s.delay} infinite`,
            }}
          />
        ))}
        <div
          style={{
            position: 'absolute',
            top: -140,
            right: 60,
            width: 420,
            height: 420,
            borderRadius: '50%',
            background: `radial-gradient(circle, rgba(201,168,106,0.28) 0%, rgba(201,168,106,0.05) 55%, transparent 70%)`,
            animation: 'dash-glow 6s ease-in-out infinite',
          }}
        />
        <div
          style={{
            position: 'absolute',
            bottom: -120,
            right: 280,
            width: 300,
            height: 300,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(70,130,190,0.35) 0%, transparent 65%)',
          }}
        />
        {/* 装饰：旋转轨道环 */}
        <div
          style={{
            position: 'absolute',
            right: 150,
            bottom: -110,
            width: 300,
            height: 300,
            animation: 'dash-orbit 46s linear infinite',
          }}
        >
          <div
            style={{
              position: 'absolute',
              inset: 0,
              borderRadius: '50%',
              border: '1px dashed rgba(201,168,106,0.38)',
            }}
          />
          <div
            style={{
              position: 'absolute',
              inset: 42,
              borderRadius: '50%',
              border: '1px solid rgba(201,168,106,0.22)',
            }}
          />
          <div
            style={{
              position: 'absolute',
              top: -4,
              left: 'calc(50% - 4px)',
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: GOLD,
              animation: 'dash-pulse 3s ease-in-out infinite',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: 16,
              right: 44,
              width: 5,
              height: 5,
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.85)',
              boxShadow: '0 0 8px rgba(255,255,255,0.9)',
            }}
          />
        </div>
        {/* 金色顶线 */}
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            height: 3,
            background: `linear-gradient(90deg, transparent, ${GOLD}, transparent)`,
            animation: 'dash-breathe 4s ease-in-out infinite',
          }}
        />

        <div style={{ position: 'relative', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 24 }}>
          <div>
            <div
              className="dash-reveal"
              style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14, animationDelay: '0.15s' }}
            >
              <span
                style={{
                  width: 34,
                  height: 1,
                  background: GOLD,
                  display: 'inline-block',
                }}
              />
              <span style={{ color: 'rgba(255,255,255,0.72)', fontSize: 14, letterSpacing: 4 }}>
                {todayText()}
              </span>
            </div>
            <div
              className="dash-reveal"
              style={{ color: '#fff', fontSize: 34, fontWeight: 700, letterSpacing: 1, lineHeight: 1.25, animationDelay: '0.28s' }}
            >
              {greeting()}，{displayName}
            </div>
            <div
              className="dash-reveal"
              style={{ color: 'rgba(255,255,255,0.62)', fontSize: 15, marginTop: 10, letterSpacing: 0.5, animationDelay: '0.42s' }}
            >
              欢迎回到 AI 本地工作台，今天也从一次高效的对话开始
            </div>
            <div className="dash-reveal" style={{ marginTop: 18, display: 'flex', gap: 10, animationDelay: '0.55s' }}>
              {platformAdmin && (
                <Tag
                  style={{
                    background: 'rgba(201,168,106,0.16)',
                    border: `1px solid ${GOLD}`,
                    color: '#ecd9b0',
                    borderRadius: 2,
                    padding: '2px 10px',
                  }}
                >
                  平台管理员
                </Tag>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12, position: 'relative' }}>
            <button
              onClick={() => navigate('/chat')}
              style={{
                cursor: 'pointer',
                border: 'none',
                borderRadius: 4,
                padding: '12px 26px',
                fontSize: 15,
                fontWeight: 600,
                letterSpacing: 2,
                color: INK,
                background: `linear-gradient(135deg, #ecd9b0, ${GOLD})`,
                boxShadow: '0 6px 18px rgba(201,168,106,0.35)',
                transition: 'transform 0.2s ease, box-shadow 0.2s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 10px 24px rgba(201,168,106,0.45)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 6px 18px rgba(201,168,106,0.35)';
              }}
            >
              开始对话
            </button>
            <button
              onClick={() => navigate('/agents')}
              style={{
                cursor: 'pointer',
                borderRadius: 4,
                padding: '12px 26px',
                fontSize: 15,
                fontWeight: 600,
                letterSpacing: 2,
                color: 'rgba(255,255,255,0.9)',
                background: 'rgba(255,255,255,0.08)',
                border: '1px solid rgba(255,255,255,0.28)',
                transition: 'background 0.2s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(255,255,255,0.16)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'rgba(255,255,255,0.08)';
              }}
            >
              管理 Agent
            </button>
          </div>
        </div>
      </div>

      {/* 指标带 */}
      <div
        style={{
          position: 'relative',
          margin: '-30px 48px 0',
          background: '#fff',
          borderRadius: 6,
          boxShadow: '0 12px 32px rgba(10,26,51,0.10)',
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
        }}
      >
        {metrics.map((m, i) => (
          <div
            key={m.label}
            className="dash-reveal"
            style={{
              animationDelay: `${0.12 + i * 0.08}s`,
              padding: '26px 30px',
              borderLeft: i > 0 ? '1px solid #eef0f4' : 'none',
              cursor: 'pointer',
              transition: 'background 0.2s ease',
            }}
            onClick={() => navigate(m.to)}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = '#fafbfd';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#8a93a3', fontSize: 13, letterSpacing: 1 }}>
              <span style={{ color: m.accent, fontSize: 16 }}>{m.icon}</span>
              {m.label}
            </div>
            <div
              style={{
                fontSize: 34,
                fontWeight: 700,
                color: INK,
                lineHeight: 1.2,
                margin: '10px 0 6px',
                fontFamily: "'Georgia', 'Songti SC', 'SimSun', serif",
              }}
            >
              {m.value}
            </div>
            <div style={{ color: '#a5adbc', fontSize: 12 }}>{m.hint}</div>
          </div>
        ))}
      </div>

      {/* 主体两栏 */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 340px',
          gap: 24,
          padding: '24px 48px 48px',
          alignItems: 'start',
        }}
      >
        {/* Agent 工作区 */}
        <section className="dash-reveal" style={{ animationDelay: '0.35s' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
              <h3 style={{ fontSize: 19, fontWeight: 700, color: INK, margin: 0, letterSpacing: 1 }}>Agent 工作区</h3>
              <span style={{ color: '#a5adbc', fontSize: 13 }}>选择一个智能体即刻开始</span>
            </div>
            <a
              onClick={() => navigate('/agents')}
              style={{ color: '#2f6fb2', fontSize: 13, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
            >
              全部管理 <ArrowRightOutlined style={{ fontSize: 11 }} />
            </a>
          </div>

          {loadingCore ? (
            <div style={{ padding: '60px 0', textAlign: 'center', background: '#fff', borderRadius: 6 }}>
              <Spin tip="加载中" />
            </div>
          ) : agents.length === 0 ? (
            <div style={{ background: '#fff', borderRadius: 6, padding: '48px 0' }}>
              <Empty description="暂无启用的智能体，先去创建一个吧" />
              <div style={{ textAlign: 'center', marginTop: 12 }}>
                <a onClick={() => navigate('/agents')} style={{ color: '#2f6fb2', cursor: 'pointer' }}>
                  <PlusOutlined /> 前往创建
                </a>
              </div>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 14 }}>
              {visibleAgents.map((a, i) => (
                <div
                  key={a.typeCode}
                  className="dash-reveal"
                  style={{
                    animationDelay: `${0.4 + Math.min(i * 0.06, 0.5)}s`,
                    background: '#fff',
                    borderRadius: 6,
                    padding: '20px 20px 16px',
                    borderTop: `2px solid ${GOLD}`,
                    boxShadow: '0 2px 10px rgba(10,26,51,0.06)',
                    cursor: 'pointer',
                    transition: 'transform 0.22s ease, box-shadow 0.22s ease',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 10,
                  }}
                  onClick={() => navigate('/chat')}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-4px)';
                    e.currentTarget.style.boxShadow = '0 12px 26px rgba(10,26,51,0.13)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 2px 10px rgba(10,26,51,0.06)';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span
                      style={{
                        width: 38,
                        height: 38,
                        borderRadius: 6,
                        background: 'linear-gradient(135deg, #123055, #1b4470)',
                        color: '#ecd9b0',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 18,
                      }}
                    >
                      {a.typeIcon || <RobotOutlined />}
                    </span>
                    <span style={{ fontWeight: 700, color: INK, fontSize: 15 }}>{a.typeName}</span>
                  </div>
                  <div
                    style={{
                      color: '#8a93a3',
                      fontSize: 12.5,
                      lineHeight: 1.6,
                      minHeight: 40,
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }}
                  >
                    {a.typeDescription || '暂无描述'}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'auto' }}>
                    <span style={{ color: '#c2c9d4', fontSize: 11, fontFamily: 'monospace' }}>{a.typeCode}</span>
                    <span style={{ color: '#a5701c', fontSize: 12, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 3 }}>
                      进入对话 <ArrowRightOutlined style={{ fontSize: 10 }} />
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* 右侧用量面板 */}
        <aside className="dash-reveal" style={{ animationDelay: '0.45s' }}>
          <div
            style={{
              background: '#fff',
              borderRadius: 6,
              padding: '24px 24px 20px',
              boxShadow: '0 2px 10px rgba(10,26,51,0.06)',
              borderTop: `2px solid ${INK}`,
            }}
          >
            <h3 style={{ fontSize: 16, fontWeight: 700, color: INK, margin: '0 0 4px', letterSpacing: 1 }}>今日用量</h3>
            <p style={{ color: '#a5adbc', fontSize: 12, margin: '0 0 18px' }}>对话 Token 消耗与额度</p>

            {statsQuery.isLoading ? (
              <div style={{ padding: '36px 0', textAlign: 'center' }}>
                <Spin />
              </div>
            ) : statsQuery.isError ? (
              <div style={{ color: '#a5adbc', fontSize: 13, padding: '24px 0', textAlign: 'center' }}>
                用量数据暂不可用
              </div>
            ) : (
              <>
                <div style={{ marginBottom: 20 }}>
                  <span
                    style={{
                      fontSize: 30,
                      fontWeight: 700,
                      color: INK,
                      fontFamily: "'Georgia', 'Songti SC', 'SimSun', serif",
                    }}
                  >
                    {formatNumber(tokensUsed)}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: '#eef0f4', borderRadius: 6, overflow: 'hidden' }}>
                  <div style={{ background: '#fff', padding: '14px 16px' }}>
                    <div style={{ color: '#a5adbc', fontSize: 12, marginBottom: 4 }}>累计消息</div>
                    <div style={{ fontWeight: 700, color: INK, fontSize: 18 }}>{formatNumber(statsQuery.data?.totalMessages)}</div>
                  </div>
                  <div style={{ background: '#fff', padding: '14px 16px' }}>
                    <div style={{ color: '#a5adbc', fontSize: 12, marginBottom: 4 }}>累计 Token</div>
                    <div style={{ fontWeight: 700, color: INK, fontSize: 18 }}>{formatNumber(statsQuery.data?.totalTokensUsed)}</div>
                  </div>
                  <div style={{ background: '#fff', padding: '14px 16px' }}>
                    <div style={{ color: '#a5adbc', fontSize: 12, marginBottom: 4 }}>活跃会话</div>
                    <div style={{ fontWeight: 700, color: INK, fontSize: 18 }}>{formatNumber(statsQuery.data?.activeSessions)}</div>
                  </div>
                  <div style={{ background: '#fff', padding: '14px 16px' }}>
                    <div style={{ color: '#a5adbc', fontSize: 12, marginBottom: 4 }}>长期记忆</div>
                    <div style={{ fontWeight: 700, color: INK, fontSize: 18 }}>{formatNumber(statsQuery.data?.longTermMemoryCount)}</div>
                  </div>
                </div>
              </>
            )}
          </div>

          <div
            style={{
              marginTop: 16,
              borderRadius: 6,
              padding: '18px 20px',
              background: HERO_BG,
              color: 'rgba(255,255,255,0.75)',
              fontSize: 12.5,
              lineHeight: 1.9,
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                bottom: 0,
                width: 2,
                background: GOLD,
              }}
            />
            <div style={{ color: '#ecd9b0', fontWeight: 600, fontSize: 13.5, letterSpacing: 2, marginBottom: 6 }}>
              平台速览
            </div>
            <div>模型 {modelsQuery.data?.total ?? 0} 个 · 知识库 {kbQuery.data?.length ?? 0} 个</div>
            <div>智能体 {agents.length} 个 · 全部就绪可用</div>
          </div>
        </aside>
      </div>
    </div>
  );
};

export default DashboardPage;
