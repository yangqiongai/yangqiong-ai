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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.connector.entity.ConnectorCredential;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.service.ConnectorCredentialService;
import com.yangqiongai.ai.platform.connector.service.ConnectorInstanceService;
import com.yangqiongai.ai.platform.connector.service.ConnectorRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 连接器管理面
 * <p>
 * 提供商目录/凭证CRUD（脱敏）/实例CRUD/启停/工具清单/回调地址。
 * </p>
 * @author yangqiong
 */
@RestController
@RequestMapping("/api/agent/connector")
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorController {

    /**
     * 提供商注册中心
     */
    @Autowired
    private ConnectorRegistry registry;

    /**
     * 凭证托管
     */
    @Autowired
    private ConnectorCredentialService credentialService;

    /**
     * 实例管理
     */
    @Autowired
    private ConnectorInstanceService instanceService;

    /**
     * 提供商目录（编码/描述/凭证字段Schema/工具清单，前端据此动态渲染配置表单）
     * @return
     */
    @GetMapping("/providers")
    public ApiResult<List<ProviderCatalogItem>> providers() {
        return ApiResult.ok(registry.listProviders().stream()
                .map(provider -> new ProviderCatalogItem(provider.providerCode(), provider.descriptor()))
                .toList());
    }

    /**
     * 分页查询凭证
     * @param pageNum
     * @param pageSize
     * @param providerCode
     * @return
     */
    @GetMapping("/credentials")
    public ApiResult<Page<ConnectorCredential>> credentials(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String providerCode) {
        return ApiResult.ok(credentialService.page(pageNum, pageSize, providerCode));
    }

    /**
     * 保存凭证（credentialJson字段承载明文JSON，返回脱敏结果）
     * @param credential
     * @return
     */
    @PostMapping("/credentials")
    public ApiResult<ConnectorCredential> saveCredential(@RequestBody ConnectorCredential credential) {
        return ApiResult.ok(credentialService.save(credential));
    }

    /**
     * 删除凭证（被实例引用时拒绝）
     * @param id
     * @return
     */
    @DeleteMapping("/credentials/{id}")
    public ApiResult<Void> deleteCredential(@PathVariable("id") Long id) {
        credentialService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 凭证连通性测试
     * @param id
     * @return
     */
    @PostMapping("/credentials/{id}/test")
    public ApiResult<String> testCredential(@PathVariable("id") Long id) {
        return ApiResult.ok(credentialService.test(id));
    }

    /**
     * 分页查询实例
     * @param pageNum
     * @param pageSize
     * @param providerCode
     * @return
     */
    @GetMapping("/instances")
    public ApiResult<Page<ConnectorInstance>> instances(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String providerCode) {
        return ApiResult.ok(instanceService.page(pageNum, pageSize, providerCode));
    }

    /**
     * 保存实例
     * @param instance
     * @return
     */
    @PostMapping("/instances")
    public ApiResult<ConnectorInstance> saveInstance(@RequestBody ConnectorInstance instance) {
        return ApiResult.ok(instanceService.save(instance));
    }

    /**
     * 删除实例
     * @param id
     * @return
     */
    @DeleteMapping("/instances/{id}")
    public ApiResult<Void> deleteInstance(@PathVariable("id") Long id) {
        instanceService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 启停实例（停用即工具摘除与网关拒绝）
     * @param id
     * @param enabled
     * @return
     */
    @PutMapping("/instances/{id}/status")
    public ApiResult<Void> changeStatus(
            @PathVariable("id") Long id,
            @RequestParam("enabled") boolean enabled) {
        instanceService.changeStatus(id, enabled);
        return ApiResult.ok();
    }

    /**
     * 实例暴露的工具清单（实时读提供商描述）
     * @param id
     * @return
     */
    @GetMapping("/instances/{id}/tools")
    public ApiResult<List<com.yangqiongai.ai.platform.connector.spi.ConnectorToolDefinition>> instanceTools(
            @PathVariable("id") Long id) {
        return ApiResult.ok(instanceService.listTools(id));
    }

    /**
     * 生成入站回调地址（复制到钉钉/飞书开放平台配置）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/instances/{id}/callback-url")
    public ApiResult<String> callbackUrl(@PathVariable("id") Long id, HttpServletRequest request) {
        String baseUrl = getBaseUrl(request);
        return ApiResult.ok(instanceService.callbackUrl(id, baseUrl));
    }

    /**
     * 从请求推断平台外部可达基础地址
     * @param request
     * @return
     */
    private String getBaseUrl(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String proto = forwardedProto != null ? forwardedProto.split(",")[0].trim() : request.getScheme();
            return proto + "://" + forwardedHost.split(",")[0].trim();
        }
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                ? "" : ":" + request.getServerPort());
    }
}
