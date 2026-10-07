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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 二维码生成工具
 * <p>
 * 提供二维码生成能力，支持自定义尺寸、颜色和纠错级别。
 * 默认使用在线API（controller.qrserver.com，免费无需Key）生成二维码图片URL。
 * </p>
 * @author yangqiong
 */
@Component
public class QrCodeTool implements Tool {

    /**
     * 内置工具自动装配到所有Agent工具箱
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.CUSTOM;
    }

    /**
     * 在线二维码生成API基础URL
     */
    private static final String QR_API_BASE = "https://api.qrserver.com/v1/create-qr-code/";

    /**
     * 是否启用二维码工具
     */
    @Value("${ai.tool.qrcode.enabled:true}")
    private boolean enabled;

    /**
     * 生成二维码（默认尺寸）
     * @param data 二维码内容（URL、文本等）
     * @return
     */
    @AgentTool("生成二维码图片URL。输入要编码的内容（URL、文本等），返回可访问的二维码图片URL。前端可直接展示该URL。")
    public String generateQrCode(@AgentToolParam("二维码内容（URL、文本等）") String data) {
        if (!enabled) {
            return "二维码工具已被禁用";
        }
        if (data == null || data.trim().isEmpty()) {
            return "错误：二维码内容不能为空";
        }
        String encoded = URLEncoder.encode(data, StandardCharsets.UTF_8);
        String url = QR_API_BASE + "?size=300x300&data=" + encoded;
        return "二维码生成成功：\n" +
                "  内容：" + data + "\n" +
                "  尺寸：300x300\n" +
                "  图片URL：" + url + "\n" +
                "  使用方式：前端可直接通过该URL加载并展示二维码图片";
    }

    /**
     * 生成自定义尺寸的二维码
     * @param data 二维码内容
     * @param size 尺寸（像素，格式 WxH，如 200x200、500x500）
     * @return
     */
    @AgentTool("生成自定义尺寸的二维码图片URL。输入内容和尺寸（格式 WxH，如200x200、500x500），返回可访问的二维码图片URL。")
    public String generateQrCodeWithSize(@AgentToolParam("二维码内容（URL、文本等）") String data, @AgentToolParam("二维码尺寸，格式 WxH，如200x200、500x500") String size) {
        if (!enabled) {
            return "二维码工具已被禁用";
        }
        if (data == null || data.trim().isEmpty()) {
            return "错误：二维码内容不能为空";
        }
        if (size == null || !size.matches("\\d+x\\d+")) {
            return "错误：尺寸格式应为 WxH（如 200x200）";
        }
        // 限制最大尺寸
        String[] parts = size.split("x");
        int width = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);
        if (width > 1000 || height > 1000) {
            return "错误：尺寸不能超过1000x1000像素";
        }
        if (width < 50 || height < 50) {
            return "错误：尺寸不能小于50x50像素";
        }

