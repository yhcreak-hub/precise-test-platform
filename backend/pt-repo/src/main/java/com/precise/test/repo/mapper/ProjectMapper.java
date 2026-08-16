package com.precise.test.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.repo.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 被测项目 Mapper（MyBatis-Plus 通用 CRUD）
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
