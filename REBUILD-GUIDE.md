# 精准测试平台 — 快速重建手册（REBUILD GUIDE）

> 本文档记录 2026-08-16 一次会话从需求到落地的完整过程，作为后续**从零快速重建**精准测试平台的操作手册。
> 两条重建路线：**A. 直接复用已有代码**（最快）/ **B. 多 Agent 从零生成**（了解全貌）。
> 关联仓库：https://github.com/yhcreak-hub/precise-test-platform

---

## 一、需求演进记录（今日完整过程）

| 阶段 | 需求 | 产出 | 状态 |
|------|------|------|------|
| 1 | 测试人员个人技术展示页怎么搭 | 建议方案（内容模块/技术选型/避坑） | 咨询 |
| 2 | 接口自动化 + 覆盖率 + 性能测试如何用 AI 加持 | 能力提升路线（作品化/展示层/交互层） | 咨询 |
| 3 | 克隆 super-jacoco 到本地 | 本地仓库 `/test1/super-jacoco` | ✅ |
| 4 | 分析 super-jacoco 有多少接口 | 5 个接口（CodeCovController） | ✅ |
| 5 | 丰富 super-jacoco README 并推远程 | README 重写 + 推送 GitHub | ✅ |
| 6 | 基于 super-jacoco 写接口自动化测试方案 | `super-jacoco-api-test-plan.md`（46 用例设计） | ✅ |
| 7 | 设计精准测试平台（AI 生成用例 + 代码用例关联 + 变更自动回归） | `precise-test-platform-design.md`（整体蓝图） | ✅ |
| 8 | 一期细化：前后端分离 + 接口识别 + 用例生成 + 关联关系 | `precise-test-platform-phase1.md`（一期方案） | ✅ |
| 9 | 检测本地环境是否满足技术栈 | JDK17 需切换；MySQL/Redis/Docker 未启动 | ✅ |
| 10 | 补齐环境 | JDK17 配置 + setjdk 函数；启动 MySQL/Redis/Docker | ✅ |
| 11 | 多 Agent 并行搭建一期 | 3 个 agent：脚手架 + SQL + 接口识别引擎 | ✅ |
| 12 | 整合多 Agent 产出 | 修复 Agent A 偏离统一模型的问题（12 处） | ✅ |
| 13 | 启动后端验证 | 登录/项目管理/鉴权全部通过 | ✅ |
| 14 | 启动前端联调 | 5173 代理 → 8080 全链路通 | ✅ |
| 15 | 补 M2 接线：注册项目→自动扫描接口 | pipeline 真实执行（11 接口识别） | ✅ |
| 16 | 测试空洞检测 + AI 用例生成 | 空洞率 100%→0%；44 条用例生成 | ✅ |
| 17 | 用例按接口分类、可编辑、可执行 | 分组展示 + 编辑（入参/断言/请求头）+ 执行引擎 | ✅ |
| 18 | 修复 jacoco-cov 执行用例失败（URL 错误） | mock 补全 11 接口 + GET query + token 头 | ✅ |
| 19 | 用例↔代码关联关系 | 44 条映射（direct/static） | ✅ |
| 20 | 版本变更分析 → 筛选对应用例 | git diff + 反查映射（命中 6 条） | ✅ |
| 21 | 推 GitHub 作为作品集仓库 | `yhcreak-hub/precise-test-platform` | ✅ |

---

## 二、最终架构与技术栈

### 2.1 架构图

