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

import com.yangqiongai.ai.agent.core.executor.PendingResumeStore;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 暂停恢复登记存储后置处理器
 * <p>
 * 将容器内底层PendingResumeStore包装为交互装饰器，挂起登记时联动交互策略与监听器，
 * 避免与底层存储的条件装配（ConditionalOnMissingBean）产生替换冲突。
 * </p>
 * @author yangqiong
 */
public class OpenInteractionStorePostProcessor implements BeanPostProcessor {

    private final OpenInteractionPolicy policy;

    public OpenInteractionStorePostProcessor(OpenInteractionPolicy policy) {
        this.policy = policy;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof PendingResumeStore store && !(bean instanceof OpenInteractionPendingResumeStore)) {
            return new OpenInteractionPendingResumeStore(store, policy);
        }
        return bean;
    }
}
