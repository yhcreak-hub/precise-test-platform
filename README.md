# 精准测试平台（Precise Test Platform）

> 自动识别被测项目接口 → 自动生成接口自动化用例 → 建立用例与代码的关联关系 → 版本变更分析筛选精准回归用例 → 执行结果可视化报告。

一个**前后端分离**的精准测试平台，覆盖"代码 → 接口 → 用例 → 回归 → 报告"的完整质量链路。测试人员导入被测项目后，即可完成接口识别、用例生成、用例执行、变更精准回归与执行记录审计。

---

## ✨ 核心能力

| 能力 | 说明 | 里程碑 |
|------|------|--------|
| **异步项目导入** | 导入仅校验 Git 仓库可达；接口分析异步后台执行（任务进度条），大项目不超时 | M7 |
| **接口自动识别** | JavaParser 静态扫描被测项目源码，自动提取接口清单（路径/HTTP 方法/参数结构/JSR-303 校验/代码定位/继承字段） | M2 |
| **用例自动生成** | 规则引擎基于接口 Schema 生成四类契约用例：正常流程 / 必填校验 / 边界值 / 异常输入 | M3 |
| **测试空洞检测** | 统计"有接口但无用例"的空洞率，一键为空洞接口补齐用例（幂等） | M3 |
| **用例编辑与执行** | 按接口分组展示；手动修改入参/断言/请求头；**手动新增用例（来源=手动，重新生成不被覆盖）**；真实 HTTP 请求执行 + 断言校验 | M4/M6 |
| **用例↔代码关联** | 建立"用例 → 接口 → Controller 方法"的静态映射（direct/static），可反向定位被测代码 | M4 |
| **版本变更分析** | git diff 对比两个版本 → **调用链影响面分析**（Service/DAO/Util 变更可追溯受影响 Controller）→ 反查映射 → 筛选需回归用例 | M5 |
| **变更结果可视化** | 变更类树形展示（按包分组）；受影响接口列表（无覆盖可一键补用例）；命中用例按接口分类 | M5 |
| **执行记录与报告** | 全量/批量执行自动生成执行记录；详情报告含**请求入参**/断言明细/响应体/HTTP 状态 | M6 |

---

## 🏗️ 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3.2 · Maven 多模块（8 个模块）· MyBatis-Plus · MySQL 8 · Redis · JWT · SpringDoc OpenAPI · JavaParser · RestTemplate · @Async |
| 前端 | Vue 3 · Vite · TypeScript · Element Plus · Pinia · Vue Router · Axios · ECharts |
| 部署 | Docker Compose（MySQL 8 / Redis 7 / 后端 8080 / 前端 Nginx 80） |

---

## 🧱 架构与模块

```
┌────────────────────────────────────────────────────────────┐
│ 前端 (Vue3)   项目管理 / 接口管理 / 用例管理 / 变更分析      │
│               执行记录（含详情报告）                         │
├────────────────────────────────────────────────────────────┤
│ 后端 (Spring Boot 多模块)                                  │
│  pt-repo     被测项目管理（CRUD / 分析 / 用例 / 执行记录）   │
│  pt-analyze  ★ 接口识别引擎（JavaParser / 调用链分析）      │
│  pt-casegen  ★ 用例生成/空洞检测/执行引擎/执行记录           │
│  pt-mapping  ★ 用例-代码关联 / 变更分析 / 受影响接口         │
│  pt-web      异步任务编排（@Async 导入/分析）                │
│  pt-task     任务生命周期（gen_task）                       │
│  pt-auth     认证鉴权（JWT / 管理员初始化）                  │
│  pt-common   统一响应 / 异常 / 常量                         │
├────────────────────────────────────────────────────────────┤
│ 存储   MySQL（9 张表）/ Redis                               │
└────────────────────────────────────────────────────────────┘
```

### 核心链路

```
导入项目（校验 Git 可达）→ 异步分析接口（进度条）→ 生成用例 → 空洞检测
   → 用例管理（分组/编辑/手动新增/全量执行）
   → 建立用例-代码关联
   → 版本变更分析（调用链追溯受影响接口）→ 精准回归（批量执行）
   → 执行记录 + 详情报告（入参/断言/响应）
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

# 2. 初始化数据库（9 张表）
mysql -uroot -p123456 < sql/init.sql

# 3. 启动后端（Java 17；注意 Maven 仓库配置见 REBUILD-GUIDE.md）
cd backend
mvn -s clean-settings.xml -gs clean-settings.xml install -DskipTests
mvn -s clean-settings.xml -gs clean-settings.xml spring-boot:run -pl pt-web

# 4. 启动前端（Vue3 dev，/api 代理到 8080）
cd frontend && npm install --cache ./.npm-cache && npm run dev   # http://localhost:5173

# 5.（可选）启动 mock 被测服务（模拟 jacoco-cov 接口，用于用例执行验证）
python3 scripts/mock-server.py 8899
```

