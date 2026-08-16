package com.precise.test.repo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.precise.test.repo.entity.Project;
import com.precise.test.repo.mapper.ProjectMapper;
import com.precise.test.repo.service.ProjectService;
import org.springframework.stereotype.Service;

/**
 * 被测项目服务实现（MyBatis-Plus ServiceImpl 提供通用 CRUD）
 */
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
}
