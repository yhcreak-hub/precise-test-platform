package com.precise.test.casegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.casegen.entity.ExecRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行记录（批次）Mapper
 */
@Mapper
public interface ExecRecordMapper extends BaseMapper<ExecRecord> {
}
