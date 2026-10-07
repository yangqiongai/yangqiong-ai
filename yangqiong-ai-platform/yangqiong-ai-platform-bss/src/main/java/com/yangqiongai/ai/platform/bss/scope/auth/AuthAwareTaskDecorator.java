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
package com.yangqiongai.ai.platform.bss.scope.auth;

import com.yangqiongai.ai.platform.bss.scope.ScopeAwareTaskDecorator;

import org.springframework.core.task.TaskDecorator;

/**
 * 鉴权上下文异步任务装饰器
 * <p>
 * 继承作用域装饰器，在 @Async 场景下同时传播 scopeId 与 AuthContext，
 * 执行后还原，避免线程池复用导致上下文泄露。
 * </p>
 * @author yangqiong
 */
public class AuthAwareTaskDecorator extends ScopeAwareTaskDecorator implements TaskDecorator {

    /**
     * 装饰Runnable，捕获当前线程的鉴权上下文并在异步线程执行前恢复
     * @param runnable
     * @return
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        AuthContext captured = AuthContextHolder.get();
        Runnable scopeDecorated = super.decorate(runnable);
        return () -> {
            AuthContext previous = AuthContextHolder.get();
            try {
                AuthContextHolder.set(captured);
                scopeDecorated.run();
            } finally {
                AuthContextHolder.set(previous);
            }
        };
    }
}
