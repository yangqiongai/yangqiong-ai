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
package com.yangqiongai.ai.data.approval.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.approval.ApprovalStatus;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.approval.repository.PendingRequestRepository;
import com.yangqiongai.ai.data.approval.entity.PendingRequest;
import com.yangqiongai.ai.data.approval.mapper.PendingRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 待审批请求
 * @author yangqiong
 */
public class DefaultPendingRequestRepository implements PendingRequestRepository {

    @Autowired
    private PendingRequestMapper pendingRequestMapper;

    @Override
    public void save(PendingRequestInfo request) {
        PendingRequest entity = toEntity(request);
        pendingRequestMapper.insert(entity);
    }

    @Override
    public Optional<PendingRequestInfo> findByRequestId(String requestId) {
        LambdaQueryWrapper<PendingRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PendingRequest::getRequestId, requestId)
                .eq(PendingRequest::getDelFlag, 0);
        PendingRequest entity = pendingRequestMapper.selectOne(wrapper);
        return Optional.ofNullable(entity).map(this::toModel);
    }

    @Override
    public Optional<PendingRequestInfo> findByApprovalToken(String token) {
        LambdaQueryWrapper<PendingRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PendingRequest::getApprovalToken, token)
                .eq(PendingRequest::getDelFlag, 0);
        PendingRequest entity = pendingRequestMapper.selectOne(wrapper);
        return Optional.ofNullable(entity).map(this::toModel);
    }

    @Override
    public void updateStatus(String requestId, String status, String resolvedBy,
                             String rejectReason, String responsePayload) {
        PendingRequest updateEntity = new PendingRequest();
        updateEntity.setStatus(status);
        updateEntity.setResolvedBy(resolvedBy);
        updateEntity.setRejectReason(rejectReason);
        updateEntity.setResponsePayload(responsePayload);
        updateEntity.setResolvedTime(LocalDateTime.now());
        updateEntity.setUpdateTime(LocalDateTime.now());

        LambdaUpdateWrapper<PendingRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PendingRequest::getRequestId, requestId);
        pendingRequestMapper.update(updateEntity, wrapper);
    }

    @Override
    public int casUpdateStatus(String requestId, String expectedStatus, String status, String resolvedBy,
                               String rejectReason, String responsePayload) {
        PendingRequest updateEntity = new PendingRequest();
        updateEntity.setStatus(status);
        updateEntity.setResolvedBy(resolvedBy);
        updateEntity.setRejectReason(rejectReason);
        updateEntity.setResponsePayload(responsePayload);
        updateEntity.setResolvedTime(LocalDateTime.now());
        updateEntity.setUpdateTime(LocalDateTime.now());

        LambdaUpdateWrapper<PendingRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PendingRequest::getRequestId, requestId)
                .eq(PendingRequest::getStatus, expectedStatus)
                .eq(PendingRequest::getDelFlag, 0);
        return pendingRequestMapper.update(updateEntity, wrapper);
    }

    @Override
    public int revertToPending(String requestId) {
        LambdaUpdateWrapper<PendingRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PendingRequest::getRequestId, requestId)
                .eq(PendingRequest::getDelFlag, 0)
                .set(PendingRequest::getStatus, ApprovalStatus.PENDING.name())
                .set(PendingRequest::getResolvedBy, null)
                .set(PendingRequest::getRejectReason, null)
                .set(PendingRequest::getResponsePayload, null)
                .set(PendingRequest::getResolvedTime, null)
                .set(PendingRequest::getUpdateTime, LocalDateTime.now());
        return pendingRequestMapper.update(null, wrapper);
    }

    @Override
    public void updateExpireTime(String requestId, LocalDateTime expireTime) {
        PendingRequest updateEntity = new PendingRequest();
        updateEntity.setExpireTime(expireTime);
        updateEntity.setUpdateTime(LocalDateTime.now());
        LambdaUpdateWrapper<PendingRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PendingRequest::getRequestId, requestId);
        pendingRequestMapper.update(updateEntity, wrapper);
    }

    @Override
    public List<PendingRequestInfo> findExpiredRequests() {
        LambdaQueryWrapper<PendingRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PendingRequest::getStatus, ApprovalStatus.PENDING.name())
                .eq(PendingRequest::getDelFlag, 0)
                .lt(PendingRequest::getExpireTime, LocalDateTime.now());
        return pendingRequestMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<PendingRequestInfo> findPendingRequests(String userId) {
        LambdaQueryWrapper<PendingRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PendingRequest::getStatus, ApprovalStatus.PENDING.name())
                .eq(PendingRequest::getDelFlag, 0);
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(PendingRequest::getUserId, userId);
        }
        wrapper.orderByDesc(PendingRequest::getCreateTime);
        return pendingRequestMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<PendingRequestInfo> findPendingRequestsByApprover(String scopeId, String approver) {
        LambdaQueryWrapper<PendingRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PendingRequest::getStatus, ApprovalStatus.PENDING.name())
                .eq(PendingRequest::getDelFlag, 0);
        if (scopeId != null && !scopeId.isBlank()) {
            wrapper.eq(PendingRequest::getScopeId, scopeId);
        }
        if (approver != null && !approver.isBlank()) {
            wrapper.eq(PendingRequest::getApprover, approver);
        }
        wrapper.orderByDesc(PendingRequest::getCreateTime);
        return pendingRequestMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    private PendingRequest toEntity(PendingRequestInfo model) {
        PendingRequest entity = new PendingRequest();
        entity.setId(model.getId());
        entity.setRequestId(model.getRequestId());
        entity.setApprovalToken(model.getApprovalToken());
        entity.setSessionId(model.getSessionId());
        entity.setUserId(model.getUserId());
        entity.setApprover(model.getApprover());
        entity.setResourceType(model.getResourceType());
        entity.setTargetName(model.getTargetName());
        entity.setReason(model.getReason());
        entity.setTargetParams(model.getTargetParams());
        entity.setInputSchema(model.getInputSchema());
        entity.setResponsePayload(model.getResponsePayload());
        entity.setStatus(model.getStatus());
        entity.setExpireTime(model.getExpireTime());
        entity.setResolvedTime(model.getResolvedTime());
        entity.setResolvedBy(model.getResolvedBy());
        entity.setRejectReason(model.getRejectReason());
        entity.setDelFlag(model.getDelFlag());
        return entity;
    }

    private PendingRequestInfo toModel(PendingRequest entity) {
        PendingRequestInfo model = new PendingRequestInfo();
        model.setId(entity.getId());
        model.setRequestId(entity.getRequestId());
        model.setApprovalToken(entity.getApprovalToken());
        model.setSessionId(entity.getSessionId());
        model.setUserId(entity.getUserId());
        model.setApprover(entity.getApprover());
        model.setResourceType(entity.getResourceType());
        model.setTargetName(entity.getTargetName());
        model.setReason(entity.getReason());
        model.setTargetParams(entity.getTargetParams());
        model.setInputSchema(entity.getInputSchema());
        model.setResponsePayload(entity.getResponsePayload());
        model.setStatus(entity.getStatus());
        model.setExpireTime(entity.getExpireTime());
        model.setResolvedTime(entity.getResolvedTime());
        model.setResolvedBy(entity.getResolvedBy());
        model.setRejectReason(entity.getRejectReason());
        model.setDelFlag(entity.getDelFlag());
        return model;
    }
}
