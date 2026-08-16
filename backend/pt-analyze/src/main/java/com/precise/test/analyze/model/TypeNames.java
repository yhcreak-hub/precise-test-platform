package com.precise.test.analyze.model;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类型名推导工具：提供"展示用简单名"与"best-effort 全限定名"。
 *
 * <p>说明：引擎不加载编译产物、不做符号解析（不引入 symbol-solver），
 * 全限定名推导是尽力而为的，优先级如下：
 * <ol>
 *   <li>源码中已写全限定名（含包名）→ 原样保留；</li>
 *   <li>简单名命中 {@link TypeIndex}（扫描范围内声明的类型）→ 替换为全限定名；</li>
 *   <li>常见 java.lang / java.util 类型 → 补充完整包名；</li>
 *   <li>其余（外部依赖 jar 中的类型）→ 保留源码写法（可能只是简单名）。</li>
 * </ol>
 */
public final class TypeNames {

    /** 常见 java.lang 类型（推导全限定名用）。 */
    private static final Map<String, String> JAVA_LANG_TYPES = Map.ofEntries(
            Map.entry("String", "java.lang.String"),
            Map.entry("Integer", "java.lang.Integer"),
            Map.entry("Long", "java.lang.Long"),
            Map.entry("Short", "java.lang.Short"),
            Map.entry("Byte", "java.lang.Byte"),
            Map.entry("Double", "java.lang.Double"),
            Map.entry("Float", "java.lang.Float"),
            Map.entry("Boolean", "java.lang.Boolean"),
            Map.entry("Character", "java.lang.Character"),
            Map.entry("Object", "java.lang.Object")
    );

    /** 常见 java.util 容器/日期类型（DTO 解析时快速排除非 DTO 类型）。 */
    private static final Map<String, String> JAVA_UTIL_TYPES = Map.ofEntries(
            Map.entry("List", "java.util.List"),
            Map.entry("Set", "java.util.Set"),
            Map.entry("Map", "java.util.Map"),
            Map.entry("Collection", "java.util.Collection"),
            Map.entry("ArrayList", "java.util.ArrayList"),
            Map.entry("HashMap", "java.util.HashMap"),
            Map.entry("Optional", "java.util.Optional"),
            Map.entry("Date", "java.util.Date"),
            Map.entry("LocalDate", "java.time.LocalDate"),
            Map.entry("LocalDateTime", "java.time.LocalDateTime"),
            Map.entry("UUID", "java.util.UUID")
    );

    private TypeNames() {
    }

    /**
     * 类型 → 展示用简单名。
     *
     * <p>数组追加 {@code []}；泛型携带类型实参（如 {@code List<UserVO>}），
     * 便于人读与断言。基本类型原样返回。
     */
    public static String simpleNameOf(Type type) {
        if (type.isArrayType()) {
            return simpleNameOf(type.asArrayType().getComponentType()) + "[]";
        }
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            String base = cit.getNameAsString();
            if (cit.getTypeArguments().isPresent()) {
                List<String> args = new ArrayList<>();
                for (Type arg : cit.getTypeArguments().get()) {
                    args.add(simpleNameOf(arg));
                }
                return base + "<" + String.join(",", args) + ">";
            }
            return base;
        }
        return type.asString();
    }

    /**
     * 类型 → best-effort 全限定名（规则见类注释；泛型递归展开类型实参）。
     */
    public static String qualifiedOf(Type type, TypeIndex index) {
        if (type.isPrimitiveType() || type.isVoidType() || type.isVarType() || type.isUnknownType()) {
            return type.asString();
        }
        if (type.isArrayType()) {
            return qualifiedOf(type.asArrayType().getComponentType(), index) + "[]";
        }
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            String asWritten = cit.getNameWithScope();
            String qualified = asWritten.contains(".")
                    ? asWritten
                    : resolveSimpleName(asWritten, index);
            if (cit.getTypeArguments().isPresent()) {
                List<String> args = new ArrayList<>();
                for (Type arg : cit.getTypeArguments().get()) {
                    args.add(qualifiedOf(arg, index));
                }
                qualified += "<" + String.join(", ", args) + ">";
            }
            return qualified;
        }
        // WildcardType / IntersectionType / UnionType 等罕见类型：直接取源码文本
        return type.asString();
    }

    /**
     * 判断是否为 JDK 内置类型（java.lang / java.util 常见类型）。
     * DTO 字段解析时用于排除"看起来像类但实际是 JDK 类型"的候选。
     *
     * @param simpleName 简单类名
     * @return 是内置类型返回 true
     */
    public static boolean isBuiltinType(String simpleName) {
        return JAVA_LANG_TYPES.containsKey(simpleName) || JAVA_UTIL_TYPES.containsKey(simpleName);
    }

    /** 简单名 → 全限定名：扫描范围类型 &gt; java.lang &gt; java.util &gt; 原样保留。 */
    private static String resolveSimpleName(String simpleName, TypeIndex index) {
        return index.qualifiedNameOf(simpleName)
                .orElse(JAVA_LANG_TYPES.getOrDefault(simpleName,
                        JAVA_UTIL_TYPES.getOrDefault(simpleName, simpleName)));
    }
}
