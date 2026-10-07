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

/**
 * 追踪导出SPI
 * <p>
 * 运行时只定义接口与默认采集逻辑，具体导出（OTel SDK/日志/内存缓冲等）由平台侧实现并注入。
 * 未注入TraceEmitter时静默降级不采集。
 * </p>
 * @author yangqiong
 */
public interface TraceEmitter {

    /**
     * 导出一个已结束的Span
     * @param spanInfo
     */
    void onSpan(SpanInfo spanInfo);
}
