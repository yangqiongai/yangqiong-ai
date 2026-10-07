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
package com.yangqiongai.ai.agent.tool.builtin;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 汇率查询工具
 * <p>
 * 提供货币汇率查询和换算能力，数据来源于欧洲央行（ECB）的开放API frankfurter.app。
 * 支持主要国际货币的实时和历史汇率查询，无需API Key。
 * </p>
 * @author yangqiong
 */
@Component
public class ExchangeRateTool implements Tool {

    /**
     * 内置工具自动装配到所有Agent工具箱
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.BUILTIN;
    }

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateTool.class);

    /**
     * Frankfurter API基础URL（免费、无需Key、数据源为欧洲央行）
     */
    private static final String API_BASE = "https://api.frankfurter.app";

    /**
     * HTTP客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // API域名迁移后返回301，需跟随重定向
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 是否启用汇率工具
     */
    @Value("${ai.tool.exchange-rate.enabled:true}")
    private boolean enabled;

    /**
     * 获取最新汇率
     * @param baseCurrency 基准货币代码（如 USD、EUR、CNY）
     * @param targetCurrency 目标货币代码（如 CNY、USD）
     * @return
     */
    @AgentTool("获取最新汇率。输入基准货币和目标货币代码（如USD、CNY、EUR、GBP、JPY），返回最新汇率。数据来源：欧洲央行。")
    public String getLatestRate(@AgentToolParam("基准货币代码，如 USD、EUR、CNY") String baseCurrency, @AgentToolParam("目标货币代码，如 CNY、USD") String targetCurrency) {
        if (!enabled) {
            return "汇率查询工具已被禁用";
        }
        String upperBase = baseCurrency.toUpperCase();
        String upperTarget = targetCurrency.toUpperCase();
        String url = API_BASE + "/latest?from=" + upperBase + "&to=" + upperTarget;
        String response = doRequest(url);
        if (response == null) {
            return "获取汇率失败，请稍后重试或检查货币代码是否正确（如USD、EUR、CNY、JPY、GBP等）";
        }
        return "查询：" + upperBase + " → " + upperTarget + "\n" + response;
    }

    /**
     * 获取历史汇率
     * @param date 日期，格式 yyyy-MM-dd
     * @param baseCurrency 基准货币代码
     * @param targetCurrency 目标货币代码
     * @return
     */
    @AgentTool("获取指定日期的历史汇率。输入日期（yyyy-MM-dd）、基准货币和目标货币代码，返回该日期的汇率。注意：周末和节假日无数据，将返回最近交易日数据。")
    public String getHistoricalRate(@AgentToolParam("日期，格式 yyyy-MM-dd") String date, @AgentToolParam("基准货币代码，如 USD、EUR、CNY") String baseCurrency, @AgentToolParam("目标货币代码，如 CNY、USD") String targetCurrency) {
        if (!enabled) {
            return "汇率查询工具已被禁用";
        }
        try {
            LocalDate.parse(date);
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式（如 2026-07-19）";
        }
        String upperBase = baseCurrency.toUpperCase();
        String upperTarget = targetCurrency.toUpperCase();
        String url = API_BASE + "/" + date + "?from=" + upperBase + "&to=" + upperTarget;
        String response = doRequest(url);
        if (response == null) {
            return "获取历史汇率失败，请检查日期和货币代码是否正确";
        }
        return "历史汇率查询：" + upperBase + " → " + upperTarget + "（" + date + "）\n" + response;
    }

