# 精准测试平台（Precise Test Platform）

> 自动识别被测项目接口 → 自动生成接口自动化用例 → 建立用例与代码的关联关系 → 版本变更分析筛选精准回归用例。

一个**前后端分离**的精准测试平台，覆盖"代码 → 接口 → 用例 → 回归"的完整质量链路，测试人员注册被测项目后即可一键完成接口识别、用例生成、用例执行与变更回归筛选。

---

## ✨ 核心能力

| 能力 | 说明 | 里程碑 |
|------|------|--------|
| **接口自动识别** | JavaParser 静态扫描被测项目源码，自动提取接口清单（路径/HTTP 方法/参数结构/JSR-303 校验/代码定位） | M2 |
| **用例自动生成** | 规则引擎基于接口 Schema 生成四类契约用例：正常流程 / 必填校验 / 边界值 / 异常输入 | M3 |
| **测试空洞检测** | 统计"有接口但无用例"的空洞率，一键为空洞接口补齐用例 | M3 |
| **用例编辑与执行** | 按接口分组展示用例；手动修改入参/断言/请求头；真实 HTTP 请求执行 + 断言校验（PASS/FAIL/ERROR） | M4 |
| **用例↔代码关联** | 建立"用例 → 接口 → Controller 方法"的静态映射（direct/static），可反向定位被测代码 | M4 |
| **版本变更分析** | git diff 对比两个版本 → 解析变更的 Controller 类 → 反查映射 → 筛选需回归的用例（精准回归） | M5 |

---

## 🏗️ 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3.2 · Maven 多模块（8 个模块）· MyBatis-Plus · MySQL 8 · Redis · JWT · SpringDoc OpenAPI · JavaParser · RestTemplate |
| 前端 | Vue 3 · Vite · TypeScript · Element Plus · Pinia · Vue Router · Axios · ECharts |
| 部署 | Docker Compose（MySQL 8 / Redis 7 / 后端 8080 / 前端 Nginx 80） |

---

## 🧱 架构与模块

```
┌────────────────────────────────────────────────────────────┐
│ 前端 (Vue3)   项目管理 / 接口管理 / 用例管理 / 变更分析       │
├────────────────────────────────────────────────────────────┤
│ 后端 (Spring Boot 多模块)                                  │
│  pt-repo     被测项目管理（CRUD / 流水线 / 变更分析入口）     │
│  pt-analyze  ★ 接口识别引擎（JavaParser 扫描 / 代码拉取）    │
│  pt-casegen  ★ 用例生成引擎（规则引擎 / 空洞检测 / 执行）     │
│  pt-mapping  ★ 用例-代码关联（映射建立 / 变更用例筛选）       │
│  pt-auth     认证鉴权（JWT / 管理员初始化）                  │
│  pt-common   统一响应 / 异常 / 常量                         │
│  pt-task / pt-web                                          │
├────────────────────────────────────────────────────────────┤
│ 存储   MySQL（7 张表）/ Redis                               │
└────────────────────────────────────────────────────────────┘
```

### 核心链路

```
注册项目 → 触发流水线（git clone + 扫描接口）
   → 空洞检测 → 一键生成用例 → 用例按接口分组
   → 编辑（入参/断言/请求头）→ 手动执行（真实 HTTP + 断言）
   → 建立用例-代码关联
   → 版本变更分析 → 筛选命中用例 → 精准回归
```

---

## 🚀 快速开始

### 方式一：Docker Compose 一键启动

```bash
docker compose up -d --build
# 前端 http://localhost  |  后端 http://localhost:8080  |  Swagger http://localhost:8080/swagger-ui.html
# 默认账号 admin / 123456
```

### 方式二：本地开发

