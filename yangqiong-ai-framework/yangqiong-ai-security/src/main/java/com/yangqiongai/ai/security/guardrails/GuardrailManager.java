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
package com.yangqiongai.ai.security.guardrails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.security.spi.GuardrailRuleEntity;
import com.yangqiongai.ai.security.spi.GuardrailRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 护栏动态管理器
 * <p>
 * 通过 {@link GuardrailRuleRepository} SPI加载规则（社区默认内存实现，商业规则中心JDBC覆盖），
 * 与内置Guardrail Bean合并，同名规则覆盖内置规则，支持定时刷新。
 * </p>
 *
 * @author yangqiong
 */
public class GuardrailManager {

    private static final Logger log = LoggerFactory.getLogger(GuardrailManager.class);

    @Autowired
    private GuardrailRuleRepository guardrailRuleRepository;

    @Autowired
    private List<Guardrail> builtInGuardrails;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 按HookPoint分组的有效护栏缓存
     */
    private final Map<HookPoint, List<Guardrail>> guardrailsCache = new ConcurrentHashMap<>();

    /**
     * DB规则中被DB覆盖的内置规则名称集合
     */
    private volatile Set<String> overriddenBuiltInNames = Set.of();

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 定时刷新护栏规则（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void reload() {
        try {
            List<GuardrailRuleEntity> dbRules = guardrailRuleRepository.findAllEnabled();
            List<Guardrail> dbGuardrails = buildDbGuardrails(dbRules);
            Set<String> dbNames = dbGuardrails.stream()
                    .map(Guardrail::name)
                    .collect(Collectors.toSet());

            // 内置规则：不被DB同名覆盖的
            List<Guardrail> effectiveBuiltIn = builtInGuardrails.stream()
                    .filter(g -> !dbNames.contains(g.name()))
                    .collect(Collectors.toList());

            // 合并：内置 + DB
            List<Guardrail> all = new ArrayList<>(effectiveBuiltIn);
            all.addAll(dbGuardrails);

            // 按HookPoint分组缓存
            Map<HookPoint, List<Guardrail>> newCache = new EnumMap<>(HookPoint.class);
            for (HookPoint hp : HookPoint.values()) {
                List<Guardrail> forHook = all.stream()
                        .filter(g -> g.hookPoints().contains(hp))
                        .sorted(Comparator.comparingInt(Guardrail::order))
                        .collect(Collectors.toList());
                newCache.put(hp, forHook);
            }

            guardrailsCache.clear();
            guardrailsCache.putAll(newCache);
            overriddenBuiltInNames = dbNames;

            log.info("护栏规则加载完成: dbRules={}, builtInCount={}, overriddenNames={}",
                    dbRules.size(), builtInGuardrails.size(),
                    dbNames.stream().filter(n -> builtInGuardrails.stream()
                            .anyMatch(g -> g.name().equals(n))).collect(Collectors.toSet()));
        } catch (Exception e) {
            log.error("护栏规则加载失败，保留缓存", e);
        }
    }

    /**
     * 获取指定挂载点的有效护栏列表
     * @param hookPoint
     * @return
     */
    public List<Guardrail> getEffectiveGuardrails(HookPoint hookPoint) {
        return guardrailsCache.getOrDefault(hookPoint, List.of());
    }

    /**
     * 获取所有内置护栏（供管理接口查看）
     * @return
     */
    public List<Guardrail> getBuiltInGuardrails() {
        return Collections.unmodifiableList(builtInGuardrails);
    }

    /**
     * 获取被覆盖的内置规则名称
     * @return
     */
    public Set<String> getOverriddenBuiltInNames() {
        return overriddenBuiltInNames;
    }

    /**
     * 将DB规则构建为Guardrail实例
     */
    private List<Guardrail> buildDbGuardrails(List<GuardrailRuleEntity> rules) {
        List<Guardrail> result = new ArrayList<>();
        for (GuardrailRuleEntity rule : rules) {
            try {
                Guardrail guardrail = buildFromRule(rule);
                if (guardrail != null) {
                    result.add(guardrail);
                }
            } catch (Exception e) {
                log.warn("构建DB护栏规则失败: name={}, ruleType={}", rule.getName(), rule.getRuleType(), e);
            }
        }
        return result;
    }

    /**
     * 根据ruleType构建具体的Guardrail实例
     */
    private Guardrail buildFromRule(GuardrailRuleEntity rule) {
        String ruleType = rule.getRuleType();
        Set<HookPoint> hookPoints = parseHookPoints(rule.getHookPoint());
        int order = rule.getSortOrder() != null ? rule.getSortOrder() : 100;

        return switch (ruleType) {
            case "KEYWORD" -> buildKeywordGuardrail(rule, hookPoints, order);
            case "REGEX" -> buildRegexGuardrail(rule, hookPoints, order);
            case "PII" -> new PiiGuardrail();
            default -> {
                log.warn("未知的护栏规则类型: ruleType={}", ruleType);
                yield null;
            }
        };
    }

    private Guardrail buildKeywordGuardrail(GuardrailRuleEntity rule, Set<HookPoint> hookPoints, int order) {
        List<String> keywords = parseKeywords(rule.getKeywords());
        if (keywords == null || keywords.isEmpty()) {
            log.warn("KEYWORD规则缺少关键词: name={}", rule.getName());
            return null;
        }
        return new KeywordGuardrail(rule.getName(), hookPoints, keywords, order, rule.getBlockMessage());
    }

    private Guardrail buildRegexGuardrail(GuardrailRuleEntity rule, Set<HookPoint> hookPoints, int order) {
        if (rule.getPattern() == null || rule.getPattern().isBlank()) {
            log.warn("REGEX规则缺少正则表达式: name={}", rule.getName());
            return null;
        }
        Pattern pattern = Pattern.compile(rule.getPattern());
        return new RegexGuardrail(rule.getName(), hookPoints, pattern, order, rule.getBlockMessage());
    }

    /**
     * 解析挂载点字符串为HookPoint集合
     */
    private Set<HookPoint> parseHookPoints(String hookPointStr) {
        if (hookPointStr == null || hookPointStr.isBlank()) {
            return Set.of(HookPoint.INPUT);
        }
        try {
            Set<HookPoint> result = new LinkedHashSet<>();
            for (String part : hookPointStr.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(HookPoint.valueOf(trimmed.toUpperCase()));
                }
            }
            return result.isEmpty() ? Set.of(HookPoint.INPUT) : result;
        } catch (IllegalArgumentException e) {
            log.warn("无效的挂载点: hookPoint={}", hookPointStr);
            return Set.of(HookPoint.INPUT);
        }
    }

    /**
     * 解析关键词JSON数组
     */
    private List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keywordsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析关键词JSON失败: keywords={}", keywordsJson, e);
            return List.of();
        }
    }
}
