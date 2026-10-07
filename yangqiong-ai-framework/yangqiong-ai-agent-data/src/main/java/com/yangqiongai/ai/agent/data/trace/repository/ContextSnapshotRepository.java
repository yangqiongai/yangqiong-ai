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
package com.yangqiongai.ai.agent.data.trace.repository;

import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;

import java.util.List;

/**
 * Agent模型调用上下文快照存储
 * @author yangqiong
 */
public interface ContextSnapshotRepository {

    /**
     * 批量保存快照(单条失败仅记录日志，不中断)
     * @param snapshots
     */
    void batchSave(List<ContextSnapshotEntity> snapshots);

    /**
     * 按任务ID查询全部快照(按调用序号升序)
     * @param taskId
     * @return
     */
    List<ContextSnapshotEntity> findByTaskId(String taskId);

    /**
     * 按任务ID与调用序号查询快照
     * @param taskId
     * @param callSeq
     * @return
     */
    ContextSnapshotEntity findByTaskIdAndCallSeq(String taskId, int callSeq);
}
