package com.precise.test.analyze.service;

import com.precise.test.analyze.entity.ApiDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * 接口定义服务（M2 实现）
 * <p>目标：自动识别被测项目接口（解析 Controller 源码），
 * 建立接口定义并与代码单元建立关联。</p>
 */
public interface ApiDefinitionService {

    /** 查询项目下的全部接口定义 */
    List<ApiDefinition> listByProject(Long projectId);

    /** 判断项目下是否存在相同接口（项目 + 路径 + 方法） */
    boolean exists(Long projectId, String apiPath, String httpMethod);

    /**
     * 从被测项目源码目录导入接口定义（扫描 Controller → 落库）
     *
     * @param projectId  项目 ID
     * @param sourceRoot 被测项目源码根目录（已 clone 到本地）
     * @return 新导入的接口数量
     */
    int importFromProject(Long projectId, Path sourceRoot);

    /** 根据接口 ID 查询 */
    ApiDefinition getById(Long id);
}
