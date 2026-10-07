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
package com.yangqiongai.ai.open.capability.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 能力规格解析
 * @author yangqiong
 */
public class CapabilityLoader {

    private static final Logger log = LoggerFactory.getLogger(CapabilityLoader.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private CapabilityLoader() {
    }

    /**
     * 从JSON字符串解析能力规格（能力定义以数据库为唯一来源）
     * @param json
     * @return
     */
    public static CapabilitySpec parseSpecFromJson(String json) {
        try {
            return JSON_MAPPER.readValue(json, CapabilitySpec.class);
        } catch (IOException e) {
            log.warn("解析能力规格JSON失败", e);
            return null;
        }
    }
}
