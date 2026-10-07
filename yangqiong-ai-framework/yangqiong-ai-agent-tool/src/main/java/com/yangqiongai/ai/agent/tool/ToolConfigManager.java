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
package com.yangqiongai.ai.agent.tool;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.common.spring.ApplicationContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * 工具配置管理
 * @author yangqiong
 */
@Service
public class ToolConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ToolConfigManager.class);

    @Autowired
    private ToolConfigRepository toolConfigRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void syncToolsFromSpring() {
        List<Tool> tools = ApplicationContextHelper.getBeanList(Tool.class);
        if (tools == null || tools.isEmpty()) {
            log.warn("未发现任何Tool实现");
            return;
        }
        int registered = 0;
        int updated = 0;
        for (Tool tool : tools) {
            try {
                String toolCode = tool.getToolCode();
                ToolConfigInfo existing = getByToolCode(toolCode);
                String toolName = tool.getToolName();
                String toolType = tool.getToolType();
                ToolCategory toolCategory = tool.getToolCategory();
                int toolOrder = tool.getToolOrder();
                String toolDesc = tool.getToolDesc();
                AgentTool annotation = findFirstAgentToolAnnotation(tool.getClass());
                if (annotation != null) {
                    if (toolDesc == null || toolDesc.isBlank()) {
                        toolDesc = annotation.value();
                    }
                    if ((toolName == null || toolName.isBlank() || toolName.equals(tool.getClass().getSimpleName()))
                            && !annotation.name().isBlank()) {
                        toolName = annotation.name();
                    }
                    if (toolType == null || toolType.isBlank()) {
                        toolType = annotation.type();
                    }
                    if (annotation.category() != ToolCategory.CUSTOM) {
                        toolCategory = annotation.category();
                    }
                }
                if (existing == null) {
                    ToolConfigInfo config = new ToolConfigInfo();
                    config.setToolCode(toolCode);
                    config.setToolName(toolName);
                    config.setToolDesc(toolDesc);
                    config.setToolClass(tool.getClass().getName());
                    config.setToolType(toolType);
                    config.setToolCategory(toolCategory != null ? toolCategory.name() : ToolCategory.CUSTOM.name());
                    config.setToolOrder(toolOrder);
                    config.setToolStatus(1);
                    toolConfigRepository.save(config);
                    registered++;
                    log.info("自动注册工具: toolCode={}, class={}", toolCode, tool.getClass().getSimpleName());
                } else {
                    boolean dirty = updateExistingConfig(existing, tool, toolName, toolDesc, toolType, toolCategory, toolOrder);
                    if (dirty) {
                        toolConfigRepository.updateById(existing);
                        updated++;
                        log.info("更新工具配置: toolCode={}", toolCode);
                    }
                }
            } catch (Exception e) {
                log.error("同步工具配置失败,跳过该工具: toolCode={}, class={}",
                        tool.getToolCode(), tool.getClass().getSimpleName(), e);
            }
        }
        log.info("工具同步完成: registered={}, updated={}, total={}", registered, updated, tools.size());
    }

    private boolean updateExistingConfig(ToolConfigInfo existing, Tool tool,
                                         String toolName, String toolDesc,
                                         String toolType, ToolCategory toolCategory, int toolOrder) {
        boolean dirty = false;
        if (!Objects.equals(tool.getClass().getName(), existing.getToolClass())) {
            existing.setToolClass(tool.getClass().getName());
            dirty = true;
        }
        if (!Objects.equals(toolName, existing.getToolName())) {
            existing.setToolName(toolName);
            dirty = true;
        }
        if (!Objects.equals(toolDesc, existing.getToolDesc())) {
            existing.setToolDesc(toolDesc);
            dirty = true;
        }
        if (!Objects.equals(toolType, existing.getToolType())) {
            existing.setToolType(toolType);
            dirty = true;
        }
        String categoryValue = toolCategory != null ? toolCategory.name() : ToolCategory.CUSTOM.name();
        if (!Objects.equals(categoryValue, existing.getToolCategory())) {
            existing.setToolCategory(categoryValue);
            dirty = true;
        }
        if (!Objects.equals(toolOrder, existing.getToolOrder())) {
            existing.setToolOrder(toolOrder);
            dirty = true;
        }
        return dirty;
    }

    private AgentTool findFirstAgentToolAnnotation(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            AgentTool a = m.getAnnotation(AgentTool.class);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    public List<ToolConfigInfo> listAll() {
        return toolConfigRepository.listAll();
    }

    public List<ToolConfigInfo> listByStatus(Integer status) {
        return toolConfigRepository.listByStatus(status);
    }

    public ToolConfigInfo getByToolCode(String toolCode) {
        return toolConfigRepository.getByToolCode(toolCode);
    }

    public boolean toggleStatus(String toolCode) {
        return toolConfigRepository.toggleStatus(toolCode);
    }
}
