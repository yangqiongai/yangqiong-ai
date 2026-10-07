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
package com.yangqiongai.ai.platform.api.llm;

import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 模型连通性测试
 * @author yangqiong
 */
@Component
public class ModelConnectivityTester {

    /**
     * OpenAI兼容对话接口路径
     */
    private static final String CHAT_PATH = "/chat/completions";

    /**
     * OpenAI兼容向量接口路径
     */
    private static final String EMBEDDING_PATH = "/embeddings";

    private final RestClient restClient;

    public ModelConnectivityTester() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 测试模型连通性（OpenAI兼容协议，按模型类型走对话或向量接口，支持未保存的表单配置）
     * @param modelInfo
     * @return
     */
    public Map<String, Object> test(ModelInfo modelInfo) {
        String endpoint = modelInfo.getApiEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return fail("接口地址为空，无法测试");
        }
        boolean embedding = isEmbedding(modelInfo);
        String url = endpoint.replaceAll("/+$", "") + (embedding ? EMBEDDING_PATH : CHAT_PATH);
        String modelName = StringUtils.hasText(modelInfo.getModelName())
                ? modelInfo.getModelName() : modelInfo.getModelCode();
        Map<String, Object> body = embedding
                ? Map.of("model", modelName, "input", "连接测试")
                : Map.of("model", modelName,
                    "messages", List.of(Map.of("role", "user", "content", "连接测试，请只回复OK")),
                    "max_tokens", 8, "stream", false);
        long start = System.currentTimeMillis();
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(url);
            if (StringUtils.hasText(modelInfo.getApiKey())) {
                spec = spec.header("Authorization", "Bearer " + modelInfo.getApiKey());
            }
            ResponseEntity<Map> response = spec.body(body).retrieve().toEntity(Map.class);
            long cost = System.currentTimeMillis() - start;
            if (!response.getStatusCode().is2xxSuccessful()) {
                return fail("HTTP " + response.getStatusCode().value());
            }
            return success(cost, extractReply(response.getBody(), embedding));
        } catch (RestClientResponseException e) {
            return fail("HTTP " + e.getStatusCode().value() + "：" + abbreviate(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return fail(StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * 判断是否向量模型
     * @param modelInfo
     * @return
     */
    private boolean isEmbedding(ModelInfo modelInfo) {
        return modelInfo.getModelType() != null
                && modelInfo.getModelType().toUpperCase().contains("EMBED");
    }

    /**
     * 从响应中提取回复预览（对话取首个回答，向量取维度）
     * @param body
     * @param embedding
     * @return
     */
    @SuppressWarnings("unchecked")
    private String extractReply(Map<String, Object> body, boolean embedding) {
        try {
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (embedding) {
                if (data != null && !data.isEmpty()) {
                    List<Object> vector = (List<Object>) data.get(0).get("embedding");
                    return "向量维度 " + (vector != null ? vector.size() : 0);
                }
                return "返回成功";
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                Object content = message != null ? message.get("content") : null;
                if (content != null && !String.valueOf(content).isBlank()) {
                    String text = String.valueOf(content).trim();
                    return text.length() > 50 ? text.substring(0, 50) + "…" : text;
                }
            }
            return "返回成功";
        } catch (Exception e) {
            return "返回成功";
        }
    }

    /**
     * 截断错误响应体，避免弹窗内容过长
     * @param text
     * @return
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "未知错误";
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }

    private Map<String, Object> success(long cost, String reply) {
        return Map.of("success", true, "latencyMs", cost, "reply", reply == null ? "" : reply);
    }

    private Map<String, Object> fail(String error) {
        return Map.of("success", false, "error", error == null ? "未知错误" : error);
    }
}
