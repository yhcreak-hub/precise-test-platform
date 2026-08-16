package com.precise.test.casegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.casegen.engine.CaseExecutor;
import com.precise.test.casegen.entity.ExecRecord;
import com.precise.test.casegen.entity.ExecRecordDetail;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.mapper.ExecRecordDetailMapper;
import com.precise.test.casegen.mapper.ExecRecordMapper;
import com.precise.test.casegen.mapper.TestCaseMapper;
import com.precise.test.casegen.service.ExecRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用例执行记录服务实现
 * <p>批量执行用例（真实 HTTP + 断言），将每条结果落库到
 * exec_record（批次统计）与 exec_record_detail（明细报告）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecRecordServiceImpl implements ExecRecordService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecRecordMapper execRecordMapper;
    private final ExecRecordDetailMapper execRecordDetailMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final CaseExecutor caseExecutor;

    @Override
    public ExecRecord executeBatch(Long projectId, String baseUrl, List<Long> caseIds,
                                   String source, String baseVersion, String nowVersion) {
        long start = System.currentTimeMillis();

        // 1. 创建批次记录
        ExecRecord record = new ExecRecord();
        record.setProjectId(projectId);
        record.setSource(source == null ? "manual" : source);
        record.setBaseVersion(baseVersion);
        record.setNowVersion(nowVersion);
        record.setTotal(caseIds == null ? 0 : caseIds.size());
        record.setPassed(0);
        record.setFailed(0);
        record.setErrorCount(0);
        record.setCostMs(0L);
        execRecordMapper.insert(record);

        // 2. 逐条执行并记录明细
        int passed = 0, failed = 0, errorCount = 0;
        if (caseIds != null) {
            for (Long caseId : caseIds) {
                ExecRecordDetail detail = new ExecRecordDetail();
                detail.setExecRecordId(record.getId());
                detail.setTestCaseId(caseId);
                try {
                    TestCase tc = testCaseMapper.selectById(caseId);
                    if (tc == null) {
                        detail.setStatus("ERROR");
                        detail.setErrorMsg("用例不存在");
                        errorCount++;
                        execRecordDetailMapper.insert(detail);
                        continue;
                    }
                    detail.setCaseTitle(tc.getTitle());
                    ApiDefinition api = apiDefinitionMapper.selectById(tc.getApiDefinitionId());
                    detail.setApiPath(api != null ? api.getApiPath() : "");
                    detail.setRequestJson(tc.getRequestJson());

                    CaseExecutor.ExecuteResult result = caseExecutor.execute(tc, api, baseUrl);
                    detail.setStatus(result.getStatus());
                    detail.setHttpStatus(result.getHttpStatus());
                    detail.setResponseBody(truncate(result.getResponseBody(), 2000));
                    detail.setAssertDetails(toJson(result.getAssertDetails()));
                    detail.setErrorMsg(result.getErrorMsg());
                    detail.setCostMs(result.getCostMs());

                    switch (result.getStatus()) {
                        case "PASS" -> passed++;
                        case "FAIL" -> failed++;
                        default -> errorCount++;
                    }
                } catch (Exception e) {
                    detail.setStatus("ERROR");
                    detail.setErrorMsg(e.getMessage());
                    errorCount++;
                }
                execRecordDetailMapper.insert(detail);
            }
        }

        // 3. 更新批次统计
        record.setPassed(passed);
        record.setFailed(failed);
        record.setErrorCount(errorCount);
        record.setCostMs(System.currentTimeMillis() - start);
        execRecordMapper.updateById(record);
        log.info("批量执行完成: recordId={}, total={}, passed={}, failed={}, error={}",
                record.getId(), record.getTotal(), passed, failed, errorCount);
        return record;
    }

    @Override
    public Page<ExecRecord> pageByProject(Long projectId, int page, int size) {
        return execRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExecRecord>()
                        .eq(ExecRecord::getProjectId, projectId)
                        .orderByDesc(ExecRecord::getId));
    }

    @Override
    public ExecRecord getById(Long id) {
        return execRecordMapper.selectById(id);
    }

    @Override
    public List<ExecRecordDetail> listDetail(Long execRecordId) {
        return execRecordDetailMapper.selectList(new LambdaQueryWrapper<ExecRecordDetail>()
                .eq(ExecRecordDetail::getExecRecordId, execRecordId)
                .orderByAsc(ExecRecordDetail::getId));
    }

    @Override
    public CaseExecutor.ExecuteResult executeOne(Long testCaseId, String baseUrl) {
        TestCase tc = testCaseMapper.selectById(testCaseId);
        if (tc == null) {
            return null;
        }
        ApiDefinition api = apiDefinitionMapper.selectById(tc.getApiDefinitionId());
        return caseExecutor.execute(tc, api, baseUrl);
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
