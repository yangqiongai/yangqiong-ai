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
package com.yangqiongai.ai.agent.runtime.spi;

/**
 * 工具调用守卫SPI
 * <p>
 * 挂接引擎工具调用链前后两钩子：before可拦截拒绝（如最小权限画像、出口白名单），
 * after做事后审计与异常检测（如动作审计哈希链）。实现方注册为Spring Bean后
 * 由框架自动收集装配，守卫自身异常不阻断主流程。
 * </p>
 * @author yangqiong
 */
public interface ToolInvocationGuard {

    /**
     * 工具调用前钩子
     * @param invocation
     * @return
     */
    Decision before(ToolInvocation invocation);

    /**
     * 工具调用后钩子（含拒绝与失败结果，实现方自行吞异常）
     * @param outcome
     */
    void after(ToolInvocationOutcome outcome);

    /**
     * 获取执行顺序，越小越先执行
     * @return
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 前置判定结果
     */
    final class Decision {

        /**
         * 是否拒绝
         */
        private final boolean denied;

        /**
         * 拒绝原因（放行时为null）
         */
        private final String reason;

        private Decision(boolean denied, String reason) {
            this.denied = denied;
            this.reason = reason;
        }

        /**
         * 放行
         * @return
         */
        public static Decision allow() {
            return new Decision(false, null);
        }

        /**
         * 拒绝
         * @param reason
         * @return
         */
        public static Decision deny(String reason) {
            return new Decision(true, reason);
        }

        public boolean isDenied() {
            return denied;
        }

        public String getReason() {
            return reason;
        }
    }
}