```
┌────────────────────────────────────────────────────────────┐
│ 前端 (Vue3 + Vite + TS + Element Plus)                     │
│   Login / ProjectList / ApiList / CaseList / ChangeAnalysis │
├────────────────────────────────────────────────────────────┤
│ 后端 (Spring Boot 3.2 多模块，Java 17)                      │
│  pt-repo     被测项目管理（CRUD / 流水线 / 变更分析入口）     │
│  pt-analyze  ★ 接口识别引擎（JavaParser 扫描 / 代码拉取）    │
│  pt-casegen  ★ 用例生成引擎（规则引擎 / 空洞检测 / 执行）     │
│  pt-mapping  ★ 用例-代码关联（映射 / 变更用例筛选）           │
│  pt-auth     认证鉴权（JWT / 管理员初始化）                  │
│  pt-common   统一响应 / 异常 / 常量                         │
│  pt-task / pt-web                                          │
├────────────────────────────────────────────────────────────┤
│ 存储   MySQL（7 张表）/ Redis                               │
└────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈清单

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17（openjdk@17 brew keg-only） | 后端 |
| Spring Boot | 3.2.5 | 父 POM 管理 |
| MyBatis-Plus | 3.5.5 | spring-boot3-starter |
| JWT | jjwt 0.12.5 | 新链式 API |
| JavaParser | 3.25.10 | 接口识别核心 |
| Vue | 3.4 | Vite 5 + TS 5.4 |
| Element Plus | 2.7 | 全量引入（zh-cn） |
| MySQL | 8.0（root/123456） | 库名 precise_test |
| Redis | 6.x | 端口 6379 |

### 2.3 端口约定

| 服务 | 端口 |
|------|------|
| 后端 Spring Boot | 8080 |
| 前端 Vite dev | 5173（/api 代理 → 8080） |
| Mock 被测服务 | 8899 |

---

## 三、数据模型（7 张表，sql/init.sql）

| 表 | 关键字段 | 用途 |
|----|---------|------|
| `project` | name, git_url, branch, build_type, **base_url**(执行用例用), status | 被测项目 |
| `api_definition` | project_id, api_path, http_method, param_schema_json, response_schema_json, controller_class, controller_method, file_path, line_no, status | 接口识别产物 |
| `test_case` | project_id, api_definition_id, title, request_json, asserts_json, **headers_json**, scenario_type, source, confidence, status | 用例资产 |
| `code_unit` | project_id, class_name, method_name, signature, file_path, line_no, code_hash(SHA-256) | 代码单元 |
| `case_code_mapping` | test_case_id, code_unit_id, relation_type(direct/called), confidence(static/dynamic) | ★ 用例↔代码关联 |
| `gen_task` | project_id, type, status, progress | 生成任务（预留异步） |
| `sys_user` | username, password_hash(BCrypt), role, status | 用户（admin 启动自动创建） |

**统一模型约定（重要）**：所有枚举字段（status/confidence/role）一律 **VARCHAR 字符串**（如 'active'/'pending'/'high'/'static'），**禁止 TINYINT 数字枚举**——多 Agent 协作时这是最易偏离、最易出问题的点。

---

## 四、功能清单与里程碑（M1-M5 已完成）

| 里程碑 | 功能 | 关键类/文件 |
|--------|------|------------|
| **M1** | 前后端骨架：登录认证 / 项目 CRUD / Docker Compose | pt-auth, pt-repo |
| **M2** | 接口自动识别：JavaParser 扫描 + 流水线 | `ApiScanner` / `ApiDefinitionBuilder` / `ParamSchemaResolver` / `CodeFetcher` |
| **M3** | 用例生成 + 空洞检测 | `RuleCaseGenerator` / `TestCaseServiceImpl` |
| **M4** | 用例编辑/执行 + 用例-代码关联 | `CaseExecutor` / `CodeUnitServiceImpl` / `CaseCodeMappingServiceImpl` |
| **M5** | 版本变更分析 + 精准回归筛选 | `GitDiffAnalyzer` / `ChangeAnalysisService` |

### 4.1 核心流程

```
注册项目 → 触发流水线（git clone + JavaParser 扫描接口）
   → 空洞检测 → 一键生成用例（规则引擎四类）
   → 用例按接口分组 → 编辑（入参/断言/请求头）→ 手动执行（真实 HTTP + 断言）
   → 建立用例-代码关联（44 条映射）
   → 版本变更分析（git diff）→ 筛选命中用例 → 精准回归
