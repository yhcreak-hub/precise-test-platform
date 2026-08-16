package com.precise.test.repo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台统计摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

    /** 项目总数 */
    private long projectCount;

    /** 接口总数 */
    private long apiCount;

    /** 用例总数 */
    private long caseCount;

    /** 用例-代码关联数 */
    private long mappingCount;

    /** 执行记录数 */
    private long execRecordCount;

    /** 最近执行通过率（0-100） */
    private double recentPassRate;

    /** 最近 7 次执行记录（趋势图） */
    private java.util.List<RecentExec> recentExecs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentExec {
        private Long id;
        private String source;
        private int total;
        private int passed;
        private long costMs;
        private String createdAt;
    }
}
