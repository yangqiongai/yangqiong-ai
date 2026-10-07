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
package com.yangqiongai.ai.platform.bss.scope;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yangqiongai.ai.common.scope.ScopeContext;

import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充
 * @author yangqiong
 */
public class AiMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充创建人、创建时间、更新人、更新时间、作用域ID
     * @param metaObject
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        String userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        String scopeId = ScopeContext.getScopeId();

        if (metaObject.hasSetter("createUser")) {
            strictInsertFill(metaObject, "createUser", String.class, userId);
        }
        if (metaObject.hasSetter("createTime")) {
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("updateUser")) {
            strictInsertFill(metaObject, "updateUser", String.class, userId);
        }
        if (metaObject.hasSetter("updateTime")) {
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("scopeId")) {
            strictInsertFill(metaObject, "scopeId", String.class, scopeId);
        }
    }

    /**
     * 更新时填充更新人、更新时间
     * @param metaObject
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        String userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();

        if (metaObject.hasSetter("updateUser")) {
            strictUpdateFill(metaObject, "updateUser", String.class, userId);
        }
        if (metaObject.hasSetter("updateTime")) {
            strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
    }
}
