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
package com.yangqiongai.ai.agent.data.mcp.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfigInfo;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigRepository;
import com.yangqiongai.ai.agent.data.mcp.entity.McpServerConfigEntity;
import com.yangqiongai.ai.agent.data.mcp.mapper.McpServerConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP服务配置仓库默认实现
 * @author yangqiong
 */
public class DefaultMcpServerConfigRepository implements McpServerConfigRepository {

    @Autowired
    private McpServerConfigMapper mcpServerConfigMapper;

    @Override
    public List<McpServerConfigInfo> list() {
        return mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfigEntity>()).stream().map(this::toInfo).toList();
    }

    @Override
    public McpServerConfigInfo getByServerCode(String serverCode) {
        McpServerConfigEntity entity = mcpServerConfigMapper.selectOne(new LambdaQueryWrapper<McpServerConfigEntity>()
                .eq(McpServerConfigEntity::getServerCode, serverCode));
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public List<McpServerConfigInfo> listEnabled() {
        return mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfigEntity>()
                .eq(McpServerConfigEntity::getServerStatus, 1)).stream().map(this::toInfo).toList();
    }

    @Override
    public void save(McpServerConfigInfo info) {
        McpServerConfigEntity entity = toEntity(info);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        mcpServerConfigMapper.insert(entity);
        info.setId(entity.getId());
    }

    @Override
    public void updateById(McpServerConfigInfo info) {
        McpServerConfigEntity entity = toEntity(info);
        entity.setUpdateTime(LocalDateTime.now());
        mcpServerConfigMapper.updateById(entity);
    }

    @Override
    public boolean toggleStatus(String serverCode, int status) {
        LambdaUpdateWrapper<McpServerConfigEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(McpServerConfigEntity::getServerCode, serverCode)
                .set(McpServerConfigEntity::getServerStatus, status)
                .set(McpServerConfigEntity::getUpdateTime, LocalDateTime.now());
        if (status == 0) {
            wrapper.set(McpServerConfigEntity::getOfflineReason, null);
        }
        return mcpServerConfigMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean autoOffline(String serverCode, String reason) {
        LambdaUpdateWrapper<McpServerConfigEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(McpServerConfigEntity::getServerCode, serverCode)
                .eq(McpServerConfigEntity::getServerStatus, 1)
                .set(McpServerConfigEntity::getServerStatus, 0)
                .set(McpServerConfigEntity::getOfflineReason, reason)
                .set(McpServerConfigEntity::getUpdateTime, LocalDateTime.now());
        return mcpServerConfigMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return mcpServerConfigMapper.deleteById(id) > 0;
    }

    @Override
    public List<McpServerConfigInfo> findRecoverableServices() {
        return mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfigEntity>()
                .eq(McpServerConfigEntity::getServerStatus, 1)
                .isNotNull(McpServerConfigEntity::getOfflineReason)).stream().map(this::toInfo).toList();
    }

    @Override
    public List<McpServerConfigInfo> listByServerCodes(List<String> serverCodes) {
        if (serverCodes == null || serverCodes.isEmpty()) {
            return List.of();
        }
        return mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfigEntity>()
                .eq(McpServerConfigEntity::getServerStatus, 1)
                .in(McpServerConfigEntity::getServerCode, serverCodes)).stream().map(this::toInfo).toList();
    }

    private McpServerConfigInfo toInfo(McpServerConfigEntity entity) {
        McpServerConfigInfo info = new McpServerConfigInfo();
        info.setId(entity.getId());
        info.setServerCode(entity.getServerCode());
        info.setServerName(entity.getServerName());
        info.setTransportType(entity.getTransportType());
        info.setConnectionConfig(entity.getConnectionConfig());
        info.setEnabledTools(entity.getEnabledTools());
        info.setDisabledTools(entity.getDisabledTools());
        info.setServerStatus(entity.getServerStatus());
        info.setRemark(entity.getRemark());
        info.setOfflineReason(entity.getOfflineReason());
        info.setCategory(entity.getCategory());
        return info;
    }

    private McpServerConfigEntity toEntity(McpServerConfigInfo info) {
        McpServerConfigEntity entity = new McpServerConfigEntity();
        entity.setId(info.getId());
        entity.setServerCode(info.getServerCode());
        entity.setServerName(info.getServerName());
        entity.setTransportType(info.getTransportType());
        entity.setConnectionConfig(info.getConnectionConfig());
        entity.setEnabledTools(info.getEnabledTools());
        entity.setDisabledTools(info.getDisabledTools());
        entity.setServerStatus(info.getServerStatus());
        entity.setRemark(info.getRemark());
        entity.setOfflineReason(info.getOfflineReason());
        entity.setCategory(info.getCategory());
        return entity;
    }
}
