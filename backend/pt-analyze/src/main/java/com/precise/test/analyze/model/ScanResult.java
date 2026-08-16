package com.precise.test.analyze.model;

import com.precise.test.analyze.entity.ApiDefinition;

import java.util.List;

/**
 * 一次扫描的完整结果：接口定义列表 + 扫描过程警告。
 *
 * <p>警告用于排查被跳过的文件/方法（如语法错误、无法解析的注解），
 * 记录警告不阻断整体扫描——扫描永远是"尽力而为"的。
 *
 * @param apis     识别出的接口定义列表（可能为空，但不会为 null）
 * @param warnings 扫描过程中的警告信息（按出现顺序）
 */
public record ScanResult(List<ApiDefinition> apis, List<String> warnings) {
}
