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
package com.yangqiongai.ai.open.capability.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据上下文枢纽
 * <p>
 * 业务系统预先推送业务数据到数据上下文枢纽，获取引用编码。
 * 后续调用能力时只需传 dataContextRefs，由能力执行引擎自动注入到Prompt。
 * </p>
 * @author yangqiong
 */
public class DataContextHub {

    private static final Logger log = LoggerFactory.getLogger(DataContextHub.class);

    private final DataContextRepository repository;

    private final long defaultTtlSeconds;

    public DataContextHub(DataContextRepository repository) {
        this(repository, 3600);
    }

    public DataContextHub(DataContextRepository repository, long defaultTtlSeconds) {
        this.repository = repository;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    /**
     * 推送数据上下文
     * @param context
     * @return 数据上下文引用编码
     */
    public String push(DataContext context) {
        if (context.getTtlSeconds() <= 0) {
            context.setTtlSeconds(defaultTtlSeconds);
        }
        context.setCreatedAt(java.time.Instant.now());
        return repository.save(context);
    }

    /**
     * 批量推送数据上下文
     * @param contexts
     * @return 引用编码列表
     */
    public List<String> pushBatch(List<DataContext> contexts) {
        List<String> refs = new ArrayList<>();
        for (DataContext context : contexts) {
            refs.add(push(context));
        }
        return refs;
    }

    /**
     * 按引用获取数据上下文
     * @param ref
     * @return
     */
    public DataContext get(String ref) {
        return repository.get(ref).orElse(null);
    }

    /**
     * 按引用列表获取数据上下文
     * @param refs
     * @return
     */
    public List<DataContext> getBatch(List<String> refs) {
        return repository.getBatch(refs);
    }

    /**
     * 渲染数据上下文为Prompt可用的文本块
     * @param refs
     * @return
     */
    public String renderToPrompt(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        List<DataContext> contexts = repository.getBatch(refs);
        if (contexts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是相关数据上下文：\n\n");
        for (DataContext ctx : contexts) {
            sb.append("--- ").append(ctx.getName() != null ? ctx.getName() : ctx.getType()).append(" ---\n");
            sb.append(ctx.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}