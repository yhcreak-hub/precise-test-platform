package com.precise.test.analyze.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 版本变更分析工具（M5）
 * <p>对比被测项目两个版本（分支/提交），提取变更的 Java 文件，
 * 并解析出变更文件中的 Controller 类全名（用于匹配代码单元）。</p>
 */
@Slf4j
public final class GitDiffAnalyzer {

    /** 变更文件信息 */
    @Data
    public static class ChangedFile {
        /** 变更类型：A 新增 / M 修改 / D 删除 */
        private String changeType;
        /** 文件路径（相对仓库根） */
        private String filePath;
    }

    private GitDiffAnalyzer() {
    }

    /**
     * 对比两个版本，返回变更文件列表（仅 Java 文件）
     *
     * @param repoRoot    仓库根目录（已 clone）
     * @param baseVersion 基线版本（分支/commit）
     * @param nowVersion  当前版本
     * @return 变更文件列表
     */
    public static List<ChangedFile> diffJavaFiles(Path repoRoot, String baseVersion, String nowVersion)
            throws IOException, InterruptedException {
        String base = resolveRef(repoRoot, baseVersion);
        String now = resolveRef(repoRoot, nowVersion);
        ProcessBuilder pb = new ProcessBuilder("git", "diff", "--name-status", base, now);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git diff 失败(exit=" + exitCode + "): " + output);
        }

        List<ChangedFile> result = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 格式：M\tsrc/main/java/.../XxxController.java  或  A\t...  或  D\t...
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }
            String type = parts[0];
            String filePath = parts[1];
            if (filePath.endsWith(".java")) {
                ChangedFile cf = new ChangedFile();
                cf.setChangeType(type);
                cf.setFilePath(filePath);
                result.add(cf);
            }
        }
        log.info("版本对比 {}..{} 完成：变更 Java 文件 {} 个", base, now, result.size());
        return result;
    }

    /**
     * 解析版本引用：本地无对应分支时自动补 origin/ 前缀（全量 clone 后远程分支在 origin 引用下）
     */
    public static String resolveRef(Path repoRoot, String version) throws IOException, InterruptedException {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("版本不能为空");
        }
        // 本地已有该引用（分支/tag/commit 均可解析）
        ProcessBuilder check = new ProcessBuilder("git", "rev-parse", "--verify", "--quiet", version);
        check.directory(repoRoot.toFile());
        Process cp = check.start();
        String checkOut = new String(cp.getInputStream().readAllBytes());
        if (cp.waitFor() == 0 && !checkOut.isBlank()) {
            return version;
        }
        // 尝试远程引用 origin/{version}
        String remoteRef = "origin/" + version;
        ProcessBuilder checkRemote = new ProcessBuilder("git", "rev-parse", "--verify", "--quiet", remoteRef);
        checkRemote.directory(repoRoot.toFile());
        Process crp = checkRemote.start();
        String crOut = new String(crp.getInputStream().readAllBytes());
        if (crp.waitFor() == 0 && !crOut.isBlank()) {
            return remoteRef;
        }
        throw new IOException("版本无法解析: " + version);
    }

    /**
     * 从变更 Java 文件内容解析 Controller 类全名
     * <p>简单解析：读取 package 声明 + 查找 @RestController/@Controller 类的类名。</p>
     *
     * @param repoRoot    仓库根目录
     * @param changedFile 变更文件
     * @return Controller 类全名（package.ClassName），非 Controller 返回 null
     */
    public static String resolveControllerClass(Path repoRoot, ChangedFile changedFile) {
        // 删除的文件无法读取内容，按路径推断类名（尽力而为）
        if ("D".equals(changedFile.getChangeType())) {
            return inferClassFromPath(changedFile.getFilePath());
        }
        Path file = repoRoot.resolve(changedFile.getFilePath());
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // 是否 Controller
            if (!content.contains("@RestController") && !content.contains("@Controller")) {
                return null;
            }
            // 解析包名
            String packageName = null;
            Pattern pkgPattern = Pattern.compile("package\\s+([\\w.]+);");
            Matcher pkgMatcher = pkgPattern.matcher(content);
            if (pkgMatcher.find()) {
                packageName = pkgMatcher.group(1);
            }
            // 找类名（包含 Controller 关键字，排除 @interface/enum）
            Pattern classPattern = Pattern.compile("public\\s+class\\s+(\\w+)");
            Matcher classMatcher = classPattern.matcher(content);
            String className = null;
            while (classMatcher.find()) {
                String name = classMatcher.group(1);
                if (name.contains("Controller")) {
                    className = name;
                    break;
                }
            }
            if (className == null) {
                return null;
            }
            return packageName == null ? className : packageName + "." + className;
        } catch (IOException e) {
            log.warn("读取变更文件失败: {}", changedFile.getFilePath(), e);
            return null;
        }
    }

    /** 从文件路径推断类全名（删除文件场景）：src/main/java/com/x/XController.java -> com.x.XController */
    private static String inferClassFromPath(String filePath) {
        int idx = filePath.indexOf("/java/");
        if (idx < 0) {
            return null;
        }
        String path = filePath.substring(idx + "/java/".length());
        if (path.endsWith(".java")) {
            path = path.substring(0, path.length() - ".java".length());
        }
        return path.replace('/', '.');
    }
}
