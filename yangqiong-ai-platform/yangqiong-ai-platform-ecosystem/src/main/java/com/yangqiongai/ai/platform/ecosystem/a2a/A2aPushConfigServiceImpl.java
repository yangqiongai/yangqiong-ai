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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.a2a.entity.A2aPushConfig;
import com.yangqiongai.ai.platform.ecosystem.a2a.mapper.A2aPushConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A2A推送配置管理
 * @author yangqiong
 */
public class A2aPushConfigServiceImpl implements A2aPushConfigService {

    @Autowired
    private A2aPushConfigMapper pushConfigMapper;

    @Override
    public A2aPushConfig register(A2aPushConfig config) {
        if (config.getTaskId() == null || config.getTaskId().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "任务ID不能为空");
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "推送URL不能为空");
        }
        A2aPushConfig existing = selectByTaskId(config.getTaskId());
        if (existing == null) {
            config.setStatus(A2aPushConfig.STATUS_ENABLED);
            pushConfigMapper.insert(config);
            return config;
        }
        existing.setUrl(config.getUrl());
        existing.setTokenHeader(config.getTokenHeader());
        existing.setToken(config.getToken());
        existing.setStatus(A2aPushConfig.STATUS_ENABLED);
        pushConfigMapper.updateById(existing);
        return existing;
    }

    @Override
    public A2aPushConfig getByTaskId(String taskId) {
        A2aPushConfig config = selectByTaskId(taskId);
        if (config == null || !A2aPushConfig.STATUS_ENABLED.equals(config.getStatus())) {
            return null;
        }
        return config;
    }

    @Override
    public void delete(String taskId) {
        A2aPushConfig existing = selectByTaskId(taskId);
        if (existing != null) {
            pushConfigMapper.deleteById(existing.getId());
        }
    }

    private A2aPushConfig selectByTaskId(String taskId) {
        return pushConfigMapper.selectOne(new LambdaQueryWrapper<A2aPushConfig>()
                .eq(A2aPushConfig::getTaskId, taskId)
                .last("LIMIT 1"));
    }
}
