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
package com.yangqiongai.ai.agent.registry.model;

/**
 * 版本状态迁移动作
 * @author yangqiong
 */
public enum VersionAction {

    /**
     * 提交审核
     */
    SUBMIT,

    /**
     * 全量发布
     */
    PUBLISH,

    /**
     * 灰度发布
     */
    PUBLISH_GRAY,

    /**
     * 拒绝
     */
    REJECT,

    /**
     * 退回草稿
     */
    BACK_TO_DRAFT,

    /**
     * 废弃
     */
    DEPRECATE,

    /**
     * 被回滚替换
     */
    ROLLBACK_FROM,

    /**
     * 回滚至此版本
     */
    ROLLBACK_TO
}
