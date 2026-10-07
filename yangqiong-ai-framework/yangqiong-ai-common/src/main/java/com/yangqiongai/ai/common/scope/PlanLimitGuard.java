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
 * 套餐数量限制校验
 * @author yangqiong
 */
public interface PlanLimitGuard {

    /**
     * 校验当前作用域是否允许再创建知识库
     * @param currentCount 当前知识库数量
     * @return
     */
    void checkKnowledgeBaseLimit(long currentCount);

    /**
     * 校验当前作用域是否允许再上传指定字节的存储内容
     * @param incomingBytes 本次上传字节数
     * @return
     */
    void checkStorageLimit(long incomingBytes);

    /**
     * 校验当前作用域当月Token用量是否超出套餐配额
     * @return
     */
    void checkMonthlyTokenQuota();

    /**
     * 登记活跃对话会话并校验并发会话数是否超限
     * @param sessionId 会话ID
     * @return
     */
    void enterSession(String sessionId);
}
