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
package com.yangqiongai.ai.platform.bss.scope;

import com.yangqiongai.ai.common.scope.ScopeContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;

/**
 * 作用域上下文异步任务装饰器
 * <p>
 * 用于 @Async 场景下跨线程传递 scopeId：在提交异步任务时捕获主线程的 ScopeContext，
 * 在异步线程执行前恢复，执行后清理，避免线程池复用导致的作用域上下文泄露。
 * </p>
 * <p>
 * Spring Boot 自动配置的 applicationTaskExecutor 会自动拾取容器中的 TaskDecorator Bean，
 * 自定义线程池（如 @Async("ragExecutor")）需自行注入本装饰器。
 * </p>
 * @author yangqiong
 */
public class ScopeAwareTaskDecorator implements TaskDecorator {

    private static final Logger log = LoggerFactory.getLogger(ScopeAwareTaskDecorator.class);

    /**
     * 装饰Runnable，捕获当前线程的scopeId并在异步线程执行前恢复
     * @param runnable
     * @return
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        // 主线程捕获scopeId
        String capturedScopeId = ScopeContext.getScopeId();
        return () -> {
            String previousScopeId = ScopeContext.getScopeId();
            try {
                ScopeContext.setScopeId(capturedScopeId);
                log.debug("异步任务恢复作用域上下文: scopeId={}", capturedScopeId);
                runnable.run();
            } finally {
                // 还原为异步线程原有值，避免线程池复用导致上下文泄露
                ScopeContext.setScopeId(previousScopeId);
            }
        };
    }
}
