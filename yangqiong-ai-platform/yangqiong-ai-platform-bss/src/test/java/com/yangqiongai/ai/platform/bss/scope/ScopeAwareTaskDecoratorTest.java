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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScopeAwareTaskDecorator单元测试
 */
@DisplayName("ScopeAwareTaskDecorator 异步作用域上下文传递测试")
class ScopeAwareTaskDecoratorTest {

    private final ScopeAwareTaskDecorator decorator = new ScopeAwareTaskDecorator();

    @AfterEach
    void cleanUp() {
        ScopeContext.clear();
    }

    @Nested
    @DisplayName("跨线程传递scopeId")
    class CrossThreadPropagationTest {

        @Test
        @DisplayName("异步线程内可读取主线程设置的scopeId")
        void shouldPropagateScopeIdToAsyncThread() throws InterruptedException {
            ScopeContext.setScopeId("scope-async-001");
            AtomicReference<String> asyncScopeId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Runnable decorated = decorator.decorate(() -> {
                asyncScopeId.set(ScopeContext.getScopeId());
                latch.countDown();
            });

            Thread worker = new Thread(decorated);
            worker.start();
            latch.await(2, TimeUnit.SECONDS);

            assertThat(asyncScopeId.get()).isEqualTo("scope-async-001");
        }

        @Test
        @DisplayName("主线程未设置时异步线程内返回default")
        void shouldReturnDefaultWhenMainThreadNotSet() throws InterruptedException {
            ScopeContext.clear();
            AtomicReference<String> asyncScopeId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Runnable decorated = decorator.decorate(() -> {
                asyncScopeId.set(ScopeContext.getScopeId());
                latch.countDown();
            });

            Thread worker = new Thread(decorated);
            worker.start();
            latch.await(2, TimeUnit.SECONDS);

            assertThat(asyncScopeId.get()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("线程池复用隔离")
    class ThreadPoolReuseIsolationTest {

        @Test
        @DisplayName("异步任务执行后还原线程原有scopeId，避免上下文泄露")
        void shouldRestorePreviousScopeIdAfterExecution() throws InterruptedException {
            ScopeContext.setScopeId("scope-main");
            AtomicReference<String> afterExecutionScopeId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Runnable decorated = decorator.decorate(() -> {
                afterExecutionScopeId.set(ScopeContext.getScopeId());
                latch.countDown();
            });

            Thread worker = new Thread(() -> {
                // worker线程预先设置scopeId，模拟线程池复用场景
                ScopeContext.setScopeId("worker-previous");
                try {
                    decorated.run();
                    // 装饰器执行后应还原为worker原有值
                    assertThat(ScopeContext.getScopeId()).isEqualTo("worker-previous");
                } finally {
                    ScopeContext.clear();
                }
            });
            worker.start();
            latch.await(2, TimeUnit.SECONDS);

            // 异步任务内部应看到主线程捕获的scopeId
            assertThat(afterExecutionScopeId.get()).isEqualTo("scope-main");
            // 主线程scopeId不受影响
            assertThat(ScopeContext.getScopeId()).isEqualTo("scope-main");
        }
    }
}
