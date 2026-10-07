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
package com.yangqiongai.ai.agent.core.context;

import java.util.Map;

/**
 * session上下文（ThreadLocal）
 * <p>
 * 在调用 @Suspendable 标注的工具方法前，需要通过此类设置当前会话信息。
 * AOP拦截器会从此上下文获取 sessionId 和 userId。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在Agent执行前设置上下文
 * SessionContext.setSessionId("session-123");
 * SessionContext.setUserId("user-456");
 * SessionContext.setScopeId("scope-001");
 * try {
 *     // 调用带 @Suspendable 的工具方法
 *     String result = dangerousTool.deleteData("data-001");
 *     // 审批通过后，可通过 getApprovalResponse 获取审批人的选择和输入
 *     Map<String, Object> response = SessionContext.getApprovalResponse();
 * } finally {
 *     SessionContext.clear();
 * }
 * }</pre>
 *
 * @author yangqiong
 */
public class SessionContext {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SCOPE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> APPROVAL_RESPONSE = new ThreadLocal<>();

    /**
     * 获取当前会话ID
     */
    public static String getSessionId() {
        return SESSION_ID.get();
    }

    /**
     * 设置当前会话ID
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置当前用户ID
     */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前作用域ID
     */
    public static String getScopeId() {
        return SCOPE_ID.get();
    }

    /**
     * 设置当前作用域ID
     */
    public static void setScopeId(String scopeId) {
        SCOPE_ID.set(scopeId);
    }

    /**
     * 获取审批响应数据，审批通过后由SuspendableAspect注入
     */
    public static Map<String, Object> getApprovalResponse() {
        return APPROVAL_RESPONSE.get();
    }

    /**
     * 设置审批响应数据
     */
    public static void setApprovalResponse(Map<String, Object> response) {
        APPROVAL_RESPONSE.set(response);
    }

    /**
     * 清除当前线程的上下文
     */
    public static void clear() {
        SESSION_ID.remove();
        USER_ID.remove();
        SCOPE_ID.remove();
        APPROVAL_RESPONSE.remove();
    }
}
