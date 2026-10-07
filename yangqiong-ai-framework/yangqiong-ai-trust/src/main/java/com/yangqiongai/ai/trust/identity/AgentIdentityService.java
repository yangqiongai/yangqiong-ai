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

import com.yangqiongai.ai.agent.registry.event.AgentPublishedEvent;
import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;

import java.util.List;

/**
 * Agent身份管理
 * @author yangqiong
 */
public interface AgentIdentityService {

    /**
     * 发布事件联动自动建档(幂等,agentCode已建档即跳过)
     * @param event
     * @return
     */
    AgentIdentity onAgentPublished(AgentPublishedEvent event);

    /**
     * 签发短时身份凭证(ok-id-{uid}格式JWS,仅返回一次明文)
     * @param identityUid
     * @return
     */
    String issueCredential(String identityUid);

    /**
     * 轮换凭证(重签+刷新指纹与轮换时间)
     * @param identityUid
     * @return
     */
    String rotate(String identityUid);

    /**
     * 吊销身份并联动禁用Agent
     * @param identityUid
     * @param operator
     * @return
     */
    void revoke(String identityUid, String operator);

    /**
     * 查询全部身份档案(填充轮换到期时间)
     * @return
     */
    List<AgentIdentity> list();

    /**
     * 查询单个身份档案(填充轮换到期时间)
     * @param identityUid
     * @return
     */
    AgentIdentity get(String identityUid);

    /**
     * 计算轮换到期时间(不轮换返回null)
     * @param identity
     * @return
     */
    java.time.LocalDateTime computeRotateDueTime(AgentIdentity identity);
}
