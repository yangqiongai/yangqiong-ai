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
package com.yangqiongai.ai.agent.data.trace;

import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent上下文快照自动配置测试
 * @author yangqiong
 */
class AgentContextSnapshotAutoConfigurationTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 启动上下文(可选注入开关属性)
     * @param props 开关属性(可空)
     */
    private void start(Map<String, Object> props) {
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("contextSnapshotRepository",
                Mockito.mock(ContextSnapshotRepository.class));
        if (props != null && !props.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        }
        context.register(AgentContextSnapshotAutoConfiguration.class);
        context.refresh();
    }

    @Test
    void 默认开关装配批量落库记录器() {
        start(Map.of());

        assertThat(context.getBean(ContextSnapshotListener.class)).isInstanceOf(PlatformContextRecorder.class);
        AgentContextSnapshotProperties properties = context.getBean(AgentContextSnapshotProperties.class);
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxItemChars()).isEqualTo(65536);
        assertThat(properties.getMaxQueueSize()).isEqualTo(10000);
        assertThat(properties.getBatchSize()).isEqualTo(200);
    }

    @Test
    void 开关关闭时不装配() {
        start(Map.of("ai.agent.context-snapshot.enabled", "false"));

        assertThat(context.getBeanNamesForType(ContextSnapshotListener.class)).isEmpty();
    }
}
