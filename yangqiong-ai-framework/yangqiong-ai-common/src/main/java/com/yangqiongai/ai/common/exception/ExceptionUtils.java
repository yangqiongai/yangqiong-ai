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
package com.yangqiongai.ai.common.exception;

/**
 * 异常处理工具
 * @author yangqiong
 */
public final class ExceptionUtils {

    /**
     * 框架合成包装异常类名前缀，其自身消息无业务语义（如Reactor重试耗尽的"Retries exhausted"）
     */
    private static final String[] SYNTHETIC_WRAPPER_PREFIXES = {
            "reactor.util.retry.", "reactor.core.Exceptions$"
    };

    private ExceptionUtils() {
    }

    /**
     * 提取面向用户可读的异常消息
     * 沿cause链向下跳过无语义的框架包装层，返回首个有效业务消息
     * @param error
     * @return
     */
    public static String readableMessage(Throwable error) {
        String fallback = error == null ? null : error.getMessage();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 10) {
            if (!isSyntheticWrapper(current)) {
                String message = current.getMessage();
                if (message != null && !message.isBlank()) {
                    return message;
                }
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
            depth++;
        }
        return fallback == null || fallback.isBlank() ? "系统内部错误，请稍后重试" : fallback;
    }

    /**
     * 判断是否为无业务语义的框架合成包装异常
     * @param error
     * @return
     */
    private static boolean isSyntheticWrapper(Throwable error) {
        String name = error.getClass().getName();
        for (String prefix : SYNTHETIC_WRAPPER_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
