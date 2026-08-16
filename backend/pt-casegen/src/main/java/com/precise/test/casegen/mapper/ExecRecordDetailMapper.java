package com.precise.test.casegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.casegen.entity.ExecRecordDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行记录（明细）Mapper
 */
@Mapper
public interface ExecRecordDetailMapper extends BaseMapper<ExecRecordDetail> {
}
