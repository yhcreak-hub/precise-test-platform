package com.precise.test.mapping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.entity.CodeUnit;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.analyze.mapper.CodeUnitMapper;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.mapper.TestCaseMapper;
import com.precise.test.mapping.entity.CaseCodeMapping;
import com.precise.test.mapping.mapper.CaseCodeMappingMapper;
import com.precise.test.mapping.service.CaseCodeMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用例-代码关联服务实现（M4）
 * <p>关联链路（静态分析）：</p>
 * <pre>
 *   test_case --(apiDefinitionId)--> api_definition --(controllerClass+controllerMethod)--> code_unit
 * </pre>
 * <ul>
 *   <li>relationType = direct（用例直接测的接口方法）</li>
 *   <li>confidence = static（静态分析；动态覆盖率回写为 dynamic 属二期增强）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseCodeMappingServiceImpl implements CaseCodeMappingService {

    private final CaseCodeMappingMapper caseCodeMappingMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final CodeUnitMapper codeUnitMapper;

    @Override
    public List<CaseCodeMapping> listByTestCase(Long testCaseId) {
        return caseCodeMappingMapper.selectList(new LambdaQueryWrapper<CaseCodeMapping>()
                .eq(CaseCodeMapping::getTestCaseId, testCaseId));
    }

    @Override
    public List<CaseCodeMapping> listByCodeUnit(Long codeUnitId) {
        return caseCodeMappingMapper.selectList(new LambdaQueryWrapper<CaseCodeMapping>()
                .eq(CaseCodeMapping::getCodeUnitId, codeUnitId));
    }

    @Override
    public int buildMapping(Long testCaseId, Long codeUnitId, String relationType, String confidence) {
        // 去重：同一 用例+代码单元 只建一条关联
        Long exists = caseCodeMappingMapper.selectCount(new LambdaQueryWrapper<CaseCodeMapping>()
                .eq(CaseCodeMapping::getTestCaseId, testCaseId)
                .eq(CaseCodeMapping::getCodeUnitId, codeUnitId));
        if (exists != null && exists > 0) {
            return 0;
        }
        CaseCodeMapping mapping = new CaseCodeMapping();
        mapping.setTestCaseId(testCaseId);
        mapping.setCodeUnitId(codeUnitId);
        mapping.setRelationType(relationType);
        mapping.setConfidence(confidence);
        caseCodeMappingMapper.insert(mapping);
        return 1;
    }

    /**
     * 为项目下所有用例建立「用例 → 接口 → 代码单元」映射
     *
     * @param projectId 项目 ID
     * @return 新建的关联数量
     */
    public int buildMappingForProject(Long projectId) {
        List<TestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, projectId));
        return buildMappingForCases(projectId, cases);
    }

    /**
     * 为指定接口下的用例建立「用例 → 接口 → 代码单元」映射
     * <p>用于：新生成的用例即时建立关联，确保后续变更分析能反查到。</p>
     *
     * @param projectId       项目 ID
     * @param apiDefinitionId 接口定义 ID
     * @return 新建的关联数量
     */
    public int buildMappingForApi(Long projectId, Long apiDefinitionId) {
        List<TestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProjectId, projectId)
                        .eq(TestCase::getApiDefinitionId, apiDefinitionId));
        return buildMappingForCases(projectId, cases);
    }

    /** 为用例集合建立映射（共用逻辑，去重幂等） */
    private int buildMappingForCases(Long projectId, List<TestCase> cases) {
        int total = 0;
        for (TestCase tc : cases) {
            ApiDefinition api = apiDefinitionMapper.selectById(tc.getApiDefinitionId());
            if (api == null || api.getControllerClass() == null || api.getControllerMethod() == null) {
                continue;
            }
            // 查找对应代码单元（类+方法）
            CodeUnit unit = codeUnitMapper.selectOne(new LambdaQueryWrapper<CodeUnit>()
                    .eq(CodeUnit::getProjectId, projectId)
                    .eq(CodeUnit::getClassName, api.getControllerClass())
                    .eq(CodeUnit::getMethodName, api.getControllerMethod())
                    .last("LIMIT 1"));
            if (unit == null) {
                continue;
            }
            total += buildMapping(tc.getId(), unit.getId(), "direct", "static");
        }
        log.info("用例-代码关联建立完成：处理 {} 个用例，新增 {} 条", cases.size(), total);
        return total;
    }
}
