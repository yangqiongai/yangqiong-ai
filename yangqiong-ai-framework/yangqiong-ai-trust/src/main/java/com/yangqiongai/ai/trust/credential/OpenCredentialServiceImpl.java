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
package com.yangqiongai.ai.trust.credential;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.trust.credential.entity.OpenCredential;
import com.yangqiongai.ai.trust.credential.mapper.OpenCredentialMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放凭证管理
 * @author yangqiong
 */
public class OpenCredentialServiceImpl implements OpenCredentialService {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String STATUS_DISABLED = "DISABLED";

    public static final String STATUS_REVOKED = "REVOKED";

    /**
     * 最近使用时间落库节流间隔(毫秒)
     */
    private static final long TOUCH_INTERVAL_MILLIS = 60_000L;

    @Autowired
    private OpenCredentialMapper credentialMapper;

    private final SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter();

    private final Map<Long, Long> lastTouchMillis = new HashMap<>();

    @Override
    public IssueResult issue(OpenCredential credential) {
        if (credential.getName() == null || credential.getName().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "凭证名称不能为空");
        }
        String code = SecretHasher.generateCredentialCode();
        String secret = SecretHasher.generateSecret(code);
        credential.setCredentialCode(code);
        credential.setSecretHash(SecretHasher.hash(code, secret));
        if (credential.getStatus() == null || credential.getStatus().isBlank()) {
            credential.setStatus(STATUS_ACTIVE);
        }
        credentialMapper.insert(credential);
        return buildResult(credential, secret);
    }

    @Override
    public IssueResult rotate(Long id) {
        OpenCredential credential = requireExisting(id);
        if (STATUS_REVOKED.equals(credential.getStatus())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "凭证已吊销，不可轮换");
        }
        String secret = SecretHasher.generateSecret(credential.getCredentialCode());
        credential.setSecretHash(SecretHasher.hash(credential.getCredentialCode(), secret));
        credentialMapper.updateById(credential);
        rateLimiter.reset(credential.getCredentialCode());
        return buildResult(credential, secret);
    }

    @Override
    public void revoke(Long id) {
        OpenCredential credential = requireExisting(id);
        credential.setStatus(STATUS_REVOKED);
        credentialMapper.updateById(credential);
        rateLimiter.reset(credential.getCredentialCode());
    }

    @Override
    public void toggle(Long id) {
        OpenCredential credential = requireExisting(id);
        if (STATUS_REVOKED.equals(credential.getStatus())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "凭证已吊销，不可变更状态");
        }
        credential.setStatus(STATUS_ACTIVE.equals(credential.getStatus()) ? STATUS_DISABLED : STATUS_ACTIVE);
        credentialMapper.updateById(credential);
    }

    @Override
    public OpenCredential get(Long id) {
        return credentialMapper.selectById(id);
    }

    @Override
    public List<OpenCredential> list(String name, String status) {
        LambdaQueryWrapper<OpenCredential> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(OpenCredential::getName, name);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OpenCredential::getStatus, status);
        }
        wrapper.orderByDesc(OpenCredential::getUpdateTime);
        return credentialMapper.selectList(wrapper);
    }

    @Override
    public OpenCredential verify(String secret) {
        String code = SecretHasher.parseCredentialCode(secret);
        if (code == null) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "密钥格式非法");
        }
        OpenCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<OpenCredential>()
                .eq(OpenCredential::getCredentialCode, code)
                .last("LIMIT 1"));
        if (credential == null) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "凭证不存在");
        }
        if (!SecretHasher.hash(code, secret).equals(credential.getSecretHash())) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "凭证校验失败");
        }
        if (STATUS_REVOKED.equals(credential.getStatus())) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "凭证已吊销");
        }
        if (STATUS_DISABLED.equals(credential.getStatus())) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "凭证已禁用");
        }
        if (credential.getExpiresTime() != null && credential.getExpiresTime().isBefore(LocalDateTime.now())) {
            throw new AiException(AiErrorCode.UNAUTHORIZED.getCode(), "凭证已过期");
        }
        return credential;
    }

    @Override
    public boolean tryAcquire(OpenCredential credential) {
        Integer qps = credential.getRateLimitQps();
        if (qps == null || qps <= 0) {
            return true;
        }
        return rateLimiter.tryAcquire(credential.getCredentialCode(), qps);
    }

    @Override
    public synchronized void touchLastUsed(OpenCredential credential) {
        long now = System.currentTimeMillis();
        Long last = lastTouchMillis.get(credential.getId());
        if (last != null && now - last < TOUCH_INTERVAL_MILLIS) {
            return;
        }
        lastTouchMillis.put(credential.getId(), now);
        OpenCredential update = new OpenCredential();
        update.setId(credential.getId());
        update.setLastUsedTime(LocalDateTime.now());
        credentialMapper.updateById(update);
    }

    private OpenCredential requireExisting(Long id) {
        if (id == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "凭证ID不能为空");
        }
        OpenCredential credential = credentialMapper.selectById(id);
        if (credential == null) {
            throw new AiException(AiErrorCode.NOT_FOUND.getCode(), "凭证不存在");
        }
        return credential;
    }

    private IssueResult buildResult(OpenCredential credential, String secret) {
        IssueResult result = new IssueResult();
        result.setId(credential.getId());
        result.setCredentialCode(credential.getCredentialCode());
        result.setSecret(secret);
        return result;
    }
}
