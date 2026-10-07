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

import com.yangqiongai.ai.agent.scheduler.model.ScheduleLogInfo;

import java.util.List;

/**
 * 调度执行历史存储
 * @author yangqiong
 */
public interface ScheduleLogRepository {

    /**
     * 保存执行历史
     * @param info
     */
    void save(ScheduleLogInfo info);

    /**
     * 统计调度执行历史总数
     * @param scheduleId
     * @return
     */
    long countByScheduleId(String scheduleId);

    /**
     * 分页查询调度执行历史（按触发时间倒序）
     * @param scheduleId
     * @param pageNum
     * @param pageSize
     * @return
     */
    List<ScheduleLogInfo> pageByScheduleId(String scheduleId, int pageNum, int pageSize);

    /**
     * 查询调度最近N条执行历史（按触发时间倒序）
     * @param scheduleId
     * @param limit
     * @return
     */
    List<ScheduleLogInfo> findRecentByScheduleId(String scheduleId, int limit);
}
