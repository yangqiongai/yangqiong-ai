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

import com.yangqiongai.ai.trust.profile.entity.AgentPermissionProfile;

import java.util.List;

/**
 * Agent权限画像管理
 * @author yangqiong
 */
public interface PermissionProfileService {

    /**
     * 按Agent编码查询画像
     * @param agentCode
     * @return 无画像时返回null
     */
    AgentPermissionProfile getByAgentCode(String agentCode);

    /**
     * 保存画像(按Agent编码upsert)
     * @param profile
     * @return
     */
    AgentPermissionProfile save(AgentPermissionProfile profile);

    /**
     * 查询画像列表
     * @param agentCode
     * @param status
     * @return
     */
    List<AgentPermissionProfile> list(String agentCode, String status);

    /**
     * 启用/禁用画像
     * @param id
     * @return
     */
    void toggle(Long id);

    /**
     * 判定工具是否放行(无画像/画像禁用/白名单空缺失=放行)
     * @param agentCode
     * @param toolName
     * @return
     */
    boolean isToolAllowed(String agentCode, String toolName);

    /**
     * 判定网络出口是否放行(无画像/画像禁用/出口白名单空缺失=放行,支持*.example.com通配)
     * @param agentCode
     * @param host
     * @return
     */
    boolean isEgressAllowed(String agentCode, String host);
}
