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
import { App, Button, Form, Input } from 'antd';
import {
  ArrowRightOutlined,
  CheckOutlined,
  LockOutlined,
  RiseOutlined,
  StarFilled,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { loginPlatformAdmin } from '@/services/auth-api';
import { useAuthStore } from '@/store/auth-store';
import { encryptCredential } from '@yangqiong/shared';
import type { PlatformAdminLoginPayload } from '@yangqiong/shared';
import './LoginPage.css';

interface LoginFormValues extends PlatformAdminLoginPayload {}

const capabilityItems = ['检查点中断续跑', '本地工作台 · 文件即产物', 'Token 成本看得见、管得住'];
const badgeItems = ['安全加密访问', 'RSA 高倍加密', '可观测留痕'];

export const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [quickLoading, setQuickLoading] = useState(false);
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const { message } = App.useApp();

  const doLogin = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      // 账号密码RSA加密后传输，避免明文抓包泄露
      const result = await loginPlatformAdmin({
        ...values,
        username: encryptCredential(values.username),
        password: encryptCredential(values.password),
      });
      setAuth(result);
      message.success('登录成功');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      message.error(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickLogin = async () => {
    setQuickLoading(true);
    try {
      const result = await loginPlatformAdmin({
        username: encryptCredential('admin'),
        password: encryptCredential('admin123'),
      });
      setAuth(result);
      message.success('登录成功');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      message.error(err instanceof Error ? err.message : '登录失败');
    } finally {
      setQuickLoading(false);
    }
  };

  return (
    <main className="yq-page">
      {/* 全幅背景装饰层：星散光晕、右侧金晖、横贯金线，皆跨中线分布使画面相通 */}
      <i className="yq-topline" aria-hidden="true" />
      <i className="yq-halo yq-halo-r" aria-hidden="true" />
      <div className="yq-glow yq-glow-l" aria-hidden="true" />
      <div className="yq-arc" aria-hidden="true">
        <i className="yq-arc-c1" />
        <i className="yq-arc-c2" />
        <i className="yq-arc-c3" />
      </div>
      <i className="yq-band" aria-hidden="true" />

      <div className="yq-frame">
        <header className="yq-brand-bar">
          <div className="yq-brand-mark" aria-hidden="true">
            <span>泱</span>
          </div>
          <div className="yq-brand-name">
            <strong>泱穹智能体</strong>
            <span>YANGQIONG AGENT PLATFORM · 社区版</span>
          </div>
        </header>

        <div className="yq-main">
          <section className="yq-hero" aria-label="泱穹智能体平台 · 社区版介绍">
            <span className="yq-eyebrow">OPEN INTELLIGENCE · OPEN CHINA</span>
            <h1 className="yq-title">让每一次智能体运行<br />都可靠、可控、可复盘</h1>
            <p className="yq-sub">
              自研 Agent 引擎、工作流编排、知识库 RAG 与本地工作台一体化，
              为生产而生——长任务敢中断、敢恢复，不空转、不烧钱。
            </p>
            <ul className="yq-capability-list">
              {capabilityItems.map((item) => (
                <li key={item}>
                  <CheckOutlined />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="yq-login" aria-label="登录">
            <div className="yq-login-card">
              <div className="yq-login-head">
                <span className="yq-login-eyebrow"><StarFilled /> SECURE SYSTEM</span>
                <h2>登录系统</h2>
                <p>欢迎回来，请登录泱穹智能体平台</p>
              </div>

              <Form
                className="yq-login-form"
                layout="vertical"
                onFinish={doLogin}
                initialValues={{ username: 'admin' }}
                requiredMark={false}
              >
                <Form.Item
                  name="username"
                  label="管理员账号"
                  rules={[{ required: true, message: '请输入管理员账号' }]}
                >
                  <Input
                    size="large"
                    prefix={<UserOutlined />}
                    placeholder="请输入管理员账号"
                    autoComplete="username"
                  />
                </Form.Item>
                <Form.Item
                  name="password"
                  label="登录密码"
                  rules={[{ required: true, message: '请输入登录密码' }]}
                >
                  <Input.Password
                    size="large"
                    prefix={<LockOutlined />}
                    placeholder="请输入登录密码"
                    autoComplete="current-password"
                  />
                </Form.Item>
                <Form.Item className="yq-submit-item">
                  <Button
                    type="primary"
                    htmlType="submit"
                    size="large"
                    block
                    loading={loading}
                    iconPosition="end"
                    icon={<ArrowRightOutlined />}
                    className="yq-submit-btn"
                  >
                    进入系统
                  </Button>
                </Form.Item>
                <div className="yq-divider"><span>或使用演示身份</span></div>
                <Button
                  className="yq-quick-btn"
                  size="large"
                  block
                  loading={quickLoading}
                  disabled={loading}
                  onClick={handleQuickLogin}
                >
                  <RiseOutlined /> 演示身份「admin」
                </Button>
              </Form>

              <div className="yq-badges">
                {badgeItems.map((item) => (
                  <span key={item}><LockOutlined /> {item}</span>
                ))}
              </div>
            </div>
          </section>
        </div>

        <footer className="yq-foot">YANGQIONG INTELLIGENCE · 2026</footer>
      </div>
    </main>
  );
};