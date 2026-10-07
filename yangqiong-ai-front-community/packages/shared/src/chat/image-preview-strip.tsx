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
import React from 'react';
import { X } from 'lucide-react';

export interface ImagePreviewStripProps {
  images: string[];

  onRemove: (index: number) => void;
}

/**
 * 待发送图片预览条（输入框上方缩略图展示，支持删除）
 * @param props
 * @return
 */
export const ImagePreviewStrip: React.FC<ImagePreviewStripProps> = ({ images, onRemove }) => {
  if (images.length === 0) {
    return null;
  }
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
      {images.map((dataUrl, index) => (
        <div
          key={`${index}-${dataUrl.slice(-16)}`}
          style={{ position: 'relative', width: 64, height: 64 }}
        >
          <img
            src={dataUrl}
            alt={`待发送图片${index + 1}`}
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              borderRadius: 6,
              border: '1px solid rgba(0,0,0,0.12)',
              display: 'block',
            }}
          />
          <button
            type="button"
            aria-label="删除图片"
            onClick={() => onRemove(index)}
            style={{
              position: 'absolute',
              top: -6,
              right: -6,
              width: 18,
              height: 18,
              borderRadius: '50%',
              border: 'none',
              background: 'rgba(0,0,0,0.55)',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              padding: 0,
            }}
          >
            <X size={12} />
          </button>
        </div>
      ))}
    </div>
  );
};
