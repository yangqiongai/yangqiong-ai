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
import { useState } from 'react';
import {
  Circle,
  CircleCheck,
  Loader2,
  Rocket,
  Sparkles,
  X,
} from 'lucide-react';

/**
 * 首次接入状态
 */
export interface OnboardingStatus {
  onboarded: boolean;

  hasAgent: boolean;

  hasModel: boolean;

  hasRunData: boolean;

  hasSignal: boolean;

  demoSeeded: boolean;
}

/**
 * 接入向导步骤定义
 */
export interface OnboardingWizardStep {
  key: keyof Omit<OnboardingStatus, 'onboarded' | 'demoSeeded'>;

  title: string;

  description: string;

  route: string;
}

/**
 * 接入向导默认四步骤(企业端)
 */
const ENTERPRISE_WIZARD_STEPS: OnboardingWizardStep[] = [
  { key: 'hasAgent', title: '创建第一个 Agent', description: '在 Agent 定义页创建你的第一个智能体', route: '/agents' },
  { key: 'hasModel', title: '绑定模型与凭证', description: '在模型路由页配置可用模型与凭证', route: '/intelligence/model-route' },
  { key: 'hasRunData', title: '发起首次运行', description: '运行 Agent 产生真实运行数据', route: '/agent-runs' },
  { key: 'hasSignal', title: '观察治理信号', description: '信号产生后回到驾驶舱查看点亮的面板', route: '/governance/inbox' },
];

/**
 * 接入状态只读清单(社区裁剪模式展示)
 */
const STATUS_CHIPS: Array<{ key: keyof Omit<OnboardingStatus, 'onboarded' | 'demoSeeded'>; label: string }> = [
  { key: 'hasAgent', label: 'Agent 定义' },
  { key: 'hasModel', label: '模型凭证' },
  { key: 'hasRunData', label: '近 7 天运行' },
  { key: 'hasSignal', label: '治理信号' },
];

/**
 * 首次接入空状态(驾驶舱冷启动：企业端向导+一键示例，社区端只读状态清单)
 * @param props
 * @return
 */
