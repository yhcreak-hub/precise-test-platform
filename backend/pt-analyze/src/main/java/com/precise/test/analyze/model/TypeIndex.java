package com.precise.test.analyze.model;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 类型索引：收集被测项目源码中出现的全部类型声明（类/接口/枚举/record，含嵌套类型），
 * 按简单类名建立索引。
 *
 * <p>用途：
 * <ol>
 *   <li>解析 {@code @RequestBody} DTO 的字段结构（跨文件查找 DTO 类声明）；</li>
 *   <li>将简单类型名推导为全限定名（best-effort）。</li>
 * </ol>
 *
 * <p>限制：引擎不加载编译产物、不做符号解析，因此同名简单类名冲突时保留最先索引到的
 * 声明（真实项目同名 DTO 罕见，可接受；如需精确可引入 javaparser-symbol-solver）。
 */
public class TypeIndex {

    /**
     * 类型索引条目。
     *
     * @param qualifiedName 全限定名（包名 + 嵌套类型名链，如 com.example.dto.UserDTO）
     * @param declaration   类型声明 AST（DTO 字段解析用）
     */
    public record TypeEntry(String qualifiedName, TypeDeclaration<?> declaration) {
    }

    /** 简单类名 → 类型条目（LinkedHashMap 保持索引顺序，冲突时首个生效）。 */
    private final Map<String, TypeEntry> bySimpleName = new LinkedHashMap<>();

    /**
     * 将一个 CompilationUnit 中所有类型声明加入索引（含嵌套类型）。
     * 简单名冲突时保留首个（putIfAbsent）。
     *
     * @param cu 已解析的编译单元
     */
    public void index(CompilationUnit cu) {
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            String simpleName = type.getNameAsString();
            bySimpleName.putIfAbsent(simpleName, new TypeEntry(qualifiedName(cu, type), type));
        }
    }

    /**
     * 按简单类名查找类型声明（DTO 解析入口）。
     *
     * @param simpleName 简单类名，如 "UserCreateDTO"
     * @return 命中条目；未找到返回 {@link Optional#empty()}
     */
    public Optional<TypeEntry> find(String simpleName) {
        return Optional.ofNullable(bySimpleName.get(simpleName));
    }

    /**
     * 简单类名 → 全限定名（best-effort）。
     *
     * @param simpleName 简单类名
     * @return 全限定名；未找到返回 {@link Optional#empty()}
     */
    public Optional<String> qualifiedNameOf(String simpleName) {
        return find(simpleName).map(TypeEntry::qualifiedName);
    }

    /**
     * 计算类型声明的全限定名：包名 + 嵌套类型名链。
     * 例如 {@code package com.example.dto; class UserDTO} → {@code com.example.dto.UserDTO}。
     *
     * @param cu   编译单元（包名来源）
     * @param type 类型声明
     * @return 全限定名；无包名时仅返回类型名链
     */
    public static String qualifiedName(CompilationUnit cu, TypeDeclaration<?> type) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        Deque<String> names = new ArrayDeque<>();
        Node node = type;
        while (node instanceof TypeDeclaration<?> td) {
            names.addFirst(td.getNameAsString());
            node = td.getParentNode().orElse(null);
        }
        String className = String.join(".", names);
        return pkg.isEmpty() ? className : pkg + "." + className;
    }
}
