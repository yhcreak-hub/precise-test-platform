package com.precise.test.mapping.service;

import com.precise.test.mapping.entity.CaseCodeMapping;

import java.util.List;

/**
 * 用例-代码关联服务（占位，M4 实现）
 * <p>目标：建立用例与代码单元的关联关系（直接调用 / 调用链 / 推断），
 * 支撑「精准测试」的回溯定位与变更影响分析。</p>
 */
public interface CaseCodeMappingService {

    /** 查询某用例关联的全部代码单元映射 */
    List<CaseCodeMapping> listByTestCase(Long testCaseId);

    /** 查询某代码单元被哪些用例关联 */
    List<CaseCodeMapping> listByCodeUnit(Long codeUnitId);

    /**
     * 建立用例-代码关联
     *
     * <p>M4 实现：通过静态分析（用例→接口→Controller 方法）建立关联。</p>
     *
     * @param testCaseId   用例 ID
     * @param codeUnitId   代码单元 ID
     * @param relationType 关联类型：direct/called
     * @param confidence   置信度：static/dynamic
     * @return 新建的关联数量
     */
    int buildMapping(Long testCaseId, Long codeUnitId, String relationType, String confidence);

    /**
     * 为项目下所有用例建立「用例 → 接口 → 代码单元」映射（M4）
     *
     * @param projectId 项目 ID
     * @return 新建的关联数量
     */
    int buildMappingForProject(Long projectId);

    /**
     * 为指定接口下的用例建立映射（M5 增强：新生成用例即时关联）
     *
     * @param projectId       项目 ID
     * @param apiDefinitionId 接口定义 ID
     * @return 新建的关联数量
     */
    int buildMappingForApi(Long projectId, Long apiDefinitionId);
}
