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
package com.yangqiongai.ai.agent.local.execution;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Docker容器执行工具
 * <p>
 * 在隔离的Docker容器中执行Shell命令，支持镜像选择、资源限制和超时控制。
 * 容器执行完毕后自动销毁，确保环境隔离。
 * </p>
 *
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.agent.local.execution.docker", name = "enabled", havingValue = "true")
public class DockerExecutionTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutionTool.class);

    @Autowired
    private ExecutionBackendProperties properties;

    private volatile DockerClient dockerClient;

    /**
     * 在Docker容器中执行命令
     * @param image
     * @param command
     * @return
     */
    @AgentTool("在Docker容器中执行Shell命令，返回标准输出、错误输出和退出码。image为镜像名（如ubuntu:22.04），command为要执行的Shell命令。")
    public String docker_exec(String image, String command) {
        if (image == null || image.isBlank()) {
            image = properties.getDocker().getDefaultImage();
        }
        log.info("Docker执行: image={}, command={}", image, command);
        try {
            DockerClient client = getDockerClient();
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode(properties.getDocker().getNetwork())
                    .withMemory(parseMemory(properties.getDocker().getMemoryLimit()))
                    .withCpuQuota(properties.getDocker().getCpuQuota());

            CreateContainerResponse container = client.createContainerCmd(image)
                    .withCmd("sh", "-c", command)
                    .withHostConfig(hostConfig)
                    .exec();
            String containerId = container.getId();

            client.startContainerCmd(containerId).exec();

            boolean finished = client.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitCompletion(properties.getDocker().getTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                client.killContainerCmd(containerId).exec();
                client.removeContainerCmd(containerId).withForce(true).exec();
                return "错误: 执行超时（" + properties.getDocker().getTimeoutSeconds() + "秒）";
            }

            final StringBuilder logBuffer = new StringBuilder();
            LogContainerResultCallback logCallback = new LogContainerResultCallback() {
                @Override
                public void onNext(Frame item) {
                    logBuffer.append(new String(item.getPayload()));
                }
            };
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(logCallback)
                    .awaitCompletion(properties.getDocker().getTimeoutSeconds(), TimeUnit.SECONDS);

            client.removeContainerCmd(containerId).withForce(true).exec();
            String output = logBuffer.toString();
            return output.isEmpty() ? "(无输出)" : output;
        } catch (Exception e) {
            log.error("Docker执行失败: image={}, command={}", image, command, e);
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * 获取Docker客户端（懒加载）
     */
    private DockerClient getDockerClient() {
        if (dockerClient == null) {
            synchronized (this) {
                if (dockerClient == null) {
                    DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                            .withDockerHost(properties.getDocker().getDockerHost())
                            .build();
                    ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                            .dockerHost(config.getDockerHost())
                            .build();
                    dockerClient = DockerClientImpl.getInstance(config, httpClient);
                }
            }
        }
        return dockerClient;
    }

    /**
     * 解析内存配置为字节数
     * @param memory
     * @return
     */
    private long parseMemory(String memory) {
        if (memory == null || memory.isBlank()) {
            return 512 * 1024 * 1024L;
        }
        memory = memory.trim().toLowerCase();
        if (memory.endsWith("g")) {
            return Long.parseLong(memory.substring(0, memory.length() - 1)) * 1024 * 1024 * 1024L;
        } else if (memory.endsWith("m")) {
            return Long.parseLong(memory.substring(0, memory.length() - 1)) * 1024 * 1024L;
        } else if (memory.endsWith("k")) {
            return Long.parseLong(memory.substring(0, memory.length() - 1)) * 1024L;
        }
        return Long.parseLong(memory);
    }
}
