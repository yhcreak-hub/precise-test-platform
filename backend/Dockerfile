# =====================================================================
# 精准测试平台 - 后端镜像
# 构建阶段：maven:3.9-eclipse-temurin-17 编译打包（多模块）
# 运行阶段：eclipse-temurin:17-jre 运行 pt-web 可执行 jar
# =====================================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 先复制全部 pom.xml，提前下载依赖，充分利用 Docker 层缓存
COPY pom.xml .
COPY pt-common/pom.xml pt-common/pom.xml
COPY pt-auth/pom.xml pt-auth/pom.xml
COPY pt-repo/pom.xml pt-repo/pom.xml
COPY pt-analyze/pom.xml pt-analyze/pom.xml
COPY pt-casegen/pom.xml pt-casegen/pom.xml
COPY pt-mapping/pom.xml pt-mapping/pom.xml
COPY pt-task/pom.xml pt-task/pom.xml
COPY pt-web/pom.xml pt-web/pom.xml
# 首次构建需联网下载依赖；go-offline 失败不影响后续正式构建
RUN mvn -q -B dependency:go-offline || true

# 复制源码并打包（跳过测试）
COPY . .
RUN mvn -q -B -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

# pt-web 的 finalName 固定为 pt-web，产物为可执行 fat jar
COPY --from=build /workspace/pt-web/target/pt-web.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
