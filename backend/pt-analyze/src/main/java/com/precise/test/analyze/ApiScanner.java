package com.precise.test.analyze;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.model.AnnotationUtils;
import com.precise.test.analyze.model.ScanResult;
import com.precise.test.analyze.model.TypeIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 接口自动识别引擎入口（M2 里程碑核心服务）。
 *
 * <p>职责：遍历被测项目源码根目录下所有 {@code .java} 文件，用 JavaParser 静态解析，
 * 识别带 {@code @RestController} / {@code @Controller} 注解的类，解析其映射方法
 * （@GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping / @RequestMapping），
 * 组装为结构化 {@link ApiDefinition} 列表。
 *
 * <p>容错策略：
 * <ul>
 *   <li>单个文件解析失败（语法错误/编码问题）不影响整体扫描，仅记录警告；</li>
 *   <li>单个方法组装失败不影响同文件其他方法，仅记录警告；</li>
 *   <li>扫描永远返回结果（可能为空），异常通过 {@link ScanResult#warnings()} 暴露。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * ApiScanner scanner = new ApiScanner();
 * List<ApiDefinition> apis = scanner.scan(1L, Path.of("/path/to/project"));
 * }</pre>
 */
public class ApiScanner {

    /** 是否忽略被 {@code @Deprecated} 标记的方法（默认 true，避免把废弃接口带入用例生成）。 */
    private boolean ignoreDeprecated = true;

    private final JavaParser javaParser;
    private final ApiDefinitionBuilder builder = new ApiDefinitionBuilder();

    public ApiScanner() {
        this(new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)));
    }

    /**
     * 允许注入自定义 {@link JavaParser}（如后续需要符号解析、自定义词法配置时）。
     *
     * @param javaParser 解析器实例
     */
    public ApiScanner(JavaParser javaParser) {
        this.javaParser = Objects.requireNonNull(javaParser, "javaParser");
    }

    public boolean isIgnoreDeprecated() {
        return ignoreDeprecated;
    }

    public void setIgnoreDeprecated(boolean ignoreDeprecated) {
        this.ignoreDeprecated = ignoreDeprecated;
    }

    /**
     * 扫描被测项目源码根目录，返回接口定义列表（便捷入口，忽略警告信息）。
     *
     * @param projectId  被测项目 ID（与统一模型 {@link ApiDefinition#getProjectId()} 一致为 Long）
     * @param sourceRoot 被测项目源码根目录（递归扫描其中所有 .java 文件）
     * @return 接口定义列表；目录不存在或扫描失败时返回空列表
     */
    public List<ApiDefinition> scan(Long projectId, Path sourceRoot) {
        return scanDetailed(projectId, sourceRoot).apis();
    }

    /**
     * 扫描被测项目源码根目录，返回接口定义列表 + 扫描过程警告。
     *
     * @param projectId  被测项目 ID
     * @param sourceRoot 被测项目源码根目录
     * @return 扫描结果（apis 与 warnings 均不为 null）
     */
    public ScanResult scanDetailed(Long projectId, Path sourceRoot) {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        List<ParsedUnit> units = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        TypeIndex index = new TypeIndex();

        // ---- 第一阶段：递归收集并解析全部 .java 文件，同时建立全量类型索引 ----
        // 索引必须先于接口组装完成：@RequestBody DTO 可能定义在任意文件中（跨文件解析）。
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            javaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            warnings.add("遍历源码目录失败: " + sourceRoot + " -> " + e.getMessage());
            return new ScanResult(List.of(), warnings);
        }

        for (Path file : javaFiles) {
            try {
                ParseResult<CompilationUnit> result = javaParser.parse(file);
                if (result.getResult().isPresent()) {
                    CompilationUnit cu = result.getResult().get();
                    units.add(new ParsedUnit(cu, relativePath(sourceRoot, file)));
                    index.index(cu);
                }
                for (Problem problem : result.getProblems()) {
                    warnings.add("解析告警 " + file + ": " + problem.getMessage());
                }
            } catch (Exception e) {
                // 单个文件失败不中断整体扫描
                warnings.add("解析失败 " + file + ": " + e.getMessage());
            }
        }

        // ---- 第二阶段：遍历所有 Controller 类的方法，组装接口定义 ----
        List<ApiDefinition> apis = new ArrayList<>();
        for (ParsedUnit unit : units) {
            for (ClassOrInterfaceDeclaration controller : findControllers(unit.cu())) {
                for (MethodDeclaration method : methodsOf(controller)) {
                    try {
                        apis.addAll(builder.build(projectId, unit.cu(), controller, method, index,
                                unit.filePath(), ignoreDeprecated));
                    } catch (Exception e) {
                        warnings.add("组装接口失败 " + TypeIndex.qualifiedName(unit.cu(), controller)
                                + "#" + method.getNameAsString() + ": " + e.getMessage());
                    }
                }
            }
        }
        return new ScanResult(List.copyOf(apis), List.copyOf(warnings));
    }

    /**
     * 便捷方法：直接解析一段源码字符串（测试 / 单文件快速识别场景）。
     *
     * <p>与 {@link #scanDetailed(Long, Path)} 的差异：不产生文件路径信息（filePath 为 null），
     * 且警告直接忽略（面向测试与交互式使用）。
     *
     * @param projectId  被测项目 ID
     * @param sourceCode Java 源码字符串（可包含多个顶层类型，如 Controller + DTO）
     * @return 接口定义列表
     */
    public List<ApiDefinition> scanSource(Long projectId, String sourceCode) {
        Objects.requireNonNull(sourceCode, "sourceCode");
        ParseResult<CompilationUnit> result = javaParser.parse(sourceCode);
        if (result.getResult().isEmpty()) {
            return List.of();
        }
        CompilationUnit cu = result.getResult().get();
        TypeIndex index = new TypeIndex();
        index.index(cu);

        List<ApiDefinition> apis = new ArrayList<>();
        for (ClassOrInterfaceDeclaration controller : findControllers(cu)) {
            for (MethodDeclaration method : methodsOf(controller)) {
                try {
                    apis.addAll(builder.build(projectId, cu, controller, method, index, null, ignoreDeprecated));
                } catch (Exception e) {
                    // 单方法失败跳过（字符串扫描场景无需对外暴露警告）
                }
            }
        }
        return List.copyOf(apis);
    }

    /** 已解析的编译单元 + 其源码文件相对路径。 */
    private record ParsedUnit(CompilationUnit cu, String filePath) {
    }

    /**
     * 查找带 {@code @RestController} 或 {@code @Controller} 注解的类（含嵌套类，跳过接口）。
     * 注意：按简单名匹配注解，若项目自定义了同名注解可能误判（真实项目罕见，可接受）。
     */
    private List<ClassOrInterfaceDeclaration> findControllers(CompilationUnit cu) {
        List<ClassOrInterfaceDeclaration> controllers = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (type.isInterface()) {
                continue;
            }
            if (AnnotationUtils.findAnnotation(type, "Controller", "RestController").isPresent()) {
                controllers.add(type);
            }
        }
        return controllers;
    }

    /** 类声明中的方法列表（过滤非方法成员；JavaParser 无 getMethods() 便捷方法，手动收集）。 */
    private List<MethodDeclaration> methodsOf(ClassOrInterfaceDeclaration type) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof MethodDeclaration method) {
                methods.add(method);
            }
        }
        return methods;
    }

    /**
     * 计算源码文件的相对路径（相对扫描根目录，统一 '/' 分隔符）。
     * 文件不在根目录下（如符号链接）时退化为文件名。
     */
    private String relativePath(Path sourceRoot, Path file) {
        try {
            return sourceRoot.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }
}
