# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims / hazmat-management 容器化部署（H2）
#   构建：docker build -t hazmat-management:1.0.0 .
#         自包含多阶段构建：前端 npm ci + build → 拷入 server 静态目录 →
#         Maven 打包出单 jar → 运行时镜像只带 jar。不需要本机预装 Node/Maven/JDK，
#         也不需要事先产出 server/target/*.jar。
#   运行：docker run -d --name hazmat -p 8080:8080 \
#            -e APP_TOKEN_SECRET="$(openssl rand -hex 32)" \
#            -e APP_SEED_ADMIN_PASSWORD="<strong-password>" \
#            -e APP_SEED_DEMO_PASSWORD="<demo-password>" \
#            -e DEEPSEEK_API_KEY="<your-api-key>" \
#            -v hazmat-data:/app/data hazmat-management:1.0.0
#   探针：GET /health（curl -fsS http://localhost:8080/health）
#   说明：镜像内为 H2 文件库（数据卷 /app/data）；改用 MySQL 时传
#        -e SPRING_PROFILES_ACTIVE=mysql 及 MYSQL_* 环境变量（见 README「MySQL 8」）
# ═══════════════════════════════════════════════════════════════════════════

# ── 阶段 1：构建前端（Vue 3 + Vite）────────────────────────────────────────
FROM node:22-bookworm-slim AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# ── 阶段 2：打包后端（前端产物进 classpath:/static）────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY server/pom.xml server/pom.xml
COPY server/src server/src
COPY --from=web /src/web/dist server/src/main/resources/static
RUN mvn -B -q -f server/pom.xml -DskipTests package

# ── 阶段 3：运行时（仅 JRE + 单 jar）───────────────────────────────────────
FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="hazmat-management" \
      org.opencontainers.image.description="实验室危险化学品全流程管理（Spring Boot 4 + Vue 3）" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app

# 时区与健康检查工具（curl 用于 HEALTHCHECK 探针）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && (command -v curl >/dev/null 2>&1 || (apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*))

# 单 jar 打包：前端静态资源已由构建阶段同步进 jar 的 classpath:/static
COPY --from=build /src/server/target/hazmat-management-1.0.0.jar /app/app.jar

# 数据卷：H2 文件库目录（备份脚本对同一目录做快照）
VOLUME ["/app/data"]
EXPOSE 8080

# 容器内以非 root 运行
RUN useradd -r -u 10001 hazmat && mkdir -p /app/data /app/logs && chown -R hazmat:hazmat /app
USER hazmat

ENV JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8" \
    SPRING_PROFILES_ACTIVE="" \
    SERVER_PORT=8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT} ${SPRING_PROFILES_ACTIVE:+--spring.profiles.active=$SPRING_PROFILES_ACTIVE}"]
