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
package com.yangqiongai.ai.data.llm.repository;

import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import com.yangqiongai.ai.data.llm.mapper.ModelInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 模型信息
 * @author yangqiong
 */
public class DefaultModelInfoRepository implements ModelInfoRepository {

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    /**
     * 按模型编码查询，当前作用域无配置时回退平台默认域共享模型
     * @param modelCode
     * @return
     */
    @Override
    public Optional<ModelInfo> findByModelCode(String modelCode) {
        LambdaQueryWrapper<com.yangqiongai.ai.data.llm.entity.ModelInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.yangqiongai.ai.data.llm.entity.ModelInfo::getModelCode, modelCode);
        com.yangqiongai.ai.data.llm.entity.ModelInfo entity = modelInfoMapper.selectOne(wrapper);
        if (entity != null) {
            return Optional.of(toModel(entity));
        }
        // 租户拦截器会过滤掉default域数据，租户侧无自定义模型时需显式回退平台共享配置
        if (!ScopeContext.DEFAULT_SCOPE_ID.equals(ScopeContext.getScopeId())) {
            com.yangqiongai.ai.data.llm.entity.ModelInfo global = modelInfoMapper.selectDefaultScopeByModelCode(modelCode);
            if (global != null) {
                return Optional.of(toModel(global));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ModelInfo> findAll() {
        List<com.yangqiongai.ai.data.llm.entity.ModelInfo> entities = modelInfoMapper.selectList(null);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    private ModelInfo toModel(com.yangqiongai.ai.data.llm.entity.ModelInfo entity) {
        ModelInfo model = new ModelInfo();
        model.setId(entity.getId());
        model.setModelCode(entity.getModelCode());
        model.setModelName(entity.getModelName());
        model.setProvider(entity.getProvider());
        model.setModelType(entity.getModelType());
        model.setApiEndpoint(entity.getApiEndpoint());
        model.setApiKey(entity.getApiKey());
        model.setModelConfig(entity.getModelConfig());
        model.setIsDefault(entity.getIsDefault());
        model.setSupportImage(entity.getSupportImage());
        model.setModelStatus(entity.getModelStatus());
        model.setRemark(entity.getRemark());
        return model;
    }
}
