-- ============================================================================
-- 精准测试平台 —— 数据库初始化脚本
-- ----------------------------------------------------------------------------
-- 数据库    : precise_test（UTF-8 / utf8mb4）
-- 适用版本  : MySQL 8.0+
-- 执行特性  : 可重复执行（DROP TABLE IF EXISTS + CREATE TABLE）
--
-- 【表清单】（共 7 张）
--   1. project            被测项目
--   2. api_definition     接口定义（自动识别产物）
--   3. test_case          用例资产
--   4. code_unit          代码单元
--   5. case_code_mapping  用例↔代码关联（核心表）
--   6. gen_task           生成任务
--   7. sys_user           用户
--
-- 【与后端实体映射关系】（表名 → 实体类）
--   project            → Project
--   api_definition     → ApiDefinition
--   test_case          → TestCase
--   code_unit          → CodeUnit
--   case_code_mapping  → CaseCodeMapping
--   gen_task           → GenTask
--   sys_user           → SysUser
--
-- 【表关系】
--   project 1:N api_definition 1:N test_case
--   test_case N:N code_unit（通过 case_code_mapping 关联）
--
-- 【约定说明】
--   1. 本脚本不插入任何种子数据：sys_user 的 admin 账号由后端启动时自动创建，
--      BCrypt hash 不在 SQL 中硬编码，避免固定 hash 带来的安全隐患。
--   2. 表间关联通过业务字段（逻辑外键）维护，不建物理外键约束，
--      由应用层保证一致性，便于一期功能迭代中的数据清理与任务重跑。
--   3. 枚举字段（status/confidence/role 等）统一使用 VARCHAR 字符串枚举，
--      与后端实体 String 类型一一对应，不使用 TINYINT 数字枚举。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS precise_test DEFAULT CHARACTER SET utf8mb4;
USE precise_test;

-- 先按依赖关系（子表在前）清空旧表，保证脚本可重复执行
DROP TABLE IF EXISTS case_code_mapping;
DROP TABLE IF EXISTS test_case;
DROP TABLE IF EXISTS api_definition;
DROP TABLE IF EXISTS code_unit;
DROP TABLE IF EXISTS gen_task;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS sys_user;

