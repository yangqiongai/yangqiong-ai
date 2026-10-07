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
package com.yangqiongai.ai.data.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 模型信息
 * @author yangqiong
 */
@Mapper
public interface ModelInfoMapper extends BaseMapper<ModelInfo> {

    /**
     * 按模型编码查询平台默认域模型（跳过租户行级过滤，供租户侧回退平台共享模型配置）
     * @param modelCode
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM ai_model_info WHERE model_code = #{modelCode} AND scope_id = 'default' LIMIT 1")
    ModelInfo selectDefaultScopeByModelCode(@Param("modelCode") String modelCode);
}
