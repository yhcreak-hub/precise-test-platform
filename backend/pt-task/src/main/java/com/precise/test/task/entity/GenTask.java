package com.precise.test.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成任务实体（表 gen_task）
 *
 * <p>字段与统一数据模型一致：
 * id / projectId / type / status / progress / logUrl / createdAt / finishedAt</p>
 *
 * <p>M2 TODO: 由 pt-task 承载「接口识别 / 用例生成 / 用例-代码映射」异步任务。</p>
 */
@Data
@TableName("gen_task")
public class GenTask {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 任务类型：analyze / casegen / mapping */
    private String type;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 进度 0-100 */
    private Integer progress;

    /** 任务日志地址 */
    private String logUrl;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间（可为空） */
    private LocalDateTime finishedAt;
}
