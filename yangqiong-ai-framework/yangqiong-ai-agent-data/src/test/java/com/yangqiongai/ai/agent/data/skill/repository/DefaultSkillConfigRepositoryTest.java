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
package com.yangqiongai.ai.agent.data.skill.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.skill.entity.SkillConfigEntity;
import com.yangqiongai.ai.agent.data.skill.entity.SkillVersionEntity;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillConfigMapper;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillVersionMapper;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技能配置仓库测试（版本快照upsert与remark链路）
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSkillConfigRepository 单元测试")
class DefaultSkillConfigRepositoryTest {

    private static final String SKILL_ID = "report-writer";

    @Mock
    private SkillConfigMapper skillConfigMapper;

    @Mock
    private SkillVersionMapper skillVersionMapper;

    private DefaultSkillConfigRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SkillConfigEntity.class);
        TableInfoHelper.initTableInfo(assistant, SkillVersionEntity.class);
        repository = new DefaultSkillConfigRepository();
        inject("baseMapper", skillConfigMapper);
        inject("skillVersionMapper", skillVersionMapper);
        inject("objectMapper", new ObjectMapper());
        setField("cacheTtlMinutes", 5L);
        setField("cacheMaxSize", 1000L);
        repository.init();
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = findField(fieldName);
        field.setAccessible(true);
        field.set(repository, value);
    }

    private void setField(String fieldName, Object value) throws Exception {
        findField(fieldName).setAccessible(true);
        inject(fieldName, value);
    }

    /**
     * 沿类层次查找字段（baseMapper位于ServiceImpl父类）
     * @param fieldName
     * @return
     */
    private Field findField(String fieldName) throws NoSuchFieldException {
        for (Class<?> type = repository.getClass(); type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    /**
     * 构建库内已存在的技能配置（版本3）
     * @return
     */
    private SkillConfigEntity existingEntity() {
        SkillConfigEntity entity = new SkillConfigEntity();
        entity.setId(1L);
        entity.setSkillId(SKILL_ID);
        entity.setSkillName("报告撰写");
        entity.setSkillContent("old-content");
        entity.setSkillStatus(1);
        entity.setSkillVersion(3);
        return entity;
    }

    /**
     * 构建待保存的技能定义
     * @param content
     * @param remark
     * @return
     */
    private SkillDefinition definition(String content, String remark) {
        SkillDefinition def = new SkillDefinition();
        def.setSkillId(SKILL_ID);
        def.setSkillName("报告撰写");
        def.setSkillContent(content);
        def.setRemark(remark);
        return def;
    }

    /**
     * 模拟同版本快照行已存在
     * @return
     */
    private SkillVersionEntity existingVersionRow() {
        SkillVersionEntity row = new SkillVersionEntity();
        row.setId(100L);
        row.setSkillId(SKILL_ID);
        row.setVersion(3);
        row.setSkillContent("stale-content");
        row.setChangeLog("评测基线");
        row.setCreateUser("eval");
        return row;
    }

    @Test
    @DisplayName("更新技能时remark透传至快照changeLog且版本号回写")
    void saveSkillUpdatePassesRemark() {
        when(skillConfigMapper.selectOne(any(), anyBoolean())).thenReturn(existingEntity());
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenReturn(1);
        when(skillConfigMapper.updateById(any(SkillConfigEntity.class))).thenReturn(1);

        SkillDefinition def = definition("new-content", "修复章节结构");
        repository.saveSkill(def, "operator-1", "修复章节结构");

        ArgumentCaptor<SkillVersionEntity> versionCaptor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).insert(versionCaptor.capture());
        SkillVersionEntity snapshot = versionCaptor.getValue();
        assertThat(snapshot.getVersion()).isEqualTo(3);
        assertThat(snapshot.getSkillContent()).isEqualTo("old-content");
        assertThat(snapshot.getChangeLog()).isEqualTo("修复章节结构");
        assertThat(snapshot.getFingerprint()).hasSize(64);

        ArgumentCaptor<SkillConfigEntity> configCaptor = ArgumentCaptor.forClass(SkillConfigEntity.class);
        verify(skillConfigMapper).updateById(configCaptor.capture());
        assertThat(configCaptor.getValue().getSkillVersion()).isEqualTo(4);
        assertThat(def.getSkillVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("更新技能时remark为空兜底默认文案")
    void saveSkillUpdateFallsBackDefaultRemark() {
        when(skillConfigMapper.selectOne(any(), anyBoolean())).thenReturn(existingEntity());
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenReturn(1);
        when(skillConfigMapper.updateById(any(SkillConfigEntity.class))).thenReturn(1);

        repository.saveSkill(definition("new-content", null), null, null);

        ArgumentCaptor<SkillVersionEntity> captor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeLog()).isEqualTo("更新前自动保存");
    }

    @Test
    @DisplayName("同版本快照已存在时upsert为更新而非插入")
    void saveVersionUpsertsWhenRowExists() {
        when(skillConfigMapper.selectOne(any(), anyBoolean())).thenReturn(existingEntity());
        when(skillVersionMapper.selectList(any())).thenReturn(List.of(existingVersionRow()));
        when(skillVersionMapper.updateById(any(SkillVersionEntity.class))).thenReturn(1);
        when(skillConfigMapper.updateById(any(SkillConfigEntity.class))).thenReturn(1);

        repository.saveSkill(definition("new-content", "调整内容"), "operator-2", "调整内容");

        ArgumentCaptor<SkillVersionEntity> captor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).updateById(captor.capture());
        SkillVersionEntity updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(100L);
        assertThat(updated.getSkillContent()).isEqualTo("old-content");
        assertThat(updated.getChangeLog()).isEqualTo("调整内容");
        // 原创建人保留，记录本次更新人
        assertThat(updated.getCreateUser()).isEqualTo("eval");
        assertThat(updated.getUpdateUser()).isEqualTo("operator-2");

        verify(skillVersionMapper, never()).insert(any(SkillVersionEntity.class));
    }

    @Test
    @DisplayName("新建技能初始化版本1且不写快照")
    void saveSkillCreateInitializesVersionOne() {
        when(skillConfigMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(skillConfigMapper.insert(any(SkillConfigEntity.class))).thenReturn(1);

        SkillDefinition def = definition("first-content", "首次创建");
        repository.saveSkill(def, "operator-3", "首次创建");

        ArgumentCaptor<SkillConfigEntity> captor = ArgumentCaptor.forClass(SkillConfigEntity.class);
        verify(skillConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getSkillVersion()).isEqualTo(1);
        assertThat(captor.getValue().getSkillStatus()).isEqualTo(1);
        assertThat(def.getSkillVersion()).isEqualTo(1);

        verify(skillVersionMapper, never()).insert(any(SkillVersionEntity.class));
        verify(skillVersionMapper, never()).updateById(any(SkillVersionEntity.class));
    }

    @Test
    @DisplayName("版本历史分页查询返回倒序快照")
    void listVersionsReturnsPage() {
        SkillVersionEntity row = existingVersionRow();
        when(skillVersionMapper.selectList(any())).thenReturn(List.of(row));

        List<SkillVersionInfo> result = repository.listVersions(SKILL_ID, 1, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersion()).isEqualTo(3);
        assertThat(result.get(0).getSkillContent()).isEqualTo("stale-content");
    }

    @Test
    @DisplayName("统计版本快照总数")
    void countVersionsReturnsTotal() {
        when(skillVersionMapper.selectCount(any())).thenReturn(7L);

        assertThat(repository.countVersions(SKILL_ID)).isEqualTo(7L);
    }

    @Test
    @DisplayName("查询单版本快照为空时返回null")
    void getVersionReturnsNullWhenMissing() {
        when(skillVersionMapper.selectList(any())).thenReturn(List.of());

        assertThat(repository.getVersion(SKILL_ID, 9)).isNull();
    }
}
