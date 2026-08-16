package com.precise.test.analyze.model;

import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 注解解析工具：统一处理 Spring / JSR-303 注解的
 * "简单名 / 全限定名"匹配与属性值提取。
 *
 * <p>设计要点：
 * <ul>
 *   <li>注解匹配按简单名（如 "GetMapping"），同时兼容全限定名写法
 *       （如 "org.springframework.web.bind.annotation.GetMapping"）与星号导入；</li>
 *   <li>属性值支持 单值 / 数组 / 单成员注解 三种形态，返回值为去引号的字符串。</li>
 * </ul>
 */
public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    /**
     * 注解的简单名（去掉包名前缀）。
     *
     * @param annotation 注解表达式
     * @return 如 "GetMapping" / "NotBlank"
     */
    public static String simpleName(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    /**
     * 判断注解是否命中给定简单名之一（兼容全限定名写法）。
     *
     * @param annotation  注解表达式
     * @param simpleNames 候选简单名
     * @return 命中返回 true
     */
    public static boolean isAnnotation(AnnotationExpr annotation, String... simpleNames) {
        String simple = simpleName(annotation);
        for (String name : simpleNames) {
            if (simple.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在节点上查找第一个命中给定简单名的注解。
     *
     * @param node        带注解的 AST 节点（类/方法/参数/字段等）
     * @param simpleNames 候选简单名
     * @return 命中的注解；未命中返回 {@link Optional#empty()}
     */
    public static Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node, String... simpleNames) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            if (isAnnotation(annotation, simpleNames)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    /**
     * 提取注解的字符串数组属性（如 {@code @RequestMapping} 的 value/path、method）。
     *
     * <p>支持三种形态：
     * <ul>
     *   <li>单字符串：{@code @GetMapping("/list")} → ["/list"]</li>
     *   <li>字符串数组：{@code @GetMapping({"/a","/b"})} → ["/a","/b"]</li>
     *   <li>单成员注解：value 属性</li>
     * </ul>
     *
     * @param annotation 注解表达式
     * @param attr       属性名（"value" / "path" / "method" 等）
     * @return 属性值列表；属性不存在时返回空列表
     */
    public static List<String> stringArrayValue(AnnotationExpr annotation, String attr) {
        return attrValue(annotation, attr)
                .map(AnnotationUtils::stringArrayOf)
                .orElseGet(List::of);
    }

    /**
     * 提取注解的单值字符串属性（如 {@code @RequestParam} 的 name/value、
     * {@code @NotBlank} 的 message）。属性不存在时返回 {@link Optional#empty()}。
     */
    public static Optional<String> stringValue(AnnotationExpr annotation, String attr) {
        return attrValue(annotation, attr).map(AnnotationUtils::literalToString);
    }

    /**
     * 提取注解的布尔属性（如 {@code @RequestParam} 的 required）。
     * 仅接受布尔字面量；属性不存在或非布尔字面量时返回 {@link Optional#empty()}。
     */
    public static Optional<Boolean> booleanValue(AnnotationExpr annotation, String attr) {
        return attrValue(annotation, attr)
                .filter(Expression::isBooleanLiteralExpr)
                .map(expr -> expr.asBooleanLiteralExpr().getValue());
    }

    /**
     * 取注解指定属性对应的表达式。
     *
     * <p>单成员注解（如 {@code @NotBlank("x")}）等价于 value 属性；
     * 普通命名属性（如 {@code required = false}）按名匹配；标记注解无属性。
     */
    public static Optional<Expression> attrValue(AnnotationExpr annotation, String attr) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return "value".equals(attr) ? Optional.of(single.getMemberValue()) : Optional.empty();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(attr)) {
                    return Optional.of(pair.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 把属性表达式解析为字符串列表（字符串字面量 / 字符串数组递归展开）。
     */
    public static List<String> stringArrayOf(Expression expr) {
        if (expr instanceof ArrayInitializerExpr array) {
            List<String> result = new ArrayList<>();
            for (Expression item : array.getValues()) {
                result.addAll(stringArrayOf(item));
            }
            return result;
        }
        return List.of(literalToString(expr));
    }

    /**
     * 表达式 → 字符串值：字符串字面量取内容，其余取源码文本（兼容带引号文本）。
     */
    public static String literalToString(Expression expr) {
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().asString();
        }
        String text = expr.toString().trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
