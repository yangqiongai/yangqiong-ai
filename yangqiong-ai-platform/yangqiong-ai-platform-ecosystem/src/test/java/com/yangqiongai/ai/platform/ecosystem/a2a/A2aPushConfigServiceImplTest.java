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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.a2a.entity.A2aPushConfig;
import com.yangqiongai.ai.platform.ecosystem.a2a.mapper.A2aPushConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A2A推送配置管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class A2aPushConfigServiceImplTest {

    @Mock
    private A2aPushConfigMapper pushConfigMapper;

    private A2aPushConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new A2aPushConfigServiceImpl();
        ReflectionTestUtils.setField(service, "pushConfigMapper", pushConfigMapper);
    }

    private A2aPushConfig config(String taskId, String url) {
        A2aPushConfig config = new A2aPushConfig();
        config.setTaskId(taskId);
        config.setUrl(url);
        return config;
    }

    @Test
    void registerShouldRequireTaskId() {
        assertThatThrownBy(() -> service.register(config(" ", "http://cb")))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("任务ID不能为空");
    }

    @Test
    void registerShouldRequireUrl() {
        assertThatThrownBy(() -> service.register(config("t-1", null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("推送URL不能为空");
    }

    @Test
    void registerShouldInsertWithEnabledStatus() {
        when(pushConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        A2aPushConfig config = config("t-1", "http://cb");

        service.register(config);

        verify(pushConfigMapper).insert(config);
        assertThat(config.getStatus()).isEqualTo(A2aPushConfig.STATUS_ENABLED);
    }

    @Test
    void registerShouldOverwriteExisting() {
        A2aPushConfig existing = config("t-1", "http://old");
        existing.setId(1L);
        existing.setStatus(A2aPushConfig.STATUS_DISABLED);
        when(pushConfigMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        A2aPushConfig config = config("t-1", "http://new");
        config.setToken("tk");

        service.register(config);

        ArgumentCaptor<A2aPushConfig> captor = ArgumentCaptor.forClass(A2aPushConfig.class);
        verify(pushConfigMapper).updateById(captor.capture());
        A2aPushConfig updated = captor.getValue();
        assertThat(updated.getUrl()).isEqualTo("http://new");
        assertThat(updated.getToken()).isEqualTo("tk");
        assertThat(updated.getStatus()).isEqualTo(A2aPushConfig.STATUS_ENABLED);
    }

    @Test
    void getByTaskIdShouldIgnoreDisabled() {
        A2aPushConfig disabled = config("t-1", "http://cb");
        disabled.setStatus(A2aPushConfig.STATUS_DISABLED);
        when(pushConfigMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThat(service.getByTaskId("t-1")).isNull();
    }

    @Test
    void deleteShouldRemoveWhenExists() {
        A2aPushConfig existing = config("t-1", "http://cb");
        existing.setId(1L);
        when(pushConfigMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        service.delete("t-1");

        verify(pushConfigMapper).deleteById(1L);
    }
}
