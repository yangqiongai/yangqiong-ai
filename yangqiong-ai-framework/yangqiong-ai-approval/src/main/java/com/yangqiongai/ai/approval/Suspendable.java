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
package com.yangqiongai.ai.approval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可暂停审批注解
 * <p>
 * 标注在需要审批的Spring Bean方法上，方法执行前触发人工审批。AOP拦截器会自动：
 * <ol>
 *   <li>创建审批请求并持久化到数据库</li>
 *   <li>阻塞当前线程等待审批结果</li>
 *   <li>审批通过后继续执行原方法；拒绝则抛出 {@link ApprovalRejectedException}</li>
 * </ol>
 * </p>
 * <p>
 * 完整使用方式、交互模式、前提条件、使用示例、响应数据格式及审批人操作接口，
 * 详见 <a href="package-summary.html">包文档</a>。
 * </p>
 *
 * @author yangqiong
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Suspendable {

    /**
     * 是否启用审批门控
     */
    boolean enabled() default true;

    /**
     * 审批原因说明，会展示给审批人
     */
    String reason() default "";

    /**
     * 等待审批超时时间（秒），超时后自动拒绝。默认5分钟
     */
    int timeoutSeconds() default 300;

    /**
     * 供审批人选择的选项列表，非空时前端渲染为选择对话框
     */
    String[] options() default {};

    /**
     * 供审批人填写的字段名列表，非空时前端渲染为表单
     */
    String[] inputFields() default {};
}
