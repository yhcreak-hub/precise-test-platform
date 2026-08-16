package com.precise.test.casegen.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.casegen.entity.TestCase;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例执行引擎（M4）
 * <p>读取用例的 requestJson（请求体）与 assertsJson（断言），
 * 向被测服务真实发起 HTTP 请求，解析响应并按断言校验，返回执行结果。</p>
 * <p>断言格式（与生成器约定一致）：</p>
 * <pre>
 *   {"body.code": 200}            // 等于
 *   {"body.code": "!=200"}        // 不等于
 *   {"body.code": "200 or !=200"} // 成功或失败均可（记录实际行为）
 * </pre>
 */
@Slf4j
@Component
public class CaseExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 执行单个用例
     *
     * @param testCase     用例
     * @param api          接口定义（提供 apiPath / httpMethod）
     * @param baseUrl      被测服务地址
     * @return 执行结果
     */
    public ExecuteResult execute(TestCase testCase, ApiDefinition api, String baseUrl) {
        long start = System.currentTimeMillis();
        ExecuteResult result = new ExecuteResult();
        result.setCaseId(testCase.getId());
        result.setTitle(testCase.getTitle());

        try {
            // 1. 解析断言
            Map<String, Object> asserts = parseAsserts(testCase.getAssertsJson());

            // 2. 发送真实 HTTP 请求（GET → query string；其余 → JSON body）
            HttpMethod method = HttpMethod.valueOf(api.getHttpMethod());
            String url = buildUrl(baseUrl, api.getApiPath());
            String bodyJson = safeJson(testCase.getRequestJson());
            if (HttpMethod.GET.equals(method)) {
                url = appendQuery(url, bodyJson);
                bodyJson = null;
            }
            result.setUrl(url);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyHeaders(headers, testCase.getHeadersJson());
            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

            ResponseEntity<String> response;
            try {
                response = restTemplate.exchange(url, method, entity, String.class);
            } catch (Exception e) {
                // 被测服务不可达 / 请求异常
                result.setStatus("ERROR");
                result.setErrorMsg("请求异常: " + e.getMessage());
                result.setCostMs(System.currentTimeMillis() - start);
                return result;
            }

            result.setHttpStatus(response.getStatusCode().value());
            String responseBody = response.getBody() == null ? "" : response.getBody();
            result.setResponseBody(responseBody);

            // 4. 断言校验
            List<String> details = new ArrayList<>();
            boolean allPass = true;
            JsonNode responseJson = parseJson(responseBody);
            for (Map.Entry<String, Object> entry : asserts.entrySet()) {
                String path = entry.getKey();        // 如 body.code
                Object expected = entry.getValue();  // 如 200 / "!=200"
                Object actual = resolvePath(responseJson, path);
                AssertResult ar = check(path, expected, actual);
                details.add(ar.getDetail());
                if (!ar.isPass()) {
                    allPass = false;
                }
            }

            result.setAssertDetails(details);
            result.setStatus(allPass ? "PASS" : "FAIL");
            // 空断言视为 PASS（仅记录响应）
            if (asserts.isEmpty()) {
                result.setStatus("PASS");
            }
        } catch (Exception e) {
            log.warn("用例执行异常: caseId={}, err={}", testCase.getId(), e.getMessage());
            result.setStatus("ERROR");
            result.setErrorMsg("执行异常: " + e.getMessage());
        }
        result.setCostMs(System.currentTimeMillis() - start);
        return result;
    }

    // ---------------- URL 构造 ----------------

    /** 解析并附加用例自定义请求头（headersJson：{"token":"xxx"}） */
    private void applyHeaders(HttpHeaders headers, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(headersJson);
            if (node.isObject()) {
                var it = node.fields();
                while (it.hasNext()) {
                    var entry = it.next();
                    headers.set(entry.getKey(), entry.getValue().asText());
                }
            }
        } catch (Exception e) {
            log.warn("请求头解析失败，忽略: {}", headersJson);
        }
    }

    /** GET 请求：把请求体 JSON 的字段转为 query string 拼接到 URL */
    private String appendQuery(String url, String bodyJson) {
        JsonNode node = parseJson(bodyJson);
        if (node == null || !node.isObject() || node.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean first = !url.contains("?");
        var it = node.fields();
        while (it.hasNext()) {
            var entry = it.next();
            sb.append(first ? "?" : "&");
            first = false;
            sb.append(entry.getKey()).append("=").append(entry.getValue().asText());
        }
        return sb.toString();
    }

    private String buildUrl(String baseUrl, String apiPath) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = apiPath == null ? "" : apiPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    // ---------------- 断言解析 ----------------

    private Map<String, Object> parseAsserts(String assertsJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (assertsJson == null || assertsJson.isBlank()) {
            return result;
        }
        try {
            JsonNode node = MAPPER.readTree(assertsJson);
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode v = e.getValue();
                    result.put(e.getKey(), v.isNumber() ? v.asInt() : v.asText());
                }
            }
        } catch (Exception e) {
            log.warn("断言解析失败，按空断言处理: {}", assertsJson);
        }
        return result;
    }

    /** 解析 JSON 路径：body.code / code / data.total / data.user.name
     *  <p>兼容处理：路径以 body. 开头时，若响应顶层无 body 字段（如平铺的 HttpResult {code,msg,data}），
     *  自动降级为从根节点解析（body.code → code）。</p> */
    private Object resolvePath(JsonNode root, String path) {
        if (root == null) {
            return null;
        }
        JsonNode cur = root;
        String[] segments = path.split("\\.");
        int start = 0;
        // body. 前缀兼容：顶层有 body 字段则进入，否则从根开始
        if (segments.length > 1 && "body".equals(segments[0])) {
            if (cur.isObject() && cur.has("body")) {
                cur = cur.get("body");
                start = 1;
            } else {
                start = 1; // 顶层无 body，降级：从根解析剩余路径
            }
        }
        for (int i = start; i < segments.length; i++) {
            if (cur == null || !cur.isObject()) {
                return null;
            }
            cur = cur.get(segments[i]);
        }
        if (cur == null) {
            return null;
        }
        if (cur.isNumber()) {
            return cur.asInt();
        }
        if (cur.isBoolean()) {
            return cur.asBoolean();
        }
        return cur.asText();
    }

    /** 单条断言检查 */
    private AssertResult check(String path, Object expected, Object actual) {
        AssertResult ar = new AssertResult();
        String expStr = String.valueOf(expected);
        String actStr = String.valueOf(actual);

        if ("200 or !=200".equals(expStr)) {
            // 记录实际行为，视为通过
            ar.setPass(true);
            ar.setDetail(String.format("断言[%s]: 记录实际行为 → 实际=%s（放行）", path, actStr));
            return ar;
        }
        if (expStr.startsWith("!=")) {
            String notExpected = expStr.substring(2);
            boolean pass = !notExpected.equals(actStr);
            ar.setPass(pass);
            ar.setDetail(String.format("断言[%s]: 期望≠%s，实际=%s → %s",
                    path, notExpected, actStr, pass ? "通过" : "失败"));
            return ar;
        }
        boolean pass = expStr.equals(actStr);
        ar.setPass(pass);
        ar.setDetail(String.format("断言[%s]: 期望=%s，实际=%s → %s",
                path, expStr, actStr, pass ? "通过" : "失败"));
        return ar;
    }

    private JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        return json;
    }

    // ---------------- 结果模型 ----------------

    @Data
    public static class ExecuteResult {
        /** 用例 ID */
        private Long caseId;
        /** 用例标题 */
        private String title;
        /** 请求 URL */
        private String url;
        /** HTTP 状态码 */
        private Integer httpStatus;
        /** 响应体 */
        private String responseBody;
        /** 断言明细 */
        private List<String> assertDetails = new ArrayList<>();
        /** 状态：PASS / FAIL / ERROR */
        private String status;
        /** 错误信息（ERROR 时） */
        private String errorMsg;
        /** 耗时 ms */
        private long costMs;
    }

    @Data
    private static class AssertResult {
        private boolean pass;
        private String detail;
    }
}
