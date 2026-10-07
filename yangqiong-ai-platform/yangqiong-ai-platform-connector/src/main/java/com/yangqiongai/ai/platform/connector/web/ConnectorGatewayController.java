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
package com.yangqiongai.ai.platform.connector.web;

import com.yangqiongai.ai.platform.connector.service.ConnectorGatewayService;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCallbackRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 连接器入站网关入口
 * <p>
 * 匿名端点（/open/**，渠道回调自带验签），GET/POST统一转发网关服务，
 * 应答渠道即时ack报文。
 * </p>
 * @author yangqiong
 */
@RestController
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorGatewayController {

    /**
     * 入站网关服务
     */
    private final ConnectorGatewayService gatewayService;

    public ConnectorGatewayController(ConnectorGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * 渠道回调统一入口（GET为url验证，POST为消息推送）
     * @param instanceCode 实例编码（路由键）
     * @param request 原始请求
     * @return 即时应答报文
     */
    @RequestMapping(value = "/open/connector/{instanceCode}/callback",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> callback(@PathVariable("instanceCode") String instanceCode,
                                           HttpServletRequest request) {
        ConnectorCallbackRequest callbackRequest = new ConnectorCallbackRequest(
                request.getMethod(), buildHeaders(request), buildQuery(request), readBody(request));
        String ack = gatewayService.handleCallback(instanceCode, callbackRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ack);
    }

    /**
     * 收集请求头（小写键）
     * @param request 原始请求
     * @return
     */
    private Map<String, String> buildHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    /**
     * 收集查询参数
     * @param request 原始请求
     * @return
     */
    private Map<String, String> buildQuery(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().length > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()[0],
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 读取原始报文
     * @param request 原始请求
     * @return 空请求体返回null
     */
    private String readBody(HttpServletRequest request) {
        try {
            return request.getReader().lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return null;
        }
    }
}
