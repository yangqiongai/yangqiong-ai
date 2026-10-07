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
package com.yangqiongai.ai.agent.skill.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 技能信任等级
 * <p>
 * BUILTIN 内置技能自动装配不过滤；其余等级需 agentConfig.skills 显式挂载或叠加来源命中。
 * 安全扫描与装配链路共用此枚举，避免双口径。
 * </p>
 * @author yangqiong
 */
public enum TrustLevel {

    /**
     * 内置技能
     */
    BUILTIN,

    /**
     * 受信任技能
     */
    TRUSTED,

    /**
     * 社区技能
     */
    COMMUNITY,

    /**
     * Agent创建的技能
     */
    AGENT_CREATED;

    private static final Logger log = LoggerFactory.getLogger(TrustLevel.class);

    /**
     * 从字符串解析信任等级，空/非法值降级COMMUNITY并告警
     * @param value
     * @return
     */
    public static TrustLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return COMMUNITY;
        }
        try {
            return TrustLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的技能信任等级: {}, 降级为COMMUNITY", value);
            return COMMUNITY;
        }
    }
}
