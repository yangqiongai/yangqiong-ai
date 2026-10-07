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
package com.yangqiongai.ai.common.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 图片处理工具
 * @author yangqiong
 */
public final class ImageUtils {

    /**
     * 图标最大边长（像素）
     */
    private static final int MAX_ICON_SIZE = 80;

    private static final String DATA_URL_PREFIX = "data:image/";

    private ImageUtils() {
    }

    /**
     * 压缩 base64 图标到最大边长 80px（等比缩放，透明通道保留）
     * 非base64图片或压缩失败时原样返回
     * @param icon
     * @return
     */
    public static String compressIconDataUrl(String icon) {
        return compressIconDataUrl(icon, MAX_ICON_SIZE);
    }

    /**
     * 压缩 base64 图标到最大边长指定像素（等比缩放，透明通道保留）
     * 非base64图片或压缩失败时原样返回
     * @param icon
     * @param maxSize 最大边长（像素）
     * @return
     */
    public static String compressIconDataUrl(String icon, int maxSize) {
        if (icon == null || !icon.startsWith(DATA_URL_PREFIX) || !icon.contains(";base64,")) {
            return icon;
        }
        try {
            byte[] bytes = Base64.getDecoder()
                    .decode(icon.substring(icon.indexOf(";base64,") + ";base64,".length()));
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return icon;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= maxSize && height <= maxSize) {
                return icon;
            }
            double scale = (double) maxSize / Math.max(width, height);
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return DATA_URL_PREFIX + "png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return icon;
        }
    }
}
