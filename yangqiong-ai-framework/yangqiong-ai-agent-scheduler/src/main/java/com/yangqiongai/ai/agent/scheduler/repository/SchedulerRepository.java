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
package com.yangqiongai.ai.agent.scheduler.repository;

import com.yangqiongai.ai.agent.scheduler.model.SchedulerInfo;

import java.util.List;

/**
 * Agent定时调度仓库
 * @author yangqiong
 */
public interface SchedulerRepository {

    /**
     * 保存调度
     * @param entity
     */
    void save(SchedulerInfo entity);

    /**
     * 按ID更新
     * @param entity
     */
    void updateById(SchedulerInfo entity);

    /**
     * 按ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 按scheduleId查询
     * @param scheduleId
     * @return
     */
    SchedulerInfo findByScheduleId(String scheduleId);

    /**
     * 按用户ID查询调度列表
     * @param userId
     * @return
     */
    List<SchedulerInfo> listByUserId(String userId);

    /**
     * 查询全部调度列表
     * @return
     */
    List<SchedulerInfo> listAll();
}
