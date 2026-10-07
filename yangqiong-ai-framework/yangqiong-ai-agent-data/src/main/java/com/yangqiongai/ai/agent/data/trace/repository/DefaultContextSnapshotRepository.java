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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.mapper.ContextSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

/**
 * Agent模型调用上下文快照存储
 * @author yangqiong
 */
public class DefaultContextSnapshotRepository implements ContextSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextSnapshotRepository.class);

    @Autowired
    private ContextSnapshotMapper contextSnapshotMapper;

    @Override
    public void batchSave(List<ContextSnapshotEntity> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (ContextSnapshotEntity snapshot : snapshots) {
            try {
                contextSnapshotMapper.insert(snapshot);
            } catch (DuplicateKeyException e) {
                // 模型调用失败重试会以相同taskId+callSeq再次产生快照，唯一键冲突时以最新一次为准回退更新
                LambdaUpdateWrapper<ContextSnapshotEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(ContextSnapshotEntity::getTaskId, snapshot.getTaskId())
                        .eq(ContextSnapshotEntity::getCallSeq, snapshot.getCallSeq())
                        .set(ContextSnapshotEntity::getTraceId, snapshot.getTraceId())
                        .set(ContextSnapshotEntity::getModelCode, snapshot.getModelCode())
                        .set(ContextSnapshotEntity::getSnapshotJson, snapshot.getSnapshotJson())
                        .set(ContextSnapshotEntity::getMsgCount, snapshot.getMsgCount())
                        .set(ContextSnapshotEntity::getTotalChars, snapshot.getTotalChars());
                contextSnapshotMapper.update(null, wrapper);
            } catch (Exception e) {
                log.warn("上下文快照落库失败: taskId={}, callSeq={}", snapshot.getTaskId(), snapshot.getCallSeq(), e);
            }
        }
    }

    @Override
    public List<ContextSnapshotEntity> findByTaskId(String taskId) {
        LambdaQueryWrapper<ContextSnapshotEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContextSnapshotEntity::getTaskId, taskId)
                .orderByAsc(ContextSnapshotEntity::getCallSeq);
        return contextSnapshotMapper.selectList(wrapper);
    }

    @Override
    public ContextSnapshotEntity findByTaskIdAndCallSeq(String taskId, int callSeq) {
        LambdaQueryWrapper<ContextSnapshotEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContextSnapshotEntity::getTaskId, taskId)
                .eq(ContextSnapshotEntity::getCallSeq, callSeq);
        return contextSnapshotMapper.selectOne(wrapper);
    }
}
