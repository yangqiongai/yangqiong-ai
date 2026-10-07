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

import com.yangqiongai.ai.common.scope.ScopeContext;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiMetaObjectHandler单元测试
 */
@DisplayName("AiMetaObjectHandler 审计字段自动填充测试")
class AiMetaObjectHandlerTest {

    private final AiMetaObjectHandler handler = new AiMetaObjectHandler();

    /**
     * 测试审计实体
     */
    @TableName("test_audit_entity")
    static class TestAuditEntity {

        /**
         * 主键
         */
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        /**
         * 名称
         */
        private String name;

        /**
         * 创建人
         */
        @TableField(fill = FieldFill.INSERT)
        private String createUser;

        /**
         * 创建时间
         */
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        /**
         * 更新人
         */
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private String updateUser;

        /**
         * 更新时间
         */
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        /**
         * 作用域ID
         */
        @TableField(value = "tenant_id", fill = FieldFill.INSERT)
        private String scopeId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCreateUser() {
            return createUser;
        }

        public void setCreateUser(String createUser) {
            this.createUser = createUser;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public String getUpdateUser() {
            return updateUser;
        }

        public void setUpdateUser(String updateUser) {
            this.updateUser = updateUser;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }

        public String getScopeId() {
            return scopeId;
        }

        public void setScopeId(String scopeId) {
            this.scopeId = scopeId;
        }
    }

    /**
     * 无审计字段实体
     */
    @TableName("test_no_audit_entity")
    static class TestNoAuditEntity {

        /**
         * 主键
         */
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        /**
         * 名称
         */
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 注册测试实体TableInfo
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, TestAuditEntity.class);
        TableInfoHelper.initTableInfo(assistant, TestNoAuditEntity.class);
    }

    @BeforeEach
    void setUpContext() {
        UserContext.setUserId("user-001");
        ScopeContext.setScopeId("scope-001");
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
        ScopeContext.clear();
    }

    @Nested
    @DisplayName("insertFill 插入填充测试")
    class InsertFillTest {

        @Test
        @DisplayName("插入时填充全部审计字段")
        void shouldFillAllAuditFieldsOnInsert() {
            TestAuditEntity entity = new TestAuditEntity();
            entity.setName("test");
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertThat(entity.getCreateUser()).isEqualTo("user-001");
            assertThat(entity.getCreateTime()).isNotNull();
            assertThat(entity.getUpdateUser()).isEqualTo("user-001");
            assertThat(entity.getUpdateTime()).isNotNull();
            assertThat(entity.getScopeId()).isEqualTo("scope-001");
        }

        @Test
        @DisplayName("已有值的审计字段不被覆盖")
        void shouldNotOverrideExistingAuditValues() {
            TestAuditEntity entity = new TestAuditEntity();
            entity.setCreateUser("original-user");
            entity.setScopeId("original-scope");
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertThat(entity.getCreateUser()).isEqualTo("original-user");
            assertThat(entity.getScopeId()).isEqualTo("original-scope");
            assertThat(entity.getUpdateUser()).isEqualTo("user-001");
        }

        @Test
        @DisplayName("无审计字段实体不抛异常")
        void shouldNotThrowForEntityWithoutAuditFields() {
            TestNoAuditEntity entity = new TestNoAuditEntity();
            entity.setName("test");
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertThat(entity.getName()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("updateFill 更新填充测试")
    class UpdateFillTest {

        @Test
        @DisplayName("更新时仅填充更新人、更新时间")
        void shouldFillOnlyUpdateFieldsOnUpdate() {
            TestAuditEntity entity = new TestAuditEntity();
            entity.setCreateUser("original-creator");
            entity.setCreateTime(LocalDateTime.now().minusDays(1));
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertThat(entity.getUpdateUser()).isEqualTo("user-001");
            assertThat(entity.getUpdateTime()).isNotNull();
            // 创建字段不应被修改
            assertThat(entity.getCreateUser()).isEqualTo("original-creator");
        }

        @Test
        @DisplayName("无审计字段实体更新不抛异常")
        void shouldNotThrowForEntityWithoutAuditFieldsOnUpdate() {
            TestNoAuditEntity entity = new TestNoAuditEntity();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertThat(entity).isNotNull();
        }
    }

    @Nested
    @DisplayName("上下文默认值测试")
    class DefaultValueTest {

        @Test
        @DisplayName("未设置UserContext时使用默认用户ID")
        void shouldUseDefaultUserIdWhenNotSet() {
            UserContext.clear();
            TestAuditEntity entity = new TestAuditEntity();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertThat(entity.getCreateUser()).isEqualTo("system");
            assertThat(entity.getUpdateUser()).isEqualTo("system");
        }

        @Test
        @DisplayName("未设置ScopeContext时使用默认作用域ID")
        void shouldUseDefaultScopeIdWhenNotSet() {
            ScopeContext.clear();
            TestAuditEntity entity = new TestAuditEntity();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertThat(entity.getScopeId()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("hasSetter 检查测试")
    class HasSetterCheckTest {

        @Test
        @DisplayName("字段不存在时严格跳过不报错")
        void shouldSkipFillWhenFieldNotExists() {
            MetaObject mockMetaObject = mock(MetaObject.class);
            when(mockMetaObject.hasSetter("createUser")).thenReturn(false);
            when(mockMetaObject.hasSetter("createTime")).thenReturn(false);
            when(mockMetaObject.hasSetter("updateUser")).thenReturn(false);
            when(mockMetaObject.hasSetter("updateTime")).thenReturn(false);
            when(mockMetaObject.hasSetter("scopeId")).thenReturn(false);

            handler.insertFill(mockMetaObject);
            handler.updateFill(mockMetaObject);
        }
    }
}