### 体验流程

1. 登录 `admin / 123456`；
2. **导入项目**：填 Git 地址（如 `https://github.com/yhcreak-hub/jacoco-cov.git`）与被测服务地址（如 `http://localhost:8899`）——**导入仅校验仓库可达**；
3. 项目列表点「**分析接口**」→ 进度条 → 自动识别接口（jacoco-cov 可识别 11 个）；
4. 点「**生成用例**」→ 用例管理页查看 44 条用例，按接口分组；
5. 用例管理：**新增用例**（手动，不被覆盖）/ **编辑** / **全量执行**；
6. 「**建立用例-代码关联**」→ 44 条映射；
7. 「**变更分析**」→ 输入 `master` → `dev_xxx` → 树形变更类 + **调用链追溯受影响接口** + 命中用例（无覆盖可一键补用例）；
8. 批量执行命中用例 → 跳转「**执行记录**」→ 详情报告（入参/断言/响应）。

---

## 📊 数据库设计（9 张表）

| 表 | 用途 |
| --- | --- |
| `project` | 被测项目（Git 地址/分支/被测服务地址） |
| `api_definition` | 接口定义（自动识别产物：路径/方法/参数 Schema/代码定位） |
| `test_case` | 用例资产（请求/断言/请求头/场景/来源 rule\|manual/置信度） |
| `code_unit` | 代码单元（Controller 类.方法 + SHA-256 hash） |
| `case_code_mapping` | ★ 用例↔代码关联（direct/static） |
| `gen_task` | 异步任务（导入/分析的进度与状态） |
| `exec_record` | ★ 用例执行记录（批次：来源/版本/统计/耗时） |
| `exec_record_detail` | ★ 执行明细（**请求入参**/断言/响应/HTTP/耗时） |
| `sys_user` | 用户（admin 启动自动创建） |

---

## 📁 目录结构

```
precise-test-platform/
├── docker-compose.yml          # 一键编排：mysql + redis + backend + frontend
├── sql/init.sql                # 建库建表脚本（9 张表，可重复执行）
├── scripts/mock-server.py      # Mock 被测服务（模拟 jacoco-cov /cov/* 11 个接口）
├── REBUILD-GUIDE.md            # 快速重建手册（环境/坑位/验证清单）
├── backend/                    # 后端（Maven 多模块，Java 17 + Spring Boot 3.2）
│   └── pt-{common,auth,repo,analyze,casegen,mapping,task,web}/
└── frontend/                   # 前端（Vue3 + Vite + TS + Element Plus）
    └── src/views/
        ├── ProjectList.vue     # 项目管理（导入/分析接口/生成用例/入口）
        ├── ApiList.vue         # 接口管理（自动识别清单）
        ├── CaseList.vue        # 用例管理（分组/编辑/新增/全量执行）
        ├── ChangeAnalysis.vue  # 变更分析（树形/调用链/补用例）
        └── ExecRecords.vue     # 执行记录（列表/详情报告）
```

---

## 🧪 验证结果（以 jacoco-cov 为被测项目）

| 能力 | 结果 |
| --- | --- |
| 接口自动识别 | ✅ 11 个接口（含跨文件 DTO、继承字段、JSR-303 校验提取） |
| 用例自动生成 | ✅ 44 条契约用例，空洞率 100% → 0% |
| 用例执行 | ✅ normal 用例全部 PASS（mock 服务）；全量执行生成执行记录 |
| 用例-代码关联 | ✅ 44 条映射，用例可反查到 Controller 方法与行号 |
| 版本变更分析 | ✅ master→dev 识别 ExactTestController 变更 + **调用链追溯 CodeCovService 变更影响**，命中 12 条用例 |
| 手动用例 | ✅ 手动新增（source=manual），重新生成不被覆盖 |
| 执行记录 | ✅ 变更分析/全量执行均生成记录，详情报告含入参/断言/响应 |

---

## 📜 演进路线

- [x] **M1** 前后端骨架：登录认证 / 项目管理 / Docker Compose
- [x] **M2** 接口自动识别：JavaParser 扫描引擎 / 接口清单
- [x] **M3** 用例生成 + 空洞检测：规则引擎四类用例
- [x] **M4** 用例编辑/执行 + 用例-代码关联
- [x] **M5** 版本变更分析：调用链影响面 + 树形展示 + 无覆盖补用例
- [x] **M6** 执行记录与报告 + 手动用例 + 全量执行
- [x] **M7** 异步任务化（导入/分析后台执行 + 进度）
- [ ] **M8** AI 用例增强（LLM 生成业务用例 + 影子验证）
- [ ] **M9** CI 流水线集成（GitHub Actions 自动跑用例）
- [ ] **M10** 动态覆盖率回写（JaCoCo 精确映射 static → dynamic）
