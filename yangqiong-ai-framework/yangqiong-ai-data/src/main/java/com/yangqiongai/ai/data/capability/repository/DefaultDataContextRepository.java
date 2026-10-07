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
import com.yangqiongai.ai.data.capability.entity.DataContextEntity;
import com.yangqiongai.ai.data.capability.mapper.DataContextMapper;
import com.yangqiongai.ai.open.capability.context.DataContext;
import com.yangqiongai.ai.open.capability.context.DataContextRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据上下文存储
 * @author yangqiong
 */
public class DefaultDataContextRepository implements DataContextRepository {

    @Autowired
    private DataContextMapper mapper;

    @Override
    public String save(DataContext context) {
        if (context == null) {
            return null;
        }
        String ref = context.getRef() != null ? context.getRef() : UUID.randomUUID().toString().replace("-", "");
        context.setRef(ref);

        DataContextEntity entity = toEntity(context);
        mapper.insert(entity);
        return ref;
    }

    @Override
    public Optional<DataContext> get(String ref) {
        LambdaQueryWrapper<DataContextEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataContextEntity::getRef, ref);
        DataContextEntity entity = mapper.selectOne(wrapper);
        if (entity == null) {
            return Optional.empty();
        }
        // 检查是否过期
        if (entity.getExpiresAt() != null) {
            LocalDateTime expiresAt = LocalDateTime.parse(entity.getExpiresAt(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (LocalDateTime.now().isAfter(expiresAt)) {
                mapper.deleteById(entity.getId());
                return Optional.empty();
            }
        }
        return Optional.of(toDomain(entity));
    }

    @Override
    public List<DataContext> getBatch(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DataContextEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DataContextEntity::getRef, refs);
        List<DataContextEntity> entities = mapper.selectList(wrapper);
        return entities.stream()
                .filter(entity -> {
                    if (entity.getExpiresAt() != null) {
                        LocalDateTime expiresAt = LocalDateTime.parse(entity.getExpiresAt(),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        if (LocalDateTime.now().isAfter(expiresAt)) {
                            mapper.deleteById(entity.getId());
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void purgeExpired() {
        LambdaQueryWrapper<DataContextEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(DataContextEntity::getExpiresAt)
                .lt(DataContextEntity::getExpiresAt, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        mapper.delete(wrapper);
    }

    private DataContext toDomain(DataContextEntity entity) {
        DataContext context = new DataContext();
        context.setRef(entity.getRef());
        context.setType(entity.getType());
        context.setName(entity.getName());
        context.setContent(entity.getContent());
        return context;
    }

    private DataContextEntity toEntity(DataContext context) {
        DataContextEntity entity = new DataContextEntity();
        entity.setRef(context.getRef());
        entity.setType(context.getType());
        entity.setName(context.getName());
        if (context.getContent() != null) {
            entity.setContent(context.getContent().toString());
        }
        if (context.getTtlSeconds() > 0) {
            entity.setExpiresAt(LocalDateTime.now().plusSeconds(context.getTtlSeconds())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return entity;
    }
}