        String encoded = URLEncoder.encode(data, StandardCharsets.UTF_8);
        String url = QR_API_BASE + "?size=" + size + "&data=" + encoded;
        return "二维码生成成功：\n" +
                "  内容：" + data + "\n" +
                "  尺寸：" + size + "\n" +
                "  图片URL：" + url;
    }

    /**
     * 生成带颜色的二维码
     * @param data 二维码内容
     * @param size 尺寸（如 300x300）
     * @param colorHex 前景色（16进制，如 000000 表示黑色）
     * @param bgColorHex 背景色（16进制，如 FFFFFF 表示白色）
     * @return
     */
    @AgentTool("生成自定义颜色和尺寸的二维码图片URL。输入内容、尺寸（WxH）、前景色和背景色（16进制，如000000和FFFFFF）。")
    public String generateQrCodeWithColor(@AgentToolParam("二维码内容（URL、文本等）") String data, @AgentToolParam("二维码尺寸，格式 WxH，如300x300") String size, @AgentToolParam("前景色（16进制，如000000表示黑色）") String colorHex, @AgentToolParam("背景色（16进制，如FFFFFF表示白色）") String bgColorHex) {
        if (!enabled) {
            return "二维码工具已被禁用";
        }
        if (data == null || data.trim().isEmpty()) {
            return "错误：二维码内容不能为空";
        }
        if (size == null || !size.matches("\\d+x\\d+")) {
            return "错误：尺寸格式应为 WxH（如 200x200）";
        }
        if (colorHex == null || !colorHex.matches("[0-9A-Fa-f]{6}")) {
            return "错误：前景色应为6位16进制（如 000000）";
        }
        if (bgColorHex == null || !bgColorHex.matches("[0-9A-Fa-f]{6}")) {
            return "错误：背景色应为6位16进制（如 FFFFFF）";
        }

        String encoded = URLEncoder.encode(data, StandardCharsets.UTF_8);
        String url = QR_API_BASE + "?size=" + size +
                "&color=" + colorHex +
                "&bgcolor=" + bgColorHex +
                "&data=" + encoded;
        return "二维码生成成功：\n" +
                "  内容：" + data + "\n" +
                "  尺寸：" + size + "\n" +
                "  前景色：#" + colorHex + "\n" +
                "  背景色：#" + bgColorHex + "\n" +
                "  图片URL：" + url;
    }

    /**
     * 生成WiFi连接二维码
     * @param ssid WiFi名称
     * @param password WiFi密码
     * @param encryption 加密类型（WPA、WEP、nopass）
     * @return
     */
    @AgentTool("生成WiFi连接二维码。输入WiFi名称、密码和加密类型（WPA/WEP/nopass），生成扫码即可连接WiFi的二维码。")
    public String generateWifiQrCode(@AgentToolParam("WiFi名称") String ssid, @AgentToolParam(value = "WiFi密码", required = false) String password, @AgentToolParam(value = "加密类型（WPA/WEP/nopass），默认WPA", required = false) String encryption) {
        if (!enabled) {
            return "二维码工具已被禁用";
        }
        if (ssid == null || ssid.trim().isEmpty()) {
            return "错误：WiFi名称不能为空";
        }
        String enc = encryption == null ? "WPA" : encryption.toUpperCase();
        if (!enc.equals("WPA") && !enc.equals("WEP") && !enc.equals("NOPASS")) {
            return "错误：加密类型必须为 WPA、WEP 或 NOPASS";
        }
        // WiFi二维码格式：WIFI:T:WPA;S:mynetwork;P:mypass;;
        String wifiData = "WIFI:T:" + enc + ";S:" + escapeWifi(ssid) + ";P:" + escapeWifi(password == null ? "" : password) + ";;";
        String encoded = URLEncoder.encode(wifiData, StandardCharsets.UTF_8);
        String url = QR_API_BASE + "?size=300x300&data=" + encoded;
        return "WiFi二维码生成成功：\n" +
                "  WiFi名称：" + ssid + "\n" +
                "  加密类型：" + enc + "\n" +
                "  图片URL：" + url + "\n" +
                "  使用方式：扫码后手机会提示连接WiFi";
    }

    /**
     * 生成名片二维码（vCard）
     * @param name 姓名
     * @param phone 电话
     * @param email 邮箱
     * @param organization 组织
     * @return
     */
    @AgentTool("生成vCard名片二维码。输入姓名、电话、邮箱、组织，生成扫码即可保存联系人的名片二维码。")
    public String generateVCardQrCode(@AgentToolParam("姓名") String name, @AgentToolParam(value = "电话号码", required = false) String phone, @AgentToolParam(value = "电子邮箱", required = false) String email, @AgentToolParam(value = "组织/公司名称", required = false) String organization) {
        if (!enabled) {
            return "二维码工具已被禁用";
        }
        if (name == null || name.trim().isEmpty()) {
            return "错误：姓名不能为空";
        }
        StringBuilder vcard = new StringBuilder();
        vcard.append("BEGIN:VCARD\n");
        vcard.append("VERSION:3.0\n");
        vcard.append("FN:").append(name).append("\n");
        if (phone != null && !phone.trim().isEmpty()) {
            vcard.append("TEL:").append(phone).append("\n");
        }
        if (email != null && !email.trim().isEmpty()) {
            vcard.append("EMAIL:").append(email).append("\n");
        }
        if (organization != null && !organization.trim().isEmpty()) {
            vcard.append("ORG:").append(organization).append("\n");
        }
        vcard.append("END:VCARD");

        String encoded = URLEncoder.encode(vcard.toString(), StandardCharsets.UTF_8);
        String url = QR_API_BASE + "?size=300x300&data=" + encoded;
        return "名片二维码生成成功：\n" +
                "  姓名：" + name + "\n" +
                "  电话：" + (phone == null ? "" : phone) + "\n" +
                "  邮箱：" + (email == null ? "" : email) + "\n" +
                "  组织：" + (organization == null ? "" : organization) + "\n" +
                "  图片URL：" + url;
    }

    /**
     * 转义WiFi字符串中的特殊字符
     * @param value 待转义的字符串
     * @return
     */
    private String escapeWifi(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }
}
