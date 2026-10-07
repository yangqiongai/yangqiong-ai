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
package com.yangqiongai.ai.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于jtokkit的精确Token估算
 * @author yangqiong
 */
@Component
public class PreciseTokenEstimator {

    private static final Logger log = LoggerFactory.getLogger(PreciseTokenEstimator.class);

    /**
     * jtokkit编码实例，使用CL100K_BASE编码适配大多数主流模型
     */
    private final Encoding encoding;

    /**
     * 加载失败标志，true表示jtokkit初始化失败，后续调用降级为快速估算
     */
    private volatile boolean fallbackEnabled = false;

    public PreciseTokenEstimator() {
        Encoding tempEncoding;
        try {
            EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
            tempEncoding = registry.getEncoding(EncodingType.CL100K_BASE);
            log.info("jtokkit精确Token估算器初始化完成, encoding={}", EncodingType.CL100K_BASE);
        } catch (Throwable e) {
            log.warn("jtokkit初始化失败，将降级使用快速估算: {}", e.getMessage());
            tempEncoding = null;
            fallbackEnabled = true;
        }
        this.encoding = tempEncoding;
    }

    /**
     * 精确估算文本的token数量，jtokkit不可用时降级为快速估算
     * @param text
     * @return
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (fallbackEnabled || encoding == null) {
            return TokenEstimator.estimateTokens(text);
        }
        return encoding.countTokens(text);
    }

    /**
     * 批量估算文本的token数量
     * @param texts
     * @return
     */
    public List<Integer> estimateTokens(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(estimateTokens(text));
        }
        return results;
    }

    /**
     * 判断文本token数是否在预算内
     * @param text
     * @param maxTokens
     * @return
     */
    public boolean isWithinBudget(String text, int maxTokens) {
        return estimateTokens(text) <= maxTokens;
    }
}
