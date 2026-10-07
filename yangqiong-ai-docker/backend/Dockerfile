# syntax=docker/dockerfile:1
# 泱穹智能体平台后端镜像（Spring Boot 单体，端口 8082）
# 构建上下文 = 公开仓根（需要 framework / platform / server 三个目录）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY yangqiong-ai-framework/ yangqiong-ai-framework/
COPY yangqiong-ai-platform/ yangqiong-ai-platform/
COPY yangqiong-ai-server/ yangqiong-ai-server/

# 三个独立 Maven 反应器按依赖顺序构建：framework -> platform -> server
# .m2 依赖目录用 BuildKit 缓存挂载，重复构建免重复下载
RUN --mount=type=cache,target=/root/.m2 \
    cd yangqiong-ai-framework \
 && mvn -q install -DskipTests \
 && cd ../yangqiong-ai-platform \
 && mvn -q install -DskipTests \
 && cd ../yangqiong-ai-server \
 && mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
# server 工程自定义 finalName：ai-server-exec.jar 为 spring-boot repackage 可执行包
COPY --from=build /build/yangqiong-ai-server/target/ai-server-exec.jar app.jar

# Flyway 首次启动自动建表；数据源 / Redis 通过环境变量覆盖（见 docker-compose.yml）
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
