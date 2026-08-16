package com.precise.test.mapping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.mapping.entity.CaseCodeMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用例-代码关联 Mapper
 */
@Mapper
public interface CaseCodeMappingMapper extends BaseMapper<CaseCodeMapping> {
}
