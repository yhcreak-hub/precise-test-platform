#!/usr/bin/env python3
"""
精准测试平台 - Mock 被测服务
模拟 jacoco-cov 的全部 11 个接口，返回标准 HttpResult 结构：
  {"code": 200, "data": ..., "msg": "success"}

覆盖接口（与 jacoco-cov CodeCovController / ExactTestController 一致）：
  1. POST /cov/triggerUnitCover          （含参数校验：uuid/gitUrl/nowVersion 必填，type 1~2）
  2. GET  /cov/getUnitCoverResult
  3. POST /cov/triggerEnvCov
  4. GET  /cov/getEnvCoverResult
  5. POST /cov/getLocalCoverResult
  6. POST /cov/getCodeCommitsByTime
  7. POST /cov/getCodeCommitsByBranch
  8. POST /cov/collectGitCommitsInfo
  9. GET  /cov/getCodeCommitsByRef
  10. POST /exact/getApiMapList          （需 token 请求头）
  11. POST /exact/getApiMapList2         （需 token 请求头）

用法：python3 mock-server.py [port]   （默认 8899）
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

TOKEN = "precise-test-token"


def parse_query(path):
    """从请求路径中解析 query 参数（如 /cov/getUnitCoverResult?uuid=abc）"""
    from urllib.parse import parse_qs, urlparse
    return {k: v[0] for k, v in parse_qs(urlparse(path).query).items()}


def cov_response(code, data, msg):
    return json.dumps({"code": code, "data": data, "msg": msg}, ensure_ascii=False).encode("utf-8")


def handle_requests(method, raw_path, body, headers):
    """模拟 jacoco-cov 的全部接口；raw_path 含 query string"""
    # 剥离 query 用于路由匹配，保留完整 path 供 parse_query 使用
    path = raw_path.split("?")[0]
    query_path = raw_path
    # ---- 1. 单测覆盖率触发（含 JSR-303 校验逻辑） ----
    if path == "/cov/triggerUnitCover" and method == "POST":
        try:
            req = json.loads(body) if body else {}
        except Exception:
            return cov_response(-1, False, "fail: 请求体不是合法 JSON")
        for field in ("uuid", "gitUrl", "nowVersion"):
            if not req.get(field):
                return cov_response(-1, False, f"fail: {field}不能为空")
        if req.get("type") not in (1, 2):
            return cov_response(-1, False, "fail: type必须在1~2之间")
        return cov_response(200, True, "success")

    # ---- 2. 单测覆盖率结果（uuid 必填） ----
    if path == "/cov/getUnitCoverResult" and method == "GET":
        query = parse_query(query_path)
        if not query.get("uuid"):
            return cov_response(-1, None, "fail: uuid不能为空")
        return cov_response(200, {
            "coverStatus": 1, "lineCoverage": 82.5, "branchCoverage": 76.3,
            "reportUrl": "http://localhost:8899/report/unit/index.html"
        }, "success")

    # ---- 3. 环境覆盖率触发 ----
    if path == "/cov/triggerEnvCov" and method == "POST":
        try:
            req = json.loads(body) if body else {}
        except Exception:
            return cov_response(-1, False, "fail: 请求体不是合法 JSON")
        for field in ("uuid", "gitUrl", "nowVersion"):
            if not req.get(field):
                return cov_response(-1, False, f"fail: {field}不能为空")
        if req.get("type") not in (1, 2):
            return cov_response(-1, False, "fail: type必须在1~2之间")
        return cov_response(200, True, "success")

    # ---- 4. 环境覆盖率结果（uuid 必填） ----
    if path == "/cov/getEnvCoverResult" and method == "GET":
        query = parse_query(query_path)
        if not query.get("uuid"):
            return cov_response(-1, None, "fail: uuid不能为空")
        return cov_response(200, {
            "coverStatus": 1, "lineCoverage": 91.2, "branchCoverage": 85.0,
            "reportUrl": "http://localhost:8899/report/env/index.html"
        }, "success")

    # ---- 5. 本机模式覆盖率（5 个特有字段 + 父类 type 必填） ----
    if path == "/cov/getLocalCoverResult" and method == "POST":
        try:
            req = json.loads(body) if body else {}
        except Exception:
            return cov_response(-1, False, "fail: 请求体不是合法 JSON")
        for field in ("Address", "port", "classFilePath", "basePath", "nowPath"):
            if not req.get(field):
                return cov_response(-1, False, f"fail: {field}不能为空")
        if req.get("type") not in (1, 2):
            return cov_response(-1, False, "fail: type必须在1~2之间")
        return cov_response(200, {"coverStatus": 1, "lineCoverage": 66.7, "branchCoverage": 55.5}, "success")

    # ---- 6. 按时间获取代码提交 ----
    if path == "/cov/getCodeCommitsByTime" and method == "POST":
        return cov_response(200, [
            {"commitId": "abc123", "message": "fix: bug", "author": "dev", "time": "2026-08-01 10:00:00"},
            {"commitId": "def456", "message": "feat: new api", "author": "dev", "time": "2026-08-02 11:30:00"}
        ], "success")

    # ---- 7. 按分支获取代码提交 ----
    if path == "/cov/getCodeCommitsByBranch" and method == "POST":
        return cov_response(200, [
            {"commitId": "abc123", "message": "fix: bug", "author": "dev", "branch": "master"}
        ], "success")

    # ---- 8. 收集 Git 提交信息 ----
    if path == "/cov/collectGitCommitsInfo" and method == "POST":
        return cov_response(200, {"collected": 42, "repo": "jacoco-cov"}, "success")

    # ---- 9. 按 ref 获取代码提交 ----
    if path == "/cov/getCodeCommitsByRef" and method == "GET":
        return cov_response(200, {
            "ref": "refs/heads/master", "commitId": "abc123", "commits": 42
        }, "success")

    # ---- 10/11. 精准测试接口（宽松 token 校验：带任意 token 即通过，便于用例演示） ----
    if path in ("/exact/getApiMapList", "/exact/getApiMapList2") and method == "POST":
        has_token = bool(headers.get("Authorization")) or bool(headers.get("token"))
        if not has_token:
            return cov_response(403, None, "permission forbidden: token wrong")
        return cov_response(200, {
            "/cov/triggerUnitCover": ["CodeCovController.triggerUnitCover"],
            "/cov/getUnitCoverResult": ["CodeCovController.getCoverResult"]
        }, "success")

    return cov_response(-1, None, f"fail: 未知接口 {method} {path}")


class Handler(BaseHTTPRequestHandler):
    def _handle(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length).decode("utf-8") if length else ""
        # 传入完整 path（含 query），路由匹配与 query 解析在 handle_requests 内部分离
        response = handle_requests(self.command, self.path, body, self.headers)
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()

    def log_message(self, fmt, *args):
        print(f"[mock] {self.command} {self.path} -> {args[0]}", flush=True)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
    print(f"Mock 被测服务启动: http://localhost:{port} （已覆盖 jacoco-cov 全部 11 个接口）", flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
