#!/usr/bin/env sh
# 泱穹 AI 开源版 Docker 镜像构建脚本（Linux / macOS）
# 用法：
#   ./build.sh                          构建 backend / frontend 镜像
#   REGISTRY=registry.example.com/yq ./build.sh        指定镜像仓库前缀
#   TAG=v1.0.0 ./build.sh               指定镜像标签（默认 latest）
#   PUSH=1 ./build.sh                   构建后推送镜像
#   UP=1 ./build.sh                     构建后启动整套服务（docker compose up -d）
set -e
cd "$(dirname "$0")"

[ -n "$REGISTRY" ] && export YQ_IMAGE_PREFIX="$REGISTRY"
[ -n "$TAG" ] && export YQ_IMAGE_TAG="$TAG"

docker compose build backend frontend

if [ "$PUSH" = "1" ]; then
  docker compose push backend frontend
fi

if [ "$UP" = "1" ]; then
  docker compose up -d
fi

echo "Docker 镜像构建完成"
