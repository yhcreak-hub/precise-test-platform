package com.precise.test.casegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用例执行记录（明细）
 * <p>批次内每条用例的执行结果，支持查看详情报告。</p>
 */
@Data
@TableName("exec_record_detail")
public class ExecRecordDetail {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 执行记录 ID */
    private Long execRecordId;

    /** 用例 ID */
    private Long testCaseId;

    /** 用例标题 */
    private String caseTitle;

    /** 接口路径 */
    private String apiPath;

    /** 请求入参 JSON */
    private String requestJson;

    /** 状态：PASS / FAIL / ERROR */
    private String status;

    /** HTTP 状态码 */
    private Integer httpStatus;

    /** 响应体 */
    private String responseBody;

    /** 断言明细 JSON */
    private String assertDetails;

    /** 错误信息 */
    private String errorMsg;

    /** 耗时 ms */
    private Long costMs;
}
