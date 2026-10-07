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
package com.yangqiongai.ai.common.banner;

import java.io.PrintStream;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;

/**
 * YangQiong启动横幅（大写点阵标识,社区版与企业版共用）
 * @author yangqiong
 */
public class YangqiongBanner implements Banner {

    /**
     * 横幅行数
     */
    private static final int ART_ROWS = 6;

    /**
     * 单元格拼接间隔
     */
    private static final String CELL_GAP = " ";

    /**
     * 默认副标题
     */
    private static final String DEFAULT_SUBTITLE = "  :: YangQiong AI ::";

    /**
     * 字母Y点阵
     */
    private static final String[] LETTER_Y = {"Y     Y", " Y   Y ", "  YYY  ", "   Y   ", "   Y   ", "   Y   "};

    /**
     * 字母A点阵
     */
    private static final String[] LETTER_A = {"   A   ", "  A A  ", " A   A ", "AAAAAAA", "A     A", "A     A"};

    /**
     * 字母N点阵
     */
    private static final String[] LETTER_N = {"N     N", "NN    N", "N N   N", "N  N  N", "N   N N", "N    NN"};

    /**
     * 字母G点阵
     */
    private static final String[] LETTER_G = {" GGGG  ", "G      ", "G  GGG ", "G     G", "G     G", " GGGG  "};

    /**
     * 字母Q点阵
     */
    private static final String[] LETTER_Q = {" QQQQ  ", "Q    Q ", "Q    Q ", "Q    Q ", "Q  Q Q ", " QQ QQ "};

    /**
     * 字母I点阵
     */
    private static final String[] LETTER_I = {"IIIII  ", "  I    ", "  I    ", "  I    ", "  I    ", "IIIII  "};

    /**
     * 字母O点阵
     */
    private static final String[] LETTER_O = {" OOOO  ", "O    O ", "O    O ", "O    O ", "O    O ", " OOOO  "};

    /**
     * YANGQIONG大字横幅（逐行拼接各字母点阵）
     */
    private static final String[] YANGQIONG_ART = buildYangqiongArt();

    /**
     * 副标题
     */
    private final String subtitle;

    public YangqiongBanner() {
        this(DEFAULT_SUBTITLE);
    }

    public YangqiongBanner(String subtitle) {
        this.subtitle = subtitle;
    }

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        for (String line : YANGQIONG_ART) {
            out.println(line);
        }
        out.println(subtitle);
        out.println();
    }

    /**
     * 逐行拼接YANGQIONG各字母点阵
     * @return
     */
    private static String[] buildYangqiongArt() {
        String[][] letters = {LETTER_Y, LETTER_A, LETTER_N, LETTER_G, LETTER_Q, LETTER_I, LETTER_O,
                LETTER_N, LETTER_G};
        String[] lines = new String[ART_ROWS];
        for (int row = 0; row < ART_ROWS; row++) {
            StringBuilder builder = new StringBuilder();
            for (String[] letter : letters) {
                if (builder.length() > 0) {
                    builder.append(CELL_GAP);
                }
                builder.append(letter[row]);
            }
            lines[row] = builder.toString();
        }
        return lines;
    }
}
