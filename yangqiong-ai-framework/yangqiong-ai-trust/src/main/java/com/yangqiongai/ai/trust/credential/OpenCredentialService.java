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
package com.yangqiongai.ai.trust.credential;

import com.yangqiongai.ai.trust.credential.entity.OpenCredential;

import java.util.List;

/**
 * 开放凭证管理
 * @author yangqiong
 */
public interface OpenCredentialService {

    /**
     * 签发凭证(生成编码与密钥,明文仅返回一次)
     * @param credential
     * @return
     */
    IssueResult issue(OpenCredential credential);

    /**
     * 轮换凭证(重置密钥,旧密钥立即失效)
     * @param id
     * @return
     */
    IssueResult rotate(Long id);

    /**
     * 吊销凭证(不可恢复)
     * @param id
     * @return
     */
    void revoke(Long id);

    /**
     * 启用/禁用凭证
     * @param id
     * @return
     */
    void toggle(Long id);

    /**
     * 查询单个凭证
     * @param id
     * @return
     */
    OpenCredential get(Long id);

    /**
     * 查询凭证列表
     * @param name
     * @param status
     * @return
     */
    List<OpenCredential> list(String name, String status);

    /**
     * 校验密钥(状态/过期/哈希比对),失败抛未授权异常
     * @param secret
     * @return
     */
    OpenCredential verify(String secret);

    /**
     * 尝试获取限流通行额度
     * @param credential
     * @return
     */
    boolean tryAcquire(OpenCredential credential);

    /**
     * 更新最近使用时间(内存节流,避免高频落库)
     * @param credential
     * @return
     */
    void touchLastUsed(OpenCredential credential);
}