```

### 4.2 接口清单（后端暴露）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 登录（admin/123456） |
| `/api/projects` | GET/POST | 项目分页/新增 |
| `/api/projects/{id}` | DELETE | 删除项目 |
| `/api/projects/{id}/pipeline` | POST | 触发流水线（clone+扫描接口） |
| `/api/projects/{id}/apis` | GET | 接口清单 |
| `/api/projects/{id}/gap` | GET | 空洞检测 |
| `/api/projects/{id}/generate-cases` | POST | 生成用例 |
| `/api/projects/{id}/cases` | GET | 用例分页 |
| `/api/projects/{id}/cases/grouped` | GET | 按接口分组用例 |
| `/api/projects/cases/{id}` | PUT | 编辑用例 |
| `/api/projects/cases/{id}/execute` | POST | 执行用例 |
| `/api/projects/{id}/build-mapping` | POST | 建立用例-代码关联 |
| `/api/projects/{id}/code-units` | GET | 代码单元清单 |
| `/api/projects/cases/{id}/mapping` | GET | 用例关联的代码 |
| `/api/projects/{id}/analyze-change` | POST | 版本变更分析 |

---

## 五、快速重建指南

### 路线 A：复用已有代码（最快，推荐）

```bash
# 1. 克隆项目
git clone https://github.com/yhcreak-hub/precise-test-platform.git
cd precise-test-platform

# 2. 准备环境（见第六节：环境准备清单）
#    - JDK17: export JAVA_HOME=/usr/local/opt/openjdk@17
#    - MySQL/Redis 已启动（root/123456）

# 3. 初始化数据库
mysql -uroot -p123456 < sql/init.sql

# 4. 启动后端（注意 Maven 仓库配置，见 6.3）
cd backend
mvn -s clean-settings.xml -gs clean-settings.xml install -DskipTests   # 首次安装到本地仓库
mvn -s clean-settings.xml -gs clean-settings.xml spring-boot:run -pl pt-web

# 5. 启动前端
cd ../frontend
npm install --cache ./.npm-cache        # 注意 npm 缓存权限问题（见 6.4）
npm run dev

# 6. （可选）启动 mock 被测服务
python3 scripts/mock-server.py 8899

