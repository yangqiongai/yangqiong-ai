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
package com.yangqiongai.ai.agent.registry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDefinitionMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentVersionMapper;
import com.yangqiongai.ai.agent.registry.config.AgentConfigValidator;
import com.yangqiongai.ai.agent.registry.event.AgentDisabledEvent;
import com.yangqiongai.ai.agent.registry.materialize.AgentConfigMaterializer;
import com.yangqiongai.ai.agent.registry.model.AgentRegistryErrorCode;
import com.yangqiongai.ai.agent.registry.model.DefinitionStatus;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent注册中心管理
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class AgentRegistryServiceImpl implements AgentRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryServiceImpl.class);

    private static final DateTimeFormatter VERSION_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 运行时Agent缺省排序
     */
    private static final int DEFAULT_SORT_ORDER = 99;

    @Autowired
    private AgentDefinitionMapper definitionMapper;

    @Autowired
    private AgentVersionMapper versionMapper;

    @Autowired
    private AgentConfigMaterializer materializer;

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDefinition createDefinition(AgentDefinition definition) {
        if (definition == null || StringUtils.isBlank(definition.getAgentCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "agentCode不能为空");
        }
        if (getDefinition(definition.getAgentCode()) != null) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "Agent定义已存在: " + definition.getAgentCode());
        }
        definition.setStatus(DefinitionStatus.DRAFT.name());
        if (definition.getRequireApproval() == null) {
            definition.setRequireApproval(0);
        }
        if (definition.getEvalEnabled() == null) {
            definition.setEvalEnabled(0);
        }
        if (definition.getEvalPassThreshold() == null) {
            definition.setEvalPassThreshold(new BigDecimal("80.00"));
        }
        if (definition.getCardEnabled() == null) {
            definition.setCardEnabled(0);
        }
        definitionMapper.insert(definition);
        materializeSkeleton(definition);
        return definition;
    }

    /**
     * 运行时缺位时联动创建骨架Agent（与definition同事务，消除前端两连调不一致中间态）
     * @param definition
     */
    private void materializeSkeleton(AgentDefinition definition) {
        if (agentManager.getByCode(definition.getAgentCode()) != null) {
            return;
        }
        Agent agent = new Agent();
        agent.setAgentCode(definition.getAgentCode());
        agent.setAgentName(definition.getAgentName());
        agent.setDescription(definition.getDescription());
        agent.setCategory(definition.getCategory());
        // 空配置按两态语义运行（仅内置工具与内置技能），版本发布时经物化链路覆盖
        agent.setAgentConfig("{}");
        agent.setStatus(1);
        agent.setSortOrder(DEFAULT_SORT_ORDER);
        agentManager.save(agent);
        log.info("Agent定义创建联动物化运行时骨架完成: agentCode={}", definition.getAgentCode());
    }

    @Override
    public AgentDefinition updateDefinition(String agentCode, AgentDefinition definition) {
        AgentDefinition existing = requireDefinition(agentCode);
        // 仅更新元数据与门禁策略，身份编码不可变更
        existing.setAgentName(definition.getAgentName());
        existing.setDescription(definition.getDescription());
        existing.setCategory(definition.getCategory());
        existing.setRequireApproval(definition.getRequireApproval());
        existing.setEvalEnabled(definition.getEvalEnabled());
        existing.setEvalPassThreshold(definition.getEvalPassThreshold());
        if (definition.getCardEnabled() != null) {
            existing.setCardEnabled(definition.getCardEnabled());
        }
        definitionMapper.updateById(existing);
        return existing;
    }

    @Override
    public AgentDefinition updateCardEnabled(String agentCode, boolean enabled) {
        AgentDefinition existing = requireDefinition(agentCode);
        // 卡片对外发布独立于定义启停，默认禁用需显式开启
        existing.setCardEnabled(enabled ? 1 : 0);
        definitionMapper.updateById(existing);
        return existing;
    }

    @Override
    public AgentDefinition getDefinition(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            return null;
        }
        // 复制模式下同一agentCode可存在于多个作用域，必须限定当前作用域
        return definitionMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getAgentCode, agentCode)
                .eq(AgentDefinition::getScopeId, ScopeContext.getScopeId()));
    }

    @Override
    public Page<AgentDefinition> listDefinitions(int pageNum, int pageSize, String status, String keyword) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.max(pageSize, 1);
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<>();
        // 复制模式下同一agentCode可存在于多个作用域，列表必须限定当前作用域
        wrapper.eq(AgentDefinition::getScopeId, ScopeContext.getScopeId());
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(AgentDefinition::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(AgentDefinition::getAgentCode, keyword)
                    .or().like(AgentDefinition::getAgentName, keyword));
        }
        wrapper.orderByDesc(AgentDefinition::getUpdateTime);
        Long total = definitionMapper.selectCount(wrapper);
        Page<AgentDefinition> page = new Page<>(safePageNum, safePageSize, total == null ? 0 : total);
        if (total != null && total > 0) {
            long offset = (safePageNum - 1) * safePageSize;
            page.setRecords(definitionMapper.selectList(wrapper.last("LIMIT " + offset + ", " + safePageSize)));
        }
        return page;
    }

    @Override
    public AgentDefinition updateStatus(String agentCode, boolean enabled) {
        AgentDefinition existing = requireDefinition(agentCode);
        if (enabled) {
            // DRAFT/DISABLED → ENABLED，存在生效版本时物化启用
            existing.setStatus(DefinitionStatus.ENABLED.name());
            definitionMapper.updateById(existing);
            if (existing.getCurrentVersionId() != null) {
                AgentVersion version = versionMapper.selectById(existing.getCurrentVersionId());
                if (version != null) {
                    materializer.materialize(existing, version);
                }
            }
        } else {
            // 仅ENABLED可禁用，禁用切type_status并发事件
            if (!DefinitionStatus.ENABLED.name().equals(existing.getStatus())) {
                throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "仅启用状态的Agent可以禁用: " + agentCode);
            }
            existing.setStatus(DefinitionStatus.DISABLED.name());
            definitionMapper.updateById(existing);
            materializer.disable(agentCode);
            eventPublisher.publishEvent(new AgentDisabledEvent(agentCode));
        }
        return existing;
    }

    @Override
    public void deleteDefinition(String agentCode) {
        AgentDefinition existing = requireDefinition(agentCode);
        if (!DefinitionStatus.DRAFT.name().equals(existing.getStatus())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "仅草稿状态的Agent定义可以删除: " + agentCode);
        }
        Long versionCount = versionMapper.selectCount(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getScopeId, ScopeContext.getScopeId()));
        if (versionCount != null && versionCount > 0) {
            throw new AiException(AgentRegistryErrorCode.DEFINITION_HAS_VERSIONS.getCode(),
                    AgentRegistryErrorCode.DEFINITION_HAS_VERSIONS.getMessage() + ": " + agentCode);
        }
        definitionMapper.deleteById(existing.getId());
    }

    @Override
    public AgentVersion createVersion(String agentCode, AgentVersion version) {
        requireDefinition(agentCode);
        if (version == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "版本内容不能为空");
        }
        AgentConfigValidator.validate(version.getConfigJson());
        version.setAgentCode(agentCode);
        if (StringUtils.isBlank(version.getVersionNo())) {
            version.setVersionNo(generateVersionNo());
        } else if (getVersionNo(agentCode, version.getVersionNo()) != null) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "版本号已存在: " + version.getVersionNo());
        }
        version.setConfigHash(AgentConfigValidator.hash(version.getConfigJson()));
        version.setStatus(com.yangqiongai.ai.agent.registry.model.VersionStatus.DRAFT.name());
        versionMapper.insert(version);
        return version;
    }

    @Override
    public AgentVersion updateDraft(Long versionId, AgentVersion version) {
        AgentVersion existing = requireVersion(versionId);
        if (!com.yangqiongai.ai.agent.registry.model.VersionStatus.DRAFT.name().equals(existing.getStatus())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "仅草稿版本可编辑: " + existing.getVersionNo());
        }
        if (version.getConfigJson() != null && !version.getConfigJson().isBlank()) {
            AgentConfigValidator.validate(version.getConfigJson());
            existing.setConfigJson(version.getConfigJson());
            // 配置变更重算hash
            existing.setConfigHash(AgentConfigValidator.hash(version.getConfigJson()));
        }
        if (version.getChangelog() != null) {
            existing.setChangelog(version.getChangelog());
        }
        if (version.getEvalDatasetLocations() != null) {
            existing.setEvalDatasetLocations(version.getEvalDatasetLocations());
        }
        versionMapper.updateById(existing);
        return existing;
    }

    @Override
    public List<AgentVersion> listVersions(String agentCode) {
        requireDefinition(agentCode);
        return versionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getScopeId, ScopeContext.getScopeId())
                .orderByDesc(AgentVersion::getCreateTime));
    }

    @Override
    public AgentVersion getVersion(Long versionId) {
        return versionMapper.selectById(versionId);
    }

    /**
     * 加载Agent定义，不存在抛异常
     * @param agentCode
     * @return
     */
    private AgentDefinition requireDefinition(String agentCode) {
        AgentDefinition definition = getDefinition(agentCode);
        if (definition == null) {
            throw new AiException(AgentRegistryErrorCode.DEFINITION_NOT_FOUND.getCode(),
                    AgentRegistryErrorCode.DEFINITION_NOT_FOUND.getMessage() + ": " + agentCode);
        }
        return definition;
    }

    /**
     * 加载版本，不存在抛异常
     * @param versionId
     * @return
     */
    private AgentVersion requireVersion(Long versionId) {
        AgentVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new AiException(AgentRegistryErrorCode.VERSION_NOT_FOUND.getCode(),
                    AgentRegistryErrorCode.VERSION_NOT_FOUND.getMessage() + ": " + versionId);
        }
        return version;
    }

    /**
     * 查询指定版本号
     * @param agentCode
     * @param versionNo
     * @return
     */
    private AgentVersion getVersionNo(String agentCode, String versionNo) {
        return versionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getVersionNo, versionNo)
                .eq(AgentVersion::getScopeId, ScopeContext.getScopeId()));
    }

    /**
     * 生成版本号：v + 时间戳 + 3位随机数
     * @return
     */
    private String generateVersionNo() {
        return "v" + LocalDateTime.now().format(VERSION_NO_FORMAT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
