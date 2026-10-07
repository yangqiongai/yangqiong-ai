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
package com.yangqiongai.ai.data.capability.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.data.capability.entity.RequestDedupEntity;
import com.yangqiongai.ai.data.capability.mapper.RequestDedupMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求去重记录
 * @author yangqiong
 */
public class DefaultRequestDedupRepository {

    @Autowired
    private RequestDedupMapper mapper;

    /**
     * 查询去重记录
     * @param dedupKey
     * @param fingerprint
     * @return 缓存的响应JSON
     */
    public String findByKey(String dedupKey, String fingerprint) {
        LambdaQueryWrapper<RequestDedupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestDedupEntity::getDedupKey, dedupKey)
                .eq(RequestDedupEntity::getRequestFingerprint, fingerprint);
        RequestDedupEntity entity = mapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        // 检查是否过期
        if (entity.getExpiresAt() != null) {
            LocalDateTime expiresAt = LocalDateTime.parse(entity.getExpiresAt(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (LocalDateTime.now().isAfter(expiresAt)) {
                mapper.deleteById(entity.getId());
                return null;
            }
        }
        return entity.getResponseCache();
    }

    /**
     * 保存去重记录
     * @param dedupKey
     * @param fingerprint
     * @param responseCache
     * @param ttlSeconds 有效期（秒）
     */
    public void save(String dedupKey, String fingerprint, String responseCache, long ttlSeconds) {
        LambdaQueryWrapper<RequestDedupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestDedupEntity::getDedupKey, dedupKey)
                .eq(RequestDedupEntity::getRequestFingerprint, fingerprint);

        RequestDedupEntity existing = mapper.selectOne(wrapper);
        if (existing != null) {
            existing.setResponseCache(responseCache);
            existing.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            mapper.updateById(existing);
        } else {
            RequestDedupEntity entity = new RequestDedupEntity();
            entity.setDedupKey(dedupKey);
            entity.setRequestFingerprint(fingerprint);
            entity.setResponseCache(responseCache);
            entity.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            mapper.insert(entity);
        }
    }

    /**
     * 删除去重记录
     * @param dedupKey
     * @param fingerprint
     */
    public void delete(String dedupKey, String fingerprint) {
        LambdaQueryWrapper<RequestDedupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestDedupEntity::getDedupKey, dedupKey)
                .eq(RequestDedupEntity::getRequestFingerprint, fingerprint);
        mapper.delete(wrapper);
    }

    /**
     * 清除所有过期记录
     */
    public void purgeExpired() {
        LambdaQueryWrapper<RequestDedupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(RequestDedupEntity::getExpiresAt)
                .lt(RequestDedupEntity::getExpiresAt, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        mapper.delete(wrapper);
    }
}