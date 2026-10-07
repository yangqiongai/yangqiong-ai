# syntax=docker/dockerfile:1
# 泱穹智能体平台前端镜像（React 管理后台，nginx 托管静态资源并反代后端 API）
# 构建上下文 = 公开仓根（需要 front-libs / front-community 两个目录）
FROM node:20-alpine AS build
WORKDIR /build

# pnpm 版本须与 pnpm-lock.yaml（lockfileVersion 9.0）匹配
RUN npm install -g pnpm@10.33.0

COPY yangqiong-ai-front-libs/ yangqiong-ai-front-libs/
COPY yangqiong-ai-front-community/ yangqiong-ai-front-community/

# admin 经 file: 相对路径引用 front-libs 的 workflow 包（types 指向 dist），必须先构建 front-libs
RUN cd yangqiong-ai-front-libs \
 && pnpm install --frozen-lockfile \
 && pnpm build \
 && cd ../yangqiong-ai-front-community \
 && pnpm install --frozen-lockfile \
 && pnpm --filter @yangqiong/shared build \
 && pnpm --filter @yangqiong/admin build

FROM nginx:1.27-alpine
COPY --from=build /build/yangqiong-ai-front-community/packages/admin/dist /usr/share/nginx/html
COPY yangqiong-ai-docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
