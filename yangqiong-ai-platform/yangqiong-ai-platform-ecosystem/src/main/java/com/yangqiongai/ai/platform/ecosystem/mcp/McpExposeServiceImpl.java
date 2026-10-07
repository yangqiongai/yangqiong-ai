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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import com.yangqiongai.ai.platform.ecosystem.mcp.mapper.McpServerExposeMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * MCP暴露白名单管理
 * @author yangqiong
 */
public class McpExposeServiceImpl implements McpExposeService {

    /**
     * 启用状态值
     */
    public static final int ENABLED_ON = 1;

    /**
     * 停用状态值
     */
    public static final int ENABLED_OFF = 0;

    @Autowired
    private McpServerExposeMapper exposeMapper;

    @Override
    public McpServerExpose save(McpServerExpose expose) {
        if (expose.getExposeType() == null || expose.getExposeType().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "暴露类型不能为空");
        }
        if (expose.getExposeCode() == null || expose.getExposeCode().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "暴露编码不能为空");
        }
        // 编码+类型唯一,存在即覆盖更新
        McpServerExpose existing = findByTypeAndCode(expose.getExposeType(), expose.getExposeCode());
        if (existing != null) {
            expose.setId(existing.getId());
            exposeMapper.updateById(expose);
            return expose;
        }
        if (expose.getEnabled() == null) {
            expose.setEnabled(ENABLED_ON);
        }
        if (expose.getRenderAllowed() == null) {
            expose.setRenderAllowed(ENABLED_OFF);
        }
        exposeMapper.insert(expose);
        return expose;
    }

    @Override
    public void toggle(Long id) {
        McpServerExpose existing = requireExisting(id);
        existing.setEnabled(isEnabled(existing) ? ENABLED_OFF : ENABLED_ON);
        exposeMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireExisting(id);
        exposeMapper.deleteById(id);
    }

    @Override
    public McpServerExpose get(Long id) {
        return exposeMapper.selectById(id);
    }

    @Override
    public List<McpServerExpose> list(String exposeType, String exposeCode) {
        LambdaQueryWrapper<McpServerExpose> wrapper = new LambdaQueryWrapper<>();
        if (exposeType != null && !exposeType.isBlank()) {
            wrapper.eq(McpServerExpose::getExposeType, exposeType);
        }
        if (exposeCode != null && !exposeCode.isBlank()) {
            wrapper.like(McpServerExpose::getExposeCode, exposeCode);
        }
        wrapper.orderByDesc(McpServerExpose::getUpdateTime);
        return exposeMapper.selectList(wrapper);
    }

    @Override
    public List<McpServerExpose> listEnabled(String exposeType) {
        return exposeMapper.selectList(new LambdaQueryWrapper<McpServerExpose>()
                .eq(McpServerExpose::getExposeType, exposeType)
                .eq(McpServerExpose::getEnabled, ENABLED_ON));
    }

    @Override
    public McpServerExpose getEnabled(String exposeType, String exposeCode) {
        if (exposeType == null || exposeType.isBlank() || exposeCode == null || exposeCode.isBlank()) {
            return null;
        }
        McpServerExpose expose = findByTypeAndCode(exposeType, exposeCode);
        return isEnabled(expose) ? expose : null;
    }

    private McpServerExpose findByTypeAndCode(String exposeType, String exposeCode) {
        return exposeMapper.selectOne(new LambdaQueryWrapper<McpServerExpose>()
                .eq(McpServerExpose::getExposeType, exposeType)
                .eq(McpServerExpose::getExposeCode, exposeCode)
                .last("LIMIT 1"));
    }

    private McpServerExpose requireExisting(Long id) {
        if (id == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "暴露配置ID不能为空");
        }
        McpServerExpose existing = exposeMapper.selectById(id);
        if (existing == null) {
            throw new AiException(AiErrorCode.NOT_FOUND.getCode(), "暴露配置不存在");
        }
        return existing;
    }

    /**
     * 判断配置是否处于启用状态
     * @param expose
     * @return
     */
    public static boolean isEnabled(McpServerExpose expose) {
        return expose.getEnabled() != null && expose.getEnabled() == ENABLED_ON;
    }
}