    /**
     * 货币换算
     * @param amount 金额
     * @param fromCurrency 源货币代码
     * @param toCurrency 目标货币代码
     * @return
     */
    @AgentTool("货币换算。输入金额、源货币和目标货币代码（如100 USD to CNY），返回换算结果。数据来源：欧洲央行最新汇率。")
    public String convertCurrency(@AgentToolParam("金额") String amount, @AgentToolParam("源货币代码，如 USD") String fromCurrency, @AgentToolParam("目标货币代码，如 CNY") String toCurrency) {
        if (!enabled) {
            return "汇率查询工具已被禁用";
        }
        BigDecimal amountVal;
        try {
            amountVal = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            return "金额格式错误：" + amount;
        }
        String upperFrom = fromCurrency.toUpperCase();
        String upperTo = toCurrency.toUpperCase();

        if (upperFrom.equals(upperTo)) {
            return amountVal + " " + upperFrom + " = " + amountVal + " " + upperTo + "（同种货币，无需换算）";
        }

        String url = API_BASE + "/latest?from=" + upperFrom + "&to=" + upperTo;
        String response = doRequest(url);
        if (response == null) {
            return "汇率获取失败，无法完成换算";
        }

        try {
            // 解析JSON获取汇率（简单解析，避免依赖JSON库）
            String rateStr = extractRate(response, upperTo);
            if (rateStr == null) {
                return "解析汇率失败：" + response;
            }
            BigDecimal rate = new BigDecimal(rateStr);
            BigDecimal result = amountVal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
            StringBuilder sb = new StringBuilder();
            sb.append("货币换算结果：\n");
            sb.append("  金额：").append(amountVal).append(" ").append(upperFrom).append("\n");
            sb.append("  汇率：1 ").append(upperFrom).append(" = ").append(rate).append(" ").append(upperTo).append("\n");
            sb.append("  换算：").append(amountVal).append(" ").append(upperFrom).append(" = ").append(result).append(" ").append(upperTo);
            return sb.toString();
        } catch (Exception e) {
            return "换算计算失败：" + e.getMessage();
        }
    }

    /**
     * 获取基准货币对所有主要货币的汇率
     * @param baseCurrency 基准货币代码
     * @return
     */
    @AgentTool("获取指定货币对所有主要货币的最新汇率。输入基准货币代码（如USD），返回该货币对所有支持货币的汇率。")
    public String getAllRates(@AgentToolParam("基准货币代码，如 USD") String baseCurrency) {
        if (!enabled) {
            return "汇率查询工具已被禁用";
        }
        String upperBase = baseCurrency.toUpperCase();
        String url = API_BASE + "/latest?from=" + upperBase;
        String response = doRequest(url);
        if (response == null) {
            return "获取汇率失败";
        }
        return "基准货币：" + upperBase + "\n" + response;
    }

    /**
     * 获取支持的货币列表
     * @return
     */
    @AgentTool("获取汇率工具支持的所有货币代码列表。")
    public String getSupportedCurrencies() {
        if (!enabled) {
            return "汇率查询工具已被禁用";
        }
        String url = API_BASE + "/currencies";
        String response = doRequest(url);
        if (response == null) {
            return "获取货币列表失败";
        }
        return "支持的货币列表：\n" + response;
    }

    /**
     * 执行HTTP GET请求
     * @param url 请求URL
     * @return
     */
    private String doRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            log.debug("查询汇率: {}", url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("汇率查询失败: status={}, body={}", response.statusCode(), response.body());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            log.warn("汇率查询异常: {}", url, e);
            return null;
        }
    }

    /**
     * 从响应JSON中提取指定货币的汇率
     * <p>
     * 响应格式：{"amount":1.0,"base":"USD","date":"2026-07-18","rates":{"CNY":7.1785}}
     * </p>
     * @param jsonResponse JSON响应
     * @param currency 目标货币代码
     * @return
     */
    private String extractRate(String jsonResponse, String currency) {
        // 查找 "CNY":7.1785 模式
        String pattern = "\"" + currency + "\":";
        int idx = jsonResponse.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int start = idx + pattern.length();
        int end = start;
        while (end < jsonResponse.length()) {
            char c = jsonResponse.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == '\n') {
                break;
            }
            end++;
        }
        return jsonResponse.substring(start, end).trim();
    }
}