```bash
# 1. 基础设施（MySQL 8 + Redis 7）
docker compose up -d mysql redis

# 2. 初始化数据库（自动执行 sql/init.sql 建 7 张表）
mysql -uroot -p123456 < sql/init.sql

# 3. 启动后端（Java 17）
cd backend && mvn spring-boot:run -pl pt-web

# 4. 启动前端（Vue3 dev，/api 代理到 8080）
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### 体验流程

1. 登录 `admin / 123456`；
2. **新增项目**：填写被测项目的 Git 地址（如 `https://github.com/yhcreak-hub/jacoco-cov.git`）与被测服务地址；
3. **触发流水线**：自动 clone 代码并识别接口（如 jacoco-cov 可识别 11 个接口）；
4. **用例管理**：查看空洞率 → 一键生成 44 条用例 → 展开按接口分组的用例 → 编辑入参/断言 → 手动执行（需被测服务可访问，可用 `scripts/mock-server.py` 模拟）；
5. **建立用例-代码关联**：生成 44 条"用例 ↔ Controller 方法"映射；
6. **变更分析**：输入两个版本（如 `master` → `dev_xxx`），自动筛选命中用例（精准回归范围）。

---

## 📊 数据库设计（7 张表）

| 表 | 用途 |
| --- | --- |
| `project` | 被测项目（Git 地址/分支/被测服务地址） |
| `api_definition` | 接口定义（自动识别产物：路径/方法/参数 Schema/代码定位） |
| `test_case` | 用例资产（请求/断言/请求头/场景/来源/置信度） |
| `code_unit` | 代码单元（Controller 类.方法 + SHA-256 hash） |
| `case_code_mapping` | ★ 用例↔代码关联（核心：direct/static） |
| `gen_task` | 生成任务（预留异步） |
| `sys_user` | 用户（admin 启动自动创建） |

---

## 📁 目录结构

```
precise-test-platform/
├── docker-compose.yml          # 一键编排：mysql + redis + backend + frontend
├── sql/
│   └── init.sql                # 建库建表脚本（7 张表，可重复执行）
├── scripts/
│   └── mock-server.py          # Mock 被测服务（模拟 jacoco-cov /cov/* 11 个接口）
├── backend/                    # 后端（Maven 多模块，Java 17 + Spring Boot 3.2）
│   ├── pom.xml                 # 父 pom：统一依赖版本管理
│   └── pt-{common,auth,repo,analyze,casegen,mapping,task,web}/
└── frontend/                   # 前端（Vue3 + Vite + TS + Element Plus）
    └── src/
        ├── api/                # axios 封装 + 各模块接口
        ├── views/              # Login / ProjectList / ApiList / CaseList / ChangeAnalysis
        └── router/ stores/ components/
```

---

## 🧪 验证结果（以 jacoco-cov 为被测项目）

| 能力 | 结果 |
| --- | --- |
| 接口自动识别 | ✅ 11 个接口（含跨文件 DTO、继承字段、JSR-303 校验提取） |
| 用例自动生成 | ✅ 44 条契约用例，空洞率 100% → 0% |
| 用例执行 | ✅ 11 个接口 normal 用例全部 PASS（mock 服务） |
| 用例-代码关联 | ✅ 44 条映射，用例可反查到 Controller 方法与行号 |
| 版本变更分析 | ✅ master→dev 识别出 ExactTestController 变更，命中 6 条用例 |

---

## 📜 演进路线

- [x] **M1** 前后端骨架：登录认证 / 项目管理 / Docker Compose
- [x] **M2** 接口自动识别：JavaParser 扫描引擎 / 接口清单
- [x] **M3** 用例生成 + 空洞检测：规则引擎四类用例
- [x] **M4** 用例编辑/执行 + 用例-代码关联
- [x] **M5** 版本变更分析 + 精准回归用例筛选
- [ ] **M6** AI 用例增强（LLM 生成业务用例 + 影子验证）
- [ ] **M7** 异步任务化 + CI 流水线集成
- [ ] **M8** 动态覆盖率回写（JaCoCo 精确映射）
