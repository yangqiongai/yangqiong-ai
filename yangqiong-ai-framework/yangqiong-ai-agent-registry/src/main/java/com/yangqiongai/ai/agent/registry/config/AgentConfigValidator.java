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
package com.yangqiongai.ai.agent.registry.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.util.AgentOutputSchemaValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Agent配置校验器
 * <p>
 * 校验 agent_config ≡ config_json 的统一契约：
 * model必填、maxIterations 1-50、temperature 0-2、tools/skills字符串数组、
 * knowledgeBase需kbCode/kbCodes、bindingMode append/replace。
 * </p>
 * @author yangqiong
 */
public final class AgentConfigValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_ITERATIONS_MIN = 1;

    private static final int MAX_ITERATIONS_MAX = 50;

    private static final double TEMPERATURE_MIN = 0.0;

    private static final double TEMPERATURE_MAX = 2.0;

    private AgentConfigValidator() {
    }

    /**
     * 校验配置JSON，非法抛AiException
     * @param configJson
     */
    public static void validate(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw paramError("configJson不能为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(configJson);
        } catch (Exception e) {
            throw paramError("configJson不是合法JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw paramError("configJson必须是JSON对象");
        }

        JsonNode model = root.get("model");
        if (model == null || !model.isTextual() || model.asText().isBlank()) {
            throw paramError("model必填且必须是字符串");
        }

        JsonNode maxIterations = root.get("maxIterations");
        if (maxIterations != null && !maxIterations.isNull()) {
            if (!maxIterations.isInt() || maxIterations.asInt() < MAX_ITERATIONS_MIN
                    || maxIterations.asInt() > MAX_ITERATIONS_MAX) {
                throw paramError("maxIterations必须是1-50之间的整数");
            }
        }

        JsonNode temperature = root.get("temperature");
        if (temperature != null && !temperature.isNull()) {
            if (!temperature.isNumber() || temperature.asDouble() < TEMPERATURE_MIN
                    || temperature.asDouble() > TEMPERATURE_MAX) {
                throw paramError("temperature必须是0-2之间的数值");
            }
        }

        checkStringArray(root, "tools");
        checkStringArray(root, "skills");

        JsonNode knowledgeBase = root.get("knowledgeBase");
        if (knowledgeBase != null && !knowledgeBase.isNull()) {
            // 数组格式：[{"kbCode":"x","kbName":"名称"}]，逐项校验kbCode必填
            if (knowledgeBase.isArray()) {
                if (knowledgeBase.size() == 0) {
                    throw paramError("knowledgeBase数组不能为空");
                }
                for (JsonNode item : knowledgeBase) {
                    if (!item.isObject()) {
                        throw paramError("knowledgeBase数组项必须是JSON对象");
                    }
                    JsonNode itemKbCode = item.get("kbCode");
                    if (itemKbCode == null || !itemKbCode.isTextual() || itemKbCode.asText().isBlank()) {
                        throw paramError("knowledgeBase数组项必须包含kbCode");
                    }
                }
            } else if (knowledgeBase.isObject()) {
                // 兼容旧格式：{"kbCode"|"kbCodes","topK"}
                JsonNode kbCode = knowledgeBase.get("kbCode");
                JsonNode kbCodes = knowledgeBase.get("kbCodes");
                boolean hasKbCode = kbCode != null && kbCode.isTextual() && !kbCode.asText().isBlank();
                boolean hasKbCodes = kbCodes != null && kbCodes.isArray() && kbCodes.size() > 0;
                if (!hasKbCode && !hasKbCodes) {
                    throw paramError("knowledgeBase必须包含kbCode或非空kbCodes数组");
                }
            } else {
                throw paramError("knowledgeBase必须是JSON对象或对象数组");
            }
        }

        JsonNode bindingMode = root.get("bindingMode");
        if (bindingMode != null && !bindingMode.isNull()) {
            if (!bindingMode.isTextual()
                    || !("append".equalsIgnoreCase(bindingMode.asText()) || "replace".equalsIgnoreCase(bindingMode.asText()))) {
                throw paramError("bindingMode必须是append或replace");
            }
        }

        checkOutputSchema(root);
    }

    /**
     * 校验输出契约Schema（治理字段，配置期把关draft-07合法性）
     * @param root
     */
    private static void checkOutputSchema(JsonNode root) {
        JsonNode outputSchema = root.get("outputSchema");
        if (outputSchema == null || outputSchema.isNull()) {
            return;
        }
        if (!outputSchema.isObject()) {
            throw paramError("outputSchema必须是JSON对象");
        }
        List<String> schemaErrors = AgentOutputSchemaValidator.validateSchema(outputSchema.toString());
        if (!schemaErrors.isEmpty()) {
            throw paramError("outputSchema不合法: " + String.join("; ", schemaErrors));
        }
    }

    /**
     * 计算配置SHA-256哈希
     * @param configJson
     * @return
     */
    public static String hash(String configJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(configJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AiException(AiErrorCode.UNKNOWN, e);
        }
    }

    /**
     * 校验字段为字符串数组（允许缺省与null）
     * @param root
     * @param field
     */
    private static void checkStringArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw paramError(field + "必须是字符串数组");
        }
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw paramError(field + "必须是字符串数组");
            }
        }
    }

    /**
     * 构造参数错误异常
     * @param message
     * @return
     */
    private static AiException paramError(String message) {
        return new AiException(AiErrorCode.PARAM_ERROR.getCode(), message);
    }
}
