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
package com.yangqiongai.ai.platform.api.skill;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技能版本管理测试（回滚校验与变更事件）
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillVersionService 单元测试")
class SkillVersionServiceTest {

    @Mock
    private SkillConfigRepository skillConfigRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillVersionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SkillVersionService();
        inject("skillConfigRepository", skillConfigRepository);
        inject("eventPublisher", eventPublisher);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = SkillVersionService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    /**
     * 构建当前技能定义（版本3）
     * @param content
     * @return
     */
    private SkillDefinition currentDefinition(String content) {
        SkillDefinition def = new SkillDefinition();
        def.setSkillId("report-writer");
        def.setSkillName("报告撰写");
        def.setSkillContent(content);
        def.setSkillType("UPLOADED");
        def.setBoundTools(List.of("tool-a"));
        def.setSkillVersion(3);
        return def;
    }

    /**
     * 构建版本快照
     * @param version
     * @param content
     * @return
     */
    private SkillVersionInfo snapshot(int version, String content) {
        SkillVersionInfo info = new SkillVersionInfo();
        info.setVersion(version);
        info.setSkillContent(content);
        info.setChangeLog("更新前自动保存");
        info.setFingerprint("abcdef1234567890");
        info.setCreateUser("tester");
        info.setCreateTime(LocalDateTime.of(2026, 9, 23, 10, 0));
        return info;
    }

    @Test
    @DisplayName("回滚成功：仅回退内容保留当前配置，产生新版本并发布ROLLBACK事件")
    void rollbackSuccess() {
        when(skillConfigRepository.getBySkillId("report-writer"))
                .thenReturn(currentDefinition("v3-content"));
        when(skillConfigRepository.getVersion("report-writer", 1))
                .thenReturn(snapshot(1, "v1-content"));
        // 模拟saveSkill契约：落库后回写新版本号（真实行为已在DefaultSkillConfigRepositoryTest验证）
        doAnswer(invocation -> {
            SkillDefinition saved = invocation.getArgument(0);
            saved.setSkillVersion(4);
            return null;
        }).when(skillConfigRepository).saveSkill(any(SkillDefinition.class), eq("operator-1"), eq("回滚至版本1"));

        SkillDefinition result = service.rollback("report-writer", 1, null, "operator-1");

        assertThat(result.getSkillContent()).isEqualTo("v1-content");
        assertThat(result.getBoundTools()).containsExactly("tool-a");
        assertThat(result.getSkillVersion()).isEqualTo(4);

        ArgumentCaptor<SkillChangedEvent> captor = ArgumentCaptor.forClass(SkillChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        SkillChangedEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo(SkillChangedEvent.ACTION_ROLLBACK);
        assertThat(event.getSkillId()).isEqualTo("report-writer");
        assertThat(event.getNewVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("回滚时remark优先作为变更说明")
    void rollbackRemarkPreferred() {
        when(skillConfigRepository.getBySkillId("report-writer"))
                .thenReturn(currentDefinition("v3-content"));
        when(skillConfigRepository.getVersion("report-writer", 2))
                .thenReturn(snapshot(2, "v2-content"));

        service.rollback("report-writer", 2, "修复格式错误", null);

        verify(skillConfigRepository).saveSkill(any(SkillDefinition.class), any(), eq("修复格式错误"));
    }

    @Test
    @DisplayName("目标版本与当前版本相同时拒绝回滚")
    void rollbackRejectsSameVersion() {
        when(skillConfigRepository.getBySkillId("report-writer"))
                .thenReturn(currentDefinition("v3-content"));

        assertThatThrownBy(() -> service.rollback("report-writer", 3, null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("目标版本与当前版本相同");

        verify(skillConfigRepository, never()).saveSkill(any(), anyString(), anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("目标版本快照不存在时拒绝回滚")
    void rollbackRejectsMissingSnapshot() {
        when(skillConfigRepository.getBySkillId("report-writer"))
                .thenReturn(currentDefinition("v3-content"));
        when(skillConfigRepository.getVersion("report-writer", 9)).thenReturn(null);

        assertThatThrownBy(() -> service.rollback("report-writer", 9, null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("目标版本快照不存在");

        verify(skillConfigRepository, never()).saveSkill(any(), anyString(), anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("目标版本非法时拒绝回滚")
    void rollbackRejectsInvalidTargetVersion() {
        assertThatThrownBy(() -> service.rollback("report-writer", null, null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("targetVersion");
        assertThatThrownBy(() -> service.rollback("report-writer", 0, null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("targetVersion");

        verify(skillConfigRepository, never()).getBySkillId(anyString());
    }

    @Test
    @DisplayName("技能不存在时拒绝回滚")
    void rollbackRejectsMissingSkill() {
        when(skillConfigRepository.getBySkillId("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.rollback("ghost", 1, null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("技能不存在");
    }

    @Test
    @DisplayName("发布变更事件携带当前作用域与最新版本号")
    void publishChangedCarriesScopeAndVersion() {
        SkillDefinition def = currentDefinition("v3-content");
        when(skillConfigRepository.getBySkillId("report-writer")).thenReturn(def);

        service.publishChanged(SkillChangedEvent.ACTION_UPDATE, "report-writer", "operator-2");

        ArgumentCaptor<SkillChangedEvent> captor = ArgumentCaptor.forClass(SkillChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        SkillChangedEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo(SkillChangedEvent.ACTION_UPDATE);
        assertThat(event.getOperator()).isEqualTo("operator-2");
        assertThat(event.getNewVersion()).isEqualTo(3);
        assertThat(event.getScopeId()).isNotBlank();
    }

    @Test
    @DisplayName("版本历史分页DTO指纹截前8位且不携带内容全文")
    void pageVersionsMasksFingerprintAndOmitsContent() {
        when(skillConfigRepository.listVersions("report-writer", 1, 10))
                .thenReturn(List.of(snapshot(2, "long-content-should-not-leak")));
        when(skillConfigRepository.countVersions("report-writer")).thenReturn(5L);

        SkillVersionService.VersionPage page = service.pageVersions("report-writer", 1, 10);

        assertThat(page.getTotal()).isEqualTo(5L);
        assertThat(page.getRecords()).hasSize(1);
        SkillVersionDto dto = page.getRecords().get(0);
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getFingerprint()).isEqualTo("abcdef12").hasSize(8);
    }

    @Test
    @DisplayName("版本快照详情返回内容全文")
    void versionDetailReturnsFullContent() {
        when(skillConfigRepository.getVersion("report-writer", 1)).thenReturn(snapshot(1, "v1-content"));

        SkillVersionDetailDto detail = service.getVersionDetail("report-writer", 1);

        assertThat(detail.getSkillContent()).isEqualTo("v1-content");
        assertThat(detail.getFingerprint()).isEqualTo("abcdef12");
    }

    @Test
    @DisplayName("版本快照不存在时查询详情抛出异常")
    void versionDetailRejectsMissingSnapshot() {
        when(skillConfigRepository.getVersion(anyString(), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> service.getVersionDetail("report-writer", 99))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("版本快照不存在");
    }
}
