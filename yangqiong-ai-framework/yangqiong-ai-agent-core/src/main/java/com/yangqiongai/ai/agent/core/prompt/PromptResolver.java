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
package com.yangqiongai.ai.agent.core.prompt;

import java.util.Map;

/**
 * 提示词解析器
 * @author yangqiong
 */
public interface PromptResolver {

    /**
     * 解析提示词，解析不到时返回默认提示词
     * @param promptCode
     * @param defaultContent
     * @return
     */
    String resolve(String promptCode, String defaultContent);

    /**
     * 解析提示词并渲染变量，解析不到时返回默认提示词
     * @param promptCode
     * @param defaultContent
     * @param variables
     * @return
     */
    String resolve(String promptCode, String defaultContent, Map<String, Object> variables);

    /**
     * 上报提示词调用结果，供A/B测试等场景采集成功与耗时
     * 默认空实现，业务模块在LLM调用后可上报，具体实现由A/B测试模块接管
     * @param promptCode
     * @param sessionId
     * @param success
     * @param latencyMs
     */
    default void recordPromptOutcome(String promptCode, String sessionId, boolean success, long latencyMs) {
    }
}
