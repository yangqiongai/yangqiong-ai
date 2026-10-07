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

/**
 * MCP工具调用防护
 * <p>
 * 调用链防护的扩展点：凭证鉴权由 T10 过滤器在 /mcp/** 路径统一承担，
 * 本防护在工具/提示词/资源分发前执行白名单与画像校验（画像校验 M6 后由企业版增强注入）。
 * </p>
 * @author yangqiong
 */
public interface McpToolsCallGuard {

    /**
     * 校验暴露项是否允许本次调用,拒绝时抛出异常
     * @param exposeType
     * @param exposeCode
     * @return
     */
    void check(String exposeType, String exposeCode);
}
