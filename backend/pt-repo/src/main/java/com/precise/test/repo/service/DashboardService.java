package com.precise.test.repo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.analyze.mapper.CodeUnitMapper;
import com.precise.test.casegen.entity.ExecRecord;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.mapper.ExecRecordMapper;
import com.precise.test.casegen.mapper.TestCaseMapper;
import com.precise.test.mapping.entity.CaseCodeMapping;
import com.precise.test.mapping.mapper.CaseCodeMappingMapper;
import com.precise.test.repo.dto.DashboardStats;
import com.precise.test.repo.entity.Project;
import com.precise.test.repo.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectMapper projectMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestCaseMapper testCaseMapper;
    private final CodeUnitMapper codeUnitMapper;
    private final CaseCodeMappingMapper caseCodeMappingMapper;
    private final ExecRecordMapper execRecordMapper;

    public DashboardStats getSummary() {
        long projectCount = projectMapper.selectCount(null);
        long apiCount = apiDefinitionMapper.selectCount(null);
        long caseCount = testCaseMapper.selectCount(null);
        long codeUnitCount = codeUnitMapper.selectCount(null);
        long mappingCount = caseCodeMappingMapper.selectCount(null);
        long execCount = execRecordMapper.selectCount(null);

        // 最近 7 条执行记录（趋势）
        List<ExecRecord> recent = execRecordMapper.selectList(
                new LambdaQueryWrapper<ExecRecord>()
                        .orderByDesc(ExecRecord::getId)
                        .last("LIMIT 7"));
        List<DashboardStats.RecentExec> recentExecs = new ArrayList<>();
        for (ExecRecord r : recent) {
            recentExecs.add(DashboardStats.RecentExec.builder()
                    .id(r.getId())
                    .source(r.getSource())
                    .total(r.getTotal())
                    .passed(r.getPassed())
                    .costMs(r.getCostMs())
                    .createdAt(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString())
                    .build());
        }

        // 最近通过率
        double passRate = 0;
        if (!recentExecs.isEmpty()) {
            int total = 0, passed = 0;
            for (DashboardStats.RecentExec e : recentExecs) {
                total += e.getTotal();
                passed += e.getPassed();
            }
            passRate = total == 0 ? 0 : Math.round(passed * 1000.0 / total) / 10.0;
        }

        return DashboardStats.builder()
                .projectCount(projectCount)
                .apiCount(apiCount)
                .caseCount(caseCount)
                .mappingCount(mappingCount)
                .execRecordCount(execCount)
                .recentPassRate(passRate)
                .recentExecs(recentExecs)
                .build();
    }
}
