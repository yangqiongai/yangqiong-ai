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
package com.yangqiongai.ai.common.scope;

/**
 * 套餐数量限制默认放行
 * @author yangqiong
 */
public class DefaultPlanLimitGuard implements PlanLimitGuard {

    /**
     * 默认放行知识库数量校验
     * @param currentCount
     * @return
     */
    @Override
    public void checkKnowledgeBaseLimit(long currentCount) {
    }

    /**
     * 默认放行存储限制校验
     * @param incomingBytes
     * @return
     */
    @Override
    public void checkStorageLimit(long incomingBytes) {
    }

    /**
     * 默认放行月度Token配额校验
     * @return
     */
    @Override
    public void checkMonthlyTokenQuota() {
    }

    /**
     * 默认放行并发会话校验
     * @param sessionId
     * @return
     */
    @Override
    public void enterSession(String sessionId) {
    }
}
