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
package com.yangqiongai.ai.trust.audit;

import com.yangqiongai.ai.trust.audit.entity.AgentActionLog;

/**
 * 动作审计
 * <p>
 * 记录Agent工具调用与治理动作，社区版为平铺日志，企业版在此之上叠加哈希链与锚点。
 * </p>
 * @author yangqiong
 */
public interface ActionAuditService {

    /**
     * 记录动作审计日志（实现方自行吞异常，不阻断调用方主流程）
     * @param actionLog
     */
    void record(AgentActionLog actionLog);

    /**
     * 动作审计是否开启
     * @return
     */
    boolean isEnabled();
}
