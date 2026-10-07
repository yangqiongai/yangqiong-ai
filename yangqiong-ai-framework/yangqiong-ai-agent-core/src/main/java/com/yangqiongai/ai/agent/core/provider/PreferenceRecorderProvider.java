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
package com.yangqiongai.ai.agent.core.provider;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;

import java.util.List;

/**
 * 用户偏好记录器提供者
 * <p>
 * 负责从Agent请求中识别用户显式声明的偏好（口令式 + 请求参数式），
 * 并持久化到长期记忆。实现方在ai-memory模块，可选依赖。
 * </p>
 * @author yangqiong
 */
public interface PreferenceRecorderProvider {

    /**
     * 从Agent请求中识别并持久化用户偏好
     * <p>
     * 识别两种来源：
     * 1. 请求参数式：从 request.body.declaredPreferences 读取结构化偏好
     * 2. 口令式：从 request.input 文本中解析"记住/以后用/请默认"等口令
     * </p>
     * @param request
     * @return 已记录的偏好数量（识别失败或无偏好返回0）
     */
    int recordFromRequest(AgentRequest request);

    /**
     * 直接记录一组显式偏好
     * @param userId
     * @param sessionId
     * @param preferences 偏好列表
     * @return 已记录的偏好数量
     */
    int record(String userId, String sessionId, List<DeclaredPreference> preferences);
}
