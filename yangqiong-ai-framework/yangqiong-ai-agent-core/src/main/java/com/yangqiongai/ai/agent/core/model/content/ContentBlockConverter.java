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
package com.yangqiongai.ai.agent.core.model.content;

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentImageBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.common.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ContentBlock与自定义DTO的双向转换
 * @author yangqiong
 */
public final class ContentBlockConverter {

    private ContentBlockConverter() {
    }

    // ==================== 输出侧：ContentBlock → OutputBlock ====================

    /**
     * 批量转换：ContentBlock列表 → OutputBlock列表
     * 过滤掉ThinkingBlock / ToolUseBlock / ToolResultBlock / HintBlock
     * @param blocks
     * @return
     */
    public static List<OutputBlock> toOutputBlocks(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(ContentBlockConverter::toOutputBlock)
                .filter(b -> b != null)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换，内部类型返回null（被过滤）
     * @param block
     * @return
     */
    public static OutputBlock toOutputBlock(AgentContentBlock block) {
        if (block instanceof AgentTextBlock tb) {
            return new TextOutputBlock(tb.getText());
        }
        return null;
    }

    /**
     * 从OutputBlock列表抽取纯文本（拼接所有TextOutputBlock，以\n连接）
     * @param blocks
     * @return
     */
    public static String toOutputText(List<OutputBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .filter(TextOutputBlock.class::isInstance)
                .map(TextOutputBlock.class::cast)
                .map(TextOutputBlock::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 纯文本包装为OutputBlock列表
     * @param text
     * @return
     */
    public static List<OutputBlock> fromOutputText(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        return List.of(new TextOutputBlock(text));
    }

    // ==================== 输入侧：InputBlock → ContentBlock ====================

    /**
     * 批量转换：InputBlock列表 → ContentBlock列表
     * @param blocks
     * @return
     */
    public static List<AgentContentBlock> fromInputBlocks(List<InputBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(ContentBlockConverter::fromInputBlock)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换：InputBlock → ContentBlock
     * @param block
     * @return
     */
    public static AgentContentBlock fromInputBlock(InputBlock block) {
        if (block instanceof TextInputBlock tb) {
            return AgentTextBlock.builder().text(tb.getText()).build();
        }
        if (block instanceof ImageInputBlock ib) {
            return convertImageInputBlock(ib);
        }
        return AgentTextBlock.builder().text(block.toString()).build();
    }

    /**
     * 将ImageInputBlock转换为AgentImageBlock
     * @param ib
     * @return
     */
    private static AgentImageBlock convertImageInputBlock(ImageInputBlock ib) {
        InputSource source = ib.getSource();
        if (source == null) {
            return AgentImageBlock.builder().build();
        }
        if (source instanceof UrlInputSource urlSource) {
            return AgentImageBlock.builder().url(urlSource.getUrl()).build();
        }
        if (source instanceof Base64InputSource base64Source) {
            return AgentImageBlock.builder()
                    .base64Data(base64Source.getData())
                    .mediaType(base64Source.getMediaType())
                    .build();
        }
        return AgentImageBlock.builder().build();
    }

    // ==================== 输入侧：ContentBlock → InputBlock ====================

    /**
     * 批量转换：ContentBlock列表 → InputBlock列表
     * @param blocks
     * @return
     */
    public static List<InputBlock> toInputBlocks(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(ContentBlockConverter::toInputBlock)
                .filter(b -> b != null)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换：ContentBlock → InputBlock
     * @param block
     * @return
     */
    public static InputBlock toInputBlock(AgentContentBlock block) {
        if (block instanceof AgentTextBlock tb) {
            return new TextInputBlock(tb.getText());
        }
        if (block instanceof AgentImageBlock ib) {
            return convertToImageInputBlock(ib);
        }
        return null;
    }

    /**
     * 将AgentImageBlock转换为ImageInputBlock
     * @param ib
     * @return
     */
    private static ImageInputBlock convertToImageInputBlock(AgentImageBlock ib) {
        InputSource source;
        if (ib.isUrlMode()) {
            source = new UrlInputSource(ib.getUrl());
        } else if (ib.isBase64Mode()) {
            source = new Base64InputSource(ib.getMediaType(), ib.getBase64Data());
        } else {
            source = null;
        }
        return new ImageInputBlock(source, null, null);
    }

    /**
     * 从InputBlock列表抽取纯文本（拼接所有TextInputBlock，以\n连接）
     * @param blocks
     * @return
     */
    public static String toInputText(List<InputBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .filter(TextInputBlock.class::isInstance)
                .map(TextInputBlock.class::cast)
                .map(TextInputBlock::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 纯文本包装为InputBlock列表
     * @param text
     * @return
     */
    public static List<InputBlock> fromInputText(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        return List.of(new TextInputBlock(text));
    }

    // ==================== 反向转换：OutputBlock → ContentBlock ====================

    /**
     * 批量转换：OutputBlock列表 → ContentBlock列表（用于会话恢复等场景）
     * @param blocks
     * @return
     */
    public static List<AgentContentBlock> fromOutputBlocks(List<OutputBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(ContentBlockConverter::fromOutputBlock)
                .collect(Collectors.toList());
    }

    /**
     * 单个转换：OutputBlock → ContentBlock
     * @param block
     * @return
     */
    public static AgentContentBlock fromOutputBlock(OutputBlock block) {
        if (block instanceof TextOutputBlock tb) {
            return AgentTextBlock.builder().text(tb.getText()).build();
        }
        return AgentTextBlock.builder().text(block.toString()).build();
    }
}
