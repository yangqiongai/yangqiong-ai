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
package com.yangqiongai.ai.agent.core.provider;

/**
 * 检索重排序选项
 * <p>
 * enabled与modelCode为null时跟随全局配置（ai.rag.rerank.*）；
 * 全局开关ai.rag.rerank.enabled为总开关，关闭时agent级与请求级开关均不生效；
 * 通过ThreadLocal在RagRetrieveService.retrieve调用期间向内部检索策略传递覆盖值。
 * </p>
 * @author yangqiong
 */
public final class RerankOptions {

    private static final ThreadLocal<RerankOptions> HOLDER = new ThreadLocal<>();

    /**
     * 是否启用重排序，null表示跟随全局配置
     */
    private final Boolean enabled;

    /**
     * 重排序模型编码，null表示跟随全局配置
     */
    private final String modelCode;

    private RerankOptions(Boolean enabled, String modelCode) {
        this.enabled = enabled;
        this.modelCode = modelCode;
    }

    /**
     * 构建重排序选项，两项均未指定时返回null（无需覆盖）
     * @param enabled
     * @param modelCode
     * @return
     */
    public static RerankOptions of(Boolean enabled, String modelCode) {
        if (enabled == null && (modelCode == null || modelCode.isBlank())) {
            return null;
        }
        return new RerankOptions(enabled, modelCode);
    }

    /**
     * 解析生效的开关状态（全局开关为总开关，关闭时覆盖值不生效，仅全局开启时覆盖值生效）
     * @param globalEnabled
     * @return
     */
    public boolean resolveEnabled(boolean globalEnabled) {
        return globalEnabled && (enabled == null || enabled);
    }

    /**
     * 解析生效的模型编码（覆盖值优先，无覆盖时使用全局值）
     * @param globalModelCode
     * @return
     */
    public String resolveModelCode(String globalModelCode) {
        return modelCode != null && !modelCode.isBlank() ? modelCode : globalModelCode;
    }

    /**
     * 绑定选项到当前线程（retrieve调用期间生效）
     * @param options
     */
    public static void bind(RerankOptions options) {
        if (options != null) {
            HOLDER.set(options);
        }
    }

    /**
     * 获取当前线程绑定的选项
     * @return
     */
    public static RerankOptions current() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程绑定，避免线程复用串值
     */
    public static void clear() {
        HOLDER.remove();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getModelCode() {
        return modelCode;
    }
}
