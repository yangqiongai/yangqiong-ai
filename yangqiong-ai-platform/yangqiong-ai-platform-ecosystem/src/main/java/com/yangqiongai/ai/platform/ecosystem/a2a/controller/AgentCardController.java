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
package com.yangqiongai.ai.platform.ecosystem.a2a.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryService;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aCardSigner;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCardSignature;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A2A代理卡端点
 * <p>
 * 由注册中心definition+PUBLISHED版本快照生成规范卡，支持主密钥派生签名的签名卡响应头。
 * </p>
 * @author yangqiong
 */
@Tag(name = "A2A代理卡端点")
@RestController
public class AgentCardController {

    private static final Logger log = LoggerFactory.getLogger(AgentCardController.class);

    /**
     * 签名卡响应头
     */
    public static final String SIGNATURE_HEADER = "X-A2A-Card-Signature";

    /**
     * 平台传输协议标识
     */
    private static final String TRANSPORT = "HTTP+JSON";

    private final AgentRegistryService registryService;

    private final A2aCardSigner cardSigner;

    private final A2aProperties properties;

    private final String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentCardController(AgentRegistryService registryService, A2aCardSigner cardSigner,
                               A2aProperties properties,
                               @Value("${ai.ecosystem.a2a.base-url:http://localhost:8082}") String baseUrl) {
        this.registryService = registryService;
        this.cardSigner = cardSigner;
        this.properties = properties;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : "";
    }

    /**
     * 获取代理卡(well-known,agentCode缺省取平台默认配置)
     * @param agentCode
     * @return
     */
    @Operation(summary = "获取A2A代理卡")
    @GetMapping("/.well-known/agent-card.json")
    public ResponseEntity<AgentCard> getAgentCard(
            @RequestParam(required = false) String agentCode) {
        String code = agentCode != null && !agentCode.isBlank() ? agentCode : properties.getDefaultAgentCode();
        if (code == null || code.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "agentCode参数不能为空");
        }
        AgentCard card = buildCard(code);
        if (properties.isCardSignatureEnabled()) {
            String compact = cardSigner.sign(toJson(card));
            card = withSignature(card, compact);
            return ResponseEntity.ok().header(SIGNATURE_HEADER, compact).body(card);
        }
        return ResponseEntity.ok(card);
    }

    /**
     * 由注册中心定义与已发布版本生成代理卡
     * @param agentCode
     * @return
     */
    private AgentCard buildCard(String agentCode) {
        AgentDefinition definition = registryService.getDefinition(agentCode);
        // 不存在、停用与未开启卡片对外发布同响应，不暴露存在性
        if (definition == null || !"ENABLED".equals(definition.getStatus())
                || definition.getCardEnabled() == null || definition.getCardEnabled() != 1) {
            throw new AiException(AiErrorCode.NOT_FOUND.getCode(), "Agent不存在: " + agentCode);
        }
        AgentVersion version = resolvePublishedVersion(definition);
        String versionNo = version != null ? version.getVersionNo() : "0.0.0";

        AgentSkill skill = AgentSkill.builder()
                .id(UUID.nameUUIDFromBytes(agentCode.getBytes()).toString())
                .name(definition.getAgentName() != null ? definition.getAgentName() : agentCode)
                .description(definition.getDescription() != null ? definition.getDescription() : agentCode)
                .tags(List.of(definition.getCategory() != null ? definition.getCategory() : "general"))
                .build();

        return AgentCard.builder()
                .name(definition.getAgentName() != null ? definition.getAgentName() : agentCode)
                .description(definition.getDescription() != null ? definition.getDescription() : agentCode)
                .version(versionNo)
                .url(baseUrl + "/a2a/v1")
                .preferredTransport(TRANSPORT)
                .supportedInterfaces(List.of(new AgentInterface(TRANSPORT, baseUrl + "/a2a/v1")))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of(skill))
                .build();
    }

    /**
     * 生成签名卡(JWS compact拆段入卡内signatures,响应头同时携带完整JWS)
     * @param card
     * @param compact
     * @return
     */
    private AgentCard withSignature(AgentCard card, String compact) {
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new IllegalStateException("JWS格式异常，无法构造签名卡");
        }
        AgentCardSignature signature = AgentCardSignature.builder()
                .protectedHeader(parts[0] + "." + parts[1])
                .signature(parts[2])
                .build();
        return AgentCard.builder(card).signatures(List.of(signature)).build();
    }

    /**
     * 解析已发布版本快照(优先currentVersionId定位)
     * @param definition
     * @return
     */
    private AgentVersion resolvePublishedVersion(AgentDefinition definition) {
        if (definition.getCurrentVersionId() != null) {
            AgentVersion version = registryService.getVersion(definition.getCurrentVersionId());
            if (version != null && "PUBLISHED".equals(version.getStatus())) {
                return version;
            }
        }
        return registryService.listVersions(definition.getAgentCode()).stream()
                .filter(v -> "PUBLISHED".equals(v.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private String toJson(AgentCard card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException("代理卡序列化失败", e);
        }
    }
}
