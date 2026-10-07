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
package com.yangqiongai.ai.agent.tool.builtin;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * 数学计算工具
 * <p>
 * 提供精确的数值计算能力，使用BigDecimal避免浮点数精度问题。
 * 支持四则运算、百分比、幂运算、开方、平均值、四舍五入等常用计算。
 * 弥补LLM在数值计算上的不足，确保财务、统计等场景的计算准确性。
 * </p>
 * @author yangqiong
 */
@Component
public class CalculatorTool implements Tool {

    /**
     * 内置工具自动装配到所有Agent工具箱
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.BUILTIN;
    }

    /**
     * 默认小数位数
     */
    private static final int DEFAULT_SCALE = 6;

    /**
     * 加法
     * @param a 被加数
     * @param b 加数
     * @return
     */
    @AgentTool("精确加法运算。输入两个数字，返回它们的和。适用于财务金额累加等需要精确计算的场景。")
    public String add(@AgentToolParam("被加数") String a, @AgentToolParam("加数") String b) {
        try {
            BigDecimal result = new BigDecimal(a).add(new BigDecimal(b));
            return formatResult("加法", a + " + " + b, result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 减法
     * @param a 被减数
     * @param b 减数
     * @return
     */
    @AgentTool("精确减法运算。输入两个数字，返回它们的差。")
    public String subtract(@AgentToolParam("被减数") String a, @AgentToolParam("减数") String b) {
        try {
            BigDecimal result = new BigDecimal(a).subtract(new BigDecimal(b));
            return formatResult("减法", a + " - " + b, result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 乘法
     * @param a 被乘数
     * @param b 乘数
     * @return
     */
    @AgentTool("精确乘法运算。输入两个数字，返回它们的积。适用于金额乘以单价、税率计算等场景。")
    public String multiply(@AgentToolParam("被乘数") String a, @AgentToolParam("乘数") String b) {
        try {
            BigDecimal result = new BigDecimal(a).multiply(new BigDecimal(b));
            return formatResult("乘法", a + " × " + b, result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 除法
     * @param a 被除数
     * @param b 除数
     * @param scale 保留小数位数
     * @return
     */
    @AgentTool("精确除法运算。输入被除数、除数和保留小数位数（默认6位），返回商。除数不能为0。")
    public String divide(@AgentToolParam("被除数") String a, @AgentToolParam("除数") String b, @AgentToolParam("保留小数位数，默认6位") int scale) {
        try {
            BigDecimal divisor = new BigDecimal(b);
            if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                return "错误：除数不能为0";
            }
            int actualScale = scale < 0 ? DEFAULT_SCALE : scale;
            BigDecimal result = new BigDecimal(a).divide(divisor, actualScale, RoundingMode.HALF_UP);
            return formatResult("除法", a + " ÷ " + b + "（保留" + actualScale + "位小数）", result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 幂运算
     * @param base 底数
     * @param exponent 指数
     * @return
     */
    @AgentTool("幂运算。输入底数和指数，返回 base 的 exponent 次幂。适用于复利计算、面积/体积计算等场景。")
    public String power(@AgentToolParam("底数") String base, @AgentToolParam("指数") String exponent) {
        try {
            double baseVal = Double.parseDouble(base);
            double expVal = Double.parseDouble(exponent);
            double result = Math.pow(baseVal, expVal);
            BigDecimal bd = BigDecimal.valueOf(result).round(new MathContext(10));
            return formatResult("幂运算", base + " ^ " + exponent, bd);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 平方根
     * @param num 被开方数
     * @return
     */
    @AgentTool("平方根计算。输入一个非负数，返回它的平方根。")
    public String sqrt(String num) {
        try {
            double val = Double.parseDouble(num);
            if (val < 0) {
                return "错误：不能对负数开平方";
            }
            double result = Math.sqrt(val);
            BigDecimal bd = BigDecimal.valueOf(result).round(new MathContext(10));
            return formatResult("平方根", "√" + num, bd);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 百分比计算
     * @param value 基数值
     * @param percent 百分比（如 15.5 表示 15.5%）
     * @return
     */
    @AgentTool("百分比计算。输入基数和百分比（如15.5表示15.5%），返回基数乘以百分比的结果。适用于税率、折扣计算等场景。")
    public String percentage(String value, String percent) {
        try {
            BigDecimal base = new BigDecimal(value);
            BigDecimal percentVal = new BigDecimal(percent);
            BigDecimal percentDecimal = percentVal.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal result = base.multiply(percentDecimal).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
            return formatResult("百分比", value + " × " + percent + "%", result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 计算平均值
     * @param numbers 逗号分隔的数字串（如 "1,2,3,4,5"）
     * @return
     */
    @AgentTool("计算平均值。输入逗号分隔的数字串（如 1,2,3,4,5），返回算术平均值。适用于统计场景。")
    public String average(String numbers) {
        try {
            double[] arr = Arrays.stream(numbers.split("[,，\\s]+"))
                    .filter(s -> !s.isEmpty())
                    .mapToDouble(Double::parseDouble)
                    .toArray();
            if (arr.length == 0) {
                return "错误：未输入有效数字";
            }
            double sum = 0;
            for (double v : arr) {
                sum += v;
            }
            double avg = sum / arr.length;
            BigDecimal bd = BigDecimal.valueOf(avg).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
            return formatResult("平均值", "共" + arr.length + "个数", bd);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入逗号分隔的数字（如 1,2,3,4,5）。";
        }
    }

    /**
     * 四舍五入
     * @param num 数字
     * @param scale 保留小数位数
     * @return
     */
    @AgentTool("四舍五入。输入数字和保留小数位数，返回四舍五入后的结果。")
    public String round(@AgentToolParam("待四舍五入的数字") String num, @AgentToolParam("保留小数位数") int scale) {
        try {
            int actualScale = scale < 0 ? 0 : scale;
            BigDecimal result = new BigDecimal(num).setScale(actualScale, RoundingMode.HALF_UP);
            return formatResult("四舍五入", num + "（保留" + actualScale + "位小数）", result);
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入有效数字。";
        }
    }

    /**
     * 最大公约数
     * @param a 第一个整数
     * @param b 第二个整数
     * @return
     */
    @AgentTool("计算两个整数的最大公约数（GCD）。")
    public String gcd(@AgentToolParam("第一个整数") String a, @AgentToolParam("第二个整数") String b) {
        try {
            long x = Long.parseLong(a);
            long y = Long.parseLong(b);
            if (x == 0 && y == 0) {
                return "错误：两个数不能同时为0";
            }
            long result = gcd(Math.abs(x), Math.abs(y));
            return formatResult("最大公约数", "gcd(" + a + ", " + b + ")", BigDecimal.valueOf(result));
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入整数。";
        }
    }

    /**
     * 最小公倍数
     * @param a 第一个整数
     * @param b 第二个整数
     * @return
     */
    @AgentTool("计算两个整数的最小公倍数（LCM）。")
    public String lcm(@AgentToolParam("第一个整数") String a, @AgentToolParam("第二个整数") String b) {
        try {
            long x = Long.parseLong(a);
            long y = Long.parseLong(b);
            if (x == 0 || y == 0) {
                return "错误：参数不能为0";
            }
            long gcdVal = gcd(Math.abs(x), Math.abs(y));
            long result = Math.abs(x) / gcdVal * Math.abs(y);
            return formatResult("最小公倍数", "lcm(" + a + ", " + b + ")", BigDecimal.valueOf(result));
        } catch (NumberFormatException e) {
            return "参数格式错误，请输入整数。";
        }
    }

    /**
     * 辗转相除法求最大公约数
     * @param a 非负整数
     * @param b 非负整数
     * @return
     */
    private long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * 格式化结果输出
     * @param operation 运算类型
     * @param expression 表达式
     * @param result 结果
     * @return
     */
    private String formatResult(String operation, String expression, BigDecimal result) {
        BigDecimal stripped = result.stripTrailingZeros();
        return operation + "：" + expression + " = " + stripped.toPlainString();
    }
}
