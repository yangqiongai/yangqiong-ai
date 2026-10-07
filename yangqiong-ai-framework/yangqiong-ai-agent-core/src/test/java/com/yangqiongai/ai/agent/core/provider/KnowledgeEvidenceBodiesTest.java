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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识命中证据body工具单元测试
 * @author yangqiong
 */
@DisplayName("KnowledgeEvidenceBodies 单元测试")
class KnowledgeEvidenceBodiesTest {

    private Map<String, Object> evidence(String kbId, String kbName, String docName) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", "shell是命令行解释器");
        map.put("kbId", kbId);
        map.put("kbName", kbName);
        map.put("sourceDocId", "doc-1");
        map.put("sourceDocName", docName);
        map.put("sliceId", "slice-1");
        map.put("score", 0.72);
        return map;
    }

    @Test
    @DisplayName("fillKbNames - 新格式对象数组按kbCode回填名称")
    void fillKbNames_newFormatList() {
        List<Map<String, Object>> config = List.of(
                Map.of("kbCode", "kb-1", "kbName", "资源管理库"),
                Map.of("kbCode", "kb-2", "kbName", "命令行库")
        );
        List<Map<String, Object>> evidences = List.of(evidence("kb-2", null, "TLCL.pdf"));

        List<Map<String, Object>> result = KnowledgeEvidenceBodies.fillKbNames(evidences, config);

        assertThat(result.get(0).get("kbName")).isEqualTo("命令行库");
    }

    @Test
    @DisplayName("fillKbNames - 旧格式kbCodes与kbNames按位置对齐回填")
    void fillKbNames_oldFormatMap() {
        Map<String, Object> config = Map.of(
                "kbCodes", List.of("kb-1", "kb-2"),
                "kbNames", List.of("资源管理库", "命令行库")
        );
        List<Map<String, Object>> evidences = List.of(evidence("kb-1", null, "TLCL.pdf"));

        List<Map<String, Object>> result = KnowledgeEvidenceBodies.fillKbNames(evidences, config);

        assertThat(result.get(0).get("kbName")).isEqualTo("资源管理库");
    }

    @Test
    @DisplayName("fillKbNames - 已有名称或无匹配编码时不覆盖")
    void fillKbNames_notOverwriteExistingName() {
        Map<String, Object> config = Map.of(
                "kbCodes", List.of("kb-1"),
                "kbNames", List.of("资源管理库")
        );
        List<Map<String, Object>> evidences = List.of(
                evidence("kb-1", "已有名称", "TLCL.pdf"),
                evidence("kb-9", null, "TLCL.pdf")
        );

        List<Map<String, Object>> result = KnowledgeEvidenceBodies.fillKbNames(evidences, config);

        assertThat(result.get(0).get("kbName")).isEqualTo("已有名称");
        assertThat(result.get(1).get("kbName")).isNull();
    }

    @Test
    @DisplayName("fillKbNames - 证据为空或配置为空时原样返回")
    void fillKbNames_emptyInputs() {
        List<Map<String, Object>> evidences = List.of(evidence("kb-1", null, "TLCL.pdf"));

        assertThat(KnowledgeEvidenceBodies.fillKbNames(null, Map.of("kbCodes", List.of("kb-1")))).isEmpty();
        assertThat(KnowledgeEvidenceBodies.fillKbNames(evidences, null)).isSameAs(evidences);
        assertThat(KnowledgeEvidenceBodies.fillKbNames(List.of(), evidences)).isEmpty();
        assertThat(KnowledgeEvidenceBodies.fillKbNames(evidences, Map.of())).isSameAs(evidences);
    }

    @Test
    @DisplayName("mergeTo - 证据写入body并在已有证据基础上追加")
    void mergeTo_appendsToBody() {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> first = List.of(evidence("kb-1", null, "TLCL.pdf"));

        KnowledgeEvidenceBodies.mergeTo(body, first);
        KnowledgeEvidenceBodies.mergeTo(body, List.of(evidence("kb-2", "命令行库", "卷3.pdf")));

        List<?> merged = (List<?>) body.get(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES);
        assertThat(merged).hasSize(2);
        assertThat(((Map<?, ?>) merged.get(0)).get("kbId")).isEqualTo("kb-1");
        assertThat(((Map<?, ?>) merged.get(1)).get("kbName")).isEqualTo("命令行库");
    }

    @Test
    @DisplayName("mergeTo - 证据为空或body为空时不写入")
    void mergeTo_skipsInvalidInputs() {
        Map<String, Object> body = new HashMap<>();

        KnowledgeEvidenceBodies.mergeTo(body, null);
        KnowledgeEvidenceBodies.mergeTo(body, List.of());
        KnowledgeEvidenceBodies.mergeTo(null, List.of(evidence("kb-1", null, "TLCL.pdf")));

        assertThat(body).doesNotContainKey(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES);
    }
}
