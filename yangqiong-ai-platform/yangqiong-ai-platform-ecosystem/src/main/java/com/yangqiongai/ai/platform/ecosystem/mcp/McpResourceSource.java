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
 * MCP资源内容来源
 * <p>
 * RESOURCE类型出口的内容提供扩展点：知识库片段/技能Markdown等资产
 * 由后续模块实现此接口注册，资源元数据仍以暴露白名单为准。
 * </p>
 * @author yangqiong
 */
public interface McpResourceSource {

    /**
     * 是否支持指定资源来源键
     * @param sourceCode
     * @return
     */
    boolean supports(String sourceCode);

    /**
     * 读取资源文本内容
     * @param sourceCode
     * @return
     */
    String read(String sourceCode);

    /**
     * 资源MIME类型(默认Markdown)
     * @param sourceCode
     * @return
     */
    default String mimeType(String sourceCode) {
        return "text/markdown";
    }
}
