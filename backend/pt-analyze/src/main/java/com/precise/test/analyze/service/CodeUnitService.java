package com.precise.test.analyze.service;

import com.precise.test.analyze.entity.CodeUnit;

import java.util.List;

/**
 * 代码单元服务（占位，M2 实现）
 * <p>目标：提取被测项目的方法级代码单元，为「用例-代码关联」提供被关联对象。</p>
 */
public interface CodeUnitService {

    /** 查询项目下的全部代码单元 */
    List<CodeUnit> listByProject(Long projectId);

    /** 根据代码单元 ID 查询 */
    CodeUnit getById(Long id);

    /**
     * 从被测项目导入代码单元
     *
     * <p>TODO(M2): 解析被测项目源码，提取方法级代码单元并计算 codeHash。</p>
     *
     * @param projectId 项目 ID
     * @return 导入的代码单元数量
     */
    int importFromProject(Long projectId);
}
