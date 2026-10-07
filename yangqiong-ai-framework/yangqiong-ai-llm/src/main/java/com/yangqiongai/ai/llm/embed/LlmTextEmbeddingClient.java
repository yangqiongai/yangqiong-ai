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
package com.yangqiongai.ai.llm.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本嵌入客户端
 * @author yangqiong
 */
public class LlmTextEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(LlmTextEmbeddingClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final int dimensions;
    private final HttpClient httpClient;

    /**
     * 构造文本嵌入客户端
     * @param baseUrl
     * @param apiKey
     * @param modelName
     * @param dimensions
     */
    public LlmTextEmbeddingClient(String baseUrl, String apiKey, String modelName, int dimensions) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 单条文本嵌入
     * @param text
     * @return
     */
    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        if (results.isEmpty()) {
            throw new RuntimeException("嵌入结果为空, text: " + text.substring(0, Math.min(50, text.length())));
        }
        return results.get(0);
    }

    /**
     * 批量文本嵌入
     * @param texts
     * @return
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("input", texts);
            if (dimensions > 0) {
                requestBody.put("dimensions", dimensions);
            }

            String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/v1/embeddings"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("嵌入API调用失败, statusCode: {}, body: {}", response.statusCode(),
                        response.body().substring(0, Math.min(500, response.body().length())));
                throw new RuntimeException("嵌入API调用失败, statusCode: " + response.statusCode());
            }

            return parseEmbeddingResponse(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("嵌入请求异常", e);
            throw new RuntimeException("嵌入请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析嵌入API响应
     * @param responseBody
     * @return
     */
    private List<float[]> parseEmbeddingResponse(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                throw new RuntimeException("嵌入响应格式异常, 缺少data字段");
            }

            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode item : dataNode) {
                JsonNode embeddingNode = item.get("embedding");
                if (embeddingNode == null || !embeddingNode.isArray()) {
                    throw new RuntimeException("嵌入响应格式异常, 缺少embedding字段");
                }
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(vector);
            }
            return embeddings;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析嵌入响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取向量维度
     * @return
     */
    @Override
    public int getDimensions() {
        return dimensions;
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
