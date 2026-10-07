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
package com.yangqiongai.ai.security.config;

import com.yangqiongai.ai.security.guardrails.GuardrailManager;
import com.yangqiongai.ai.security.spi.GuardrailRuleRepository;
import com.yangqiongai.ai.security.spi.TriggerAuditStore;
import com.yangqiongai.ai.security.store.InMemoryGuardrailRuleRepository;
import com.yangqiongai.ai.security.store.InMemoryTriggerAuditStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 护栏自动装配（社区默认内存实现，商业规则中心/触发审计以@Primary覆盖）
 *
 * @author yangqiong
 */
@AutoConfiguration
public class GuardrailAutoConfiguration {

    /**
     * 注册护栏动态管理器
     * @param
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public GuardrailManager guardrailManager() {
        return new GuardrailManager();
    }

    /**
     * 注册护栏规则内存仓库（社区默认实现）
     * @param
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(GuardrailRuleRepository.class)
    public GuardrailRuleRepository inMemoryGuardrailRuleRepository() {
        return new InMemoryGuardrailRuleRepository();
    }

    /**
     * 注册触发审计内存存储（社区默认实现）
     * @param
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(TriggerAuditStore.class)
    public TriggerAuditStore inMemoryTriggerAuditStore() {
        return new InMemoryTriggerAuditStore();
    }
}
