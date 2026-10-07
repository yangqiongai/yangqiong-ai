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
package com.yangqiongai.ai.open.capability.catalog;

import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 能力目录
 * <p>
 * 管理所有能力规格，能力定义以数据库为唯一来源。
 * 启动时从数据库读取全部能力定义：
 *    - enabled=false: 禁用该能力
 *    - definitionJson不为空: 解析为完整CapabilitySpec，注册到目录中
 * </p>
 * @author yangqiong
 */
public class CapabilityCatalog {

    private static final Logger log = LoggerFactory.getLogger(CapabilityCatalog.class);

    private final Map<String, CapabilitySpec> specs = new ConcurrentHashMap<>();

    /**
     * 已禁用的能力编码集合
     */
    private final Map<String, Boolean> disabledCodes = new ConcurrentHashMap<>();

    /**
     * 注册能力规格（同名时以最新版本为准）
     * @param spec
     */
    public void register(CapabilitySpec spec) {
        if (spec == null || spec.getCode() == null) {
            return;
        }
        // 版本比较：已注册版本更高时跳过
        CapabilitySpec existing = specs.get(spec.getCode());
        if (shouldSkipByVersion(existing, spec)) {
            return;
        }
        specs.put(spec.getCode(), spec);
        log.info("注册能力规格: code={}, name={}, version={}",
                spec.getCode(), spec.getName(), spec.getVersion());
    }

    /**
     * 合并数据库能力定义（支持新增/覆盖/禁用）
     * <p>
     * 版本比较规则：
     * 1. 同名同版本：以数据库为准
     * 2. 同名不同版本：以最新版本号为准，只生效最新版本号
     * 3. 版本号为空时：视为兼容降级，数据库版本覆盖
     * </p>
     * @param entity 数据库能力定义实体
     */
    public void mergeDatabaseDefinition(CapabilityDefinition entity) {
        if (entity == null || entity.getCode() == null) {
            return;
        }
        // 禁用能力（只有明确设置为false时才禁用）
        if (entity.getEnabled() != null && !entity.getEnabled()) {
            disabledCodes.put(entity.getCode(), Boolean.TRUE);
            log.info("数据库禁用能力: code={}", entity.getCode());
            return;
        }
        // 解析并注册能力定义（覆盖或新增）
        if (entity.getDefinitionJson() != null) {
            CapabilitySpec spec = CapabilityLoader.parseSpecFromJson(entity.getDefinitionJson());
            if (spec != null) {
                // 版本比较：数据库版本 < 现有版本时跳过
                CapabilitySpec existing = specs.get(spec.getCode());
                if (shouldSkipByVersion(existing, spec)) {
                    return;
                }
                // definitionJson为唯一来源，独立列仅在内容缺失时兜底（历史数据兼容）
                if (spec.getInputSchemaContent() == null && entity.getInputSchema() != null) {
                    spec.setInputSchemaContent(entity.getInputSchema());
                }
                if (spec.getOutputSchemaContent() == null && entity.getOutputSchema() != null) {
                    spec.setOutputSchemaContent(entity.getOutputSchema());
                }
                if (spec.getPromptTemplateContent() == null && entity.getPromptTemplate() != null) {
                    spec.setPromptTemplateContent(entity.getPromptTemplate());
                }
                specs.put(spec.getCode(), spec);
                log.info("数据库注册能力: code={}, name={}, version={}",
                        spec.getCode(), spec.getName(), spec.getVersion());
            }
        }
    }

    /**
     * 注销能力规格（删除数据库定义后调用，能力从目录移除）
     * @param code
     */
    public void unregister(String code) {
        if (code == null) {
            return;
        }
        specs.remove(code);
        disabledCodes.remove(code);
    }

    /**
     * 判断是否因版本号过低而跳过数据库定义
     * @param existing 当前已注册的规格
     * @param dbSpec 数据库解析的规格
     * @return true表示跳过，false表示允许覆盖
     */
    private boolean shouldSkipByVersion(CapabilitySpec existing, CapabilitySpec dbSpec) {
        if (existing == null) {
            return false;
        }
        String existingVersion = existing.getVersion();
        String dbVersion = dbSpec.getVersion();
        // 两者都无版本号，按原有逻辑覆盖
        if (existingVersion == null && dbVersion == null) {
            return false;
        }
        // 现有有版本号，数据库无版本号，保留现有
        if (existingVersion != null && dbVersion == null) {
            log.info("数据库版本为空，低于现有版本({})，跳过: code={}",
                    existingVersion, existing.getCode());
            return true;
        }
        // 数据库有版本号，现有无版本号，允许覆盖
        if (existingVersion == null) {
            return false;
        }
        // 两者都有版本号，比较版本大小
        int cmp = compareVersions(dbVersion, existingVersion);
        if (cmp < 0) {
            log.info("数据库版本({})低于现有版本({})，跳过: code={}",
                    dbVersion, existingVersion, existing.getCode());
            return true;
        }
        return false;
    }

    /**
     * 比较语义化版本号（支持 x.y.z 格式）
     * @param v1
     * @param v2
     * @return v1 > v2 返回正数，v1 < v2 返回负数，相等返回0
     */
    static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    /**
     * 版本号补丁位递增（1.2.3 → 1.2.4，保证每次保存形成新版本）
     * @param version
     * @return 空版本返回1.0.0，末位非数字时追加.1
     */
    public static String bumpPatchVersion(String version) {
        if (version == null || version.isBlank()) {
            return "1.0.0";
        }
        String[] parts = version.split("\\.");
        try {
            int last = Integer.parseInt(parts[parts.length - 1]);
            parts[parts.length - 1] = String.valueOf(last + 1);
            return String.join(".", parts);
        } catch (NumberFormatException e) {
            return version + ".1";
        }
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 按编码获取能力规格
     * @param code
     * @return
     */
    public CapabilitySpec get(String code) {
        if (isDisabled(code)) {
            return null;
        }
        return specs.get(code);
    }

    /**
     * 列出所有启用的能力规格
     * @return
     */
    public List<CapabilitySpec> list() {
        return specs.values().stream()
                .filter(spec -> !isDisabled(spec.getCode()))
                .collect(Collectors.toList());
    }

    /**
     * 按分类列出能力
     * @param category
     * @return
     */
    public List<CapabilitySpec> listByCategory(String category) {
        if (category == null) {
            return Collections.emptyList();
        }
        return specs.values().stream()
                .filter(spec -> !isDisabled(spec.getCode()))
                .filter(spec -> category.equals(spec.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 检查能力是否启用
     * @param code
     * @return
     */
    public boolean isEnabled(String code) {
        return specs.containsKey(code) && !isDisabled(code);
    }

    private boolean isDisabled(String code) {
        return Boolean.TRUE.equals(disabledCodes.get(code));
    }
}