# 7. 验证
#    浏览器 http://localhost:5173 → admin/123456
#    新增项目（Git 地址填 jacoco-cov）→ 触发流水线 → 用例管理 → 变更分析
```

### 路线 B：多 Agent 从零生成（了解全貌）

关键原则：**三个 Agent 并行前，必须下发完全一致的数据模型契约**（表结构/字段/枚举约定），否则整合阶段必然出现字段类型不一致（本次 Agent A 就把 status/confidence 改成了数字类型，导致 12 处修复）。

分工建议：

| Agent | 任务 | 边界 |
|-------|------|------|
| A | 前后端脚手架（Maven 多模块 + Vue3） | 严格按数据模型契约建实体 |
| B | 建表 SQL + 初始化 | 与契约完全一致（VARCHAR 枚举） |
| C | 接口识别引擎（JavaParser） | 只写 pt-analyze 模块，不碰其他模块 |

整合清单（必做）：
1. 校验实体字段 ↔ SQL ↔ 前端类型一致性
2. `mvn install` 后 `spring-boot:run` 启动
3. 登录 + 项目 CRUD + 流水线冒烟

---

## 六、环境准备清单与坑位记录

### 6.1 环境检测结论（本机）

| 组件 | 本机状态 | 处理 |
|------|---------|------|
| JDK | 默认 1.8，**openjdk@17 已装（brew）** | 切 JAVA_HOME |
| MySQL | 8.0.23 已装，服务未启动 | `brew services start mysql` |
| Redis | 6.0.10 已装，服务未启动 | `brew services start redis` |
| Docker | Desktop 已装，daemon 未启动 | 手动打开 Docker Desktop |
| Node | v24（nvm 22 也可） | 直接可用 |
| Maven | 3.6.3 | 需 clean-settings.xml（见 6.3） |

### 6.2 JDK 切换（.bash_profile 已配置）

```bash
# 已写入 ~/.bash_profile：
export JAVA_8_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_212.jdk/Contents/Home
export JAVA_17_HOME=/usr/local/opt/openjdk@17
export JAVA_HOME=$JAVA_17_HOME          # 默认 17
setjdk() { case "$1" in 8) export JAVA_HOME=$JAVA_8_HOME;; 17) export JAVA_HOME=$JAVA_17_HOME;; esac; export PATH=$JAVA_HOME/bin:$PATH; java -version; }
# 用法：setjdk 8 | setjdk 17
```

### 6.3 ⚠️ Maven 仓库配置（最大坑）

本机 Maven 全局配置指向**内网 Nexus**（guahao-inc.com，不可达），必须用独立 settings 覆盖：

```bash
# backend/clean-settings.xml（已存在，内容是阿里云镜像）
mvn -s clean-settings.xml -gs clean-settings.xml <goal>
# -s 覆盖用户 settings；-gs 覆盖全局 settings（两者都要，否则仍走内网）
```

### 6.4 ⚠️ npm 缓存权限问题

`~/.npm` 有 root 文件导致 install 失败 → 用独立缓存目录：
```bash
npm install --cache ./.npm-cache
```
（永久修复：`sudo chown -R 501:20 ~/.npm`）

### 6.5 数据库初始化

```bash
mysql -uroot -p123456 < sql/init.sql   # root 密码 123456
# 已建库 precise_test，7 张表；脚本可重复执行（DROP + CREATE）
```

---

## 七、验证清单（冒烟测试）

| 步骤 | 预期 | 命令 |
|------|------|------|
| 后端启动 | 8080 有响应 | `curl localhost:8080/api/auth/login -X POST -d '{"username":"admin","password":"123456"}' -H "Content-Type: application/json"` |
| 登录 | code=200 + token | 同上 |
| 无 token 访问 | 401 | `curl localhost:8080/api/projects` |
| 新增项目 | 返回项目实体 | POST /api/projects |
| 触发流水线 | importedCount>0 | POST /api/projects/{id}/pipeline |
| 接口清单 | 列表非空 | GET /api/projects/{id}/apis |
| 空洞检测 | totalApis=gapApis | GET /api/projects/{id}/gap |
| 生成用例 | 数量>0 | POST /api/projects/{id}/generate-cases |
| 执行用例 | PASS | POST /api/projects/cases/{id}/execute |
| 变更分析 | 命中用例>0 | POST /api/projects/{id}/analyze-change |
| 前端 | 5173 页面 + 代理登录 | 浏览器 / curl localhost:5173 |

---

## 八、关键实现要点备忘

### 8.1 接口识别（M2）
- JavaParser 遍历所有 .java → 找 `@RestController`/`@Controller` → 解析类级+方法级 `@*Mapping`
- 参数：`@RequestBody` 走类型索引解析 DTO 字段（跨文件）；支持继承字段合并（`inheritedFrom`）；JSR-303 校验注解提取
- 容错：单文件失败 try-catch 不中断；`@Deprecated` 方法默认过滤

### 8.2 用例生成（M3）
- 规则引擎生成四类：normal（全字段）/ required（逐个缺失必填）/ boundary（极值/空体）/ exception（非法类型）
- **QUERY 参数也要并入请求体**（GET 接口执行时转 query string）
- 空洞检测 = 有接口但无用例；`generateForProject` 只对空洞接口生成（幂等）

### 8.3 用例执行（M4）
- `CaseExecutor`：GET → query string 拼接；POST → JSON body；headers_json 支持自定义请求头
- 断言格式：`{"body.code": 200}` / `{"body.code": "!=200"}` / `{"body.code": "200 or !=200"}`
- **body. 前缀兼容**：响应顶层无 body 字段时自动降级从根解析（HttpResult 平铺结构）

### 8.4 用例-代码关联（M4）
- 链路：test_case → api_definition（controller_class+method）→ code_unit → case_code_mapping
- code_hash = SHA-256(类名.方法名)；relationType=direct, confidence=static

### 8.5 变更分析（M5）
- `git clone`（全量）→ `git diff --name-status base now` → 解析变更 Controller 类 → 匹配 code_unit → 反查映射
- ⚠️ **远程分支解析**：本地无 `dev_xxx` 分支时自动补 `origin/` 前缀（`resolveRef`）

### 8.6 Mock 被测服务（验证用）
- `scripts/mock-server.py`：模拟 jacoco-cov 全部 11 个接口（含参数校验、token 宽松校验）
- GET 接口需剥离 query 匹配路由，再单独 parse_query

---

## 九、下一步（M6-M8 规划）

- [ ] **M6** AI 用例增强：LLM 生成业务用例（Schema 约束 + 影子验证 + 人工审核），对接精准测试平台方案中的防幻觉三关
- [ ] **M7** 异步任务化（pt-task 落地）+ CI 流水线（GitHub Actions 自动跑用例）
- [ ] **M8** JaCoCo 动态覆盖率回写 → 映射从 static 升级 dynamic

---

*手册版本 v1.0 · 记录日期 2026-08-16 · 关联仓库 yhcreak-hub/precise-test-platform*
