package com.precise.test.analyze;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.precise.test.analyze.model.AnnotationUtils;
import com.precise.test.analyze.model.ParamSource;
import com.precise.test.analyze.model.TypeIndex;
import com.precise.test.analyze.model.TypeNames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 参数结构解析器：把 Controller 方法参数列表解析为结构化 JSON（paramSchemaJson）。
 *
 * <p>输出结构（简单 Map/嵌套结构，便于下游用例生成引擎消费）：
 * <pre>
 * {
 *   "params": [
 *     { "name":"page", "type":"int", "qualifiedType":"int", "source":"QUERY",
 *       "required":false, "defaultValue":"1" },
 *     { "name":"dto", "type":"UserCreateDTO", "qualifiedType":"com.x.UserCreateDTO",
 *       "source":"BODY", "required":true, "validated":true,
 *       "fields":[
 *         { "name":"name", "type":"String", "qualifiedType":"java.lang.String",
 *           "validations":[ { "annotation":"NotBlank", "message":"姓名不能为空" } ] }
 *       ]}
 *   ]
 * }
 * </pre>
 *
 * <p>DTO 字段解析规则：
 * <ul>
 *   <li>直接读取字段声明（不依赖 getter/setter），兼容 Lombok 注解的 DTO；</li>
 *   <li>支持 record 类型（读取组件参数作为字段）；</li>
 *   <li>支持继承：父类在扫描范围内时合并父类字段（带 inheritedFrom 标记），深度上限
 *       {@link #MAX_DTO_DEPTH} 防循环；</li>
 *   <li>字段上的 JSR-303 校验注解（jakarta/javax.validation.constraints.*）提取为
 *       {@code {"annotation":注解简单名, 属性:值...}}，message 等属性值原样保留；</li>
 *   <li>泛型/数组参数（如 {@code List<UserDTO>}、{@code UserDTO[]}）自动解包取内层 DTO。</li>
 * </ul>
 */
public class ParamSchemaResolver {

    /** DTO 继承链解析深度上限（防止继承环导致死循环）。 */
    private static final int MAX_DTO_DEPTH = 5;

    /** 常见 JSR-303 / Bean Validation 校验注解（按简单名匹配，兼容 jakarta 与 javax 双包）。 */
    private static final Set<String> VALIDATION_ANNOTATIONS = Set.of(
            "NotNull", "NotBlank", "NotEmpty", "Null",
            "Min", "Max", "Size", "Pattern", "Email",
            "DecimalMin", "DecimalMax", "Digits",
            "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
            "AssertTrue", "AssertFalse",
            "Future", "FutureOrPresent", "Past", "PastOrPresent"
    );

    /**
     * 方法参数列表 → paramSchemaJson。
     *
     * @param parameters 方法参数（可能为空）
     * @param index      全量类型索引（DTO 字段解析）
     * @return JSON 字符串；无参数时返回 {@code {"params":[]}}
     */
    public String resolve(List<Parameter> parameters, TypeIndex index) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (Parameter parameter : parameters) {
            params.add(resolveParam(parameter, index));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("params", params);
        return JsonUtils.toJson(root);
    }

    /** 解析单个方法参数 → schema 条目。 */
    private Map<String, Object> resolveParam(Parameter parameter, TypeIndex index) {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 参数来源：按优先级识别参数注解（RequestBody > PathVariable > RequestHeader > RequestParam） ----
        Optional<AnnotationExpr> body = AnnotationUtils.findAnnotation(parameter, "RequestBody");
        Optional<AnnotationExpr> path = AnnotationUtils.findAnnotation(parameter, "PathVariable");
        Optional<AnnotationExpr> header = AnnotationUtils.findAnnotation(parameter, "RequestHeader");
        Optional<AnnotationExpr> query = AnnotationUtils.findAnnotation(parameter, "RequestParam");

        ParamSource source = ParamSource.NONE;
        Optional<AnnotationExpr> target = Optional.empty();
        if (body.isPresent()) {
            source = ParamSource.BODY;
            target = body;
        } else if (path.isPresent()) {
            source = ParamSource.PATH;
            target = path;
        } else if (header.isPresent()) {
            source = ParamSource.HEADER;
            target = header;
        } else if (query.isPresent()) {
            source = ParamSource.QUERY;
            target = query;
        }

        // ---- 参数名：注解 name/value 显式声明优先，其次源码参数名 ----
        // 注意：源码参数名依赖编译期 -parameters 开关；未开启时可能退化为 arg0 之类，属预期行为。
        String name = target.flatMap(a -> firstString(a, "name", "value"))
                .orElse(parameter.getNameAsString());
        result.put("name", name);

        // ---- 类型（简单名 + best-effort 全限定名） ----
        Type type = parameter.getType();
        result.put("type", TypeNames.simpleNameOf(type));
        result.put("qualifiedType", TypeNames.qualifiedOf(type, index));

        // ---- 来源与必填 ----
        result.put("source", source.name());
        // 带注解未显式声明 required 时按 Spring 默认必填（true）；无注解参数按可选处理
        boolean required = source == ParamSource.NONE
                ? false
                : target.flatMap(a -> AnnotationUtils.booleanValue(a, "required")).orElse(true);
        result.put("required", required);

        // ---- defaultValue（@RequestParam / @RequestHeader 支持） ----
        target.flatMap(a -> firstString(a, "defaultValue")).ifPresent(v -> result.put("defaultValue", v));

        // ---- @RequestBody：解析 DTO 字段结构 ----
        if (source == ParamSource.BODY) {
            if (AnnotationUtils.findAnnotation(parameter, "Valid", "Validated").isPresent()) {
                result.put("validated", true);
            }
            result.put("fields", resolveDtoFields(type, index, 0));
        }
        return result;
    }

    /**
     * 解析 DTO 字段结构。
     *
     * @param type  BODY 参数类型（自动解包数组/泛型容器）
     * @param index 类型索引
     * @param depth 继承链深度（防止循环）
     * @return 字段 schema 列表；无法解析时为空列表
     */
    private List<Map<String, Object>> resolveDtoFields(Type type, TypeIndex index, int depth) {
        Type candidate = unwrapContainer(type);
        if (candidate == null || !candidate.isClassOrInterfaceType()) {
            return List.of();
        }
        String simpleName = candidate.asClassOrInterfaceType().getNameAsString();

        // JDK 内置类型（String/List/Map 等）不是 DTO
        if (TypeNames.isBuiltinType(simpleName)) {
            return List.of();
        }

        Optional<TypeIndex.TypeEntry> entry = index.find(simpleName);
        if (entry.isEmpty()) {
            return List.of();
        }

        TypeDeclaration<?> declaration = entry.get().declaration();
        List<Map<String, Object>> fields = new ArrayList<>();
        if (declaration instanceof ClassOrInterfaceDeclaration clazz && !clazz.isInterface()) {
            collectClassFields(clazz, index, depth, fields, null);
        } else if (declaration instanceof RecordDeclaration record) {
            collectRecordFields(record, index, fields);
        }
        return fields;
    }

    /**
     * 解包容器/数组类型，取最内层的候选 DTO 类型：
     * <ul>
     *   <li>数组（{@code UserDTO[]}）→ 元素类型；</li>
     *   <li>泛型（{@code List<UserDTO>}、{@code Page<UserDTO>}）→ 第一个类型实参；</li>
     *   <li>其他 → 原类型。</li>
     * </ul>
     * 非引用类型（基本类型/void 等）返回 null。
     */
    private Type unwrapContainer(Type type) {
        if (type.isArrayType()) {
            return unwrapContainer(type.asArrayType().getComponentType());
        }
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            if (cit.getTypeArguments().isPresent()) {
                Optional<Type> first = cit.getTypeArguments().get().stream().findFirst();
                if (first.isPresent()) {
                    return unwrapContainer(first.get());
                }
            }
            return cit;
        }
        return null;
    }

    /**
     * 收集普通类的字段（含继承链上的父类字段）。
     *
     * @param clazz        类声明
     * @param index        类型索引（解析父类用）
     * @param depth        当前继承深度
     * @param fields       输出字段列表
     * @param inheritedFrom 父类简单名（本类字段为 null，父类字段为父类名）
     */
    private void collectClassFields(ClassOrInterfaceDeclaration clazz, TypeIndex index, int depth,
                                    List<Map<String, Object>> fields, String inheritedFrom) {
        for (BodyDeclaration<?> member : clazz.getMembers()) {
            if (!(member instanceof FieldDeclaration field)) {
                continue;
            }
            if (field.isStatic()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                fields.add(fieldEntry(variable, field, index, inheritedFrom));
            }
        }
        // 继承：父类在扫描范围内且未达深度上限时合并父类字段
        if (depth < MAX_DTO_DEPTH) {
            for (ClassOrInterfaceType parent : clazz.getExtendedTypes()) {
                String parentName = parent.getNameAsString();
                if ("Object".equals(parentName)) {
                    continue;
                }
                index.find(parentName).ifPresent(entry -> {
                    TypeDeclaration<?> parentDecl = entry.declaration();
                    if (parentDecl instanceof ClassOrInterfaceDeclaration parentClass && !parentClass.isInterface()) {
                        collectClassFields(parentClass, index, depth + 1, fields, parentName);
                    }
                });
            }
        }
    }

    /** 收集 record 的组件参数（作为字段）。 */
    private void collectRecordFields(RecordDeclaration record, TypeIndex index,
                                     List<Map<String, Object>> fields) {
        for (Parameter component : record.getParameters()) {
            fields.add(recordFieldEntry(component, index));
        }
    }

    /** 字段声明 → schema 条目（字段名、类型、校验注解、继承标记）。 */
    private Map<String, Object> fieldEntry(VariableDeclarator variable, FieldDeclaration field,
                                           TypeIndex index, String inheritedFrom) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", variable.getNameAsString());
        Type type = variable.getType();
        entry.put("type", TypeNames.simpleNameOf(type));
        entry.put("qualifiedType", TypeNames.qualifiedOf(type, index));
        List<Map<String, Object>> validations = extractValidations(field.getAnnotations());
        if (!validations.isEmpty()) {
            entry.put("validations", validations);
        }
        if (inheritedFrom != null) {
            entry.put("inheritedFrom", inheritedFrom);
        }
        return entry;
    }

    /** record 组件 → schema 条目（组件上的校验注解同样提取）。 */
    private Map<String, Object> recordFieldEntry(Parameter component, TypeIndex index) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", component.getNameAsString());
        Type type = component.getType();
        entry.put("type", TypeNames.simpleNameOf(type));
        entry.put("qualifiedType", TypeNames.qualifiedOf(type, index));
        List<Map<String, Object>> validations = extractValidations(component.getAnnotations());
        if (!validations.isEmpty()) {
            entry.put("validations", validations);
        }
        return entry;
    }

    /**
     * 提取字段/组件上的 JSR-303 校验注解。
     *
     * <p>输出：{@code {"annotation":"NotBlank","message":"姓名不能为空"}}；
     * 注解的其他命名属性（min/max 等）原样保留为字符串值；
     * 单成员注解（如 {@code @Min(1)}）映射为 value 属性。
     */
    private List<Map<String, Object>> extractValidations(List<AnnotationExpr> annotations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            String simple = AnnotationUtils.simpleName(annotation);
            if (!VALIDATION_ANNOTATIONS.contains(simple)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("annotation", simple);
            if (annotation instanceof NormalAnnotationExpr normal) {
                for (MemberValuePair pair : normal.getPairs()) {
                    item.put(pair.getNameAsString(), AnnotationUtils.literalToString(pair.getValue()));
                }
            } else if (annotation instanceof SingleMemberAnnotationExpr single) {
                item.put("value", AnnotationUtils.literalToString(single.getMemberValue()));
            }
            result.add(item);
        }
        return result;
    }

    /** 依次尝试多个属性名，返回第一个存在的字符串值（如 name/value 别名）。 */
    private Optional<String> firstString(AnnotationExpr annotation, String... attrs) {
        for (String attr : attrs) {
            Optional<String> value = AnnotationUtils.stringValue(annotation, attr);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
