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
package com.yangqiongai.ai.trust.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.trust.audit.ActionAuditGuard;
import com.yangqiongai.ai.trust.audit.ActionAuditProperties;
import com.yangqiongai.ai.trust.audit.ActionAuditService;
import com.yangqiongai.ai.trust.audit.ActionAuditServiceImpl;
import com.yangqiongai.ai.trust.audit.mapper.AgentActionLogMapper;
import com.yangqiongai.ai.trust.identity.AgentIdentityProperties;
import com.yangqiongai.ai.trust.identity.AgentIdentityRepository;
import com.yangqiongai.ai.trust.identity.AgentIdentityService;
import com.yangqiongai.ai.trust.identity.AgentIdentityServiceImpl;
import com.yangqiongai.ai.trust.identity.DefaultAgentIdentityRepository;
import com.yangqiongai.ai.trust.profile.PermissionProfileService;
import com.yangqiongai.ai.trust.profile.PermissionProfileServiceImpl;
import com.yangqiongai.ai.trust.profile.mapper.AgentPermissionProfileMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 信任底座自动配置
 * <p>
 * 装配Agent身份、权限画像与动作审计能力；开放凭证鉴权为企业专属能力，
 * 由企业版yangqiong-ai-open-enterprise模块装配，社区版不包含。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass(BaseMapper.class)
@ConditionalOnProperty(prefix = "ai.trust", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({AgentIdentityProperties.class, ActionAuditProperties.class})
public class TrustAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActionAuditService actionAuditService(ActionAuditProperties actionAuditProperties,
                                                 AgentActionLogMapper actionLogMapper) {
        return new ActionAuditServiceImpl(actionAuditProperties, actionLogMapper);
    }

    /**
     * 工具调用审计守卫（平台侧经AbstractAgentProcessor自动收集挂入引擎）
     * @param actionAuditService
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.trust.action-audit", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ActionAuditGuard actionAuditGuard(ActionAuditService actionAuditService) {
        return new ActionAuditGuard(actionAuditService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentIdentityRepository agentIdentityRepository() {
        return new DefaultAgentIdentityRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentIdentityService agentIdentityService(AgentIdentityProperties identityProperties) {
        return new AgentIdentityServiceImpl(identityProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionProfileService permissionProfileService(AgentPermissionProfileMapper profileMapper) {
        return new PermissionProfileServiceImpl(profileMapper);
    }
}
