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
package com.yangqiongai.ai.agent.data.skill;

import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import com.yangqiongai.ai.agent.data.skill.repository.DefaultSkillCategoryRepository;
import com.yangqiongai.ai.agent.data.skill.repository.DefaultSkillConfigRepository;
import com.yangqiongai.ai.agent.data.skill.repository.DefaultSkillGenDraftRepository;
import com.yangqiongai.ai.agent.data.skill.repository.DefaultSkillUsageRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillCategoryRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillGenDraftRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillUsageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent技能数据层自动配置
 * @author yangqiong
 */
@Configuration
public class AiAgentSkillDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SkillConfigRepository.class)
    public SkillConfigRepository skillConfigRepository() {
        return new DefaultSkillConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SkillUsageRepository.class)
    public SkillUsageRepository skillUsageRepository(SkillProperties properties) {
        return new DefaultSkillUsageRepository(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SkillGenDraftRepository.class)
    public SkillGenDraftRepository skillGenDraftRepository() {
        return new DefaultSkillGenDraftRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SkillCategoryRepository.class)
    public SkillCategoryRepository skillCategoryRepository() {
        return new DefaultSkillCategoryRepository();
    }
}