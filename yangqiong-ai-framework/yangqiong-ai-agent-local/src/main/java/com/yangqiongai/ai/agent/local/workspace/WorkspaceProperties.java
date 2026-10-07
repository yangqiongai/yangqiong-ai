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
package com.yangqiongai.ai.agent.local.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作区配置
 * @author yangqiong
 */
@Configuration
@ConfigurationProperties(prefix = "ai.agent.local.workspace")
public class WorkspaceProperties {

    /**
     * 是否启用用户工作区
     */
    private boolean enabled = false;

    /**
     * 允许登记目录的白名单根（空表示不限）
     */
    private List<String> roots = new ArrayList<>();

    /**
     * 每用户工作区数量上限
     */
    private int maxPerUser = 20;

    /**
     * 审批实现方式（framework框架审批门阻塞式/engine引擎确认暂停重放式）
     */
    private String approvalEngine = "framework";

    /**
     * 二进制文件生成能力配置
     */
    private Generate generate = new Generate();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRoots() {
        return roots;
    }

    public void setRoots(List<String> roots) {
        this.roots = roots;
    }

    public int getMaxPerUser() {
        return maxPerUser;
    }

    public void setMaxPerUser(int maxPerUser) {
        this.maxPerUser = maxPerUser;
    }

    public String getApprovalEngine() {
        return approvalEngine;
    }

    public void setApprovalEngine(String approvalEngine) {
        this.approvalEngine = approvalEngine;
    }

    public Generate getGenerate() {
        return generate;
    }

    public void setGenerate(Generate generate) {
        this.generate = generate;
    }

    /**
     * 二进制文件生成能力配置
     */
    public static class Generate {

        /**
         * 是否启用Excel生成工具
         */
        private boolean enabled = true;

        /**
         * 单文件单元格总数上限
         */
        private int maxCells = 100000;

        /**
         * 单文件大小上限（字节）
         */
        private long maxFileSize = 5L * 1024 * 1024;

        /**
         * sheet数量上限
         */
        private int maxSheets = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxCells() {
            return maxCells;
        }

        public void setMaxCells(int maxCells) {
            this.maxCells = maxCells;
        }

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public int getMaxSheets() {
            return maxSheets;
        }

        public void setMaxSheets(int maxSheets) {
            this.maxSheets = maxSheets;
        }
    }
}
