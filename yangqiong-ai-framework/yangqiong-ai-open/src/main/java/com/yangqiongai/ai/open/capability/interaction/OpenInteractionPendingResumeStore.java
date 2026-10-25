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
package com.yangqiongai.ai.open.capability.interaction;

import com.yangqiongai.ai.agent.core.executor.PendingResumeEntry;
import com.yangqiongai.ai.agent.core.executor.PendingResumeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 暂停恢复登记存储装饰器
 * <p>
 * 包装底层PendingResumeStore，在登记挂起现场时回调交互策略（关闭交互的调用即时截止），
 * 并支持注册开启交互场景的监听器（交互增强按sessionId+resumeType做登记联动）。
 * </p>
 * @author yangqiong
 */
public class OpenInteractionPendingResumeStore implements PendingResumeStore {

    private static final Logger log = LoggerFactory.getLogger(OpenInteractionPendingResumeStore.class);

    private final PendingResumeStore delegate;

    private final OpenInteractionPolicy policy;

    /**
     * 挂起登记监听器（开启交互场景的联动登记入口）
     */
    private final List<Consumer<PendingResumeEntry>> registerListeners = new CopyOnWriteArrayList<>();

    public OpenInteractionPendingResumeStore(PendingResumeStore delegate, OpenInteractionPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    /**
     * 注册挂起登记监听器
     * @param listener
     */
    public void addRegisterListener(Consumer<PendingResumeEntry> listener) {
        registerListeners.add(listener);
    }

    @Override
    public void register(PendingResumeEntry entry) {
        if (entry != null) {
            try {
                // 先回调关闭语义兜底（先于执行线程收尾，保证任务失败态不被完成态覆盖）
                policy.onSuspendRegistered(entry.getSessionId());
                for (Consumer<PendingResumeEntry> listener : registerListeners) {
                    listener.accept(entry);
                }
            } catch (Exception e) {
                log.warn("挂起登记联动处理失败: sessionId={}, requestId={}", entry.getSessionId(),
                        entry.getRequestId(), e);
            }
        }
        delegate.register(entry);
    }

    @Override
    public PendingResumeEntry pop(String requestId) {
        return delegate.pop(requestId);
    }

    @Override
    public boolean exists(String requestId) {
        return delegate.exists(requestId);
    }

    @Override
    public List<PendingResumeEntry> listActive() {
        return delegate.listActive();
    }
}
