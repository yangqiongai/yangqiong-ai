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
package com.yangqiongai.ai.agent.data.eval.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalRunCaseMapper;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalRunMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测运行存储测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultEvalRunRepositoryTest {

    @Mock
    private EvalRunMapper evalRunMapper;

    @Mock
    private EvalRunCaseMapper evalRunCaseMapper;

    private DefaultEvalRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EvalRunEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvalRunCaseEntity.class);
        repository = new DefaultEvalRunRepository();
        java.lang.reflect.Field runField = DefaultEvalRunRepository.class.getDeclaredField("evalRunMapper");
        runField.setAccessible(true);
        runField.set(repository, evalRunMapper);
        java.lang.reflect.Field caseField = DefaultEvalRunRepository.class.getDeclaredField("evalRunCaseMapper");
        caseField.setAccessible(true);
        caseField.set(repository, evalRunCaseMapper);
    }

    private EvalRunEntity run(Long id, String status) {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(id);
        entity.setDatasetId(1L);
        entity.setStatus(status);
        entity.setTotalCases(10);
        entity.setPassedCases(8);
        entity.setFailedCases(2);
        entity.setAvgScore(new BigDecimal("0.8000"));
        return entity;
    }

    private EvalRunCaseEntity runCase(Long runId, String caseNo) {
        EvalRunCaseEntity entity = new EvalRunCaseEntity();
        entity.setRunId(runId);
        entity.setCaseNo(caseNo);
        return entity;
    }

    @Test
    void 保存运行回填主键() {
        EvalRunEntity entity = run(null, "RUNNING");
        when(evalRunMapper.insert(entity)).thenReturn(1);

        EvalRunEntity result = repository.insertRun(entity);

        assertThat(result).isSameAs(entity);
        verify(evalRunMapper).insert(entity);
    }

    @Test
    void 更新运行委托updateById() {
        EvalRunEntity entity = run(1L, "PASSED");

        repository.updateRun(entity);

        verify(evalRunMapper).updateById(entity);
    }

    @Test
    void 按ID查询运行() {
        when(evalRunMapper.selectById(1L)).thenReturn(run(1L, "PASSED"));

        assertThat(repository.findRunById(1L).getStatus()).isEqualTo("PASSED");
    }

    @Test
    void 分页查询与统计委托Mapper() {
        when(evalRunMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(run(1L, "PASSED")));
        when(evalRunMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.findRuns("recruiter", "recruit", "PASSED",
                now.minusDays(1), now, 0, 10)).hasSize(1);
        assertThat(repository.countRuns(null, null, null, null, null)).isEqualTo(2L);
    }

    @Test
    void 批量保存运行用例明细() {
        when(evalRunCaseMapper.insert(any(EvalRunCaseEntity.class))).thenReturn(1);

        repository.insertRunCases(List.of(runCase(1L, "c1"), runCase(1L, "c2")));

        verify(evalRunCaseMapper, times(2)).insert(any(EvalRunCaseEntity.class));
    }

    @Test
    void 空列表批量保存不触发插入() {
        repository.insertRunCases(List.of());
        repository.insertRunCases(null);

        verify(evalRunCaseMapper, never()).insert(any(EvalRunCaseEntity.class));
    }

    @Test
    void 查询运行用例明细按编号排序() {
        when(evalRunCaseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(runCase(1L, "c1")));

        List<EvalRunCaseEntity> cases = repository.findRunCases(1L);

        assertThat(cases).hasSize(1);
    }
}
