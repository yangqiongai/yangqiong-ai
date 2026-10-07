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

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * SSH远程执行工具
 * <p>
 * 通过SSH协议在远程服务器执行命令，支持密码和私钥认证。
 * 主机必须预先在配置中声明（白名单模式），确保安全。
 * </p>
 *
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.agent.local.execution.ssh", name = "enabled", havingValue = "true")
public class SshExecutionTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SshExecutionTool.class);

    @Autowired
    private ExecutionBackendProperties properties;

    /**
     * 在远程主机执行SSH命令
     * @param hostName
     * @param command
     * @return
     */
    @AgentTool("通过SSH在远程服务器执行命令。hostName为预配置的主机别名（需在配置文件中声明），command为要执行的Shell命令。")
    public String ssh_exec(String hostName, String command) {
        ExecutionBackendProperties.SshHost host = resolveHost(hostName);
        if (host == null) {
            return "错误: 未知主机 '" + hostName + "'，请在配置中预先声明SSH主机";
        }
        log.info("SSH执行: host={}, user={}, command={}", host.getHost(), host.getUser(), command);
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            if ("KEY".equalsIgnoreCase(host.getAuthType()) && host.getPrivateKeyPath() != null) {
                jsch.addIdentity(host.getPrivateKeyPath());
            }
            session = jsch.getSession(host.getUser(), host.getHost(), host.getPort());
            if ("PASSWORD".equalsIgnoreCase(host.getAuthType())) {
                session.setPassword(host.getPassword());
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.write(tmp, 0, i);
                }
                while (err.available() > 0) {
                    int i = err.read(tmp, 0, 1024);
                    if (i < 0) break;
                    errorBuffer.write(tmp, 0, i);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    if (err.available() > 0) continue;
                    break;
                }
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            StringBuilder result = new StringBuilder();
            String stdout = outputBuffer.toString();
            String stderr = errorBuffer.toString();
            if (!stdout.isEmpty()) {
                result.append("STDOUT:\n").append(stdout).append("\n");
            }
            if (!stderr.isEmpty()) {
                result.append("STDERR:\n").append(stderr).append("\n");
            }
            result.append("EXIT_CODE: ").append(exitCode);
            return result.toString();
        } catch (Exception e) {
            log.error("SSH执行失败: host={}, command={}", host.getHost(), command, e);
            return "执行失败: " + e.getMessage();
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * 根据别名解析SSH主机配置
     * @param hostName
     * @return
     */
    private ExecutionBackendProperties.SshHost resolveHost(String hostName) {
        List<ExecutionBackendProperties.SshHost> hosts = properties.getSsh().getHosts();
        if (hosts == null || hosts.isEmpty()) {
            return null;
        }
        return hosts.stream()
                .filter(h -> hostName.equalsIgnoreCase(h.getName()))
                .findFirst()
                .orElse(null);
    }
}
