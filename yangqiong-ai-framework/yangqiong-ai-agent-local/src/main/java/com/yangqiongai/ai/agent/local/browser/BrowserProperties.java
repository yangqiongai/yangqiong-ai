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
package com.yangqiongai.ai.agent.local.browser;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 浏览器自动化配置
 * @author yangqiong
 */
@Configuration
@ConfigurationProperties(prefix = "ai.agent.local.browser")
public class BrowserProperties {

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 驱动类型（chrome/firefox）
     */
    private String driverType = "chrome";

    /**
     * 是否无头模式
     */
    private boolean headless = true;

    /**
     * 截图保存目录
     */
    private String screenshotDir = System.getProperty("java.io.tmpdir") + "/agent-screenshots";

    /**
     * 页面加载超时（秒）
     */
    private int pageLoadTimeoutSeconds = 30;

    /**
     * 隐式等待时间（秒）
     */
    private int implicitWaitSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        this.driverType = driverType;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public String getScreenshotDir() {
        return screenshotDir;
    }

    public void setScreenshotDir(String screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    public int getPageLoadTimeoutSeconds() {
        return pageLoadTimeoutSeconds;
    }

    public void setPageLoadTimeoutSeconds(int pageLoadTimeoutSeconds) {
        this.pageLoadTimeoutSeconds = pageLoadTimeoutSeconds;
    }

    public int getImplicitWaitSeconds() {
        return implicitWaitSeconds;
    }

    public void setImplicitWaitSeconds(int implicitWaitSeconds) {
        this.implicitWaitSeconds = implicitWaitSeconds;
    }
}
