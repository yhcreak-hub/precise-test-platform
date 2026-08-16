package com.precise.test.casegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.precise.test.casegen.engine.CaseExecutor;
import com.precise.test.casegen.entity.TestCase;

import java.util.List;
import java.util.Map;

/**
 * 测试用例服务（M3/M4 实现）
 * <p>目标：基于接口定义（参数/响应 Schema）自动生成用例，
 * 检测「测试空洞」（有接口但无用例），并支持用例编辑与手动执行。</p>
 */
public interface TestCaseService {

    /** 分页查询项目下的用例 */
    Page<TestCase> pageByProject(Long projectId, int page, int size);

    /** 按接口分组查询项目下的用例：apiDefinitionId -> 用例列表 */
    Map<Long, List<TestCase>> groupByApi(Long projectId);

    /** 根据用例 ID 查询 */
    TestCase getById(Long id);

    /** 编辑用例（入参/断言/请求头/标题） */
    TestCase updateCase(Long id, String title, String requestJson, String assertsJson, String headersJson);

    /** 执行用例（真实 HTTP 请求 + 断言校验）；baseUrl 为被测服务地址，由调用方（项目模块）传入 */
    CaseExecutor.ExecuteResult executeCase(Long id, String baseUrl);

    /**
     * 基于接口定义自动生成用例
     *
     * @param apiDefinitionId 接口定义 ID
     * @param scenarioType    场景类型（normal/required/boundary/exception，null=全部）
     * @return 生成的用例数量
     */
    Long generateCases(Long apiDefinitionId, String scenarioType);

    /** 批量保存生成的用例，返回保存数量 */
    int saveGenerated(List<TestCase> cases);

    /**
     * 为项目下所有「空洞接口」（无用例）生成用例
     *
     * @param projectId 项目 ID
     * @return 本次生成的用例总数
     */
    int generateForProject(Long projectId);

    /**
     * 测试空洞检测
     *
     * @param projectId 项目 ID
     * @return 空洞报告（接口总数 / 空洞数 / 空洞率）
     */
    GapReport detectGap(Long projectId);

    /** 空洞检测报告 */
    @lombok.Builder
    @lombok.Data
    class GapReport {
        /** 接口总数 */
        int totalApis;
        /** 空洞接口数（无用例） */
        int gapApis;
        /** 空洞率（%） */
        double gapRate;
    }
}
