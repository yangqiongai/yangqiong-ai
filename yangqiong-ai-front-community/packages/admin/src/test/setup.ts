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
import '@testing-library/jest-dom';

// jsdom 未实现 matchMedia（antd responsiveObserver / 治理组件动效降级依赖），桩定非降级环境
// 注意：用普通函数而非 vi.fn()，避免测试内 vi.restoreAllMocks() 将其重置为 undefined
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

// jsdom 无 canvas 2d 上下文（echarts/zrender 渲染依赖），桩定宽容的 ctx
const gradientStub = { addColorStop: () => undefined };
const ctxStub = new Proxy({}, {
  get: (target: Record<string, unknown>, prop: string | symbol) => {
    if (prop === 'measureText') {
      return () => ({ width: 10 });
    }
    if (prop === 'createLinearGradient' || prop === 'createRadialGradient' || prop === 'createPattern') {
      return () => gradientStub;
    }
    return target[prop as string] ?? (() => undefined);
  },
  set: (target: Record<string, unknown>, prop: string | symbol, value) => {
    target[prop as string] = value;
    return true;
  },
});

Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
  writable: true,
  value: () => ctxStub as unknown as CanvasRenderingContext2D,
});
