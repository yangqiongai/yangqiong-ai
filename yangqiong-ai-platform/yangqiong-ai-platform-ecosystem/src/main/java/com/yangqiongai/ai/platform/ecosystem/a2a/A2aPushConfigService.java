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

import com.yangqiongai.ai.platform.ecosystem.a2a.entity.A2aPushConfig;

/**
 * A2A推送配置管理
 * @author yangqiong
 */
public interface A2aPushConfigService {

    /**
     * 注册任务推送配置(同任务覆盖更新)
     * @param config
     * @return
     */
    A2aPushConfig register(A2aPushConfig config);

    /**
     * 查询任务的启用推送配置
     * @param taskId
     * @return
     */
    A2aPushConfig getByTaskId(String taskId);

    /**
     * 删除任务推送配置
     * @param taskId
     * @return
     */
    void delete(String taskId);
}
