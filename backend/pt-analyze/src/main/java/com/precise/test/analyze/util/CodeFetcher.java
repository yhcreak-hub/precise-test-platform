package com.precise.test.analyze.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 被测项目代码拉取工具（M2）
 * <p>通过 git clone 将被测项目源码拉取到本地临时目录，供接口识别引擎扫描。</p>
 */
@Slf4j
public final class CodeFetcher {

    /** 源码根目录：{user.home}/precise-test-repos/{projectId} */
    public static final Path REPO_ROOT = Path.of(System.getProperty("user.home"), "precise-test-repos");

    private CodeFetcher() {
    }

    /**
     * 拉取被测项目源码到本地
     *
     * @param projectId 项目 ID
     * @param gitUrl    Git 仓库地址（http/https/ssh 均可）
     * @param branch    分支
     * @return 源码根目录（已 clone 完成）
     * @throws IOException          拉取失败（网络/仓库不存在/分支不存在）
     * @throws InterruptedException 进程被中断
     */
    public static Path fetch(Long projectId, String gitUrl, String branch) throws IOException, InterruptedException {
        Path target = REPO_ROOT.resolve(String.valueOf(projectId));
        // 清空旧目录，保证每次扫描基于最新代码
        deleteRecursively(target);
        Files.createDirectories(target.getParent());

        log.info("拉取被测项目源码: projectId={}, branch={}, url={}", projectId, branch, gitUrl);
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", "-b", branch, gitUrl, target.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git clone 失败(exit=" + exitCode + "): " + output);
        }
        log.info("被测项目源码拉取完成: {}", target);
        return target;
    }

    /**
     * 完整拉取被测项目源码（不指定分支，可切换任意分支/提交对比）
     * <p>用于版本变更分析：需要同时访问 base 与 now 两个版本。</p>
     *
     * @param projectId 项目 ID
     * @param gitUrl    Git 仓库地址
     * @return 仓库根目录（默认分支工作区）
     */
    public static Path fetchFull(Long projectId, String gitUrl) throws IOException, InterruptedException {
        Path target = REPO_ROOT.resolve(String.valueOf(projectId));
        deleteRecursively(target);
        Files.createDirectories(target.getParent());

        log.info("完整拉取被测项目源码: projectId={}, url={}", projectId, gitUrl);
        ProcessBuilder pb = new ProcessBuilder("git", "clone", gitUrl, target.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git clone 失败(exit=" + exitCode + "): " + output);
        }
        log.info("被测项目源码完整拉取完成: {}", target);
        return target;
    }

    /** 在仓库目录内切换分支/提交（本地无该分支时自动使用 origin/ 远程引用） */
    public static void checkout(Path repoRoot, String version) throws IOException, InterruptedException {
        String resolved = GitDiffAnalyzer.resolveRef(repoRoot, version);
        log.info("切换版本: {} -> {}", repoRoot, resolved);
        ProcessBuilder pb = new ProcessBuilder("git", "checkout", resolved);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git checkout 失败(exit=" + exitCode + "): " + output);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", p, e);
                }
            });
        }
    }
}
