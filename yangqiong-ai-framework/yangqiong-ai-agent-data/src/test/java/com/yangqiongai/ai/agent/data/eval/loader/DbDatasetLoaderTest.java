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
package com.yangqiongai.ai.agent.data.eval.loader;

import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 数据库评测数据集加载测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DbDatasetLoaderTest {

    @Mock
    private EvalDatasetRepository evalDatasetRepository;

    private DbDatasetLoader loader;

    @BeforeEach
    void setUp() {
        loader = new DbDatasetLoader(evalDatasetRepository);
    }

    private EvalDatasetEntity dataset(Long id, String code) {
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setId(id);
        entity.setDatasetCode(code);
        entity.setName("面试筛选数据集");
        entity.setDescription("用于发布门禁");
        return entity;
    }

    @Test
    void 按编码加载并转换为Golden数据集() {
        when(evalDatasetRepository.findDatasetByCode("recruit")).thenReturn(dataset(1L, "recruit"));
        EvalDatasetCaseEntity withBody = new EvalDatasetCaseEntity();
        withBody.setCaseNo("c1");
        withBody.setQueryText("你好");
        withBody.setExpectedOutput("您好");
        withBody.setBodyJson("{\"agentCode\":\"interviewer\"}");
        withBody.setScoringCriteria("contains_all");
        EvalDatasetCaseEntity withoutBody = new EvalDatasetCaseEntity();
        withoutBody.setCaseNo("c2");
        withoutBody.setQueryText("再见");
        withoutBody.setExpectedOutput("再见");
        withoutBody.setScoringCriteria("");
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(withBody, withoutBody));

        GoldenDataset result = loader.load("recruit");

        assertThat(result.getDatasetId()).isEqualTo("recruit");
        assertThat(result.getName()).isEqualTo("面试筛选数据集");
        assertThat(result.getCases()).hasSize(2);
        assertThat(result.getCases().get(0).getCaseId()).isEqualTo("c1");
        assertThat(result.getCases().get(0).getQuery()).isEqualTo("你好");
        assertThat(result.getCases().get(0).getScoringCriteria()).isEqualTo("contains_all");
        assertThat(result.getCases().get(0).getBody()).containsEntry("agentCode", "interviewer");
        assertThat(result.getCases().get(1).getBody()).isNull();
    }

    @Test
    void 数据集不存在时抛出异常() {
        when(evalDatasetRepository.findDatasetByCode("nope")).thenReturn(null);

        assertThatThrownBy(() -> loader.load("nope"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Dataset not found");
    }

    @Test
    void bodyJson非法时抛出异常并携带用例编号() {
        when(evalDatasetRepository.findDatasetByCode("recruit")).thenReturn(dataset(1L, "recruit"));
        EvalDatasetCaseEntity badBody = new EvalDatasetCaseEntity();
        badBody.setCaseNo("bad");
        badBody.setBodyJson("{invalid");
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(badBody));

        assertThatThrownBy(() -> loader.load("recruit"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bad");
    }

    @Test
    void bodyJson空白时body为空() {
        when(evalDatasetRepository.findDatasetByCode("recruit")).thenReturn(dataset(1L, "recruit"));
        EvalDatasetCaseEntity blankBody = new EvalDatasetCaseEntity();
        blankBody.setCaseNo("c1");
        blankBody.setBodyJson("   ");
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(blankBody));

        GoldenDataset result = loader.load("recruit");

        assertThat(result.getCases().get(0).getBody()).isNull();
    }

    @Test
    void 从JSON字符串加载数据集() {
        String json = "{\"datasetId\":\"ds1\",\"name\":\"n\",\"description\":\"d\",\"cases\":"
                + "[{\"caseId\":\"c1\",\"query\":\"q\",\"expectedOutput\":\"e\",\"body\":{\"k\":1}}]}";

        GoldenDataset result = loader.loadFromJson(json);

        assertThat(result.getDatasetId()).isEqualTo("ds1");
        assertThat(result.getCases()).hasSize(1);
        assertThat(result.getCases().get(0).getBody()).containsEntry("k", 1);
    }

    @Test
    void 从非法JSON加载时抛出异常() {
        assertThatThrownBy(() -> loader.loadFromJson("{invalid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void 用例体解析为Map类型() {
        when(evalDatasetRepository.findDatasetByCode("recruit")).thenReturn(dataset(1L, "recruit"));
        EvalDatasetCaseEntity entity = new EvalDatasetCaseEntity();
        entity.setCaseNo("c1");
        entity.setBodyJson("{\"nested\":{\"a\":2}}");
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(entity));

        GoldenDataset result = loader.load("recruit");

        Map<String, Object> body = result.getCases().get(0).getBody();
        assertThat(body).containsKey("nested");
    }
}
