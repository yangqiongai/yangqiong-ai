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
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.config.ApplicationContextProvider;
import com.yangqiongai.ai.workflow.model.AssignNode;
import com.yangqiongai.ai.workflow.model.HttpNode;
import com.yangqiongai.ai.workflow.model.ScriptNode;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 脚本与HTTP节点执行
 * @author yangqiong
 */
@Service
public class ScriptHttpNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptHttpNodeExecutor.class);

    private final WorkflowStateService stateService;

    /**
     * 共享HttpClient实例
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 缓存ScriptEngineManager避免每次调用扫描ScriptEngineFactory
     */
    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    public ScriptHttpNodeExecutor(WorkflowStateService stateService) {
        this.stateService = stateService;
    }

    /**
     * 执行脚本/表达式节点
     * 支持：SPEL（Spring EL）、JS（JavaScript）、GROOVY
     * @param state
     * @param node
     * @return
     */
    public AgentResult executeScriptNode(WorkflowState state, WorkflowNode node) {
        ScriptNode scriptNode = stateService.castNode(node, ScriptNode.class);
        String scriptType = scriptNode.getScriptType();
        String expression = scriptNode.getExpression();

        if (scriptType == null || scriptType.isBlank()) {
            return AgentResult.failure("脚本节点缺少scriptType配置: " + node.getId());
        }
        if (expression == null || expression.isBlank()) {
            return AgentResult.failure("脚本节点缺少expression/script配置: " + node.getId());
        }

        try {
            Object result;
            switch (scriptType.toUpperCase()) {
                case "SPEL" -> result = executeSpelExpression(expression, state);
                case "JS" -> result = executeJsScript(expression, state);
                case "GROOVY" -> result = executeGroovyScript(expression, state);
                default -> {
                    return AgentResult.failure("不支持的脚本类型: " + scriptType);
                }
            }

            String resultStr = result != null ? result.toString() : "";
            String outputVar = scriptNode.getOutputVar();
            if (outputVar != null && !outputVar.isBlank()) {
                state.setVariable(outputVar, result);
            }
            state.setVariable(node.getId() + ".output", resultStr);
            state.setVariable(node.getId() + ".result", result);
            return AgentResult.success(resultStr);
        } catch (Exception e) {
            return AgentResult.failure("脚本执行失败[" + scriptType + "]: " + e.getMessage());
        }
    }

    /**
     * 执行SpEL表达式
     * 变量以 #varName 形式访问，Bean以 @beanName 形式访问
     * @param expression
     * @param state
     * @return
     */
    private Object executeSpelExpression(String expression, WorkflowState state) {
        ExpressionParser parser = new SpelExpressionParser();
        org.springframework.expression.Expression exp = parser.parseExpression(expression);

        StandardEvaluationContext context = new StandardEvaluationContext();

        // 注入工作流变量为SpEL变量（#varName）
        if (state.getVariables() != null) {
            for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
                if (!entry.getKey().startsWith("_")) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
        }

        // 注入Spring Bean（@beanName）
        try {
            ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
            if (applicationContext != null) {
                context.setBeanResolver(new BeanFactoryResolver(applicationContext));
            }
        } catch (Exception e) {
            log.warn("SpEL BeanResolver注入失败: {}", e.getMessage());
        }

        return exp.getValue(context);
    }

    /**
     * 执行JavaScript脚本
     * 工作流变量作为脚本局部变量注入
     * @param script
     * @param state
     * @return
     */
    private Object executeJsScript(String script, WorkflowState state) {
        ScriptEngineManager manager = scriptEngineManager;
        ScriptEngine engine = manager.getEngineByName("js");
        if (engine == null) {
            throw new RuntimeException("JavaScript引擎不可用");
        }

        // 注入工作流变量
        if (state.getVariables() != null) {
            for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
                if (!entry.getKey().startsWith("_")) {
                    engine.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 非函数脚本统一包装为立即执行函数（Nashorn 15不允许顶层return语句）
        String wrappedScript = script;
        if (!script.trim().startsWith("function")) {
            if (script.contains("return ")) {
                wrappedScript = "(function() { " + script + " })()";
            } else {
                wrappedScript = "(function() { return (" + script + "); })()";
            }
        }

        try {
            return engine.eval(wrappedScript);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行Groovy脚本
     * @param script
     * @param state
     * @return
     */
    private Object executeGroovyScript(String script, WorkflowState state) {
        ScriptEngineManager manager = scriptEngineManager;
        ScriptEngine engine = manager.getEngineByName("groovy");
        if (engine == null) {
            throw new RuntimeException("Groovy引擎不可用，请添加groovy-jsr223依赖");
        }

        // 注入工作流变量
        if (state.getVariables() != null) {
            for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
                if (!entry.getKey().startsWith("_")) {
                    engine.put(entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            return engine.eval(script);
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行HTTP请求节点
     * @param state
     * @param node
     * @return
     */
    public AgentResult executeHttpNode(WorkflowState state, WorkflowNode node) {
        HttpNode httpNode = stateService.castNode(node, HttpNode.class);
        String url = stateService.resolveTemplateString(httpNode.getUrl(), state);
        String method = httpNode.getMethod();
        int timeout = httpNode.getTimeout();

        if (url == null || url.isBlank()) {
            return AgentResult.failure("HTTP节点缺少url配置: " + node.getId());
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout));

            // 设置请求头
            Map<String, String> headers = httpNode.getHeaders();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), stateService.resolveTemplateString(entry.getValue(), state));
                }
            }

            // 设置请求体
            String body = httpNode.getBody();
            if (body != null) {
                body = stateService.resolveTemplateString(body, state);
            }

            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "DELETE" -> {
                    if (body != null) {
                        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.DELETE();
                    }
                }
                default -> {
                    return AgentResult.failure("不支持的HTTP方法: " + method);
                }
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            int statusCode = response.statusCode();

            // 写入变量
            String responseVar = httpNode.getResponseVar();
            if (responseVar != null && !responseVar.isBlank()) {
                state.setVariable(responseVar, responseBody);
            }
            state.setVariable(node.getId() + ".response", responseBody);
            state.setVariable(node.getId() + ".statusCode", statusCode);

            if (statusCode >= 200 && statusCode < 300) {
                return AgentResult.success(responseBody);
            } else {
                return AgentResult.failure("HTTP请求失败: status=" + statusCode + ", body=" +
                        (responseBody != null && responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
            }
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return AgentResult.failure("HTTP请求异常: " + reason);
        }
    }

    /**
     * 执行变量赋值/聚合节点
     * 支持：常量值、变量引用 ${varName}、SpEL表达式 #{expression}
     * @param state
     * @param node
     * @return
     */
    public AgentResult executeAssignNode(WorkflowState state, WorkflowNode node) {
        AssignNode assignNode = stateService.castNode(node, AssignNode.class);
        Map<String, String> assignments = assignNode.getAssignments();

        if (assignments == null || assignments.isEmpty()) {
            return AgentResult.success("无赋值操作");
        }

        Map<String, Object> assignedValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            String varName = entry.getKey();
            String valueExpr = entry.getValue();
            Object resolvedValue;

            if (valueExpr == null) {
                resolvedValue = null;
            } else if (valueExpr.startsWith("#{") && valueExpr.endsWith("}")) {
                // SpEL表达式
                String spelExpr = valueExpr.substring(2, valueExpr.length() - 1);
                // 先替换 ${var} 为实际值
                String resolved = stateService.resolveTemplateString(spelExpr, state);
                resolvedValue = executeSpelExpression(resolved, state);
            } else if (valueExpr.startsWith("${") && valueExpr.endsWith("}")) {
                // 变量引用
                String refVarName = valueExpr.substring(2, valueExpr.length() - 1);
                resolvedValue = state.getVariable(refVarName);
            } else {
                // 常量或混合模板：替换其中的${var}变量引用，无变量引用时原样返回
                resolvedValue = stateService.resolveTemplateString(valueExpr, state);
            }

            state.setVariable(varName, resolvedValue);
            assignedValues.put(varName, resolvedValue);
        }

        String resultStr = assignedValues.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        state.setVariable(node.getId() + ".output", resultStr);
        return AgentResult.success(resultStr);
    }
}
