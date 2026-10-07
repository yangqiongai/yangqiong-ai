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
package com.yangqiongai.ai.agent.core.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextInputBlock;

import java.io.IOException;
import java.util.List;

/**
 * 用户输入灵活反序列化，兼容纯文本字符串与内容块数组
 * @author yangqiong
 */
public class InputBlocksDeserializer extends JsonDeserializer<List<InputBlock>> {

    @Override
    public List<InputBlock> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.getCurrentToken() == JsonToken.VALUE_STRING) {
            return List.of(new TextInputBlock(parser.getText()));
        }
        CollectionType type = context.getTypeFactory()
                .constructCollectionType(List.class, InputBlock.class);
        return context.readValue(parser, type);
    }
}