-- ----------------------------------------------------------------------------
-- 1. project：被测项目
-- ----------------------------------------------------------------------------
CREATE TABLE project (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL COMMENT '项目名称',
    git_url    VARCHAR(500) NOT NULL COMMENT 'Git 仓库地址',
    branch     VARCHAR(100) NOT NULL DEFAULT 'master' COMMENT '分支',
    build_type VARCHAR(20)  NOT NULL DEFAULT 'maven' COMMENT '构建方式 maven/gradle',
    status     VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '状态 active/disabled',
    base_url   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '被测服务地址，用于执行用例',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='被测项目';

-- ----------------------------------------------------------------------------
-- 2. api_definition：接口定义（自动识别产物）
--    自动扫描 Controller 后生成，等待人工确认
-- ----------------------------------------------------------------------------
CREATE TABLE api_definition (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    api_path            VARCHAR(255)    NOT NULL COMMENT '接口路径',
    http_method         VARCHAR(10)     NOT NULL COMMENT 'GET/POST/PUT/DELETE',
    param_schema_json   TEXT COMMENT '参数结构 JSON',
    response_schema_json TEXT COMMENT '返回结构 JSON',
    controller_class    VARCHAR(255)    NOT NULL COMMENT 'Controller 类全名',
    controller_method   VARCHAR(255)    NOT NULL COMMENT 'Controller 方法名',
    file_path           VARCHAR(500)    NOT NULL COMMENT '源码文件路径',
    line_no             INT             NOT NULL DEFAULT 0 COMMENT '代码行号',
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending待确认/confirmed已确认/ignored已忽略',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_project (project_id),
    UNIQUE KEY uk_path (project_id, api_path, http_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='接口定义（自动识别产物）';

-- ----------------------------------------------------------------------------
-- 3. test_case：用例资产
--    由规则/AI/人工生成，归属于某个接口定义
-- ----------------------------------------------------------------------------
CREATE TABLE test_case (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    api_definition_id BIGINT UNSIGNED NOT NULL COMMENT '所属接口定义',
    title             VARCHAR(255)    NOT NULL COMMENT '用例标题',
    request_json      TEXT COMMENT '请求参数 JSON',
    asserts_json      TEXT COMMENT '断言 JSON',
    headers_json      TEXT COMMENT '请求头 JSON（可选，如 {"token":"xxx"}）',
    scenario_type     VARCHAR(20)     NOT NULL DEFAULT 'normal' COMMENT 'normal/required/boundary/exception/business',
    source            VARCHAR(10)     NOT NULL DEFAULT 'rule' COMMENT 'rule/ai/manual',
    confidence        VARCHAR(10)     NOT NULL DEFAULT 'high' COMMENT 'high/medium/low',
    status            VARCHAR(20)     NOT NULL DEFAULT 'draft' COMMENT 'draft/active/deprecated',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_api (api_definition_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例资产';

-- ----------------------------------------------------------------------------
-- 4. code_unit：代码单元
--    被接口调用链覆盖到的类/方法，静态扫描或动态覆盖产物
-- ----------------------------------------------------------------------------
CREATE TABLE code_unit (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    class_name  VARCHAR(255)    NOT NULL COMMENT '类全名',
    method_name VARCHAR(255)    NOT NULL COMMENT '方法名',
    signature   VARCHAR(500) COMMENT '方法签名',
    file_path   VARCHAR(500)    NOT NULL COMMENT '源码文件路径',
    line_no     INT             NOT NULL DEFAULT 0 COMMENT '代码行号',
    code_hash   VARCHAR(64)     NOT NULL COMMENT '字节码/源码 hash',
    KEY idx_project (project_id),
    UNIQUE KEY uk_unit (project_id, class_name, method_name, code_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码单元';

-- ----------------------------------------------------------------------------
-- 5. case_code_mapping：用例↔代码关联（核心表）
--    建立用例与代码单元的多对多关系，支持直接调用与调用链两种粒度
-- ----------------------------------------------------------------------------
CREATE TABLE case_code_mapping (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    test_case_id  BIGINT UNSIGNED NOT NULL COMMENT '用例 ID',
    code_unit_id  BIGINT UNSIGNED NOT NULL COMMENT '代码单元 ID',
    relation_type VARCHAR(10)     NOT NULL DEFAULT 'direct' COMMENT 'direct直接/called调用链',
    confidence    VARCHAR(10)     NOT NULL DEFAULT 'static' COMMENT 'static静态分析/dynamic动态覆盖',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_case (test_case_id),
    KEY idx_unit (code_unit_id),
    UNIQUE KEY uk_map (test_case_id, code_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例与代码关联（核心表）';

-- ----------------------------------------------------------------------------
-- 6. gen_task：生成任务
--    scan（接口扫描）/ generate（用例生成）/ mapping（关联映射）三类异步任务
-- ----------------------------------------------------------------------------
CREATE TABLE gen_task (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    type        VARCHAR(20)     NOT NULL COMMENT 'scan/generate/mapping',
    status      VARCHAR(20)     NOT NULL DEFAULT 'queued' COMMENT 'queued/running/success/failed',
    progress    INT             NOT NULL DEFAULT 0 COMMENT '0-100',
    log_url     VARCHAR(500) COMMENT '日志地址',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at DATETIME NULL COMMENT '完成时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生成任务';

-- ----------------------------------------------------------------------------
-- 7. sys_user：用户
--    admin 初始账号由后端启动时自动创建（BCrypt hash 在应用层生成）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    role          VARCHAR(20)  NOT NULL DEFAULT 'admin' COMMENT '角色',
    status        VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '状态',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';