export function OnboardingEmptyState(props: {
  status: OnboardingStatus;

  title?: string;

  description?: string;

  steps?: OnboardingWizardStep[];

  seeding?: boolean;

  cleaning?: boolean;

  error?: string;

  onSeed?: () => void;

  onClean?: () => void;

  onNavigate: (route: string) => void;
}): JSX.Element {
  const { status, title, description, steps, seeding, cleaning, error, onSeed, onClean, onNavigate } = props;
  const wizardSteps = steps ?? ENTERPRISE_WIZARD_STEPS;
  const showWizard = wizardSteps.length > 0;
  const [wizardOpen, setWizardOpen] = useState(false);
  const doneCount = wizardSteps.filter((step) => status[step.key]).length;

  return (
    <div
      className="relative flex min-h-[calc(100vh-6rem)] flex-col items-center justify-center overflow-hidden rounded-2xl p-8"
      data-testid="onboarding-empty-state"
    >
      <span
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-40 opacity-15"
        style={{ background: 'linear-gradient(90deg, #0a5bd8, #6d28d9)', filter: 'blur(120px)' }}
      />
      <div className="relative z-10 flex max-w-lg flex-col items-center gap-4 text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl border border-sky-400/30 bg-sky-400/10">
          <Rocket size={30} className="text-sky-300" />
        </div>
        <h2 className="text-xl font-semibold text-slate-100">
          {title ?? '开始接入你的第一个智能体'}
        </h2>
        <p className="text-sm leading-6 text-slate-400">
          {description ?? '当前还没有 Agent 数据。你可以按向导逐步完成接入，也可以一键导入示例数据，立即体验治理驾驶舱的全部面板。'}
        </p>
        <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
          {onSeed ? (
            <button
              type="button"
              onClick={onSeed}
              disabled={seeding || cleaning}
              data-testid="onboarding-seed"
              className="flex items-center gap-2 rounded-lg border border-sky-400/40 bg-sky-500/20 px-5 py-2.5 text-sm font-medium text-sky-200 transition-colors hover:bg-sky-500/30 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {seeding ? <Loader2 size={15} className="animate-spin" /> : <Sparkles size={15} />}
              {seeding ? '导入中…' : '一键导入示例数据'}
            </button>
          ) : null}
          {showWizard ? (
            <button
              type="button"
              onClick={() => setWizardOpen(true)}
              data-testid="onboarding-wizard-open"
              className="flex items-center gap-2 rounded-lg border border-white/15 px-5 py-2.5 text-sm text-slate-200 transition-colors hover:bg-white/10"
            >
              接入向导
              <span className="rounded-full bg-white/10 px-1.5 py-0.5 text-[10px] text-slate-300">
                {doneCount}/{wizardSteps.length}
              </span>
            </button>
          ) : null}
          {onClean && status.demoSeeded ? (
            <button
              type="button"
              onClick={onClean}
              disabled={seeding || cleaning}
              data-testid="onboarding-clean"
              className="flex items-center gap-2 rounded-lg px-4 py-2.5 text-xs text-slate-400 transition-colors hover:text-rose-300 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {cleaning ? <Loader2 size={13} className="animate-spin" /> : null}
              {cleaning ? '清理中…' : '清理示例数据'}
            </button>
          ) : null}
        </div>
        {showWizard || onSeed ? (
          <p className="text-xs text-slate-500">
            {status.demoSeeded ? '示例数据已导入，清理后不会影响真实数据。' : null}
          </p>
        ) : (
          <div className="mt-2 flex flex-wrap items-center justify-center gap-2" data-testid="onboarding-status-chips">
            {STATUS_CHIPS.map((chip) => {
              const done = status[chip.key];
              return (
                <span
                  key={chip.key}
                  className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs ${
                    done ? 'border-emerald-400/40 bg-emerald-400/10 text-emerald-300' : 'border-white/10 bg-white/5 text-slate-400'
                  }`}
                >
                  {done ? <CircleCheck size={13} /> : <Circle size={13} />}
                  {chip.label}
                </span>
              );
            })}
          </div>
        )}
        {error ? (
          <p className="text-xs text-rose-400" data-testid="onboarding-error">
            {error}
          </p>
        ) : null}
      </div>

      <div
        aria-hidden={!wizardOpen}
        className={`fixed inset-0 z-40 bg-black/40 backdrop-blur-sm transition-opacity duration-500 ${
          wizardOpen ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        onClick={() => setWizardOpen(false)}
      />
      <aside
        data-testid="onboarding-wizard"
        className={`fixed right-0 top-0 z-50 flex h-full w-[26rem] max-w-[90vw] flex-col border-l border-white/10 bg-[#0B1220]/95 shadow-2xl backdrop-blur-xl transition-transform duration-500 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          wizardOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <header className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <div>
            <h3 className="text-sm font-semibold text-slate-100">接入向导</h3>
            <p className="mt-0.5 text-xs text-slate-500">完成全部步骤后，驾驶舱面板将自动点亮</p>
          </div>
          <button
            type="button"
            aria-label="关闭向导"
            onClick={() => setWizardOpen(false)}
            className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-slate-200"
          >
            <X size={16} />
          </button>
        </header>
        <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-5">
          {wizardSteps.map((step, index) => {
            const done = status[step.key];
            return (
              <div
                key={step.key}
                data-testid={`onboarding-step-${step.key}`}
                className={`rounded-xl border p-4 transition-colors ${
                  done ? 'border-emerald-400/30 bg-emerald-400/5' : 'border-white/10 bg-white/5'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  {done ? (
                    <CircleCheck size={18} className="shrink-0 text-emerald-400" />
                  ) : (
                    <Circle size={18} className="shrink-0 text-slate-500" />
                  )}
                  <span className={`flex-1 text-sm font-medium ${done ? 'text-emerald-300' : 'text-slate-200'}`}>
                    {index + 1}. {step.title}
                  </span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] ${
                      done ? 'bg-emerald-400/15 text-emerald-300' : 'bg-white/10 text-slate-400'
                    }`}
                  >
                    {done ? '已完成' : '待完成'}
                  </span>
                </div>
                <p className="mt-2 pl-[28px] text-xs leading-5 text-slate-500">{step.description}</p>
                <div className="mt-3 pl-[28px]">
                  <button
                    type="button"
                    onClick={() => onNavigate(step.route)}
                    className="rounded-md border border-sky-400/30 bg-sky-400/10 px-3 py-1.5 text-xs text-sky-300 transition-colors hover:bg-sky-400/20"
                  >
                    {done ? '去查看' : '去完成'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
        {onSeed ? (
          <footer className="border-t border-white/10 px-5 py-4">
            <button
              type="button"
              onClick={onSeed}
              disabled={seeding || cleaning}
              className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-sky-400/30 bg-sky-400/10 px-4 py-2.5 text-sm text-sky-300 transition-colors hover:bg-sky-400/20 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {seeding ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
              没有真实环境？一键导入示例数据
            </button>
          </footer>
        ) : null}
      </aside>
    </div>
  );
}
