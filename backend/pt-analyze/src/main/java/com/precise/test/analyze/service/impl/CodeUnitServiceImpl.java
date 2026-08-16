package com.precise.test.analyze.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.entity.CodeUnit;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.analyze.mapper.CodeUnitMapper;
import com.precise.test.analyze.service.CodeUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 代码单元服务实现（M4）
 * <p>从接口定义（api_definition）提取方法级代码单元：
 * 每个接口的 Controller 类 + 方法 作为一个代码单元（className.methodName），
 * codeHash 用 SHA-256 对「类名+方法名」计算，作为变更检测与关联的稳定标识。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeUnitServiceImpl implements CodeUnitService {

    private final CodeUnitMapper codeUnitMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;

    @Override
    public List<CodeUnit> listByProject(Long projectId) {
        return codeUnitMapper.selectList(new LambdaQueryWrapper<CodeUnit>()
                .eq(CodeUnit::getProjectId, projectId)
                .orderByAsc(CodeUnit::getClassName, CodeUnit::getMethodName));
    }

    @Override
    public CodeUnit getById(Long id) {
        return codeUnitMapper.selectById(id);
    }

    @Override
    public int importFromProject(Long projectId) {
        List<ApiDefinition> apis = apiDefinitionMapper.selectList(
                new LambdaQueryWrapper<ApiDefinition>().eq(ApiDefinition::getProjectId, projectId));
        int imported = 0;
        for (ApiDefinition api : apis) {
            if (api.getControllerClass() == null || api.getControllerMethod() == null) {
                continue;
            }
            String className = api.getControllerClass();
            String methodName = api.getControllerMethod();
            // 去重：同一 类+方法 只保留一个代码单元
            Long exists = codeUnitMapper.selectCount(new LambdaQueryWrapper<CodeUnit>()
                    .eq(CodeUnit::getProjectId, projectId)
                    .eq(CodeUnit::getClassName, className)
                    .eq(CodeUnit::getMethodName, methodName));
            if (exists != null && exists > 0) {
                continue;
            }
            CodeUnit unit = new CodeUnit();
            unit.setProjectId(projectId);
            unit.setClassName(className);
            unit.setMethodName(methodName);
            unit.setSignature(className + "." + methodName);
            unit.setFilePath(api.getFilePath());
            unit.setLineNo(api.getLineNo());
            unit.setCodeHash(sha256(className + "." + methodName));
            codeUnitMapper.insert(unit);
            imported++;
        }
        log.info("项目 {} 代码单元导入完成：新增 {} 个（共 {} 个接口）", projectId, imported, apis.size());
        return imported;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
