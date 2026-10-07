# 泱穹智能体平台前端（YangQiong Agent Platform Frontend）

泱穹智能体平台前端项目（社区版），使用 pnpm workspace 管理的 monorepo 结构。

## 📁 项目结构

```
yangqiong-ai-front-community/
├── packages/
│   ├── admin/          # 管理后台 (Ant Design)
│   └── shared/         # 共享库 (类型定义、工具函数、常量)
├── pnpm-workspace.yaml
├── tsconfig.base.json
├── .editorconfig
├── .npmrc
├── .prettierrc
└── package.json
```

## 🛠️ 技术栈

- **构建工具**: Vite
- **包管理**: pnpm (workspace monorepo)
- **开发语言**: TypeScript
- **核心框架**: React 18 + React Router 6
- **状态管理**:
  - 服务端缓存: TanStack Query
  - 客户端状态: Zustand
- **UI 组件库**:
  - 管理端 (admin): Ant Design
- **API 通信**: Axios + TanStack Query
- **测试**: Vitest + React Testing Library + Playwright

## 🚀 开发指南

### 环境要求

- Node.js >= 18.0.0
- pnpm >= 8.0.0

### 安装依赖

```bash
pnpm install
```

### 开发模式

```bash
# 启动管理端 (端口 3001)
pnpm dev

# 单独启动管理端
pnpm dev:admin
```

### 构建

```bash
# 构建所有项目
pnpm build

# 单独构建
pnpm build:admin
```

### 测试

```bash
# 运行所有测试
pnpm test
```

### 代码检查

```bash
# 类型检查
pnpm type-check

# 代码格式化
pnpm format

# ESLint 检查
pnpm lint
```

## 📦 包说明

### admin (管理后台)

- **端口**: 3001
- **UI 库**: Ant Design
- **路由**: 基于角色的路由守卫
- **状态管理**: Zustand (认证) + TanStack Query (数据)

### shared (共享库)

- 类型定义 (`types/`)
- 常量定义 (`constants/`)
- 工具函数 (`utils/`)
- 公共 Hooks (`hooks/`)

## 🔧 环境变量

每个包都支持 `.env` 文件配置：

```env
VITE_API_BASE_URL=http://localhost:8082
VITE_APP_NAME=YangQiongAI
VITE_ENV=development
```

### 后端代理

开发环境通过 Vite dev server proxy 将 `/api` 请求代理到后端服务（默认端口 8082）：

```ts
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8082',
      changeOrigin: true,
    },
  },
}
```

## 📝 部署

构建产物为纯静态资源（SPA），可部署到 CDN + Nginx。

### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name admin.yangqiong-ai.com;
    root /var/www/yangqiong-ai/admin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8082;
    }
}
```

## 开源协议

本项目依据 [AGPL-3.0](./LICENSE) 协议开源（社区版）。多租户管理、配额、用量计费、SSO 等企业能力为商业版（yangqiong-ai-front-saas-enterprise，未随社区分发），另行商业授权，详见[开源许可与商业授权](https://yangqiongai.com/license.html)。

## 商标与品牌

「泱穹」「泱穹AI」「YangQiong AI」及相关 Logo 为泱穹的商标。开源许可不授予商标使用权：派生发行不得在产品名称、宣传物料或域名中使用"泱穹"名称、Logo 或易引起混淆的近似标识。详见[商标使用政策](https://yangqiongai.com/trademark-policy.html)。
