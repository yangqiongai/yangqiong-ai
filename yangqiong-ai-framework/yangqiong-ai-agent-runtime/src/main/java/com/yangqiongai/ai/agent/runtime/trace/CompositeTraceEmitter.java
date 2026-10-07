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
package com.yangqiongai.ai.agent.runtime.trace;

import java.util.List;

/**
 * 组合追踪导出器
 * <p>
 * 按注册顺序向多个{@link TraceEmitter}广播Span，单个导出器异常不影响其他导出器与执行流程。
 * </p>
 * @author yangqiong
 */
public class CompositeTraceEmitter implements TraceEmitter {

    private final List<TraceEmitter> delegates;

    /**
     * 构造组合导出器
     * @param delegates 导出器列表
     */
    public CompositeTraceEmitter(List<TraceEmitter> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void onSpan(SpanInfo spanInfo) {
        for (TraceEmitter delegate : delegates) {
            try {
                delegate.onSpan(spanInfo);
            } catch (Exception e) {
                // 单个导出器失败仅记录，不中断广播
            }
        }
    }
}
