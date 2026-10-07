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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;

/**
 * 白名单防护
 * <p>
 * 默认全不暴露：仅放行暴露白名单中启用状态的配置项。
 * </p>
 * @author yangqiong
 */
public class ExposeWhitelistGuard implements McpToolsCallGuard {

    private final McpExposeService exposeService;

    public ExposeWhitelistGuard(McpExposeService exposeService) {
        this.exposeService = exposeService;
    }

    /**
     * 校验类型+编码在启用白名单内,否则拒绝
     * @param exposeType
     * @param exposeCode
     * @return
     */
    @Override
    public void check(String exposeType, String exposeCode) {
        boolean allowed = exposeService.listEnabled(exposeType).stream()
                .anyMatch(expose -> expose.getExposeCode().equals(exposeCode));
        if (!allowed) {
            throw new AiException(AiErrorCode.FORBIDDEN.getCode(), "资源未开放: " + exposeCode);
        }
    }
}
