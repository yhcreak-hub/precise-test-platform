package com.precise.test.casegen.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.casegen.entity.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则用例生成引擎（M3）
 * <p>基于接口定义的 paramSchemaJson，按契约规则生成四类用例：</p>
 * <ul>
 *   <li>normal    正常流程：所有参数取合法值</li>
 *   <li>required  必填校验：逐个缺失必填参数</li>
 *   <li>boundary  边界值：数值极值 / 空字符串 / 超长字符串</li>
 *   <li>exception 异常输入：非法类型 / 非法枚举值</li>
 * </ul>
 * <p>纯规则、零 AI 依赖、结果确定可复现——作为用例生成的第一道防线，
 * AI 业务用例生成作为后续增强（见 {@code AiCaseGenerator} 预留）。</p>
 */
@Slf4j
@Component
public class RuleCaseGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 为单个接口生成四类契约用例
     *
     * @param api  接口定义（含 paramSchemaJson / responseSchemaJson）
     * @param projectId 项目 ID
     * @return 生成的用例列表（未落库，由调用方保存）
     */
    public List<TestCase> generateForApi(ApiDefinition api, Long projectId) {
        List<TestCase> cases = new ArrayList<>();
        try {
            JsonNode schema = MAPPER.readTree(api.getParamSchemaJson());
            JsonNode params = schema.path("params");
            // BODY 参数（对象结构）是接口测试的主体；QUERY/PATH 参数也支持
            List<ParamInfo> bodyParams = new ArrayList<>();
            List<ParamInfo> simpleParams = new ArrayList<>();
            if (params.isArray()) {
                for (JsonNode p : params) {
                    ParamInfo info = parseParam(p);
                    if (info != null) {
                        if ("BODY".equals(info.source)) {
                            bodyParams.add(info);
                        } else {
                            simpleParams.add(info);
                        }
                    }
                }
            }

            // 1. 正常用例（BODY 字段 + QUERY/PATH 参数合并为一个请求体，执行器按 HTTP 方法分发）
            cases.add(buildCase(api, projectId, "normal", "正常流程",
                    buildBody(bodyParams, simpleParams, null), assertSuccess(api)));

            // 2. 必填校验：逐个缺失必填字段（BODY 对象字段 + QUERY/PATH 必填参数）
            List<String> requiredFields = collectRequiredFields(bodyParams, simpleParams);
            for (String field : requiredFields) {
                cases.add(buildCase(api, projectId, "required",
                        "必填校验-缺失[" + field + "]",
                        buildBody(bodyParams, simpleParams, field), assertValidateFail("required")));
            }

            // 3. 边界用例：数值边界 / 空串 / 超长
            for (ParamInfo p : simpleParams) {
                if (p.type != null && p.type.toLowerCase().contains("int")
                        || "Integer".equals(p.type) || "Long".equals(p.type)) {
                    cases.add(buildCase(api, projectId, "boundary",
                            "边界值-[" + p.name + "]取0",
                            buildSimpleBody(simpleParams, p.name, 0), assertSuccessOrFail()));
                }
            }
            // 空 Body 场景（POST 类接口）
            if (!bodyParams.isEmpty()) {
                cases.add(buildCase(api, projectId, "boundary",
                        "边界值-空请求体", "{}", assertSuccessOrFail()));
            }

            // 4. 异常用例：非法类型
            if (!simpleParams.isEmpty()) {
                cases.add(buildCase(api, projectId, "exception",
                        "异常输入-非法类型参数",
                        buildSimpleBody(simpleParams, simpleParams.get(0).name, "not-a-number"),
                        assertValidateFail("type")));
            }
        } catch (Exception e) {
            log.warn("规则生成用例失败: apiPath={}, err={}", api.getApiPath(), e.getMessage());
        }
        return cases;
    }

    // ---------------- 内部模型 ----------------

    /** 参数信息（从 schema 解析） */
    private static class ParamInfo {
        String name;
        String type;
        String source;   // BODY / QUERY / PATH / HEADER
        boolean required;
        List<String> validations = new ArrayList<>();
        List<FieldInfo> fields = new ArrayList<>(); // BODY 对象的字段
    }

    private static class FieldInfo {
        String name;
        String type;
        List<String> validations = new ArrayList<>();
    }

    private static ParamInfo parseParam(JsonNode p) {
        ParamInfo info = new ParamInfo();
        info.name = p.path("name").asText();
        info.type = p.path("type").asText();
        info.source = p.path("source").asText("QUERY");
        info.required = p.path("required").asBoolean(false);
        JsonNode v = p.path("validations");
        if (v.isArray()) {
            for (JsonNode x : v) {
                info.validations.add(x.path("annotation").asText());
            }
        }
        JsonNode fields = p.path("fields");
        if (fields.isArray()) {
            for (JsonNode f : fields) {
                FieldInfo fi = new FieldInfo();
                fi.name = f.path("name").asText();
                fi.type = f.path("type").asText();
                JsonNode fv = f.path("validations");
                if (fv.isArray()) {
                    for (JsonNode x : fv) {
                        fi.validations.add(x.path("annotation").asText());
                    }
                }
                info.fields.add(fi);
            }
        }
        return info;
    }

    /** 收集所有必填字段名（BODY 对象字段 + 简单参数） */
    private static List<String> collectRequiredFields(List<ParamInfo> bodyParams, List<ParamInfo> simpleParams) {
        List<String> result = new ArrayList<>();
        for (ParamInfo bp : bodyParams) {
            for (FieldInfo f : bp.fields) {
                if (!f.validations.isEmpty()) {
                    result.add(f.name);
                }
            }
        }
        for (ParamInfo sp : simpleParams) {
            if ("NONE".equals(sp.source)) {
                continue;
            }
            if (sp.required) {
                result.add(sp.name);
            }
        }
        return result;
    }

    /** 构造 BODY JSON；excludeField 不为空时剔除该字段（必填缺失场景） */
    /** 构造请求体：BODY 对象字段 + QUERY/PATH 简单参数合并；
     *  excludeField 不为空时剔除该字段（必填缺失场景，BODY 字段与 QUERY 参数均适用） */
    private static String buildBody(List<ParamInfo> bodyParams, List<ParamInfo> simpleParams, String excludeField) {
        ObjectNode body = MAPPER.createObjectNode();
        for (ParamInfo bp : bodyParams) {
            for (FieldInfo f : bp.fields) {
                if (excludeField != null && excludeField.equals(f.name)) {
                    continue;
                }
                body.set(f.name, sampleValue(f.type));
            }
        }
        for (ParamInfo sp : simpleParams) {
            // 过滤 HttpServletRequest 等框架注入对象（NONE 来源，无业务意义）
            if ("NONE".equals(sp.source)) {
                continue;
            }
            if (excludeField != null && excludeField.equals(sp.name)) {
                continue;
            }
            body.set(sp.name, sampleValue(sp.type));
        }
        return body.toString();
    }

    /** 构造简单参数（QUERY/PATH）的 body 表示 */
    private static String buildSimpleBody(List<ParamInfo> simpleParams, String overrideName, Object overrideValue) {
        ObjectNode body = MAPPER.createObjectNode();
        for (ParamInfo sp : simpleParams) {
            if (overrideName != null && overrideName.equals(sp.name)) {
                body.set(sp.name, MAPPER.valueToTree(overrideValue));
            } else {
                body.set(sp.name, sampleValue(sp.type));
            }
        }
        return body.toString();
    }

    /** 按类型生成合法示例值 */
    private static JsonNode sampleValue(String type) {
        if (type == null) {
            return MAPPER.getNodeFactory().textNode("test");
        }
        String t = type.toLowerCase();
        if (t.contains("int") || t.equals("long") || t.equals("short") || t.equals("byte")) {
            return MAPPER.getNodeFactory().numberNode(1);
        }
        if (t.contains("double") || t.contains("float") || t.equals("bigdecimal")) {
            return MAPPER.getNodeFactory().numberNode(1.0);
        }
        if (t.contains("bool")) {
            return MAPPER.getNodeFactory().booleanNode(true);
        }
        return MAPPER.getNodeFactory().textNode("test");
    }

    // ---------------- 用例构建 ----------------

    private static TestCase buildCase(ApiDefinition api, Long projectId, String scenarioType,
                                      String title, String requestJson, String assertsJson) {
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setApiDefinitionId(api.getId());
        tc.setTitle(title);
        tc.setRequestJson(requestJson);
        tc.setAssertsJson(assertsJson);
        // 精准测试接口（/exact/*）需要 token 请求头（对应 jacoco-cov 的 jacoco.token 校验）
        if (api.getApiPath() != null && api.getApiPath().startsWith("/exact")) {
            tc.setHeadersJson("{\"token\":\"precise-test-token\"}");
        }
        tc.setScenarioType(scenarioType);
        tc.setSource("rule");
        tc.setConfidence("high");
        tc.setStatus("draft");
        return tc;
    }

    /** 正常用例断言：code==200 */
    private static String assertSuccess(ApiDefinition api) {
        Map<String, Object> assertMap = new LinkedHashMap<>();
        assertMap.put("body.code", 200);
        return toJson(assertMap);
    }

    /** 校验失败断言：code!=200（400/500 均可，重点是失败）
     *  <p>只断言核心状态码，不附加 reason 等被测服务不返回的辅助字段。</p> */
    private static String assertValidateFail(String reason) {
        Map<String, Object> assertMap = new LinkedHashMap<>();
        assertMap.put("body.code", "!=200");
        return toJson(assertMap);
    }

    /** 边界用例断言：成功或失败皆可（记录实际行为） */
    private static String assertSuccessOrFail() {
        Map<String, Object> assertMap = new LinkedHashMap<>();
        assertMap.put("body.code", "200 or !=200");
        return toJson(assertMap);
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
