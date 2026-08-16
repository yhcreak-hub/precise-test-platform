package com.precise.test.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.task.entity.GenTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成任务 Mapper
 */
@Mapper
public interface GenTaskMapper extends BaseMapper<GenTask> {
}
