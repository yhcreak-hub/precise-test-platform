package com.precise.test.casegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.casegen.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试用例 Mapper
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {
}
