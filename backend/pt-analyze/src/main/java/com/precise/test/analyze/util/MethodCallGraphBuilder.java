package com.precise.test.analyze.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 方法调用关系图构建器（M5 增强）
 * <p>扫描被测项目全部源码，建立"方法调用"索引，支持调用链影响面分析：</p>
 * <pre>
 *   callerMap: key = 被调用方法(类全名.方法名), value = 调用它的方法列表
 * </pre>
 * <p>当 Service/DAO/Util 方法变更时，通过反向查询找到"谁调用了它"，
 * 逐层向上直到 Controller 方法，从而定位受影响的接口。</p>
 *
 * <p>限制：不做符号级精确解析（同名方法/重载/多态按尽力而为处理），
 * 变量类型通过声明推断（局部变量/参数/字段/方法返回值）。</p>
 */
@Slf4j
public class MethodCallGraphBuilder {

    private final JavaParser javaParser = new JavaParser();

    /** 调用方类全名 -> 该类的方法名集合（用于识别方法归属） */
    private final Map<String, ClassInfo> classIndex = new HashMap<>();

    /** 被调用方法(类.方法) -> 调用它的方法列表(类.方法) */
    private final Map<String, List<String>> reverseCalls = new HashMap<>();

    /** 类信息 */
    private static class ClassInfo {
        String qualifiedName;
        boolean isController;
    }

