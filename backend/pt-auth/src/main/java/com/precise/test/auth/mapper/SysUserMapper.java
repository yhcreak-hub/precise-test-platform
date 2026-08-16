package com.precise.test.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.precise.test.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper（MyBatis-Plus 通用 CRUD）
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
