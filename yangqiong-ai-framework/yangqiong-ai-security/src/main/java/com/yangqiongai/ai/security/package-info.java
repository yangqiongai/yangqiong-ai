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
/**
 * AI 安全模块（护栏执行引擎）
 * <p>
 * 社区版结构：
 * <ul>
 *   <li><b>guardrails</b>：执行引擎（GuardrailManager / GuardrailChain / HookPoint / GuardrailContext），
 *       {@code guardrails.builtin} 内置基础规则（PII / 敏感词 / Prompt注入）</li>
 *   <li><b>spi</b>：{@code GuardrailRuleRepository} / {@code TriggerAuditStore} 商业实现挂载点，
 *       社区缺省装配内存实现（{@code store} 包），行为与内置规则一致</li>
 *   <li><b>商业能力</b>：规则中心管理面与触发审计落库位于商业模块 yangqiong-ai-security-enterprise，
 *       经 {@code GuardrailAutoConfiguration} 默认实现让位、商业 JDBC 实现 {@code @Primary} 覆盖接入</li>
 * </ul>
 */
package com.yangqiongai.ai.security;
