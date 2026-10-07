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
import { Result } from 'antd';

interface PlaceholderPageProps {
  menu?: string;
}

/**
 * 通用占位页：{menu} 开发中
 */
export const PlaceholderPage: React.FC<PlaceholderPageProps> = ({ menu }) => {
  return (
    <Result
      status="info"
      title={`${menu ?? '该模块'} 开发中`}
      subTitle="此模块将在后续任务中实现"
    />
  );
};
