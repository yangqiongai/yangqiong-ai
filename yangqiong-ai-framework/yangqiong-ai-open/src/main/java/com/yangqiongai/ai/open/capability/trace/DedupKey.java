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
package com.yangqiongai.ai.open.capability.trace;

import java.util.Objects;

/**
 * 去重键
 * <p>
 * 由外部去重键 + 请求指纹组成，用于唯一标识一个请求。
 * 相同DedupKey的请求在有效期内返回缓存结果。
 * </p>
 * @author yangqiong
 */
public class DedupKey {

    /**
     * 外部去重键（由调用方传入）
     */
    private final String key;

    /**
     * 请求指纹（基于请求参数自动计算）
     */
    private final String fingerprint;

    public DedupKey(String key, String fingerprint) {
        this.key = key;
        this.fingerprint = fingerprint;
    }

    public String getKey() {
        return key;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * 获取组合键
     * @return
     */
    public String getCompositeKey() {
        return key + ":" + fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DedupKey dedupKey = (DedupKey) o;
        return Objects.equals(key, dedupKey.key) && Objects.equals(fingerprint, dedupKey.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, fingerprint);
    }
}