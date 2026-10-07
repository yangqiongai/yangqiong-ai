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
import java.math.RoundingMode;

/**
 * 单位换算工具
 * <p>
 * 提供常用单位之间的换算能力，包括长度、面积、重量、温度、数据存储等。
 * 使用BigDecimal保证换算精度，避免浮点数误差。
 * </p>
 * @author yangqiong
 */
@Component
public class UnitConverterTool implements Tool {

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
     * 长度换算（支持：mm、cm、dm、m、km、inch、ft、yd、mile、里、丈、尺、寸）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("长度单位换算。支持单位：mm(毫米)、cm(厘米)、dm(分米)、m(米)、km(千米)、inch(英寸)、ft(英尺)、yd(码)、mile(英里)、li(市里)、zhang(市丈)、chi(市尺)、cun(市寸)。输入数值、源单位、目标单位。")
    public String convertLength(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal meters = toMeters(new BigDecimal(value), fromUnit);
            BigDecimal result = fromMeters(meters, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "长度换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 面积换算（支持：mm²、cm²、dm²、m²、are、ha、km²、sq.in、sq.ft、acre、mu、qing、顷）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("面积单位换算。支持单位：mm2(平方毫米)、cm2(平方厘米)、m2(平方米)、are(公亩)、ha(公顷)、km2(平方千米)、sqin(平方英寸)、sqft(平方英尺)、acre(英亩)、mu(亩)、qing(顷)。")
    public String convertArea(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal sqMeters = toSquareMeters(new BigDecimal(value), fromUnit);
            BigDecimal result = fromSquareMeters(sqMeters, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "面积换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 重量换算（支持：mg、g、kg、t、lb、oz、斤、两）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("重量单位换算。支持单位：mg(毫克)、g(克)、kg(千克)、t(吨)、lb(磅)、oz(盎司)、jin(市斤)、liang(市两)。")
    public String convertWeight(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal grams = toGrams(new BigDecimal(value), fromUnit);
            BigDecimal result = fromGrams(grams, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "重量换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 温度换算（支持：C摄氏度、F华氏度、K开尔文）
     * @param value 数值
     * @param fromUnit 源单位（C/F/K）
     * @param toUnit 目标单位（C/F/K）
     * @return
     */
    @AgentTool("温度单位换算。支持单位：C(摄氏度)、F(华氏度)、K(开尔文)。")
    public String convertTemperature(@AgentToolParam("数值") String value, @AgentToolParam("源单位（C/F/K）") String fromUnit, @AgentToolParam("目标单位（C/F/K）") String toUnit) {
        try {
            BigDecimal celsius = toCelsius(new BigDecimal(value), fromUnit);
            BigDecimal result = fromCelsius(celsius, toUnit);
            return formatResult(value + "°" + fromUnit, result.toPlainString() + "°" + toUnit, "温度换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 数据存储换算（支持：B、KB、MB、GB、TB、PB、bit、Kb、Mb、Gb）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("数据存储单位换算。支持单位：B(字节)、KB、MB、GB、TB、PB、bit(比特)、Kb、Mb、Gb、Tb。采用1024进制。")
    public String convertDataSize(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal bytes = toBytes(new BigDecimal(value), fromUnit);
            BigDecimal result = fromBytes(bytes, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "数据存储换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 时间换算（支持：ms、s、min、h、d、week、month、year）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("时间单位换算。支持单位：ms(毫秒)、s(秒)、min(分钟)、h(小时)、d(天)、week(周)、month(月,按30天)、year(年,按365天)。")
    public String convertTime(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal seconds = toSeconds(new BigDecimal(value), fromUnit);
            BigDecimal result = fromSeconds(seconds, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "时间换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 体积换算（支持：mL、L、m³、gal、qt、pt、cup）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("体积单位换算。支持单位：mL(毫升)、L(升)、m3(立方米)、gal(加仑)、qt(夸脱)、pt(品脱)、cup(杯)。")
    public String convertVolume(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal liters = toLiters(new BigDecimal(value), fromUnit);
            BigDecimal result = fromLiters(liters, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "体积换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    /**
     * 速度换算（支持：m/s、km/h、mph、knot、mach）
     * @param value 数值
     * @param fromUnit 源单位
     * @param toUnit 目标单位
     * @return
     */
    @AgentTool("速度单位换算。支持单位：mps(米/秒)、kmh(千米/时)、mph(英里/时)、knot(节)、mach(马赫)。")
    public String convertSpeed(@AgentToolParam("数值") String value, @AgentToolParam("源单位") String fromUnit, @AgentToolParam("目标单位") String toUnit) {
        try {
            BigDecimal mps = toMetersPerSecond(new BigDecimal(value), fromUnit);
            BigDecimal result = fromMetersPerSecond(mps, toUnit);
            return formatResult(value + fromUnit, result.toPlainString() + toUnit, "速度换算");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "数值格式错误：" + value;
        }
    }

    // ========== 长度换算内部方法 ==========
    private BigDecimal toMeters(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "mm": return value.movePointLeft(3);
            case "cm": return value.movePointLeft(2);
            case "dm": return value.movePointLeft(1);
            case "m":  return value;
            case "km": return value.movePointRight(3);
            case "inch": return value.multiply(new BigDecimal("0.0254"));
            case "ft":   return value.multiply(new BigDecimal("0.3048"));
            case "yd":   return value.multiply(new BigDecimal("0.9144"));
            case "mile": return value.multiply(new BigDecimal("1609.344"));
            case "li":   return value.multiply(new BigDecimal("500"));
            case "zhang":return value.multiply(new BigDecimal("3.333333"));
            case "chi":  return value.multiply(new BigDecimal("0.3333333"));
            case "cun":  return value.multiply(new BigDecimal("0.03333333"));
            default: throw new IllegalArgumentException("不支持的长度单位：" + unit);
        }
    }

    private BigDecimal fromMeters(BigDecimal meters, String unit) {
        switch (unit.toLowerCase()) {
            case "mm": return meters.movePointRight(3);
            case "cm": return meters.movePointRight(2);
            case "dm": return meters.movePointRight(1);
            case "m":  return meters;
            case "km": return meters.movePointLeft(3);
            case "inch": return meters.divide(new BigDecimal("0.0254"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "ft":   return meters.divide(new BigDecimal("0.3048"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "yd":   return meters.divide(new BigDecimal("0.9144"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mile": return meters.divide(new BigDecimal("1609.344"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "li":   return meters.divide(new BigDecimal("500"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "zhang":return meters.divide(new BigDecimal("3.333333"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "chi":  return meters.divide(new BigDecimal("0.3333333"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "cun":  return meters.divide(new BigDecimal("0.03333333"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的长度单位：" + unit);
        }
    }

    // ========== 面积换算内部方法 ==========
    private BigDecimal toSquareMeters(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "mm2": return value.movePointLeft(6);
            case "cm2": return value.movePointLeft(4);
            case "dm2": return value.movePointLeft(2);
            case "m2":  return value;
            case "are": return value.multiply(new BigDecimal("100"));
            case "ha":  return value.multiply(new BigDecimal("10000"));
            case "km2": return value.movePointRight(6);
            case "sqin":return value.multiply(new BigDecimal("0.00064516"));
            case "sqft":return value.multiply(new BigDecimal("0.09290304"));
            case "acre":return value.multiply(new BigDecimal("4046.8564224"));
            case "mu":  return value.multiply(new BigDecimal("666.6666667"));
            case "qing":return value.multiply(new BigDecimal("6666.666667"));
            default: throw new IllegalArgumentException("不支持的面积单位：" + unit);
        }
    }

    private BigDecimal fromSquareMeters(BigDecimal sqMeters, String unit) {
        switch (unit.toLowerCase()) {
            case "mm2": return sqMeters.movePointRight(6);
            case "cm2": return sqMeters.movePointRight(4);
            case "dm2": return sqMeters.movePointRight(2);
            case "m2":  return sqMeters;
            case "are": return sqMeters.divide(new BigDecimal("100"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "ha":  return sqMeters.divide(new BigDecimal("10000"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "km2": return sqMeters.movePointLeft(6);
            case "sqin":return sqMeters.divide(new BigDecimal("0.00064516"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "sqft":return sqMeters.divide(new BigDecimal("0.09290304"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "acre":return sqMeters.divide(new BigDecimal("4046.8564224"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mu":  return sqMeters.divide(new BigDecimal("666.6666667"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "qing":return sqMeters.divide(new BigDecimal("6666.666667"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的面积单位：" + unit);
        }
    }

    // ========== 重量换算内部方法 ==========
    private BigDecimal toGrams(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "mg": return value.movePointLeft(3);
            case "g":  return value;
            case "kg": return value.movePointRight(3);
            case "t":  return value.movePointRight(6);
            case "lb": return value.multiply(new BigDecimal("453.59237"));
            case "oz": return value.multiply(new BigDecimal("28.3495231"));
            case "jin":   return value.multiply(new BigDecimal("500"));
            case "liang": return value.multiply(new BigDecimal("50"));
            default: throw new IllegalArgumentException("不支持的重量单位：" + unit);
        }
    }

    private BigDecimal fromGrams(BigDecimal grams, String unit) {
        switch (unit.toLowerCase()) {
            case "mg": return grams.movePointRight(3);
            case "g":  return grams;
            case "kg": return grams.movePointLeft(3);
            case "t":  return grams.movePointLeft(6);
            case "lb": return grams.divide(new BigDecimal("453.59237"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "oz": return grams.divide(new BigDecimal("28.3495231"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "jin":   return grams.divide(new BigDecimal("500"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "liang": return grams.divide(new BigDecimal("50"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的重量单位：" + unit);
        }
    }

    // ========== 温度换算内部方法 ==========
    private BigDecimal toCelsius(BigDecimal value, String unit) {
        switch (unit.toUpperCase()) {
            case "C": return value;
            case "F": return value.subtract(new BigDecimal("32")).multiply(new BigDecimal("5")).divide(new BigDecimal("9"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "K": return value.subtract(new BigDecimal("273.15"));
            default: throw new IllegalArgumentException("不支持的温度单位：" + unit);
        }
    }

    private BigDecimal fromCelsius(BigDecimal celsius, String unit) {
        switch (unit.toUpperCase()) {
            case "C": return celsius;
            case "F": return celsius.multiply(new BigDecimal("9")).divide(new BigDecimal("5"), DEFAULT_SCALE, RoundingMode.HALF_UP).add(new BigDecimal("32"));
            case "K": return celsius.add(new BigDecimal("273.15"));
            default: throw new IllegalArgumentException("不支持的温度单位：" + unit);
        }
    }

    // ========== 数据存储换算内部方法 ==========
    private BigDecimal toBytes(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "b":   return value;
            case "kb":  return value.movePointRight(3).divide(new BigDecimal("1"), DEFAULT_SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal("1024")).divide(new BigDecimal("1024"), DEFAULT_SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal("1024"));
            case "mb":  return value.multiply(new BigDecimal("1048576"));
            case "gb":  return value.multiply(new BigDecimal("1073741824"));
            case "tb":  return value.multiply(new BigDecimal("1099511627776L"));
            case "pb":  return value.multiply(new BigDecimal("1125899906842624L"));
            case "bit": return value.divide(new BigDecimal("8"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "kbit":
            case "kbps":return value.multiply(new BigDecimal("128"));
            case "mbit": return value.multiply(new BigDecimal("131072"));
            case "gbit": return value.multiply(new BigDecimal("134217728"));
            case "tbit": return value.multiply(new BigDecimal("137438953472"));
            default: throw new IllegalArgumentException("不支持的数据存储单位：" + unit);
        }
    }

    private BigDecimal fromBytes(BigDecimal bytes, String unit) {
        switch (unit.toLowerCase()) {
            case "b":   return bytes;
            case "kb":  return bytes.divide(new BigDecimal("1024"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mb":  return bytes.divide(new BigDecimal("1048576"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "gb":  return bytes.divide(new BigDecimal("1073741824"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "tb":  return bytes.divide(new BigDecimal("1099511627776L"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "pb":  return bytes.divide(new BigDecimal("1125899906842624L"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "bit": return bytes.multiply(new BigDecimal("8"));
            case "kbit":
            case "kbps":return bytes.divide(new BigDecimal("128"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mbit": return bytes.divide(new BigDecimal("131072"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "gbit": return bytes.divide(new BigDecimal("134217728"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "tbit": return bytes.divide(new BigDecimal("137438953472"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的数据存储单位：" + unit);
        }
    }

    // ========== 时间换算内部方法 ==========
    private BigDecimal toSeconds(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "ms": return value.movePointLeft(3);
            case "s":  return value;
            case "min":return value.multiply(new BigDecimal("60"));
            case "h":  return value.multiply(new BigDecimal("3600"));
            case "d":  return value.multiply(new BigDecimal("86400"));
            case "week":return value.multiply(new BigDecimal("604800"));
            case "month":return value.multiply(new BigDecimal("2592000"));
            case "year":return value.multiply(new BigDecimal("31536000"));
            default: throw new IllegalArgumentException("不支持的时间单位：" + unit);
        }
    }

    private BigDecimal fromSeconds(BigDecimal seconds, String unit) {
        switch (unit.toLowerCase()) {
            case "ms": return seconds.movePointRight(3);
            case "s":  return seconds;
            case "min":return seconds.divide(new BigDecimal("60"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "h":  return seconds.divide(new BigDecimal("3600"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "d":  return seconds.divide(new BigDecimal("86400"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "week":return seconds.divide(new BigDecimal("604800"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "month":return seconds.divide(new BigDecimal("2592000"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "year":return seconds.divide(new BigDecimal("31536000"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的时间单位：" + unit);
        }
    }

    // ========== 体积换算内部方法 ==========
    private BigDecimal toLiters(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "ml": return value.movePointLeft(3);
            case "l":  return value;
            case "m3": return value.multiply(new BigDecimal("1000"));
            case "gal":return value.multiply(new BigDecimal("3.785411784"));
            case "qt": return value.multiply(new BigDecimal("0.946352946"));
            case "pt": return value.multiply(new BigDecimal("0.473176473"));
            case "cup":return value.multiply(new BigDecimal("0.2365882365"));
            default: throw new IllegalArgumentException("不支持的体积单位：" + unit);
        }
    }

    private BigDecimal fromLiters(BigDecimal liters, String unit) {
        switch (unit.toLowerCase()) {
            case "ml": return liters.movePointRight(3);
            case "l":  return liters;
            case "m3": return liters.divide(new BigDecimal("1000"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "gal":return liters.divide(new BigDecimal("3.785411784"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "qt": return liters.divide(new BigDecimal("0.946352946"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "pt": return liters.divide(new BigDecimal("0.473176473"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "cup":return liters.divide(new BigDecimal("0.2365882365"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的体积单位：" + unit);
        }
    }

    // ========== 速度换算内部方法 ==========
    private BigDecimal toMetersPerSecond(BigDecimal value, String unit) {
        switch (unit.toLowerCase()) {
            case "mps": return value;
            case "kmh": return value.divide(new BigDecimal("3.6"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mph": return value.multiply(new BigDecimal("0.44704"));
            case "knot":return value.multiply(new BigDecimal("0.514444444"));
            case "mach":return value.multiply(new BigDecimal("340.29"));
            default: throw new IllegalArgumentException("不支持的速度单位：" + unit);
        }
    }

    private BigDecimal fromMetersPerSecond(BigDecimal mps, String unit) {
        switch (unit.toLowerCase()) {
            case "mps": return mps;
            case "kmh": return mps.multiply(new BigDecimal("3.6"));
            case "mph": return mps.divide(new BigDecimal("0.44704"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "knot":return mps.divide(new BigDecimal("0.514444444"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            case "mach":return mps.divide(new BigDecimal("340.29"), DEFAULT_SCALE, RoundingMode.HALF_UP);
            default: throw new IllegalArgumentException("不支持的速度单位：" + unit);
        }
    }

    /**
     * 格式化结果输出
     * @param from 源表示
     * @param to 目标表示
     * @param type 类型
     * @return
     */
    private String formatResult(String from, String to, String type) {
        return type + "：" + from + " = " + to;
    }
}
