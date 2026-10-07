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
package com.yangqiongai.ai.platform.api.agent;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 轨迹分叉请求
 * @author yangqiong
 */
public class ForkRequest {

    /**
     * 分叉点：来源任务的模型调用序号（不传默认最后一次调用）
     */
    @Schema(description = "分叉点模型调用序号(不传默认最后一次调用)")
    private Integer callSeq;

    /**
     * 覆盖的用户输入（不传沿用原任务输入）
     */
    @Schema(description = "覆盖的用户输入(不传沿用原任务输入)")
    private String userInputOverride;

    /**
     * 分叉备注
     */
    @Schema(description = "分叉备注")
    private String remark;

    public Integer getCallSeq() {
        return callSeq;
    }

    public void setCallSeq(Integer callSeq) {
        this.callSeq = callSeq;
    }

    public String getUserInputOverride() {
        return userInputOverride;
    }

    public void setUserInputOverride(String userInputOverride) {
        this.userInputOverride = userInputOverride;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
