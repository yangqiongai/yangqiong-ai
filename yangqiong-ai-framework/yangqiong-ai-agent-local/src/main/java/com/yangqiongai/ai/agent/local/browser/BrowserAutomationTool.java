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

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 浏览器自动化工具
 * <p>
 * 提供网页导航、元素操作、截图和文本提取能力。
 * 支持Chrome和Firefox无头模式，适用于网页数据抓取和表单交互。
 * </p>
 *
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.agent.local.browser", name = "enabled", havingValue = "true")
public class BrowserAutomationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutomationTool.class);

    @Autowired
    private BrowserProperties properties;

    private volatile WebDriver driver;

    /**
     * 执行浏览器操作
     * @param action
     * @param selector
     * @param value
     * @return
     */
    @AgentTool("执行浏览器操作。action支持: navigate(导航到URL)、click(点击元素)、input(输入文本)、screenshot(截图并返回Base64)、get_text(提取文本)、scroll(滚动页面)。selector为CSS选择器（click/input/get_text时需要），value为URL或输入文本。")
    public String browser_action(String action, String selector, String value) {
        if (action == null || action.isBlank()) {
            return "错误: action不能为空";
        }
        log.info("浏览器操作: action={}, selector={}", action, selector);
        try {
            WebDriver webDriver = getDriver();
            return switch (action.toLowerCase()) {
                case "navigate" -> doNavigate(webDriver, value);
                case "click" -> doClick(webDriver, selector);
                case "input" -> doInput(webDriver, selector, value);
                case "screenshot" -> doScreenshot(webDriver);
                case "get_text" -> doGetText(webDriver, selector);
                case "scroll" -> doScroll(webDriver, value);
                default -> "错误: 不支持的操作 '" + action + "'";
            };
        } catch (Exception e) {
            log.error("浏览器操作失败: action={}, selector={}", action, selector, e);
            return "操作失败: " + e.getMessage();
        }
    }

    /**
     * 导航到URL
     */
    private String doNavigate(WebDriver driver, String url) {
        if (url == null || url.isBlank()) {
            return "错误: URL不能为空";
        }
        driver.get(url);
        return "已导航到: " + driver.getCurrentUrl() + "\n标题: " + driver.getTitle();
    }

    /**
     * 点击元素
     */
    private String doClick(WebDriver driver, String selector) {
        WebElement element = waitForElement(driver, selector);
        if (element == null) {
            return "错误: 未找到元素 '" + selector + "'";
        }
        element.click();
        return "已点击元素: " + selector;
    }

    /**
     * 输入文本
     */
    private String doInput(WebDriver driver, String selector, String value) {
        WebElement element = waitForElement(driver, selector);
        if (element == null) {
            return "错误: 未找到元素 '" + selector + "'";
        }
        element.clear();
        element.sendKeys(value);
        return "已在 " + selector + " 中输入: " + value;
    }

    /**
     * 截图并返回Base64
     */
    private String doScreenshot(WebDriver driver) throws Exception {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String screenshotPath = properties.getScreenshotDir() + File.separator + "screenshot_" + System.currentTimeMillis() + ".png";
        File destFile = new File(screenshotPath);
        Files.copy(screenshot.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        byte[] imageBytes = java.nio.file.Files.readAllBytes(destFile.toPath());
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return "截图已保存: " + screenshotPath + "\nBase64(前100字符): " + base64.substring(0, Math.min(100, base64.length())) + "...";
    }

    /**
     * 提取文本
     */
    private String doGetText(WebDriver driver, String selector) {
        if (selector == null || selector.isBlank()) {
            return "页面文本:\n" + driver.findElement(By.tagName("body")).getText();
        }
        List<WebElement> elements = driver.findElements(By.cssSelector(selector));
        if (elements.isEmpty()) {
            return "错误: 未找到匹配 '" + selector + "' 的元素";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            sb.append("[").append(i).append("] ").append(elements.get(i).getText()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 滚动页面
     */
    private String doScroll(WebDriver driver, String value) {
        int pixels = 500;
        if (value != null && !value.isBlank()) {
            try {
                pixels = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, " + pixels + ")");
        return "已滚动 " + pixels + " 像素";
    }

    /**
     * 等待元素出现
     */
    private WebElement waitForElement(WebDriver driver, String selector) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(properties.getImplicitWaitSeconds()));
            return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
        } catch (TimeoutException e) {
            return null;
        }
    }

    /**
     * 获取WebDriver（懒加载，线程安全）
     */
    private WebDriver getDriver() {
        if (driver == null) {
            synchronized (this) {
                if (driver == null) {
                    driver = createDriver();
                }
            }
        }
        return driver;
    }

    /**
     * 创建WebDriver实例
     */
    private WebDriver createDriver() {
        String type = properties.getDriverType();
        if ("firefox".equalsIgnoreCase(type)) {
            FirefoxOptions options = new FirefoxOptions();
            if (properties.isHeadless()) {
                options.addArguments("--headless");
            }
            return new FirefoxDriver(options);
        }
        // 默认Chrome
        ChromeOptions options = new ChromeOptions();
        if (properties.isHeadless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
        return new ChromeDriver(options);
    }
}
