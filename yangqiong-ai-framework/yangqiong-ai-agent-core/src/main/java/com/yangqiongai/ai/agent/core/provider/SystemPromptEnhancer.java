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
package com.yangqiongai.ai.agent.core.provider;

/**
 * 系统提示词增强器
 * <p>
 * SPI接口，允许外部模块在Agent执行前增强系统提示词。
 * 通过Spring的{@code @Autowired(required = false)}注入，当无实现时降级为原始提示词。
 * </p>
 * @author yangqiong
 */
public interface SystemPromptEnhancer {

    /**
     * 增强系统提示词
     * @param agentCode
     * @param userInput
     * @param originalPrompt
     * @return 增强后的系统提示词，如无需增强则返回originalPrompt
     */
    String enhance(String agentCode, String userInput, String originalPrompt);
}
