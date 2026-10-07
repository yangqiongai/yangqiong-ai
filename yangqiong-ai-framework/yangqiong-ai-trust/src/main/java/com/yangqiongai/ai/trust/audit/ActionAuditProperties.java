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
package com.yangqiongai.ai.trust.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 动作审计配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.trust.action-audit")
public class ActionAuditProperties {

    /**
     * 动作审计开关(开启后工具调用守卫落审计日志)
     */
    private boolean enabled = true;

    /**
     * 单条摘要最大字符数(防敏感内容与大结果入库)
     */
    private int summaryMaxChars = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSummaryMaxChars() {
        return summaryMaxChars;
    }

    public void setSummaryMaxChars(int summaryMaxChars) {
        this.summaryMaxChars = summaryMaxChars;
    }
}
