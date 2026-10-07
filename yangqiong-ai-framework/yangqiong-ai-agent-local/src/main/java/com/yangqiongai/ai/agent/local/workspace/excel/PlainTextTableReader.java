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
package com.yangqiongai.ai.agent.local.workspace.excel;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯文本表格读回（CSV/TSV与伪装成表格扩展名的文本文件降级解析为工作簿结构）
 * @author yangqiong
 */
public class PlainTextTableReader {

    /**
     * 读回数据行数上限（与ExcelReader保持一致）
     */
    private static final int MAX_ROWS = 100;

    /**
     * 最大读取字节数（超长文件只解析前2MB即可覆盖截断后的行数上限）
     */
    private static final int MAX_READ_BYTES = 2 * 1024 * 1024;

    private PlainTextTableReader() {
    }

    /**
     * 解析CSV/TSV为工作簿结构（UTF-8严格解码失败回退GBK，数据行超100行截断）
     * @param file
     * @return
     * @throws IOException
     */
    public static Map<String, Object> readAsMap(Path file) throws IOException {
        List<String> lines = readLines(file);
        // 去掉末尾连续空行，避免统计行数虚高
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        char delimiter = detectDelimiter(lines);
        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("name", file.getFileName().toString());
        sheet.put("rowCount", lines.size());
        List<Object> header = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        if (!lines.isEmpty()) {
            header.addAll(parseLine(lines.get(0), delimiter));
            for (int i = 1; i < lines.size(); i++) {
                if (rows.size() >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
                rows.add(parseLine(lines.get(i), delimiter));
            }
        }
        sheet.put("header", header);
        sheet.put("rows", rows);
        sheet.put("truncated", truncated);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sheets", List.of(sheet));
        return result;
    }

    /**
     * 读取文件为文本行（UTF-8严格解码失败回退GBK，超2MB截断）
     * @param file
     * @return
     * @throws IOException
     */
    private static List<String> readLines(Path file) throws IOException {
        byte[] bytes;
        try (var in = Files.newInputStream(file)) {
            bytes = in.readNBytes(MAX_READ_BYTES);
        }
        String text;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        // 统一换行符后按行拆分，包装为可变列表供调用方去除末尾空行
        return new ArrayList<>(List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
    }

    /**
     * 分隔符探测（取前几行中制表符与逗号出现较多者）
     * @param lines
     * @return
     */
    private static char detectDelimiter(List<String> lines) {
        int tabs = 0;
        int commas = 0;
        for (String line : lines.subList(0, Math.min(lines.size(), 5))) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\t') {
                    tabs++;
                } else if (c == ',') {
                    commas++;
                }
            }
        }
        return tabs > commas ? '\t' : ',';
    }

    /**
     * 解析单行为单元格列表（处理双引号包裹与""转义）
     * @param line
     * @param delimiter
     * @return
     */
    private static List<Object> parseLine(String line, char delimiter) {
        List<Object> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }
}
