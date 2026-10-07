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
package com.yangqiongai.ai.trust.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.trust.profile.entity.AgentPermissionProfile;
import com.yangqiongai.ai.trust.profile.mapper.AgentPermissionProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Agent权限画像管理
 * <p>
 * 白名单即权限的降级语义：无画像、画像禁用或白名单空缺失一律放行（零破坏），
 * 配置非空清单后按精确清单判定；JSON解析失败视为不限制。
 * </p>
 * @author yangqiong
 */
public class PermissionProfileServiceImpl implements PermissionProfileService {

    private static final Logger log = LoggerFactory.getLogger(PermissionProfileServiceImpl.class);

    /**
     * 启用状态
     */
    public static final String STATUS_ENABLED = "ENABLED";

    /**
     * 禁用状态
     */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 不脱敏级别
     */
    public static final String MASK_NONE = "NONE";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentPermissionProfileMapper profileMapper;

    public PermissionProfileServiceImpl(AgentPermissionProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    @Override
    public AgentPermissionProfile getByAgentCode(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return null;
        }
        return profileMapper.selectOne(new LambdaQueryWrapper<AgentPermissionProfile>()
                .eq(AgentPermissionProfile::getAgentCode, agentCode)
                .last("limit 1"));
    }

    @Override
    public AgentPermissionProfile save(AgentPermissionProfile profile) {
        if (profile.getStatus() == null || profile.getStatus().isBlank()) {
            profile.setStatus(STATUS_ENABLED);
        }
        if (profile.getDataMaskLevel() == null || profile.getDataMaskLevel().isBlank()) {
            profile.setDataMaskLevel(MASK_NONE);
        }
        AgentPermissionProfile existing = getByAgentCode(profile.getAgentCode());
        if (existing != null) {
            profile.setId(existing.getId());
            profileMapper.updateById(profile);
        } else {
            profileMapper.insert(profile);
        }
        return profile;
    }

    @Override
    public List<AgentPermissionProfile> list(String agentCode, String status) {
        LambdaQueryWrapper<AgentPermissionProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentCode != null && !agentCode.isBlank(), AgentPermissionProfile::getAgentCode, agentCode);
        wrapper.eq(status != null && !status.isBlank(), AgentPermissionProfile::getStatus, status);
        wrapper.orderByDesc(AgentPermissionProfile::getId);
        return profileMapper.selectList(wrapper);
    }

    @Override
    public void toggle(Long id) {
        AgentPermissionProfile profile = profileMapper.selectById(id);
        if (profile == null) {
            return;
        }
        profile.setStatus(STATUS_DISABLED.equals(profile.getStatus()) ? STATUS_ENABLED : STATUS_DISABLED);
        profileMapper.updateById(profile);
    }

    @Override
    public boolean isToolAllowed(String agentCode, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return true;
        }
        AgentPermissionProfile profile = getByAgentCode(agentCode);
        if (!isEnforcing(profile)) {
            return true;
        }
        List<String> whitelist = parseList(profile.getToolWhitelist());
        return whitelist.isEmpty() || whitelist.contains(toolName);
    }

    @Override
    public boolean isEgressAllowed(String agentCode, String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        AgentPermissionProfile profile = getByAgentCode(agentCode);
        if (!isEnforcing(profile)) {
            return true;
        }
        List<String> whitelist = parseList(profile.getEgressWhitelist());
        if (whitelist.isEmpty()) {
            return true;
        }
        String normalizedHost = host.toLowerCase();
        for (String pattern : whitelist) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String normalizedPattern = pattern.trim().toLowerCase();
            // 精确匹配或*.example.com通配后缀匹配
            if (normalizedHost.equals(normalizedPattern)
                    || (normalizedPattern.startsWith("*.")
                    && normalizedHost.endsWith(normalizedPattern.substring(1)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 画像是否处于执行状态(存在且启用)
     * @param profile
     * @return
     */
    private boolean isEnforcing(AgentPermissionProfile profile) {
        return profile != null && !STATUS_DISABLED.equals(profile.getStatus());
    }

    /**
     * 解析JSON数组清单(解析失败视为不限制)
     * @param json
     * @return
     */
    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> items = OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            return items != null ? items : Collections.emptyList();
        } catch (Exception e) {
            log.warn("权限画像清单解析失败,视为不限制: {}", json, e);
            return Collections.emptyList();
        }
    }
}
