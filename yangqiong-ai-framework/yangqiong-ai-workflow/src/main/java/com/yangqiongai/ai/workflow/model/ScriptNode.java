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
package com.yangqiongai.ai.workflow.model;

import java.util.Map;

/**
 * 脚本/表达式执行节点
 * <p>
 * 支持的脚本类型（scriptType）：
 * - SPEL: Spring Expression Language，可调用Spring Bean、访问变量
 *   config: {"expression": "#input.toUpperCase() + '!'"} 或 {"expression": "@myBean.process(#input)"}
 * - JS: JavaScript（通过ScriptEngine）
 *   config: {"script": "var s = input; return s.substring(1);"}
 * - GROOVY: Groovy脚本（如果classpath中有Groovy）
 *   config: {"script": "def s = input; s.reverse()"}
 * <p>
 * 变量绑定：
 * - SPEL: 工作流变量作为 #varName 访问，Bean用 @beanName 访问
 * - JS/Groovy: 工作流变量直接作为脚本局部变量注入
 *
 * @author yangqiong
 */
public class ScriptNode extends WorkflowNode {

    /**
     * 获取脚本类型（SPEL/JS/GROOVY）
     */
    public String getScriptType() {
        return getConfigString("scriptType");
    }

    /**
     * 设置脚本类型
     */
    public void setScriptType(String scriptType) {
        setConfigValue("scriptType", scriptType);
    }

    /**
     * 获取表达式/脚本内容
     * SPEL用expression，JS/Groovy用script
     */
    public String getExpression() {
        String expr = getConfigString("expression");
        if (expr == null) {
            expr = getConfigString("script");
        }
        return expr;
    }

    /**
     * 设置表达式
     */
    public void setExpression(String expression) {
        setConfigValue("expression", expression);
    }

    /**
     * 设置脚本内容
     */
    public void setScript(String script) {
        setConfigValue("script", script);
    }

    /**
     * 获取输出变量名（兼容旧配置，优先使用outputMappings）
     */
    public String getOutputVar() {
        return getConfigString("outputVar");
    }

    /**
     * 设置输出变量名
     */
    public void setOutputVar(String outputVar) {
        setConfigValue("outputVar", outputVar);
    }
}
