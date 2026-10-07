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
import { KbListPage } from '@/pages/knowledge/KbListPage';
import { KbDocumentPage } from '@/pages/knowledge/KbDocumentPage';
import { RagRetrievePanel } from '@/pages/knowledge/RagRetrievePanel';
import { DataSourcePage } from '@/pages/knowledge/DataSourcePage';
import { VectorStorePage } from '@/pages/knowledge/VectorStorePage';
import { WorkflowListPage } from '@/pages/workflow/WorkflowListPage';
import { WorkflowEditPage } from '@/pages/workflow/WorkflowEditPage';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 数据类管理页面路由
 */
export const dataRoutes: RouteItem[] = [
  {
    path: '/knowledge-bases',
    key: 'knowledge-bases',
    label: '知识库',
    element: <KbListPage />,
  },
  {
    path: '/knowledge-bases/document',
    key: 'knowledge-bases-document',
    label: '文档与切片',
    element: <KbDocumentPage />,
  },
  {
    path: '/knowledge-bases/rag',
    key: 'knowledge-bases-rag',
    label: 'RAG 检索',
    element: <RagRetrievePanel />,
  },
  {
    path: '/data-sources',
    key: 'data-sources',
    label: '数据源接入',
    element: <DataSourcePage />,
  },
  {
    path: '/vector-stores',
    key: 'vector-stores',
    label: '向量存储',
    element: <VectorStorePage />,
  },
  {
    path: '/workflows',
    key: 'workflows',
    label: '工作流',
    element: <WorkflowListPage />,
  },
  {
    path: '/workflow/:name/edit',
    key: 'workflow-edit',
    label: '流程设计',
    element: <WorkflowEditPage />,
  },
];
