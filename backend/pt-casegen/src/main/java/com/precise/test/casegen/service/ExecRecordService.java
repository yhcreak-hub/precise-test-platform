package com.precise.test.casegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.precise.test.casegen.engine.CaseExecutor;
import com.precise.test.casegen.entity.ExecRecord;
import com.precise.test.casegen.entity.ExecRecordDetail;

import java.util.List;

/**
 * 用例执行记录服务
 * <p>记录批量执行（如变更分析后的精准回归）的结果，支持批次列表与明细报告查询。</p>
 */
public interface ExecRecordService {

    /**
     * 批量执行用例并记录结果
     *
     * @param projectId   项目 ID
     * @param baseUrl     被测服务地址
     * @param caseIds     用例 ID 列表
     * @param source      触发来源（manual/change_analysis/all）
     * @param baseVersion 基线版本（可空）
     * @param nowVersion  当前版本（可空）
     * @return 执行批次记录
     */
    ExecRecord executeBatch(Long projectId, String baseUrl, List<Long> caseIds,
                            String source, String baseVersion, String nowVersion);

    /** 分页查询项目的执行记录 */
    Page<ExecRecord> pageByProject(Long projectId, int page, int size);

    /** 查询执行记录 */
    ExecRecord getById(Long id);

    /** 查询执行记录的明细列表 */
    List<ExecRecordDetail> listDetail(Long execRecordId);

    /** 单条用例执行（供单条执行时也记录，或复用） */
    CaseExecutor.ExecuteResult executeOne(Long testCaseId, String baseUrl);
}
