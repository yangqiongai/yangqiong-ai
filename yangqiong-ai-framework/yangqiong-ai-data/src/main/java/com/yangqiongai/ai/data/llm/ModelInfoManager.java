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
package com.yangqiongai.ai.data.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import com.yangqiongai.ai.data.llm.mapper.ModelInfoMapper;
import com.yangqiongai.ai.llm.event.ModelConfigChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 模型信息管理
 * @author yangqiong
 */
@Service
public class ModelInfoManager {

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public ModelInfo create(ModelInfo modelInfo) {
        modelInfoMapper.insert(modelInfo);
        return modelInfo;
    }

    public Optional<ModelInfo> findByModelCode(String modelCode) {
        LambdaQueryWrapper<ModelInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelInfo::getModelCode, modelCode);
        ModelInfo modelInfo = modelInfoMapper.selectOne(wrapper);
        return Optional.ofNullable(modelInfo);
    }

    public List<ModelInfo> findAll() {
        return modelInfoMapper.selectList(null);
    }

    public void update(ModelInfo modelInfo) {
        // 雪花id超出前端JS安全整数范围，前端未传可靠id时按业务键modelCode定位
        if (modelInfo.getId() == null) {
            ModelInfo existing = findByModelCode(modelInfo.getModelCode())
                    .orElseThrow(() -> new AiException(AiErrorCode.MODEL_NOT_FOUND, modelInfo.getModelCode()));
            modelInfo.setId(existing.getId());
        }
        modelInfoMapper.updateById(modelInfo);
        // 配置变更后失效各层模型缓存
        eventPublisher.publishEvent(new ModelConfigChangedEvent(modelInfo.getModelCode()));
    }

    public void delete(String modelCode) {
        Optional<ModelInfo> optional = findByModelCode(modelCode);
        if (optional.isPresent()) {
            modelInfoMapper.deleteById(optional.get().getId());
            eventPublisher.publishEvent(new ModelConfigChangedEvent(modelCode));
        }
    }
}
