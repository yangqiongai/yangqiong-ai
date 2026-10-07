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
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalDatasetCaseMapper;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalDatasetMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测数据集存储测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultEvalDatasetRepositoryTest {

    @Mock
    private EvalDatasetMapper evalDatasetMapper;

    @Mock
    private EvalDatasetCaseMapper evalDatasetCaseMapper;

    private DefaultEvalDatasetRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EvalDatasetEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvalDatasetCaseEntity.class);
        repository = new DefaultEvalDatasetRepository();
        java.lang.reflect.Field datasetField = DefaultEvalDatasetRepository.class.getDeclaredField("evalDatasetMapper");
        datasetField.setAccessible(true);
        datasetField.set(repository, evalDatasetMapper);
        java.lang.reflect.Field caseField = DefaultEvalDatasetRepository.class.getDeclaredField("evalDatasetCaseMapper");
        caseField.setAccessible(true);
        caseField.set(repository, evalDatasetCaseMapper);
    }

    private EvalDatasetEntity dataset(Long id, String code) {
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setId(id);
        entity.setDatasetCode(code);
        entity.setName("数据集A");
        entity.setCaseCount(2);
        return entity;
    }

    private EvalDatasetCaseEntity caseEntity(Long datasetId, String caseNo) {
        EvalDatasetCaseEntity entity = new EvalDatasetCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setCaseNo(caseNo);
        return entity;
    }

    @Test
    void 保存数据集回填主键() {
        EvalDatasetEntity entity = dataset(null, "recruit");
        when(evalDatasetMapper.insert(entity)).thenReturn(1);

        EvalDatasetEntity result = repository.saveDataset(entity);

        assertThat(result).isSameAs(entity);
        verify(evalDatasetMapper).insert(entity);
    }

    @Test
    void 更新数据集委托updateById() {
        EvalDatasetEntity entity = dataset(1L, "recruit");
        when(evalDatasetMapper.updateById(entity)).thenReturn(1);

        repository.updateDataset(entity);

        verify(evalDatasetMapper).updateById(entity);
    }

    @Test
    void 按ID与编码查询() {
        when(evalDatasetMapper.selectById(1L)).thenReturn(dataset(1L, "recruit"));
        when(evalDatasetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dataset(2L, "other"));

        assertThat(repository.findDatasetById(1L).getDatasetCode()).isEqualTo("recruit");
        assertThat(repository.findDatasetByCode("other").getId()).isEqualTo(2L);
    }

    @Test
    void 分页查询与统计委托Mapper() {
        when(evalDatasetMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(dataset(1L, "a")));
        when(evalDatasetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThat(repository.findDatasets("数据", "ENABLED", 0, 10)).hasSize(1);
        assertThat(repository.countDatasets(null, null)).isEqualTo(3L);
    }

    @Test
    void 删除数据集级联删除用例() {
        when(evalDatasetCaseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
        when(evalDatasetMapper.deleteById(1L)).thenReturn(1);

        repository.deleteDataset(1L);

        verify(evalDatasetCaseMapper).delete(any(LambdaQueryWrapper.class));
        verify(evalDatasetMapper).deleteById(1L);
    }

    @Test
    void 查询用例按编号排序() {
        when(evalDatasetCaseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(caseEntity(1L, "c1"), caseEntity(1L, "c2")));

        List<EvalDatasetCaseEntity> cases = repository.findCases(1L);

        assertThat(cases).hasSize(2);
    }

    @Test
    void 全量替换用例并刷新冗余数() {
        when(evalDatasetCaseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
        when(evalDatasetCaseMapper.insert(any(EvalDatasetCaseEntity.class))).thenReturn(1);
        when(evalDatasetMapper.selectById(1L)).thenReturn(dataset(1L, "recruit"));
        when(evalDatasetMapper.updateById(any(EvalDatasetEntity.class))).thenReturn(1);

        int count = repository.replaceCases(1L, List.of(caseEntity(1L, "c1"), caseEntity(1L, "c2")));

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCaseCount()).isEqualTo(2);
    }

    @Test
    void 空用例列表替换后冗余数归零() {
        when(evalDatasetCaseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(evalDatasetMapper.selectById(1L)).thenReturn(dataset(1L, "recruit"));
        when(evalDatasetMapper.updateById(any(EvalDatasetEntity.class))).thenReturn(1);

        int count = repository.replaceCases(1L, null);

        assertThat(count).isZero();
        verify(evalDatasetCaseMapper, never()).insert(any(EvalDatasetCaseEntity.class));
        ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
        verify(evalDatasetMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCaseCount()).isZero();
    }

    @Test
    void 替换用例时数据集不存在不报错() {
        when(evalDatasetCaseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(evalDatasetCaseMapper.insert(any(EvalDatasetCaseEntity.class))).thenReturn(1);
        when(evalDatasetMapper.selectById(9L)).thenReturn(null);

        int count = repository.replaceCases(9L, List.of(caseEntity(9L, "c1")));

        assertThat(count).isEqualTo(1);
        verify(evalDatasetMapper, never()).updateById(any(EvalDatasetEntity.class));
    }

    @Test
    void 多条用例逐条插入() {
        when(evalDatasetCaseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(evalDatasetCaseMapper.insert(any(EvalDatasetCaseEntity.class))).thenReturn(1);
        when(evalDatasetMapper.selectById(1L)).thenReturn(dataset(1L, "recruit"));

        repository.replaceCases(1L, List.of(caseEntity(1L, "c1"), caseEntity(1L, "c2"), caseEntity(1L, "c3")));

        verify(evalDatasetCaseMapper, times(3)).insert(any(EvalDatasetCaseEntity.class));
    }
}
