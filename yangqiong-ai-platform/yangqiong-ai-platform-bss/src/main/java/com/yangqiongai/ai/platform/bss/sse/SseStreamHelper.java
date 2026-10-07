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
package com.yangqiongai.ai.platform.bss.sse;

import com.yangqiongai.ai.common.exception.ExceptionUtils;
import com.yangqiongai.ai.common.sse.AiStreamConstants;
import com.yangqiongai.ai.common.sse.StreamEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE流式输出辅助
 * @author yangqiong
 */
public class SseStreamHelper {

    private static final Logger log = LoggerFactory.getLogger(SseStreamHelper.class);

    /**
     * 心跳间隔（秒），默认25秒
     */
    private static final long DEFAULT_HEARTBEAT_INTERVAL = 25;

    private static final ExecutorService SSE_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "sse-stream-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 创建心跳Flux，定时发送SSE注释格式的保活信号
     * @param heartbeatInterval
     * @return
     */
    private static Flux<String> createHeartbeatFlux(long heartbeatInterval) {
        return Flux.interval(Duration.ofSeconds(heartbeatInterval))
                .map(i -> ": heartbeat\n\n");
    }

    /**
     * 将Flux流式输出到SseEmitter
     * @param emitter
     * @param flux
     */
    public static void streamToResponse(SseEmitter emitter, Flux<String> flux) {
        streamToResponse(emitter, flux, null, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 将Flux流式输出到SseEmitter，带完成回调
     * @param emitter
     * @param flux
     * @param onComplete
     */
    public static void streamToResponse(SseEmitter emitter, Flux<String> flux, Runnable onComplete) {
        streamToResponse(emitter, flux, onComplete, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 将Flux流式输出到SseEmitter，带完成回调和心跳间隔
     * @param emitter
     * @param flux
     * @param onComplete
     * @param heartbeatInterval
     */
    public static void streamToResponse(SseEmitter emitter, Flux<String> flux, Runnable onComplete, long heartbeatInterval) {
        Flux<String> heartbeat = createHeartbeatFlux(heartbeatInterval);
        // 完成信号经Sinks传递：源终止时心跳随之结束。不用share()/takeUntilOther直接作用于源，
        // 该结构会让takeUntilOther先订阅ignoreElements，源在连接期同步发射的首个事件（如头部告警）会被其吞掉
        Sinks.Empty<Object> done = Sinks.empty();
        Flux<String> merged = Flux.merge(
                flux.doFinally(signal -> done.tryEmitEmpty()),
                heartbeat.takeUntilOther(done.asMono()));
        SSE_EXECUTOR.submit(() -> {
            Disposable disposable = merged.subscribe(
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.warn("SSE发送中断: {}", e.getMessage());
                        }
                    },
                    error -> completeWithError(emitter, error),
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        } catch (IOException e) {
                            log.warn("SSE完成信号发送失败: {}", e.getMessage());
                        }
                    }
            );
            emitter.onCompletion(disposable::dispose);
            emitter.onTimeout(disposable::dispose);
            emitter.onError(ex -> disposable.dispose());
        });
    }

