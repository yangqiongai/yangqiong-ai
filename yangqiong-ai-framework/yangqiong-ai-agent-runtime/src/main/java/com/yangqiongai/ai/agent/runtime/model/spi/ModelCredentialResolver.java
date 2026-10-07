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
package com.yangqiongai.ai.agent.runtime.model.spi;

/**
 * 模型凭据解析器
 * <p>
 * 按隔离域维度解析模型API密钥，注入后插入模型密钥解析链最前端；
 * 返回null时走既有解析链（模型配置→options→默认配置）。
 * </p>
 * @author yangqiong
 */
public interface ModelCredentialResolver {

    /**
     * 解析隔离域级模型密钥
     * @param modelCode 模型编码
     * @param scopeId 隔离域ID，可为null
     * @return API密钥，无scope级凭据时返回null
     */
    String resolveApiKey(String modelCode, String scopeId);

    /**
     * 失效解析缓存
     * @param modelCode 模型编码
     * @param scopeId 隔离域ID，可为null
     */
    void evict(String modelCode, String scopeId);
}
