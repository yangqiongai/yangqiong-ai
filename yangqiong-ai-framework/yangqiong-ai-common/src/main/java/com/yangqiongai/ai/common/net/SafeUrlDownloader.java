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
package com.yangqiongai.ai.common.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 安全URL内容拉取
 * <p>
 * 面向外部可调用场景的URL拉取工具：内置协议白名单、内网地址防护、
 * 逐跳重定向校验、大小与超时限制，防止利用URL拉取探测内网。
 * </p>
 * @author yangqiong
 */
public class SafeUrlDownloader {

    /**
     * Content-Disposition中filename的提取格式（支持带引号与裸值）
     */
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("filename\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))");

    /**
     * 单文件大小上限（字节）
     */
    private final long maxSizeBytes;

    /**
     * 连接超时（毫秒）
     */
    private final int connectTimeoutMs;

    /**
     * 读取超时（毫秒）
     */
    private final int readTimeoutMs;

    /**
     * 最大重定向跳数
     */
    private final int maxRedirects;

    /**
     * 允许拉取的内网地址白名单（精确IP/主机名或CIDR网段，用于放行内网MinIO等受信地址）
     */
    private final java.util.List<String> allowedInternalHosts;

    /**
     * 拉取结果
     */
    public static class Result {

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 内容类型
         */
        private String contentType;

        /**
         * 文件内容
         */
        private byte[] bytes;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    public SafeUrlDownloader(long maxSizeBytes, int connectTimeoutMs, int readTimeoutMs, int maxRedirects) {
        this(maxSizeBytes, connectTimeoutMs, readTimeoutMs, maxRedirects, java.util.Collections.emptyList());
    }

    public SafeUrlDownloader(long maxSizeBytes, int connectTimeoutMs, int readTimeoutMs, int maxRedirects,
                             java.util.List<String> allowedInternalHosts) {
        this.maxSizeBytes = maxSizeBytes;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxRedirects = maxRedirects;
        this.allowedInternalHosts = allowedInternalHosts != null
                ? java.util.List.copyOf(allowedInternalHosts) : java.util.List.of();
    }

    /**
     * 拉取URL内容（含安全校验与重定向逐跳跟随）
     * @param url
     * @return
     * @throws IOException
     */
    public Result fetch(String url) throws IOException {
        return fetch(url, 0);
    }

    /**
     * 按跳递归拉取（重定向逐跳重新校验目标地址）
     * @param url
     * @param redirectCount
     * @return
     * @throws IOException
     */
    private Result fetch(String url, int redirectCount) throws IOException {
        URL parsed = parseAndValidateUrl(url);
        HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        // 重定向手动跟随，确保每一跳目标都经过内网地址校验
        conn.setInstanceFollowRedirects(false);
        int status = conn.getResponseCode();
        if (isRedirect(status)) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (redirectCount >= maxRedirects) {
                throw new IOException("重定向次数超过限制: " + maxRedirects);
            }
            if (location == null || location.isBlank()) {
                throw new IOException("重定向响应缺少Location头");
            }
            // Location可能为相对路径，基于当前URL解析为绝对地址
            return fetch(new URL(parsed, location).toString(), redirectCount + 1);
        }
        if (status < 200 || status >= 300) {
            conn.disconnect();
            throw new IOException("URL响应状态异常: " + status);
        }
        long contentLength = conn.getContentLengthLong();
        if (contentLength > maxSizeBytes) {
            conn.disconnect();
            throw new IOException("文件大小超过限制: " + maxSizeBytes + "字节");
        }
        try {
            Result result = new Result();
            result.setContentType(conn.getContentType());
            result.setBytes(readLimited(conn.getInputStream()));
            result.setFileName(resolveFileName(conn.getHeaderField("Content-Disposition"), parsed));
            return result;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 判断是否重定向状态码
     * @param status
     * @return
     */
    private boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == 303 || status == 307 || status == 308;
    }

