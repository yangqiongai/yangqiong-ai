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
package com.yangqiongai.ai.trust.identity;

/**
 * 运行实例标识解析
 * <p>
 * runner_id 语义升级为 identity_uid:instance（Agent身份+实例标识），
 * 兼容解析旧格式 hostname:port:uuid，历史数据不迁移。
 * </p>
 * @author yangqiong
 */
public final class RunnerIdParser {

    /**
     * 旧格式段数(hostname:port:uuid)
     */
    private static final int LEGACY_SEGMENTS = 3;

    private RunnerIdParser() {
    }

    /**
     * 判断是否旧格式实例标识(hostname:port:uuid)
     * @param runnerId
     * @return
     */
    public static boolean isLegacyFormat(String runnerId) {
        if (runnerId == null || runnerId.isBlank()) {
            return false;
        }
        String[] segments = runnerId.split(":", -1);
        if (segments.length != LEGACY_SEGMENTS) {
            return false;
        }
        return isNumeric(segments[1]) && !segments[0].startsWith("aid-");
    }

    /**
     * 解析身份唯一标识,新格式返回uid段,旧格式或空返回null
     * @param runnerId
     * @return
     */
    public static String resolveIdentityUid(String runnerId) {
        if (runnerId == null || runnerId.isBlank()) {
            return null;
        }
        String[] segments = runnerId.split(":", -1);
        if (segments.length == 2 && !segments[0].isBlank()) {
            return segments[0];
        }
        return null;
    }

    /**
     * 解析实例标识段,旧格式原样返回,新格式返回instance段,空返回null
     * @param runnerId
     * @return
     */
    public static String resolveInstance(String runnerId) {
        if (runnerId == null || runnerId.isBlank()) {
            return null;
        }
        if (isLegacyFormat(runnerId)) {
            return runnerId;
        }
        String[] segments = runnerId.split(":", -1);
        if (segments.length == 2) {
            return segments[1];
        }
        return null;
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
