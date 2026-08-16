package com.precise.test.analyze;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.model.AnnotationUtils;
import com.precise.test.analyze.model.ApiHttpMethod;
import com.precise.test.analyze.model.TypeIndex;
import com.precise.test.analyze.model.TypeNames;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 接口定义组装器：把"Controller 类 + 映射方法 + 类级/方法级注解"组装为一个或多个
 * {@link ApiDefinition}（路径拼接、HTTP 方法解析、返回结构生成均在本类完成）。
 *
 * <p>一个方法可能展开为多个 ApiDefinition 的场景：
 * <ul>
 *   <li>类级/方法级路径声明为数组（如 {@code @GetMapping({"/a","/b"})}）——按笛卡尔积展开；</li>
 *   <li>{@code @RequestMapping(method = {GET, POST})} 声明多个 HTTP 方法——按方法展开。</li>
 * </ul>
 * 单方法展开上限为 {@link #MAX_ENDPOINTS_PER_METHOD}，防止病态注解导致输出爆炸。
 *
 * <p>路径拼接规则：
 * <ol>
 *   <li>取类级 {@code @RequestMapping} 的 value/path（无前缀时为 ""）；</li>
 *   <li>取方法级映射注解的 value/path（未声明时为 ""）；</li>
 *   <li>归一化：去首尾 '/' 与空白，按 {@code /前缀/路径} 拼接，两侧为空时映射根路径 "/"。</li>
 * </ol>
 */
public class ApiDefinitionBuilder {

    /** 单个方法展开的最大接口数上限（防御病态注解）。 */
    private static final int MAX_ENDPOINTS_PER_METHOD = 32;

    /** 快捷映射注解 → HTTP 方法（@RequestMapping 的 method 属性单独解析）。 */
    private static final Map<String, ApiHttpMethod> SHORTCUT_MAPPINGS = Map.of(
            "GetMapping", ApiHttpMethod.GET,
            "PostMapping", ApiHttpMethod.POST,
            "PutMapping", ApiHttpMethod.PUT,
            "DeleteMapping", ApiHttpMethod.DELETE,
            "PatchMapping", ApiHttpMethod.PATCH
    );

    /** 方法级可识别的映射注解（简单名匹配，兼容全限定名/星号导入写法）。 */
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"
    );

    private final ParamSchemaResolver paramSchemaResolver = new ParamSchemaResolver();

    /**
     * 构建接口定义列表。
     *
     * @param projectId        被测项目 ID（与统一模型 {@link ApiDefinition#getProjectId()} 一致为 Long）
     * @param cu               编译单元（包名来源）
     * @param controller       Controller 类声明
     * @param method           候选接口方法
     * @param index            全量类型索引（DTO 解析用）
     * @param filePath         源码文件相对路径（可为 null，如字符串解析场景）
     * @param ignoreDeprecated 为 true 时跳过被 {@code @Deprecated} 标记的方法
     * @return 接口定义列表；方法不是接口方法（无映射注解/静态/抽象/已废弃）时返回空列表
     */
    public List<ApiDefinition> build(Long projectId, CompilationUnit cu,
                                     ClassOrInterfaceDeclaration controller, MethodDeclaration method,
                                     TypeIndex index, String filePath, boolean ignoreDeprecated) {
        // ---- 前置过滤：静态/抽象方法、被 @Deprecated 标记的方法不是有效接口 ----
        if (method.isStatic() || method.isAbstract()) {
            return List.of();
        }
        if (ignoreDeprecated && AnnotationUtils.findAnnotation(method, "Deprecated").isPresent()) {
            return List.of();
        }

        // ---- 方法级映射注解：没有映射注解的方法不是接口 ----
        Optional<AnnotationExpr> mappingOptional = findMappingAnnotation(method);
        if (mappingOptional.isEmpty()) {
            return List.of();
        }
        AnnotationExpr mapping = mappingOptional.get();
        String mappingSimpleName = AnnotationUtils.simpleName(mapping);

        // ---- 路径：类级前缀 × 方法级路径（笛卡尔积，去重） ----
        List<String> paths = joinPaths(classLevelPaths(controller), methodLevelPaths(mapping));

        // ---- HTTP 方法 ----
        List<ApiHttpMethod> httpMethods = resolveHttpMethods(mappingSimpleName, mapping);

        // ---- 公共信息只计算一次，路径 × 方法展开时复用 ----
        String paramSchemaJson = paramSchemaResolver.resolve(method.getParameters(), index);
        String responseSchemaJson = buildResponseSchema(method.getType(), index);
        String controllerClass = TypeIndex.qualifiedName(cu, controller);
        String controllerMethod = method.getNameAsString();
        Integer lineNo = method.getBegin().map(pos -> pos.line).orElse(null);

        List<ApiDefinition> result = new ArrayList<>();
        int count = 0;
        outer:
        for (String path : paths) {
            for (ApiHttpMethod httpMethod : httpMethods) {
                if (++count > MAX_ENDPOINTS_PER_METHOD) {
                    break outer;
                }
                ApiDefinition def = new ApiDefinition();
                def.setProjectId(projectId);
                def.setApiPath(path);
                def.setHttpMethod(httpMethod.name());
                def.setParamSchemaJson(paramSchemaJson);
                def.setResponseSchemaJson(responseSchemaJson);
                def.setControllerClass(controllerClass);
                def.setControllerMethod(controllerMethod);
                def.setFilePath(filePath);
                def.setLineNo(lineNo);
                def.setStatus(ApiDefinition.STATUS_PENDING);
                def.setCreatedAt(LocalDateTime.now());
                result.add(def);
            }
        }
        return result;
    }

    /** 查找方法上的映射注解（简单名匹配）。 */
    private Optional<AnnotationExpr> findMappingAnnotation(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            if (MAPPING_ANNOTATIONS.contains(AnnotationUtils.simpleName(annotation))) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    /**
     * 类级路径前缀：类上 {@code @RequestMapping} 的 value/path 属性；
     * 无注解或未声明路径时返回 {@code [""]}（表示无前缀）。
     */
    private List<String> classLevelPaths(ClassOrInterfaceDeclaration controller) {
        Optional<AnnotationExpr> requestMapping = AnnotationUtils.findAnnotation(controller, "RequestMapping");
        if (requestMapping.isEmpty()) {
            return List.of("");
        }
        List<String> values = pathValues(requestMapping.get());
        return values.isEmpty() ? List.of("") : values;
    }

    /**
     * 方法级路径：映射注解的 value/path 属性；
     * 未声明时返回 {@code [""]}（映射到类级前缀本身）。
     */
    private List<String> methodLevelPaths(AnnotationExpr mapping) {
        List<String> values = pathValues(mapping);
        return values.isEmpty() ? List.of("") : values;
    }

    /** 读取注解的 value 与 path 属性（Spring 支持两者互为别名，合并去重）。 */
    private List<String> pathValues(AnnotationExpr annotation) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(AnnotationUtils.stringArrayValue(annotation, "value"));
        values.addAll(AnnotationUtils.stringArrayValue(annotation, "path"));
        return new ArrayList<>(values);
    }

    /**
     * 路径拼接（笛卡尔积 + 归一化 + 去重）。
     *
     * @param classPrefixes 类级前缀集合
     * @param methodPaths   方法级路径集合
     * @return 归一化后的完整路径集合，如 ["/user/list", "/user/{id}"]
     */
    private List<String> joinPaths(List<String> classPrefixes, List<String> methodPaths) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String prefix : classPrefixes) {
            for (String path : methodPaths) {
                result.add(normalizePath(prefix, path));
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 归一化拼接：去首尾 '/' 与空白；两侧为空时返回 "/"（根路径）。
     * 路径变量（如 {id}）原样保留。
     */
    private String normalizePath(String classPrefix, String methodPath) {
        String prefix = trimSlashes(classPrefix);
        String path = trimSlashes(methodPath);
        if (prefix.isEmpty() && path.isEmpty()) {
            return "/";
        }
        if (prefix.isEmpty()) {
            return "/" + path;
        }
        if (path.isEmpty()) {
            return "/" + prefix;
        }
        return "/" + prefix + "/" + path;
    }

    /** 去除字符串首尾的 '/' 与空白。 */
    private String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /**
     * 解析 HTTP 方法：
     * <ul>
     *   <li>快捷注解（@GetMapping 等）直接对应固定方法；</li>
     *   <li>{@code @RequestMapping} 解析 method 属性（RequestMethod 枚举数组，兼容字符串写法）；
     *       未声明 method 时按 Spring 语义默认 GET。</li>
     * </ul>
     */
    private List<ApiHttpMethod> resolveHttpMethods(String mappingSimpleName, AnnotationExpr mapping) {
        ApiHttpMethod shortcut = SHORTCUT_MAPPINGS.get(mappingSimpleName);
        if (shortcut != null) {
            return List.of(shortcut);
        }
        List<ApiHttpMethod> methods = new ArrayList<>();
        for (String raw : AnnotationUtils.stringArrayValue(mapping, "method")) {
            ApiHttpMethod parsed = ApiHttpMethod.fromName(raw);
            if (parsed != null && !methods.contains(parsed)) {
                methods.add(parsed);
            }
        }
        return methods.isEmpty() ? List.of(ApiHttpMethod.GET) : methods;
    }

    /**
     * 组装返回结构 JSON：{@code {"returnType":"HttpResult<UserVO>","returnTypeQualified":"..."}}。
     * returnType 保留源码写法；returnTypeQualified 为 best-effort 全限定名（推导规则见 {@link TypeNames}）。
     */
    private String buildResponseSchema(Type returnType, TypeIndex index) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("returnType", returnType.asString());
        schema.put("returnTypeQualified", TypeNames.qualifiedOf(returnType, index));
        return JsonUtils.toJson(schema);
    }
}