    /**
     * 解析并校验URL（协议白名单+主机内网防护）
     * @param url
     * @return
     * @throws IOException
     */
    private URL parseAndValidateUrl(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("URL不能为空");
        }
        URL parsed;
        try {
            parsed = new URL(url.trim());
        } catch (Exception e) {
            throw new IOException("URL格式不合法: " + e.getMessage());
        }
        String protocol = parsed.getProtocol() == null ? "" : parsed.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IOException("仅支持http/https协议: " + protocol);
        }
        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("URL缺少主机地址");
        }
        validateHost(host);
        return parsed;
    }

    /**
     * 校验主机解析出的所有IP均非内网或保留地址（白名单命中的受信内网地址放行）
     * @param host
     * @throws IOException
     */
    protected void validateHost(String host) throws IOException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IOException("主机地址解析失败: " + host);
        }
        if (matchesWhitelist(host, addresses)) {
            return;
        }
        for (InetAddress address : addresses) {
            if (isForbiddenAddress(address)) {
                throw new IOException("URL指向内网或保留地址，禁止拉取: " + address.getHostAddress());
            }
        }
    }

    /**
     * 判断主机或其解析地址是否命中内网白名单（精确主机名/IP或CIDR网段）
     * @param host
     * @param addresses
     * @return
     */
    private boolean matchesWhitelist(String host, InetAddress[] addresses) {
        for (String entry : allowedInternalHosts) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String item = entry.trim();
            int slash = item.indexOf('/');
            if (slash > 0) {
                InetAddress network = parseIpLiteral(item.substring(0, slash));
                if (network == null) {
                    continue;
                }
                int prefixLength;
                try {
                    prefixLength = Integer.parseInt(item.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                for (InetAddress address : addresses) {
                    if (cidrMatches(address, network, prefixLength)) {
                        return true;
                    }
                }
            } else if (item.equalsIgnoreCase(host)) {
                return true;
            } else {
                InetAddress literal = parseIpLiteral(item);
                if (literal == null) {
                    continue;
                }
                for (InetAddress address : addresses) {
                    if (literal.equals(address)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 解析IP字面量（仅IP形式，避免对主机名条目触发DNS查询）
     * @param text
     * @return
     */
    private InetAddress parseIpLiteral(String text) {
        String value = text.trim();
        boolean ipv4 = value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        if (!ipv4 && !value.contains(":")) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断地址是否落在CIDR网段内
     * @param address
     * @param network
     * @param prefixLength
     * @return
     */
    private boolean cidrMatches(InetAddress address, InetAddress network, int prefixLength) {
        byte[] addrBytes = address.getAddress();
        byte[] netBytes = network.getAddress();
        if (addrBytes.length != netBytes.length || prefixLength < 0
                || prefixLength > addrBytes.length * 8) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addrBytes[i] != netBytes[i]) {
                return false;
            }
        }
        int remainBits = prefixLength % 8;
        if (remainBits > 0) {
            int mask = 0xFF << (8 - remainBits) & 0xFF;
            return (addrBytes[fullBytes] & mask) == (netBytes[fullBytes] & mask);
        }
        return true;
    }

    /**
     * 判断地址是否禁止拉取（protected便于子类按部署环境调整策略）
     * @param address
     * @return
     */
    protected boolean isForbiddenAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()
                || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (bytes.length == 16) {
                // IPv6唯一本地地址fc00::/7
                if ((bytes[0] & 0xfe) == 0xfc) {
                    return true;
                }
                // IPv4映射地址::ffff:x.x.x.x（未归一化为Inet4Address时逐段校验）
                boolean v4Mapped = true;
                for (int i = 0; i < 10; i++) {
                    if (bytes[i] != 0) {
                        v4Mapped = false;
                        break;
                    }
                }
                if (v4Mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                    return isForbiddenIpv4(bytes[12] & 0xff, bytes[13] & 0xff);
                }
            }
        }
        return false;
    }

    /**
     * 校验IPv4段是否禁止（用于映射地址判断）
     * @param first
     * @param second
     * @return
     */
    private boolean isForbiddenIpv4(int first, int second) {
        return first == 0 || first == 127 || first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254)
                || (first & 0xf0) == 0xe0;
    }

    /**
     * 限量读取响应体（防止超限内容打爆内存）
     * @param in
     * @return
     * @throws IOException
     */
    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int n;
        try {
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > maxSizeBytes) {
                    throw new IOException("文件大小超过限制: " + maxSizeBytes + "字节");
                }
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * 解析文件名（Content-Disposition优先，回退URL路径末段，兜底download）
     * @param contentDisposition
     * @param parsed
     * @return
     */
    private String resolveFileName(String contentDisposition, URL parsed) {
        if (contentDisposition != null) {
            Matcher matcher = FILENAME_PATTERN.matcher(contentDisposition);
            if (matcher.find()) {
                String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (name != null && !name.isBlank()) {
                    return URLDecoder.decode(name, StandardCharsets.UTF_8);
                }
            }
        }
        String path = parsed.getPath();
        if (path != null && !path.isBlank()) {
            int slash = path.lastIndexOf('/');
            String name = URLDecoder.decode(slash >= 0 ? path.substring(slash + 1) : path, StandardCharsets.UTF_8)
                    .trim();
            if (!name.isBlank()) {
                return name;
            }
        }
        return "download";
    }
}
