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
package com.yangqiongai.ai.agent.data.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.agent.data.core.entity.AgentTaskStepEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent任务步骤Mapper
 * @author yangqiong
 */
@Mapper
public interface AgentTaskStepMapper extends BaseMapper<AgentTaskStepEntity> {

    /**
     * 递归查询任务及其所有子任务的步骤（含所有子代的步骤）
     * <p>
     * 通过 parent_task_id 递归任务，再关联步骤，
     * 返回所有任务的全部步骤，按 task_id 和 step_order 排序。
     * </p>
     * @param taskId 根任务ID
     * @return 步骤列表
     */
    @Select("<script>"
            + "WITH RECURSIVE task_tree AS ("
            + "  SELECT task_id FROM ai_agent_task WHERE task_id = #{taskId}"
            + "  UNION ALL"
            + "  SELECT t.task_id FROM ai_agent_task t JOIN task_tree tt ON t.parent_task_id = tt.task_id"
            + ")"
            + "SELECT s.* FROM ai_agent_task_step s "
            + "WHERE s.task_id IN (SELECT task_id FROM task_tree) "
            + "ORDER BY s.task_id, s.step_order"
            + "</script>")
    List<AgentTaskStepEntity> selectStepsByTaskTree(@Param("taskId") String taskId);

    /**
     * 按步骤类型统计某任务的步骤数量
     * @param taskId 任务ID
     * @return 各类型步骤数量
     */
    @Select("SELECT step_type, COUNT(*) AS cnt FROM ai_agent_task_step "
            + "WHERE task_id = #{taskId} GROUP BY step_type")
    List<java.util.Map<String, Object>> selectStepCountByType(@Param("taskId") String taskId);
}

