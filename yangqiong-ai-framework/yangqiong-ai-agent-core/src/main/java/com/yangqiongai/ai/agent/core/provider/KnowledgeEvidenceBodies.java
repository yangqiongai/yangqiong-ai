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

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识命中证据body工具
 * <p>
 * 统一维护知识命中证据在请求body中的写入与合并，
 * 供agentConfig注入链路与@KnowledgeRag切面链路共用，
 * 证据在执行完成后随结果finalPayload透出。
 * </p>
 * @author yangqiong
 */
public final class KnowledgeEvidenceBodies {

    private KnowledgeEvidenceBodies() {
    }

    /**
     * 回填证据的知识库名称（从agentConfig知识库配置解析kbCode与kbName映射）
     * <p>
     * 兼容新格式对象数组[{"kbCode":"x","kbName":"名称"}]与旧格式对象{"kbCodes":[],"kbNames":[]}，
     * 仅填充证据缺失kbName的字段，避免覆盖检索层已带回的名称。
     * </p>
     * @param evidences 检索证据列表
     * @param knowledgeBase agentConfig知识库配置
     * @return
     */
    public static List<Map<String, Object>> fillKbNames(List<Map<String, Object>> evidences, Object knowledgeBase) {
        if (evidences == null || evidences.isEmpty() || knowledgeBase == null) {
            return evidences != null ? evidences : List.of();
        }
        Map<String, String> kbNameByCode = new HashMap<>();
        if (knowledgeBase instanceof List<?> kbList) {
            for (Object item : kbList) {
                if (item instanceof Map<?, ?> entry
                        && entry.get("kbCode") instanceof String code && !code.isBlank()
                        && entry.get("kbName") instanceof String name && !name.isBlank()) {
                    kbNameByCode.put(code, name);
                }
            }
        } else if (knowledgeBase instanceof Map<?, ?> kbMap) {
            List<String> codes = new ArrayList<>();
            if (kbMap.get("kbCodes") instanceof List<?> codeList) {
                for (Object c : codeList) {
                    if (c instanceof String s && !s.isBlank()) {
                        codes.add(s);
                    }
                }
            }
            if (kbMap.get("kbNames") instanceof List<?> nameList) {
                List<String> names = new ArrayList<>();
                for (Object n : nameList) {
                    if (n instanceof String s && !s.isBlank()) {
                        names.add(s);
                    }
                }
                // 按位置对齐回填名称
                for (int i = 0; i < codes.size() && i < names.size(); i++) {
                    kbNameByCode.put(codes.get(i), names.get(i));
                }
            }
        }
        if (kbNameByCode.isEmpty()) {
            return evidences;
        }
        for (Map<String, Object> evidence : evidences) {
            Object kbId = evidence.get("kbId");
            if ((evidence.get("kbName") == null || String.valueOf(evidence.get("kbName")).isBlank())
                    && kbId instanceof String code && kbNameByCode.containsKey(code)) {
                evidence.put("kbName", kbNameByCode.get(code));
            }
        }
        return evidences;
    }

    /**
     * 合并知识命中证据到请求body
     * @param body
     * @param evidences
     */
    @SuppressWarnings("unchecked")
    public static void mergeTo(Map<String, Object> body, List<Map<String, Object>> evidences) {
        if (body == null || evidences == null || evidences.isEmpty()) {
            return;
        }
        Object existing = body.get(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES);
        List<Map<String, Object>> merged = new ArrayList<>();
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    merged.add((Map<String, Object>) map);
                }
            }
        }
        merged.addAll(evidences);
        body.put(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES, merged);
    }
}
