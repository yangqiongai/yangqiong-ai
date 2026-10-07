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
package com.yangqiongai.ai.agent.core.util;

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentImageBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.common.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ContentBlock 工具（服务于 agentscope 内部类型，与 API 边界的 ContentBlockConverter 职责不同）
 * @author yangqiong
 */
public final class ContentBlockUtils {

    private ContentBlockUtils() {
    }

    /**
     * 从内容块列表中抽取纯文本（拼接所有 TextBlock，以 \n 连接）
     * @param blocks
     * @return
     */
    public static String toText(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .filter(AgentTextBlock.class::isInstance)
                .map(AgentTextBlock.class::cast)
                .map(AgentTextBlock::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 包装纯文本为 TextBlock 列表
     * @param text
     * @return
     */
    public static List<AgentContentBlock> fromText(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        return List.of(AgentTextBlock.builder().text(text).build());
    }

    /**
     * 将图片块转换为Markdown图片文本
     * @param img
     * @return
     */
    public static String toImageMarkdown(AgentImageBlock img) {
        if (img == null) {
            return "";
        }
        if (img.isUrlMode()) {
            return "\n\n![图片](" + img.getUrl() + ")\n\n";
        }
        if (img.isBase64Mode()) {
            String mediaType = img.getMediaType() == null || img.getMediaType().isBlank()
                    ? "image/png" : img.getMediaType();
            return "\n\n![图片](data:" + mediaType + ";base64," + img.getBase64Data() + ")\n\n";
        }
        return "";
    }

    /**
     * 提取内容块列表中的图片块并转换为Markdown文本
     * @param blocks
     * @return
     */
    public static String toImageMarkdown(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : blocks) {
            if (block instanceof AgentImageBlock img) {
                sb.append(toImageMarkdown(img));
            }
        }
        return sb.toString();
    }

    /**
     * 持久化前将消息中的图片块转为Markdown文本（base64替换为占位符，URL保留链接）
     * <p>
     * 避免会话历史携带兆级base64数据反复进入后续轮次上下文，
     * 同时保证assistant历史消息在OpenAI等协议下兼容（assistant消息不支持携带图像块）。
     * </p>
     * @param message
     * @return
     */
    public static AgentMessage sanitizeImagesForPersist(AgentMessage message) {
        if (message == null || message.getContent() == null
                || message.getContent().stream().noneMatch(AgentImageBlock.class::isInstance)) {
            return message;
        }
        List<AgentContentBlock> sanitized = new ArrayList<>(message.getContent().size());
        for (AgentContentBlock block : message.getContent()) {
            if (block instanceof AgentImageBlock img) {
                sanitized.add(AgentTextBlock.builder().text(toImageMarkdown(img)).build());
            } else {
                sanitized.add(block);
            }
        }
        return AgentMessage.builder()
                .name(message.getName())
                .role(message.getRole())
                .content(sanitized)
                .chatUsage(message.getChatUsage())
                .latency(message.getLatency())
                .build();
    }

    /**
     * 将文本中的base64图片Markdown替换为占位符，URL图片原样保留
     * @param answer
     * @return
     */
    public static String replaceBase64ImageMarkdown(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        return answer.replaceAll("!\\[[^\\]]*\\]\\(data:[^;)]+;base64,[^)]*\\)", "![图片]()");
    }
}
