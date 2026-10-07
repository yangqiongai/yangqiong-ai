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

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.util.SecretCipherUtil;
import com.yangqiongai.ai.platform.connector.entity.ConnectorCredential;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorCredentialMapper;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorField;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 连接器凭证托管
 * <p>
 * 凭证明文AES-GCM加密落库（AAD绑定providerCode防密文挪用），
 * 列表/详情仅返回尾号掩码永不回传密文与明文。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorCredentialService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorCredentialService.class);

    /**
     * 启用状态
     */
    public static final String STATUS_ENABLED = "ENABLED";

    /**
     * 停用状态
     */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 连接器凭证Mapper
     */
    private final ConnectorCredentialMapper credentialMapper;

    /**
     * 连接器实例Mapper（删除时引用校验）
     */
    private final ConnectorInstanceMapper instanceMapper;

    /**
     * 提供商注册中心
     */
    private final ConnectorRegistry registry;

    public ConnectorCredentialService(ConnectorCredentialMapper credentialMapper,
                                      ConnectorInstanceMapper instanceMapper,
                                      ConnectorRegistry registry) {
        this.credentialMapper = credentialMapper;
        this.instanceMapper = instanceMapper;
        this.registry = registry;
    }

    /**
     * 分页查询凭证列表（密文字段置空，仅返回掩码）
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param providerCode 提供商过滤，可空
     * @return
     */
    public Page<ConnectorCredential> page(long pageNum, long pageSize, String providerCode) {
        LambdaQueryWrapper<ConnectorCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(providerCode), ConnectorCredential::getProviderCode, providerCode);
        wrapper.orderByDesc(ConnectorCredential::getUpdateTime);
        Page<ConnectorCredential> page = credentialMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        for (ConnectorCredential credential : page.getRecords()) {
            credential.setCredentialJson(null);
        }
        return page;
    }

    /**
     * 保存凭证（有id更新，凭证内容为空时保留原密钥）
     * @param credential 凭证参数（credentialJson字段承载明文JSON，保存后置空）
     * @return 脱敏后的凭证
     */
    public ConnectorCredential save(ConnectorCredential credential) {
        if (credential == null || !StringUtils.hasText(credential.getProviderCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "提供商编码不能为空");
        }
        if (!StringUtils.hasText(credential.getName())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "凭证名称不能为空");
        }
        ConnectorProvider provider = registry.getProvider(credential.getProviderCode());
        if (provider == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "未知的提供商: " + credential.getProviderCode());
        }
        ConnectorCredential existing = credential.getDbId() != null ? requireExisting(credential.getDbId()) : null;
        ConnectorCredential entity = existing == null ? new ConnectorCredential() : existing;
        entity.setProviderCode(credential.getProviderCode());
        entity.setName(credential.getName());
        entity.setStatus(StringUtils.hasText(credential.getStatus())
                ? credential.getStatus() : STATUS_ENABLED);
        // 凭证内容为空时保留原密钥
        if (StringUtils.hasText(credential.getCredentialJson())) {
            String plainJson = credential.getCredentialJson().trim();
            validateCredentialJson(provider, plainJson);
            entity.setCredentialJson(SecretCipherUtil.encrypt(plainJson, credential.getProviderCode()));
            entity.setMaskedJson(JSON.toJSONString(buildMaskedMap(provider, plainJson)));
        } else if (existing == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "新增凭证时凭证内容不能为空");
        }
        if (existing == null) {
            credentialMapper.insert(entity);
        } else {
            credentialMapper.updateById(entity);
        }
        return masked(entity);
    }

    /**
     * 删除凭证（被实例引用时拒绝）
     * @param id
     */
    public void delete(Long id) {
        ConnectorCredential credential = requireExisting(id);
        Long refCount = instanceMapper.selectCount(new LambdaQueryWrapper<ConnectorInstance>()
                .eq(ConnectorInstance::getCredentialId, id));
        if (refCount != null && refCount > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "凭证已被" + refCount + "个实例引用,无法删除");
        }
        credentialMapper.deleteById(id);
        log.info("删除连接器凭证: id={}, name={}, provider={}",
                id, credential.getName(), credential.getProviderCode());
    }

    /**
     * 凭证连通性测试
     * @param id
     * @return 失败原因，通过时返回null
     */
    public String test(Long id) {
        ConnectorCredential credential = requireExisting(id);
        ConnectorProvider provider = registry.getProvider(credential.getProviderCode());
        if (provider == null) {
            return "未知的提供商: " + credential.getProviderCode();
        }
        try {
            return provider.testCredential(loadCredentialView(credential));
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * 加载并解密凭证视图（含必填校验）
     * @param credentialId 凭证ID，为空返回空视图（免凭证提供商）
     * @return
     */
    public ConnectorCredentialView loadCredentialView(Long credentialId) {
        if (credentialId == null) {
            return new ConnectorCredentialView(Map.of());
        }
        ConnectorCredential credential = requireExisting(credentialId);
        return loadCredentialView(credential);
    }

    /**
     * 解密指定凭证为一次性视图
     * @param credential
     * @return
     */
    public ConnectorCredentialView loadCredentialView(ConnectorCredential credential) {
        ConnectorProvider provider = registry.getProvider(credential.getProviderCode());
        String plainJson = SecretCipherUtil.decrypt(credential.getCredentialJson(), credential.getProviderCode());
        Map<String, String> values = parseCredentialJson(plainJson);
        ConnectorCredentialView view = new ConnectorCredentialView(values);
        if (provider != null) {
            view.validate(provider.descriptor().getCredentialFields());
        }
        return view;
    }

    /**
     * 构建脱敏副本（密文与掩码置空）
     * @param entity
     * @return
     */
    public ConnectorCredential masked(ConnectorCredential entity) {
        ConnectorCredential masked = new ConnectorCredential();
        masked.setDbId(entity.getDbId());
        masked.setProviderCode(entity.getProviderCode());
        masked.setName(entity.getName());
        masked.setStatus(entity.getStatus());
        masked.setMaskedJson(entity.getMaskedJson());
        masked.setScopeId(entity.getScopeId());
        masked.setCreateTime(entity.getCreateTime());
        masked.setUpdateTime(entity.getUpdateTime());
        return masked;
    }

    /**
     * 按id查询凭证，不存在时抛出资源不存在异常
     * @param id
     * @return
     */
    public ConnectorCredential requireExisting(Long id) {
        if (id == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "凭证ID不能为空");
        }
        ConnectorCredential credential = credentialMapper.selectById(id);
        if (credential == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, String.valueOf(id));
        }
        return credential;
    }

    /**
     * 校验凭证JSON必填字段完整性
     * @param provider
     * @param plainJson 明文凭证JSON
     */
    private void validateCredentialJson(ConnectorProvider provider, String plainJson) {
        Map<String, String> values = parseCredentialJson(plainJson);
        for (ConnectorField field : provider.descriptor().getCredentialFields()) {
            if (field.isRequired() && !StringUtils.hasText(values.get(field.getName()))) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "凭证缺少必填字段: " + field.getName());
            }
        }
    }

    /**
     * 对敏感字段生成尾号掩码
     * @param provider
     * @param plainJson 明文凭证JSON
     * @return 字段名->掩码值
     */
    private Map<String, String> buildMaskedMap(ConnectorProvider provider, String plainJson) {
        Map<String, String> values = parseCredentialJson(plainJson);
        Map<String, String> masked = new LinkedHashMap<>();
        for (ConnectorField field : provider.descriptor().getCredentialFields()) {
            String value = values.get(field.getName());
            if (StringUtils.hasText(value)) {
                masked.put(field.getName(), field.isSecret()
                        ? SecretCipherUtil.mask(value) : value);
            }
        }
        return masked;
    }

    /**
     * 解析凭证JSON为字符串Map
     * @param plainJson
     * @return
     */
    private Map<String, String> parseCredentialJson(String plainJson) {
        if (!StringUtils.hasText(plainJson)) {
            return Map.of();
        }
        try {
            return JSON.parseObject(plainJson, Map.class);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "凭证内容必须为JSON格式");
        }
    }
}