    /**
     * 扫描源码根目录，构建方法调用反向索引
     *
     * @param sourceRoot 被测项目源码根目录
     */
    public void build(Path sourceRoot) {
        // 第一遍：收集所有类的全限定名（含嵌套类归属）
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    CompilationUnit cu = javaParser.parse(content).getResult().orElse(null);
                    if (cu == null) {
                        return;
                    }
                    String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (!cls.isStatic() && !cls.isInterface() && !cls.isEnumDeclaration() && !cls.isAnnotationDeclaration()) {
                            String simple = cls.getNameAsString();
                            String qualified = pkg.isEmpty() ? simple : pkg + "." + simple;
                            ClassInfo info = new ClassInfo();
                            info.qualifiedName = qualified;
                            info.isController = cls.getAnnotations().stream()
                                    .anyMatch(a -> a.getNameAsString().contains("Controller"));
                            classIndex.put(qualified, info);
                            // 简单名 → 全限定（仅当无冲突）
                            classIndex.putIfAbsent(simple, info);
                        }
                    }
                } catch (Exception e) {
                    // 单文件失败不中断
                }
            });
        } catch (IOException e) {
            log.warn("扫描源码失败: {}", sourceRoot, e);
        }

        // 第二遍：解析每个类的方法体内的方法调用
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    CompilationUnit cu = javaParser.parse(content).getResult().orElse(null);
                    if (cu == null) {
                        return;
                    }
                    String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        String className = cls.getNameAsString();
                        String qualified = pkg.isEmpty() ? className : pkg + "." + className;
                        for (MethodDeclaration method : cls.getMethods()) {
                            String callerKey = qualified + "." + method.getNameAsString();
                            List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class);
                            for (MethodCallExpr call : calls) {
                                String calleeKey = resolveCallee(call, cls, qualified);
                                if (calleeKey != null) {
                                    reverseCalls.computeIfAbsent(calleeKey, k -> new ArrayList<>()).add(callerKey);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            });
        } catch (IOException e) {
            log.warn("扫描方法调用失败: {}", sourceRoot, e);
        }
        log.info("调用关系图构建完成: 类={} 反向调用边={}", classIndex.size(), reverseCalls.size());
    }

    /** 解析方法调用的被调用方（类.方法） */
    private String resolveCallee(MethodCallExpr call, ClassOrInterfaceDeclaration currentClass, String currentQualified) {
        String methodName = call.getNameAsString();
        Optional<com.github.javaparser.ast.expr.Expression> scope = call.getScope();
        String calleeQualified;
        if (scope.isEmpty()) {
            // 同类方法调用（无前缀）
            calleeQualified = currentQualified;
        } else {
            String expr = scope.get().toString();
            // 变量名 → 类型：查局部变量声明、参数、字段（尽力而为）
            calleeQualified = inferType(expr, currentClass, currentQualified);
            if (calleeQualified == null) {
                return null;
            }
        }
        return calleeQualified + "." + methodName;
    }

    /** 从表达式推断类型全限定名（支持 this. / 变量名 / 嵌套访问） */
    private String inferType(String expr, ClassOrInterfaceDeclaration currentClass, String currentQualified) {
        // 去掉 this. / 链式调用的前缀，取最左对象
        String base = expr.replaceAll("^this\\.", "").split("\\.")[0];
        // 1. 局部变量声明类型
        Optional<String> localType = currentClass.getMethods().stream()
                .flatMap(m -> m.findAll(VariableDeclarationExpr.class).stream())
                .flatMap(v -> v.getVariables().stream())
                .filter(v -> v.getNameAsString().equals(base))
                .map(v -> v.getType().asString())
                .findFirst();
        if (localType.isPresent()) {
            return qualify(localType.get());
        }
        // 2. 字段类型
        Optional<String> fieldType = currentClass.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .filter(v -> v.getNameAsString().equals(base))
                .map(v -> v.getType().asString())
                .findFirst();
        if (fieldType.isPresent()) {
            return qualify(fieldType.get());
        }
        // 3. 方法参数类型
        Optional<String> paramType = currentClass.getMethods().stream()
                .flatMap(m -> m.getParameters().stream())
                .filter(p -> p.getNameAsString().equals(base))
                .map(p -> p.getType().asString())
                .findFirst();
        if (paramType.isPresent()) {
            return qualify(paramType.get());
        }
        return null;
    }

    /** 简单类型名 → 全限定名（无则按简单名处理） */
    private String qualify(String type) {
        String simple = type.replaceAll("[<>\\[\\], ]", "");
        if (simple.contains(".")) {
            return simple;
        }
        ClassInfo info = classIndex.get(simple);
        return info != null ? info.qualifiedName : simple;
    }

    /**
     * 反向查找：从变更方法出发，沿调用链向上找到所有 Controller 方法
     *
     * @param changedClass    变更类全限定名
     * @param changedMethods  变更的方法名集合（null = 该类全部方法）
     * @param maxDepth        最大向上深度
     * @return 受影响（调用到变更代码）的 Controller 方法列表（类.方法）
     */
    public List<String> findAffectedControllers(String changedClass, List<String> changedMethods, int maxDepth) {
        List<String> affected = new ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        // 初始：变更类的方法
        List<String> seeds = new ArrayList<>();
        ClassInfo info = classIndex.get(changedClass);
        if (info == null) {
            info = classIndex.get(changedClass.substring(changedClass.lastIndexOf('.') + 1));
        }
        if (info != null) {
            if (changedMethods == null || changedMethods.isEmpty()) {
                // 变更方法未知：以类为单位，从所有"调用该类方法"的调用者出发
                // 简化：直接把类名作为前缀匹配
                seeds.add(changedClass + ".#");
            } else {
                for (String m : changedMethods) {
                    seeds.add(changedClass + "." + m);
                }
            }
        } else {
            seeds.add(changedClass + ".#");
        }

        for (String seed : seeds) {
            queue.add(new Object[]{seed, 0});
        }

        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String callee = (String) item[0];
            int depth = (Integer) item[1];
            if (depth > maxDepth || !visited.add(callee)) {
                continue;
            }
            // 找出调用 callee 的方法
            List<String> callers = new ArrayList<>();
            if (callee.endsWith(".#")) {
                // 类级：匹配所有 该类.xxx 的反向边
                String prefix = callee.substring(0, callee.length() - 1);
                for (Map.Entry<String, List<String>> e : reverseCalls.entrySet()) {
                    if (e.getKey().startsWith(prefix)) {
                        callers.addAll(e.getValue());
                    }
                }
            } else {
                List<String> direct = reverseCalls.get(callee);
                if (direct != null) {
                    callers.addAll(direct);
                }
            }
            for (String caller : callers) {
                String callerClass = caller.substring(0, caller.lastIndexOf('.'));
                String callerMethod = caller.substring(caller.lastIndexOf('.') + 1);
                ClassInfo ci = classIndex.get(callerClass);
                if (ci == null) {
                    ci = classIndex.get(callerClass.substring(callerClass.lastIndexOf('.') + 1));
                }
                if (ci != null && ci.isController) {
                    affected.add(caller); // Controller 方法
                } else {
                    queue.add(new Object[]{caller, depth + 1});
                }
            }
        }
        return affected.stream().distinct().toList();
    }

    /** 判断某类是否为 Controller */
    public boolean isControllerClass(String className) {
        ClassInfo info = classIndex.get(className);
        if (info == null) {
            info = classIndex.get(className.substring(className.lastIndexOf('.') + 1));
        }
        return info != null && info.isController;
    }
}
