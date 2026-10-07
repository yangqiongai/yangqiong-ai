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

import java.util.Set;

/**
 * 统一护栏接口
 * <p>
 * 所有护栏实现此接口，通过 {@link HookPoint} 区分挂载点。
 * 实现类注册为Spring Bean后由 {@link GuardrailChain} 自动发现并调度。
 * </p>
 *
 * @author yangqiong
 */
public interface Guardrail {

    /**
     * 本护栏挂载的挂载点集合
     * @return
     */
    Set<HookPoint> hookPoints();

    /**
     * 护栏唯一名称（用于DB覆盖内置规则的匹配键）
     * @return
     */
    String name();

    /**
     * 对content执行检查
     *
     * @param hookPoint 当前触发的挂载点
     * @param content 待检查的文本内容
     * @param context 护栏上下文
     * @return 检查结果
     */
    GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context);

    /**
     * 排序优先级，数值越小越先执行，默认100
     * @return
     */
    default int order() {
        return 100;
    }
}
