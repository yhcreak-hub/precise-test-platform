package com.precise.test.casegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.casegen.engine.CaseExecutor;
import com.precise.test.casegen.engine.RuleCaseGenerator;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.mapper.TestCaseMapper;
import com.precise.test.casegen.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用例服务实现（M3/M4）
 * <p>核心能力：</p>
 * <ul>
 *   <li>规则引擎：基于接口 Schema 自动生成 normal/required/boundary/exception 四类用例；</li>
 *   <li>测试空洞检测：统计项目中「有接口定义但无用例」的接口；</li>
 *   <li>用例编辑：手动修改入参与断言；</li>
 *   <li>用例执行：真实 HTTP 请求 + 断言校验。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final RuleCaseGenerator ruleCaseGenerator;
    private final CaseExecutor caseExecutor;

    @Override
    public Page<TestCase> pageByProject(Long projectId, int page, int size) {
        return testCaseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProjectId, projectId)
                        .orderByDesc(TestCase::getId));
    }

    @Override
    public TestCase getById(Long id) {
        return testCaseMapper.selectById(id);
    }

    @Override
    public Map<Long, List<TestCase>> groupByApi(Long projectId) {
        List<TestCase> all = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProjectId, projectId)
                        .orderByAsc(TestCase::getApiDefinitionId, TestCase::getId));
        Map<Long, List<TestCase>> grouped = new LinkedHashMap<>();
        for (TestCase tc : all) {
            grouped.computeIfAbsent(tc.getApiDefinitionId(), k -> new java.util.ArrayList<>()).add(tc);
        }
        return grouped;
    }

    @Override
    public TestCase updateCase(Long id, String title, String requestJson, String assertsJson, String headersJson) {
        TestCase tc = testCaseMapper.selectById(id);
        if (tc == null) {
            return null;
        }
        if (title != null) {
            tc.setTitle(title);
        }
        if (requestJson != null) {
            tc.setRequestJson(requestJson);
        }
        if (assertsJson != null) {
            tc.setAssertsJson(assertsJson);
        }
        if (headersJson != null) {
            tc.setHeadersJson(headersJson);
        }
        tc.setSource("manual");
        testCaseMapper.updateById(tc);
        log.info("用例已编辑: id={}, title={}", id, tc.getTitle());
        return tc;
    }

    @Override
    public CaseExecutor.ExecuteResult executeCase(Long id, String baseUrl) {
        TestCase tc = testCaseMapper.selectById(id);
        if (tc == null) {
            CaseExecutor.ExecuteResult err = new CaseExecutor.ExecuteResult();
            err.setCaseId(id);
            err.setStatus("ERROR");
            err.setErrorMsg("用例不存在");
            return err;
        }
        ApiDefinition api = apiDefinitionMapper.selectById(tc.getApiDefinitionId());
        if (api == null) {
            CaseExecutor.ExecuteResult err = new CaseExecutor.ExecuteResult();
            err.setCaseId(id);
            err.setStatus("ERROR");
            err.setErrorMsg("接口定义不存在");
            return err;
        }
        return caseExecutor.execute(tc, api, baseUrl);
    }

    @Override
    public Long generateCases(Long apiDefinitionId, String scenarioType) {
        ApiDefinition api = apiDefinitionMapper.selectById(apiDefinitionId);
        if (api == null) {
            return 0L;
        }
        List<TestCase> generated = ruleCaseGenerator.generateForApi(api, api.getProjectId());
        // 按场景过滤（null 表示生成全部场景）
        List<TestCase> filtered = generated.stream()
                .filter(tc -> scenarioType == null || scenarioType.equals(tc.getScenarioType()))
                .toList();
        saveGenerated(filtered);
        return (long) filtered.size();
    }

    @Override
    public int saveGenerated(List<TestCase> cases) {
        int saved = 0;
        for (TestCase tc : cases) {
            testCaseMapper.insert(tc);
            saved++;
        }
        return saved;
    }

    /**
     * 测试空洞检测：统计项目下「有接口定义但无用例覆盖」的接口
     *
     * @param projectId 项目 ID
     * @return 空洞信息（空洞接口数 / 接口总数 / 空洞接口 id 列表）
     */
    public GapReport detectGap(Long projectId) {
        List<ApiDefinition> apis = apiDefinitionMapper.selectList(
                new LambdaQueryWrapper<ApiDefinition>().eq(ApiDefinition::getProjectId, projectId));
        int total = apis.size();
        int gapCount = 0;
        for (ApiDefinition api : apis) {
            Long caseCount = testCaseMapper.selectCount(new LambdaQueryWrapper<TestCase>()
                    .eq(TestCase::getApiDefinitionId, api.getId()));
            if (caseCount == null || caseCount == 0) {
                gapCount++;
            }
        }
        return GapReport.builder()
                .totalApis(total)
                .gapApis(gapCount)
                .gapRate(total == 0 ? 0.0 : Math.round(gapCount * 1000.0 / total) / 10.0)
                .build();
    }

    /**
     * 为项目下所有接口生成用例（先空洞检测 → 对空洞接口生成 → 返回生成数）
     *
     * @param projectId 项目 ID
     * @return 本次生成的用例总数
     */
    public int generateForProject(Long projectId) {
        List<ApiDefinition> apis = apiDefinitionMapper.selectList(
                new LambdaQueryWrapper<ApiDefinition>().eq(ApiDefinition::getProjectId, projectId));
        int total = 0;
        for (ApiDefinition api : apis) {
            Long caseCount = testCaseMapper.selectCount(new LambdaQueryWrapper<TestCase>()
                    .eq(TestCase::getApiDefinitionId, api.getId()));
            if (caseCount == null || caseCount == 0) {
                List<TestCase> generated = ruleCaseGenerator.generateForApi(api, projectId);
                total += saveGenerated(generated);
            }
        }
        log.info("项目 {} 用例生成完成：新增 {} 条", projectId, total);
        return total;
    }
}
