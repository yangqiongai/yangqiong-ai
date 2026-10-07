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
package com.yangqiongai.ai.open.capability.guard;

import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 输出契约守卫
 * <p>
 * 基于JSON Schema校验AI输出是否符合契约定义，支持自动修复。
 * </p>
 * @author yangqiong
 */
public class OutputSchemaGuard {

    private static final Logger log = LoggerFactory.getLogger(OutputSchemaGuard.class);

    private final JsonSchemaValidator validator;
    private final OutputRepairer repairer;

    public OutputSchemaGuard(JsonSchemaValidator validator, OutputRepairer repairer) {
        this.validator = validator;
        this.repairer = repairer;
    }

    /**
     * 校验输出
     * @param output AI输出内容
     * @param spec 能力规格
     * @return 校验通过后的结构化输出
     * @throws OutputViolationException 校验失败时抛出
     */
    public Object validate(Object output, CapabilitySpec spec) {
        ValidationResult result = doValidate(output, spec);
        if (!result.isValid()) {
            log.warn("输出契约校验失败: capability={}, errors={}", spec.getCode(), result.getErrors());
            throw new OutputViolationException(result.getErrors());
        }
        return result.getData();
    }

    /**
     * 校验并自动修复
     * @param output AI输出内容
     * @param spec 能力规格
     * @param repairContext 修复上下文（含原始Prompt和消息历史）
     * @return 修复后的结构化输出
     */
    public Object validateAndRepair(Object output, CapabilitySpec spec, RepairContext repairContext) {
        ValidationResult result = doValidate(output, spec);
        if (result.isValid()) {
            return result.getData();
        }

        log.warn("输出契约校验失败，尝试自动修复: capability={}, errors={}", spec.getCode(), result.getErrors());

        if (repairContext == null) {
            repairContext = new RepairContext("", spec.getCode());
        }

        String schemaContent = getSchemaContent(spec);
        // 尝试修复
        int maxAttempts = spec.getContract() != null ? spec.getContract().getMaxRepairAttempts() : 1;
        Object currentOutput = output;

        for (int i = 0; i < maxAttempts; i++) {
            Object repaired = repairer.repair(currentOutput, schemaContent, repairContext);
            ValidationResult repairResult = doValidate(repaired, spec);
            if (repairResult.isValid()) {
                log.info("输出修复成功: capability={}, attempt={}", spec.getCode(), i + 1);
                return repairResult.getData();
            }
            currentOutput = repaired;
        }

        log.warn("输出修复失败: capability={}", spec.getCode());
        return output;
    }

    private String buildSchemaPath(CapabilitySpec spec) {
        return "capabilities/" + spec.getCode() + "/" + spec.getOutputSchema();
    }

    /**
     * 执行校验，优先使用outputSchemaContent，其次从classpath加载
     * @param output
     * @param spec
     * @return
     */
    private ValidationResult doValidate(Object output, CapabilitySpec spec) {
        if (spec.getOutputSchemaContent() != null) {
            return validator.validateWithSchemaContent(output, spec.getOutputSchemaContent());
        }
        return validator.validate(output, buildSchemaPath(spec));
    }

    /**
     * 获取Schema内容，优先使用outputSchemaContent，其次从classpath加载
     * @param spec
     * @return
     */
    private String getSchemaContent(CapabilitySpec spec) {
        if (spec.getOutputSchemaContent() != null) {
            return spec.getOutputSchemaContent();
        }
        try {
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                            .getResource("classpath:" + buildSchemaPath(spec));
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("加载Schema内容失败: {}", buildSchemaPath(spec), e);
        }
        return "";
    }
}