    /**
     * 将StreamEvent事件流输出到SseEmitter，按事件类型分发SSE事件名
     * @param emitter
     * @param eventFlux
     */
    public static void streamEventsToResponse(SseEmitter emitter, Flux<StreamEvent> eventFlux) {
        streamEventsToResponse(emitter, eventFlux, null, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 将StreamEvent事件流输出到SseEmitter，按事件类型分发SSE事件名，带完成回调
     * @param emitter
     * @param eventFlux
     * @param onComplete
     */
    public static void streamEventsToResponse(SseEmitter emitter, Flux<StreamEvent> eventFlux, Runnable onComplete) {
        streamEventsToResponse(emitter, eventFlux, onComplete, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 将StreamEvent事件流输出到SseEmitter，按事件类型分发SSE事件名，带完成回调和心跳间隔
     * @param emitter
     * @param eventFlux
     * @param onComplete
     * @param heartbeatInterval
     */
    public static void streamEventsToResponse(SseEmitter emitter, Flux<StreamEvent> eventFlux, Runnable onComplete, long heartbeatInterval) {
        Flux<String> heartbeat = createHeartbeatFlux(heartbeatInterval);
        // 完成信号经Sinks传递：源终止时心跳随之结束。不用share()/takeUntilOther直接作用于源，
        // 该结构会让takeUntilOther先订阅ignoreElements，源在连接期同步发射的首个事件（如头部预算/配额告警）会被其吞掉
        Sinks.Empty<Object> done = Sinks.empty();
        Flux<Object> merged = Flux.merge(
                eventFlux.cast(Object.class).doFinally(signal -> done.tryEmitEmpty()),
                heartbeat.cast(Object.class).takeUntilOther(done.asMono()));
        SSE_EXECUTOR.submit(() -> {
            Disposable disposable = merged.subscribe(
                    item -> {
                        try {
                            if (item instanceof StreamEvent) {
                                StreamEvent event = (StreamEvent) item;
                                String eventName = resolveEventName(event.getKind());
                                String payload = event.getPayload();
                                emitter.send(SseEmitter.event().name(eventName).data(payload));
                            } else if (item instanceof String) {
                                // 心跳消息，以SSE注释格式发送
                                emitter.send(((String) item).trim());
                            }
                        } catch (IOException e) {
                            log.warn("SSE事件发送中断: {}", e.getMessage());
                        }
                    },
                    error -> completeWithError(emitter, error),
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name(AiStreamConstants.EVENT_DONE).data(AiStreamConstants.DONE_MARKER));
                            emitter.complete();
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        } catch (IOException e) {
                            log.warn("SSE完成信号发送失败: {}", e.getMessage());
                        }
                    }
            );
            emitter.onCompletion(disposable::dispose);
            emitter.onTimeout(disposable::dispose);
            emitter.onError(ex -> disposable.dispose());
        });
    }

    /**
     * 根据StreamEvent类型映射SSE事件名
     * @param kind
     * @return
     */
    private static String resolveEventName(StreamEvent.Kind kind) {
        if (kind == StreamEvent.Kind.THINKING_DELTA) {
            return AiStreamConstants.EVENT_THINKING;
        }
        if (kind == StreamEvent.Kind.TOOL_CALL_DELTA) {
            return AiStreamConstants.EVENT_TOOL_CALL;
        }
        if (kind == StreamEvent.Kind.APPROVAL_REQUIRED) {
            return AiStreamConstants.EVENT_APPROVAL_REQUIRED;
        }
        if (kind == StreamEvent.Kind.CLARIFICATION_REQUIRED) {
            return AiStreamConstants.EVENT_CLARIFICATION_REQUIRED;
        }
        if (kind == StreamEvent.Kind.CONFIRM_REQUIRED) {
            return AiStreamConstants.EVENT_CONFIRM_REQUIRED;
        }
        if (kind == StreamEvent.Kind.BUDGET_WARNING) {
            return AiStreamConstants.EVENT_BUDGET_WARNING;
        }
        return "message";
    }

    /**
     * 发送命名SSE事件
     * @param emitter
     * @param event
     * @param data
     */
    public static void sendEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.warn("SSE事件发送失败: event={}, error={}", event, e.getMessage());
        }
    }

    /**
     * 异常完成SSE
     * <p>
     * 先推送 error 事件携带失败原因，再正常结束连接。
     * 不使用 completeWithError，避免 Spring 异常解析链向已提交的 text/event-stream
     * 响应写入 ApiResult（无转换器）导致连接被重置，前端只能看到 ERR_CONNECTION_RESET。
     * </p>
     * @param emitter
     * @param error
     */
    public static void completeWithError(SseEmitter emitter, Throwable error) {
        // 解包cause链提取真实业务原因，避免前端只看到"Retries exhausted"等框架包装消息
        String readable = ExceptionUtils.readableMessage(error);
        log.error("SSE流异常: {}", readable, error);
        try {
            emitter.send(SseEmitter.event().name("error").data(readable));
        } catch (IOException e) {
            log.warn("SSE错误信号发送失败: {}", e.getMessage());
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE正常结束失败: {}", e.getMessage());
        }
    }
}
