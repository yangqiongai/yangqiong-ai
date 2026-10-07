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
import { useCallback, useState } from 'react';
import type { ClipboardEvent } from 'react';

/**
 * 单图大小上限（5MB）
 */
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * 文件转为 dataURL
 * @param file
 * @return
 */
const fileToDataUrl = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('图片读取失败'));
    reader.readAsDataURL(file);
  });

/**
 * 聊天输入框图片粘贴（提取剪贴板图片、类型与大小校验、dataURL状态与增删）
 * @param onInvalid 校验不通过时的提示回调（如toast）
 * @return
 */
export const useImagePaste = (onInvalid?: (message: string) => void) => {
  const [images, setImages] = useState<string[]>([]);

  /**
   * 追加图片文件（过滤非图片与超限文件，合法文件转dataURL入列）
   * @param files
   */
  const addFiles = useCallback(
    (files: FileList | File[] | null) => {
      if (!files) {
        return;
      }
      const list = Array.from(files);
      const imageFiles: File[] = [];
      for (const file of list) {
        if (!file.type.startsWith('image/')) {
          continue;
        }
        if (file.size > MAX_IMAGE_BYTES) {
          onInvalid?.('图片大小不能超过5MB');
          continue;
        }
        imageFiles.push(file);
      }
      if (imageFiles.length === 0) {
        return;
      }
      Promise.all(imageFiles.map(fileToDataUrl))
        .then((dataUrls) => setImages((prev) => [...prev, ...dataUrls]))
        .catch(() => onInvalid?.('图片读取失败'));
    },
    [onInvalid],
  );

  /**
   * 处理粘贴事件（仅拦截含图片的粘贴，纯文本粘贴保持默认行为）
   * @param event
   */
  const handlePaste = useCallback(
    (event: ClipboardEvent<HTMLElement>) => {
      const files = Array.from(event.clipboardData?.files ?? []);
      const hasImage = files.some((file) => file.type.startsWith('image/'));
      if (!hasImage) {
        return;
      }
      event.preventDefault();
      addFiles(files);
    },
    [addFiles],
  );

  /**
   * 移除指定位置的待发送图片
   * @param index
   */
  const removeImage = useCallback((index: number) => {
    setImages((prev) => prev.filter((_, i) => i !== index));
  }, []);

  /**
   * 清空待发送图片
   */
  const clearImages = useCallback(() => {
    setImages([]);
  }, []);

  return { images, addFiles, handlePaste, removeImage, clearImages };
};
