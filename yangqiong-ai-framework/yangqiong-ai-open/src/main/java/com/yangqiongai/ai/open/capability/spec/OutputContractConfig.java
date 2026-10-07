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
 * 输出契约配置
 * @author yangqiong
 */
public class OutputContractConfig {

    /**
     * 是否严格校验输出
     */
    private boolean strict = true;

    /**
     * 是否自动修复不合规输出
     */
    private boolean autoRepair = true;

    /**
     * 最大修复尝试次数
     */
    private int maxRepairAttempts = 1;

    /**
     * 是否强制要求结构化JSON输出（非JSON输出视为失败）
     */
    private boolean forceStructured = false;

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public boolean isAutoRepair() {
        return autoRepair;
    }

    public void setAutoRepair(boolean autoRepair) {
        this.autoRepair = autoRepair;
    }

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public void setMaxRepairAttempts(int maxRepairAttempts) {
        this.maxRepairAttempts = maxRepairAttempts;
    }

    public boolean isForceStructured() {
        return forceStructured;
    }

    public void setForceStructured(boolean forceStructured) {
        this.forceStructured = forceStructured;
    }
}