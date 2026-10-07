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
package com.yangqiongai.ai.common.config;

import com.yangqiongai.ai.common.util.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Token估算配置
 * @author yangqiong
 */
@Configuration
public class TokenEstimatorConfiguration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TokenEstimatorConfiguration.class);

    /**
     * 中文字符的token系数，默认1.5
     */
    @Value("${ai.conversation.token.chinese-ratio:1.5}")
    private double chineseRatio;

    /**
     * 英文单词的token系数，默认1.3
     */
    @Value("${ai.conversation.token.english-ratio:1.3}")
    private double englishRatio;

    /**
     * 应用启动时将配置的系数应用到TokenEstimator
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        TokenEstimator.setRatios(chineseRatio, englishRatio);
        log.info("Token估算系数已配置: chineseRatio={}, englishRatio={}", chineseRatio, englishRatio);
    }
}
