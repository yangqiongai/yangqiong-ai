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
package com.yangqiongai.ai.agent.core.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CancellationToken单元测试
 */
class CancellationTokenTest {

    private CancellationToken token;

    @BeforeEach
    void setUp() {
        token = new CancellationToken();
    }

    @Test
    @DisplayName("初始状态未取消")
    void initiallyNotCancelled() {
        assertThat(token.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("cancel()设置cancelled为true")
    void cancel_setsCancelledToTrue() {
        token.cancel();

        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("cancel()触发onCancel回调")
    void cancel_triggersOnCancelCallback() {
        AtomicInteger counter = new AtomicInteger(0);
        token.onCancel(counter::incrementAndGet);

        token.cancel();

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("多次cancel()只触发回调一次")
    void multipleCancelCalls_onlyTriggerCallbackOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        token.onCancel(counter::incrementAndGet);

        token.cancel();
        token.cancel();
        token.cancel();

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancel后注册onCancel仍会触发")
    void onCancel_registeredAfterCancel_stillFires() {
        AtomicInteger counter = new AtomicInteger(0);
        token.cancel();

        token.onCancel(counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelAfter()超时后触发取消")
    void cancelAfter_triggersCancellationAfterDelay() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        token.onCancel(counter::incrementAndGet);

        token.cancelAfter(Duration.ofMillis(100));

        assertThat(token.isCancelled()).isFalse();
        assertThat(counter.get()).isEqualTo(0);

        TimeUnit.MILLISECONDS.sleep(250);

        assertThat(token.isCancelled()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancel()设置取消原因")
    void cancel_withReason_setsReason() {
        token.cancel("手动取消");

        assertThat(token.reason()).isEqualTo("手动取消");
    }

    @Test
    @DisplayName("cancelAfter()超时原因包含超时信息")
    void cancelAfter_timeoutReason_containsTimeoutInfo() throws InterruptedException {
        token.cancelAfter(Duration.ofMillis(50));

        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(token.reason()).contains("超时");
    }
}
