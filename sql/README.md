# precise_test 数据库说明

精准测试平台一期数据库（MySQL 8.0+，库名 `precise_test`）。

## 1. 执行方式

在 `sql` 目录下执行：

```bash
mysql -uroot -p123456 < init.sql
```

或指定绝对路径：

```bash
mysql -uroot -p123456 < /path/to/precise-test-platform/sql/init.sql
```

Docker 环境无需手动执行：`docker-compose.yml` 已将本脚本挂载到 MySQL 容器的
`/docker-entrypoint-initdb.d`，首次初始化数据卷时自动执行。

说明：

- 脚本使用 `CREATE TABLE IF NOT EXISTS`，可**重复执行**且**不会清空已有数据**。
- 脚本**不插入任何种子数据**：`sys_user` 的 admin 账号由后端启动时自动创建
  （BCrypt hash 在应用层生成，避免固定 hash 硬编码）。

## 2. 表清单

| 表名 | 说明 |
| --- | --- |
| `sys_user` | 用户（admin 由后端启动时自动创建） |
| `project` | 被测项目（Git 地址、分支、构建方式） |
| `api_definition` | 接口定义（自动识别 Controller 产物） |
| `test_case` | 用例资产（规则/AI/人工生成，绑定接口定义） |
| `code_unit` | 代码单元（被用例覆盖的类/方法） |
| `case_code_mapping` | 用例↔代码关联（核心表，建立多对多关系） |
| `gen_task` | 生成任务（analyze/casegen/mapping 三类异步任务） |

## 3. 表关系

```
project 1 ── N api_definition 1 ── N test_case
                                        │
                                        │ N
                                        ▼
                                 case_code_mapping
                                        │ N
                                        ▼
                                      code_unit
```

- `project` 1:N `api_definition`：一个项目可扫描出多个接口定义。
- `api_definition` 1:N `test_case`：一个接口可生成/维护多个用例。
- `test_case` N:N `code_unit`：通过 `case_code_mapping` 中间表关联，一个用例可覆盖多个
  代码单元（直接调用 + 调用链），一个代码单元可被多个用例覆盖。
- `project` 1:N `gen_task`：一个项目可发起多个扫描/生成/映射任务。
- `sys_user` 为独立表，与其他表无直接关联。

## 4. 设计约定

- 所有表使用 `InnoDB` + `utf8mb4` + `utf8mb4_unicode_ci`，统一支持中文注释与存储。
- 表间关联通过**业务字段（逻辑外键）**维护，不建物理外键约束，由应用层保证一致性，
  便于一期数据清理与任务重跑。
- 实体字段与后端 Java 实体一一对应（下划线转驼峰，如 `git_url` ↔ `gitUrl`），
  见 `backend` 各模块的 `entity` 包。
