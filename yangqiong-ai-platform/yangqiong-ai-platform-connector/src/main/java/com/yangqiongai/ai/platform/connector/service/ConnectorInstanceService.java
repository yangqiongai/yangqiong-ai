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
package com.yangqiongai.ai.platform.connector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 连接器实例管理
 * <p>
 * 实例CRUD/启停/工具清单/回调地址生成；
 * 停用即工具摘除与网关拒绝，启停走乐观锁防竞态。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorInstanceService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorInstanceService.class);

    /**
     * 入站回调地址路径模板
     */
    public static final String CALLBACK_PATH_TEMPLATE = "/open/connector/%s/callback";

    /**
     * 连接器实例Mapper
     */
    private final ConnectorInstanceMapper instanceMapper;

    /**
     * 提供商注册中心
     */
    private final ConnectorRegistry registry;

    /**
     * 实例生命周期监听器（提供商侧资源即时失效）
     */
    private final List<ConnectorInstanceLifecycleListener> lifecycleListeners;

    public ConnectorInstanceService(ConnectorInstanceMapper instanceMapper, ConnectorRegistry registry,
                                    List<ConnectorInstanceLifecycleListener> lifecycleListeners) {
        this.instanceMapper = instanceMapper;
        this.registry = registry;
        this.lifecycleListeners = lifecycleListeners == null ? List.of() : lifecycleListeners;
    }

    /**
     * 分页查询实例列表
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param providerCode 提供商过滤，可空
     * @return
     */
    public Page<ConnectorInstance> page(long pageNum, long pageSize, String providerCode) {
        LambdaQueryWrapper<ConnectorInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(providerCode), ConnectorInstance::getProviderCode, providerCode);
        wrapper.orderByDesc(ConnectorInstance::getUpdateTime);
        return instanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 保存实例（有id更新）
     * @param instance 实例参数
     * @return
     */
    public ConnectorInstance save(ConnectorInstance instance) {
        if (instance == null || !StringUtils.hasText(instance.getInstanceCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "实例编码不能为空");
        }
        if (!StringUtils.hasText(instance.getProviderCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "提供商编码不能为空");
        }
        if (!StringUtils.hasText(instance.getName())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "实例名称不能为空");
        }
        ConnectorProvider provider = registry.getProvider(instance.getProviderCode());
        if (provider == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "未知的提供商: " + instance.getProviderCode());
        }
        // 入站型提供商必须绑定入站目标Agent
        ConnectorDescriptor descriptor = provider.descriptor();
        if (descriptor.isInboundSupported() && !StringUtils.hasText(instance.getAgentCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "入站型提供商实例必须绑定目标Agent编码");
        }
        checkInstanceCodeUnique(instance);
        if (instance.getDbId() == null) {
            instance.setStatus(StringUtils.hasText(instance.getStatus())
                    ? instance.getStatus() : ConnectorCredentialService.STATUS_ENABLED);
            instanceMapper.insert(instance);
        } else {
            requireExisting(instance.getDbId());
            instanceMapper.updateById(instance);
            // 配置变更即时失效提供商侧资源
            notifyChanged(instance);
        }
        log.info("保存连接器实例: code={}, provider={}, agentCode={}",
                instance.getInstanceCode(), instance.getProviderCode(), instance.getAgentCode());
        return instance;
    }

    /**
     * 删除实例
     * @param id
     */
    public void delete(Long id) {
        ConnectorInstance instance = requireExisting(id);
        instanceMapper.deleteById(id);
        notifyDeleted(instance.getInstanceCode());
    }

    /**
     * 启停实例（乐观锁防启停竞态，停用即工具摘除与网关拒绝）
     * @param id
     * @param enabled true启用 false停用
     */
    public void changeStatus(Long id, boolean enabled) {
        ConnectorInstance existing = requireExisting(id);
        existing.setStatus(enabled
                ? ConnectorCredentialService.STATUS_ENABLED : ConnectorCredentialService.STATUS_DISABLED);
        int updated = instanceMapper.updateById(existing);
        if (updated == 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "实例状态已被并发修改,请刷新后重试");
        }
        notifyChanged(existing);
        log.info("变更连接器实例状态: code={}, enabled={}", existing.getInstanceCode(), enabled);
    }

    /**
     * 查询实例暴露的工具清单（实时读提供商描述）
     * @param id
     * @return
     */
    public List<com.yangqiongai.ai.platform.connector.spi.ConnectorToolDefinition> listTools(Long id) {
        ConnectorInstance instance = requireExisting(id);
        ConnectorProvider provider = registry.getProvider(instance.getProviderCode());
        if (provider == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "未知的提供商: " + instance.getProviderCode());
        }
        return provider.descriptor().getTools();
    }

    /**
     * 生成入站回调地址
     * @param id
     * @param baseUrl 平台外部可达基础地址
     * @return
     */
    public String callbackUrl(Long id, String baseUrl) {
        ConnectorInstance instance = requireExisting(id);
        if (!StringUtils.hasText(baseUrl)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "平台外部基础地址不能为空");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + String.format(CALLBACK_PATH_TEMPLATE, instance.getInstanceCode());
    }

    /**
     * 按实例编码查询启用实例（网关路由键）
     * @param instanceCode
     * @return 实例，不存在或已停用返回null
     */
    public ConnectorInstance findEnabledByCode(String instanceCode) {
        if (!StringUtils.hasText(instanceCode)) {
            return null;
        }
        ConnectorInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<ConnectorInstance>()
                .eq(ConnectorInstance::getInstanceCode, instanceCode));
        return instance != null && ConnectorCredentialService.STATUS_ENABLED.equals(instance.getStatus())
                ? instance : null;
    }

    /**
     * 按id查询实例，不存在时抛出资源不存在异常
     * @param id
     * @return
     */
    public ConnectorInstance requireExisting(Long id) {
        if (id == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "实例ID不能为空");
        }
        ConnectorInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, String.valueOf(id));
        }
        return instance;
    }

    /**
     * 校验实例编码在当前隔离域内唯一
     * @param instance
     */
    private void checkInstanceCodeUnique(ConnectorInstance instance) {
        LambdaQueryWrapper<ConnectorInstance> wrapper = new LambdaQueryWrapper<ConnectorInstance>()
                .eq(ConnectorInstance::getInstanceCode, instance.getInstanceCode());
        ConnectorInstance existing = instanceMapper.selectOne(wrapper);
        if (existing != null && !existing.getDbId().equals(instance.getDbId())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "实例编码已存在: " + instance.getInstanceCode());
        }
    }

    /**
     * 通知监听器实例已变更（单个监听器异常不影响其余）
     * @param instance
     */
    private void notifyChanged(ConnectorInstance instance) {
        for (ConnectorInstanceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onChanged(instance);
            } catch (Exception e) {
                log.warn("连接器实例变更通知失败: code={}, listener={}",
                        instance.getInstanceCode(), listener.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 通知监听器实例已删除
     * @param instanceCode 实例编码
     */
    private void notifyDeleted(String instanceCode) {
        for (ConnectorInstanceLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onDeleted(instanceCode);
            } catch (Exception e) {
                log.warn("连接器实例删除通知失败: code={}, listener={}",
                        instanceCode, listener.getClass().getSimpleName(), e);
            }
        }
    }
}
