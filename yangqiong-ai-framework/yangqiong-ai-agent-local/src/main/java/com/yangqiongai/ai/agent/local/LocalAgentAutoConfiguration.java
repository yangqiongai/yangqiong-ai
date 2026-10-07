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
package com.yangqiongai.ai.agent.local;

import com.yangqiongai.ai.agent.local.browser.BrowserAutomationTool;
import com.yangqiongai.ai.agent.local.execution.DockerExecutionTool;
import com.yangqiongai.ai.agent.local.execution.SshExecutionTool;
import com.yangqiongai.ai.agent.local.processor.LocalAgentProcessor;
import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;
import com.yangqiongai.ai.agent.local.workspace.gateway.ServerWorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGatewayRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 本地Agent自动配置
 * @author yangqiong
 */
@Configuration
@EnableScheduling
@EnableAsync
public class LocalAgentAutoConfiguration {

    @Bean
    public LocalAgentProcessor localAgentProcessor() {
        return new LocalAgentProcessor();
    }

    @Bean
    public UserWorkspaceService userWorkspaceService() {
        return new UserWorkspaceService();
    }

    @Bean
    public ServerWorkspaceGateway serverWorkspaceGateway(UserWorkspaceService userWorkspaceService) {
        return new ServerWorkspaceGateway(userWorkspaceService);
    }

    @Bean
    public WorkspaceGatewayRouter workspaceGatewayRouter(List<WorkspaceGateway> workspaceGateways) {
        return new WorkspaceGatewayRouter(workspaceGateways);
    }
}
