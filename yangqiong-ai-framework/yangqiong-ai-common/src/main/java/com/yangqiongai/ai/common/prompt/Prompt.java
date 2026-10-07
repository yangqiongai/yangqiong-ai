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
package com.yangqiongai.ai.common.prompt;

import com.yangqiongai.ai.common.enums.PromptCategory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 提示词声明
 * <p>
 * 标注在提示词常量字段上
 * </p>
 * @author yangqiong
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Prompt {

    /**
     * 提示词编码
     */
    String code();

    /**
     * 提示词名称
     */
    String name();

    /**
     * 提示词描述
     */
    String description();

    /**
     * 提示词分类
     */
    PromptCategory category() default PromptCategory.SYSTEM;

    /**
     * 是否只读，只读提示词不允许编辑内容
     */
    boolean readonly() default false;

    /**
     * 是否可见，不可见提示词在管理端隐藏
     */
    boolean visible() default true;
}