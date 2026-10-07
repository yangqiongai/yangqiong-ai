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
package com.yangqiongai.ai.agent.runtime.prompt;

/**
 * 系统提示词分块常量
 * <p>
 * 统一最终系统提示词的markdown分块格式：各注入块以一级标题（#）作为分块标记，
 * 块内次级信息使用二级标题（##）或列表呈现，标题常量同时充当各注入点的判重锚点。
 * </p>
 * @author yangqiong
 */
public final class SystemPromptSections {

    /**
     * 可用技能块标题
     */
    public static final String SKILL_SECTION = "# 可用技能";

    /**
     * 技能使用指引标题，同时作为判重锚点
     */
    public static final String SKILL_GUIDE_SECTION = "## 技能使用指引";

    /**
     * 技能使用指引，紧随技能摘要之后，引导LLM按需加载匹配技能并防止滥用
     */
    public static final String SKILL_USAGE_GUIDE = "\n" + SKILL_GUIDE_SECTION + "\n\n"
            + "用户请求与上述技能匹配时，先调用 load_skill 加载对应技能，并按其工作流与规范执行任务。\n"
            + "没有匹配的技能时，直接依据自身能力回答，不要调用 load_skill。\n";

    /**
     * 环境信息块标题，同时作为判重锚点
     */
    public static final String ENV_SECTION = "# 环境信息";

    /**
     * 工具使用规则块标题，同时作为判重锚点
     */
    public static final String TOOL_RULE_SECTION = "# 工具使用规则";

    /**
     * 工具使用规则，约束精确计算与时效信息必须调用工具，覆盖所有Agent（含自定义系统提示词）
     */
    public static final String TOOL_RULE_HINT = "\n" + TOOL_RULE_SECTION + "\n\n"
            + "- 涉及金额、百分比、多位小数、单位换算等精确计算时，必须调用计算工具完成计算，禁止心算或估算。\n"
            + "- 汇率、节假日调休、当前时刻等时效信息必须调用对应工具查询，禁止凭记忆回答。\n"
            + "- 用户需要二维码时调用二维码工具生成，不要用文字或符号模拟二维码。\n";

    private SystemPromptSections() {
    }
}
