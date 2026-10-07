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
package com.yangqiongai.ai.workflow.spi;

/**
 * 通知渠道校验扩展点（企业版提供实现，社区版缺失时跳过渠道存在性校验）
 * @author yangqiong
 */
public interface NotifyChannelValidator {

    /**
     * 校验渠道配置有效性
     * @param channelId
     * @return null表示校验通过，否则返回错误信息
     */
    String validate(String channelId);
}
