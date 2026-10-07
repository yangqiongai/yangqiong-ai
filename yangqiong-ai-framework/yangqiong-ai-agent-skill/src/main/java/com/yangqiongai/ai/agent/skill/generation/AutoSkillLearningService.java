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
package com.yangqiongai.ai.agent.skill.generation;

import com.yangqiongai.ai.agent.core.event.TaskCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 自进化技能学习
 * <p>
 * 监听任务完成事件，当任务成功且满足条件时自动触发技能生成。
 * 复用已有的 {@link SkillGenerationService} 生成技能草稿。
 * </p>
 *
 * @author yangqiong
 */
@Service
public class AutoSkillLearningService {

    private static final Logger log = LoggerFactory.getLogger(AutoSkillLearningService.class);

    @Value("${ai.agent.skill.auto-learn.enabled:false}")
    private boolean enabled;

    @Value("${ai.agent.skill.auto-learn.min-input-length:50}")
    private int minInputLength;

    @Autowired
    private SkillGenerationService skillGenerationService;

    /**
     * 监听任务完成事件，异步处理自进化学习
     * @param event
     */
    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (!enabled) {
            return;
        }
        if (!event.isSuccess()) {
            return;
        }
        if (event.getInputText() == null || event.getInputText().length() < minInputLength) {
            return;
        }
        try {
            String skillName = generateSkillName(event);
            String description = buildDescription(event);
            SkillGenerationService.SkillDraft draft = skillGenerationService.generate(skillName, description);
            log.info("自动学习技能完成: taskId={}, draftId={}, skillName={}",
                    event.getTaskId(), draft.getDraftId(), skillName);
        } catch (Exception e) {
            log.warn("自动学习技能失败（不影响主流程）: taskId={}, error={}", event.getTaskId(), e.getMessage());
        }
    }

    /**
     * 根据任务输入生成技能名称
     * @param event
     * @return
     */
    private String generateSkillName(TaskCompletedEvent event) {
        String input = event.getInputText();
        if (input.length() > 30) {
            input = input.substring(0, 30);
        }
        String sanitized = input.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_").replaceAll("_+", "_");
        return "auto_" + sanitized + "_" + System.currentTimeMillis() % 10000;
    }

    /**
     * 构造技能描述
     * @param event
     * @return
     */
    private String buildDescription(TaskCompletedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("从任务执行中自动学习的技能。\n");
        sb.append("Agent: ").append(event.getAgentCode()).append("\n");
        sb.append("用户输入: ").append(truncate(event.getInputText(), 500)).append("\n");
        sb.append("执行结果: ").append(truncate(event.getOutputText(), 500)).append("\n");
        sb.append("请根据以上任务模式，生成可复用的技能定义。");
        return sb.toString();
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
