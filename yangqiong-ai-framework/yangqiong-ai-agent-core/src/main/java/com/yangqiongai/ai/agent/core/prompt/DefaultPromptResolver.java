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

import com.yangqiongai.ai.common.util.PromptTemplateUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认提示词解析器
 * <p>
 * 作为 {@link PromptResolver} 的默认实现，可根据需求扩展解析逻辑：
 * 直接返回调用方传入的默认提示词，保证核心功能不受提示词管理模块缺失影响。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnMissingBean(PromptResolver.class)
public class DefaultPromptResolver implements PromptResolver {

    @Override
    public String resolve(String promptCode, String defaultContent) {
        return defaultContent;
    }

    @Override
    public String resolve(String promptCode, String defaultContent, Map<String, Object> variables) {
        return PromptTemplateUtils.render(defaultContent, variables);
    }
}
