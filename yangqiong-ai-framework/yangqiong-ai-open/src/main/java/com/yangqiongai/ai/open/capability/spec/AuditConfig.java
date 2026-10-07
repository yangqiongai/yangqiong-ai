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
package com.yangqiongai.ai.open.capability.spec;

/**
 * 审计配置
 * @author yangqiong
 */
public class AuditConfig {

    /**
     * 是否启用审计
     */
    private boolean enabled = true;

    /**
     * 是否记录请求输入
     */
    private boolean logInput = true;

    /**
     * 是否记录响应输出
     */
    private boolean logOutput = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogInput() {
        return logInput;
    }

    public void setLogInput(boolean logInput) {
        this.logInput = logInput;
    }

    public boolean isLogOutput() {
        return logOutput;
    }

    public void setLogOutput(boolean logOutput) {
        this.logOutput = logOutput;
    }
}