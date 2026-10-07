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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行后端配置
 * @author yangqiong
 */
@Configuration
@ConfigurationProperties(prefix = "ai.agent.local.execution")
public class ExecutionBackendProperties {

    /**
     * Docker 执行配置
     */
    private DockerConfig docker = new DockerConfig();

    /**
     * SSH 执行配置
     */
    private SshConfig ssh = new SshConfig();

    public DockerConfig getDocker() {
        return docker;
    }

    public void setDocker(DockerConfig docker) {
        this.docker = docker;
    }

    public SshConfig getSsh() {
        return ssh;
    }

    public void setSsh(SshConfig ssh) {
        this.ssh = ssh;
    }

    /**
     * Docker 执行配置
     */
    public static class DockerConfig {

        /**
         * 是否启用
         */
        private boolean enabled = false;

        /**
         * Docker daemon 地址
         */
        private String dockerHost = "unix:///var/run/docker.sock";

        /**
         * 默认镜像
         */
        private String defaultImage = "ubuntu:22.04";

        /**
         * 网络模式（none/host/bridge）
         */
        private String network = "none";

        /**
         * 内存限制
         */
        private String memoryLimit = "512m";

        /**
         * CPU 配额（微秒）
         */
        private long cpuQuota = 100000L;

        /**
         * 执行超时时间（秒）
         */
        private int timeoutSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDockerHost() {
            return dockerHost;
        }

        public void setDockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
        }

        public String getDefaultImage() {
            return defaultImage;
        }

        public void setDefaultImage(String defaultImage) {
            this.defaultImage = defaultImage;
        }

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public String getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        public long getCpuQuota() {
            return cpuQuota;
        }

        public void setCpuQuota(long cpuQuota) {
            this.cpuQuota = cpuQuota;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * SSH 执行配置
     */
    public static class SshConfig {

        /**
         * 是否启用
         */
        private boolean enabled = false;

        /**
         * SSH 主机白名单
         */
        private List<SshHost> hosts = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<SshHost> getHosts() {
            return hosts;
        }

        public void setHosts(List<SshHost> hosts) {
            this.hosts = hosts;
        }
    }

    /**
     * SSH 主机配置
     */
    public static class SshHost {

        /**
         * 主机名称（别名）
         */
        private String name;

        /**
         * 主机地址
         */
        private String host;

        /**
         * 端口
         */
        private int port = 22;

        /**
         * 用户名
         */
        private String user;

        /**
         * 认证类型（PASSWORD/KEY）
         */
        private String authType = "KEY";

        /**
         * 密码（PASSWORD 认证时使用）
         */
        private String password;

        /**
         * 私钥路径（KEY 认证时使用）
         */
        private String privateKeyPath;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }
    }
}
