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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import com.yangqiongai.ai.platform.ecosystem.mcp.mapper.McpServerExposeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP暴露白名单管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class McpExposeServiceImplTest {

    @Mock
    private McpServerExposeMapper exposeMapper;

    private McpExposeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new McpExposeServiceImpl();
        ReflectionTestUtils.setField(service, "exposeMapper", exposeMapper);
    }

    private McpServerExpose expose(String type, String code) {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(type);
        expose.setExposeCode(code);
        return expose;
    }

    @Test
    void saveShouldRequireExposeType() {
        McpServerExpose expose = expose(null, "demo");

        assertThatThrownBy(() -> service.save(expose))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("暴露类型不能为空");
    }

    @Test
    void saveShouldRequireExposeCode() {
        McpServerExpose expose = expose(McpServerExpose.TYPE_TOOL, " ");

        assertThatThrownBy(() -> service.save(expose))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("暴露编码不能为空");
    }

    @Test
    void saveShouldInsertWithDefaults() {
        when(exposeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        McpServerExpose expose = expose(McpServerExpose.TYPE_TOOL, "demo_tool");

        service.save(expose);

        verify(exposeMapper).insert(expose);
        assertThat(expose.getEnabled()).isEqualTo(McpExposeServiceImpl.ENABLED_ON);
        assertThat(expose.getRenderAllowed()).isEqualTo(McpExposeServiceImpl.ENABLED_OFF);
    }

    @Test
    void saveShouldUpdateWhenExists() {
        McpServerExpose existing = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        existing.setId(100L);
        when(exposeMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        McpServerExpose expose = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        expose.setDescription("更新后的描述");

        service.save(expose);

        assertThat(expose.getId()).isEqualTo(100L);
        verify(exposeMapper).updateById(expose);
    }

    @Test
    void toggleShouldDisableWhenEnabled() {
        McpServerExpose existing = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        existing.setId(1L);
        existing.setEnabled(McpExposeServiceImpl.ENABLED_ON);
        when(exposeMapper.selectById(1L)).thenReturn(existing);

        service.toggle(1L);

        ArgumentCaptor<McpServerExpose> captor = ArgumentCaptor.forClass(McpServerExpose.class);
        verify(exposeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(McpExposeServiceImpl.ENABLED_OFF);
    }

    @Test
    void toggleShouldEnableWhenDisabled() {
        McpServerExpose existing = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        existing.setId(1L);
        existing.setEnabled(McpExposeServiceImpl.ENABLED_OFF);
        when(exposeMapper.selectById(1L)).thenReturn(existing);

        service.toggle(1L);

        ArgumentCaptor<McpServerExpose> captor = ArgumentCaptor.forClass(McpServerExpose.class);
        verify(exposeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(McpExposeServiceImpl.ENABLED_ON);
    }

    @Test
    void toggleShouldFailWhenMissing() {
        when(exposeMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.toggle(9L))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).getCode())
                .isEqualTo(AiErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void toggleShouldFailWhenIdNull() {
        assertThatThrownBy(() -> service.toggle(null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("暴露配置ID不能为空");
    }

    @Test
    void deleteShouldFailWhenMissing() {
        when(exposeMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).getCode())
                .isEqualTo(AiErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void deleteShouldRemoveExisting() {
        McpServerExpose existing = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        existing.setId(1L);
        when(exposeMapper.selectById(1L)).thenReturn(existing);

        service.delete(1L);

        verify(exposeMapper).deleteById(1L);
    }

    @Test
    void listEnabledShouldDelegateMapper() {
        McpServerExpose expose = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        when(exposeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expose));

        List<McpServerExpose> result = service.listEnabled(McpServerExpose.TYPE_TOOL);

        assertThat(result).containsExactly(expose);
    }

    @Test
    void getShouldDelegateMapper() {
        McpServerExpose expose = expose(McpServerExpose.TYPE_TOOL, "demo_tool");
        when(exposeMapper.selectById(1L)).thenReturn(expose);

        assertThat(service.get(1L)).isSameAs(expose);
    }
}
