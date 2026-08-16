package com.precise.test.analyze.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.ApiScanner;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.analyze.service.ApiDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 接口定义服务实现（M2）
 * <p>核心能力：使用 {@link ApiScanner} 扫描被测项目源码目录，
 * 识别 Controller 接口并去重落库（api_definition 表）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionServiceImpl implements ApiDefinitionService {

    private final ApiDefinitionMapper apiDefinitionMapper;

    @Override
    public List<ApiDefinition> listByProject(Long projectId) {
        return apiDefinitionMapper.selectList(new LambdaQueryWrapper<ApiDefinition>()
                .eq(ApiDefinition::getProjectId, projectId)
                .orderByAsc(ApiDefinition::getApiPath, ApiDefinition::getHttpMethod));
    }

    @Override
    public boolean exists(Long projectId, String apiPath, String httpMethod) {
        Long count = apiDefinitionMapper.selectCount(new LambdaQueryWrapper<ApiDefinition>()
                .eq(ApiDefinition::getProjectId, projectId)
                .eq(ApiDefinition::getApiPath, apiPath)
                .eq(ApiDefinition::getHttpMethod, httpMethod));
        return count != null && count > 0;
    }

    @Override
    public int importFromProject(Long projectId, Path sourceRoot) {
        // 1. 使用 JavaParser 接口识别引擎扫描源码
        List<ApiDefinition> scanned = new ApiScanner().scan(projectId, sourceRoot);
        log.info("项目 {} 扫描完成，识别到 {} 个接口", projectId, scanned.size());

        // 2. 去重落库（项目 + 路径 + 方法 唯一）
        int imported = 0;
        for (ApiDefinition def : scanned) {
            if (!exists(projectId, def.getApiPath(), def.getHttpMethod())) {
                apiDefinitionMapper.insert(def);
                imported++;
            }
        }
        log.info("项目 {} 导入完成：新增 {} 个接口", projectId, imported);
        return imported;
    }

    @Override
    public ApiDefinition getById(Long id) {
        return apiDefinitionMapper.selectById(id);
    }
}
