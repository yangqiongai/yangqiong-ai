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
package com.yangqiongai.ai.security.store;

import com.yangqiongai.ai.security.spi.GuardrailRuleEntity;
import com.yangqiongai.ai.security.spi.GuardrailRuleRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 护栏规则内存仓库（社区默认实现，不依赖数据库表；商业规则中心以JDBC仓库@Primary覆盖）
 *
 * @author yangqiong
 */
public class InMemoryGuardrailRuleRepository implements GuardrailRuleRepository {

    private final Map<String, GuardrailRuleEntity> rules = new ConcurrentHashMap<>();

    @Override
    public List<GuardrailRuleEntity> findAllEnabled() {
        return rules.values().stream()
                .filter(rule -> rule.getIsEnabled() != null && rule.getIsEnabled() == 1)
                .collect(Collectors.toList());
    }

    @Override
    public List<GuardrailRuleEntity> findEnabledByHookPoint(String hookPoint) {
        return findAllEnabled().stream()
                .filter(rule -> rule.getHookPoint() != null && rule.getHookPoint().equalsIgnoreCase(hookPoint))
                .collect(Collectors.toList());
    }

    @Override
    public List<GuardrailRuleEntity> findAll() {
        return List.copyOf(rules.values());
    }

    @Override
    public GuardrailRuleEntity findByName(String name) {
        if (name == null) {
            return null;
        }
        return rules.values().stream()
                .filter(rule -> name.equals(rule.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void saveRule(GuardrailRuleEntity rule) {
        // 按name去重，存在则保留原主键更新，不存在则生成主键
        GuardrailRuleEntity existing = findByName(rule.getName());
        if (existing != null) {
            rule.setId(existing.getId());
        } else if (rule.getId() == null || rule.getId().isBlank()) {
            rule.setId(java.util.UUID.randomUUID().toString());
        }
        rules.put(rule.getId(), rule);
    }

    @Override
    public boolean toggleStatus(String ruleId, int isEnabled) {
        GuardrailRuleEntity rule = rules.get(ruleId);
        if (rule == null) {
            return false;
        }
        rule.setIsEnabled(isEnabled);
        return true;
    }

    @Override
    public boolean deleteByRuleId(String ruleId) {
        return rules.remove(ruleId) != null;
    }
}
