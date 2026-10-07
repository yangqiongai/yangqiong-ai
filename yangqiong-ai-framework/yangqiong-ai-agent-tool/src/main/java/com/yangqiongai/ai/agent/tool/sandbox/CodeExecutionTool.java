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
package com.yangqiongai.ai.agent.tool.sandbox;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 代码执行工具
 * @author yangqiong
 */
@Component
public class CodeExecutionTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionTool.class);

    @Autowired
    private SandboxHttpClient sandboxHttpClient;

    /**
     * 执行沙箱代码
     * @param language
     * @param sourceCode
     * @return
     */
    @AgentTool("执行沙箱代码，支持多种编程语言的代码执行")
    public String runCode(@AgentToolParam("编程语言，如 python、java、javascript 等") String language, @AgentToolParam("要执行的源代码") String sourceCode) {
        log.debug("执行沙箱代码: language={}", language);
        return sandboxHttpClient.submitCode(language, sourceCode);
    }
}
