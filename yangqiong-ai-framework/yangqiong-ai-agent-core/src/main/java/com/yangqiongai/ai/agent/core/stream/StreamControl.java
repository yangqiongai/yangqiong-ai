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
package com.yangqiongai.ai.agent.core.stream;

import reactor.core.publisher.Flux;

/**
 * 流式控制工具
 * @author yangqiong
 */
public final class StreamControl {

    private StreamControl() {
    }

    /**
     * 应用thinking标签剥离
     * @param flux
     * @return
     */
    public static Flux<String> stripThinking(Flux<String> flux) {
        ThinkTagSplitter splitter = new ThinkTagSplitter();
        return flux
                .map(splitter::process)
                .filter(s -> !s.isEmpty())
                .concatWith(Flux.defer(() -> {
                    ThinkTagSplitter.ThinkSplitResult remaining = splitter.flush();
                    return remaining.visible().isEmpty() ? Flux.empty() : Flux.just(remaining.visible());
                }));
    }

    /**
     * 应用取消令牌
     * @param flux
     * @param token
     * @return
     */
    public static Flux<String> withCancellation(Flux<String> flux, CancellationToken token) {
        return flux.takeWhile(s -> !token.isCancelled());
    }
}
