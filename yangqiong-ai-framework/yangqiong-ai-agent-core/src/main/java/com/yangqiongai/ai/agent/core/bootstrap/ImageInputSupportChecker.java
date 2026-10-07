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
package com.yangqiongai.ai.agent.core.bootstrap;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.content.ImageInputBlock;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.llm.LlmModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 图片输入能力校验
 * @author yangqiong
 */
@Service
public class ImageInputSupportChecker {

    private static final Logger log = LoggerFactory.getLogger(ImageInputSupportChecker.class);

    @Autowired
    private AgentBootstrapService agentBootstrapService;

    @Autowired
    private LlmModelService llmModelService;

    /**
     * 校验图片输入与模型支持能力，模型未开启图片支持时拒绝请求
     * @param context
     */
    public void check(AgentContext context) {
        AgentRequest request = context.getRequest();
        if (request == null || request.getInput() == null || request.getInput().isEmpty()) {
            return;
        }
        boolean hasImage = request.getInput().stream().anyMatch(b -> b instanceof ImageInputBlock);
        if (!hasImage) {
            return;
        }
        String modelCode = agentBootstrapService.resolveModelCode(request);
        boolean supported = llmModelService.findByModelCode(modelCode)
                .map(m -> m.getSupportImage() != null && m.getSupportImage() == 1)
                .orElse(false);
        if (!supported) {
            log.warn("请求包含图片输入但模型不支持: modelCode={}", modelCode);
            throw new AiException(AiErrorCode.AGENT_INPUT_BLOCKED, "当前模型不支持图片输入");
        }
    }
}
