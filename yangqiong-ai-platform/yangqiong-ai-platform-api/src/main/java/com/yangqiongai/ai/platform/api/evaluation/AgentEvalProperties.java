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
package com.yangqiongai.ai.platform.api.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent评测面配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.evaluation")
public class AgentEvalProperties {

    /**
     * 评测面总开关(数据集/运行/加载器条件装配)
     */
    private boolean enabled = false;

    /**
     * run编排worker线程数(引擎内部用例并发由引擎自管)
     */
    private int runWorkers = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRunWorkers() {
        return runWorkers;
    }

    public void setRunWorkers(int runWorkers) {
        this.runWorkers = runWorkers;
    }
}
