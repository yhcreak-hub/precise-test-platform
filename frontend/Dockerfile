# =====================================================================
# 精准测试平台 - 前端镜像
# 构建阶段：node:20-alpine 安装依赖并构建（vue-tsc + vite build）
# 运行阶段：nginx:1.25-alpine 托管静态资源并反向代理 /api 到 backend
# =====================================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json ./
# 国内网络可优先使用 npmmirror 镜像，失败则回退官方源
RUN npm install --registry=https://registry.npmmirror.com || npm install

COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.25-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
