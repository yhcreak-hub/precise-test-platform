package com.precise.test.repo.dto;

import lombok.Data;

/**
 * 项目分页查询参数
 */
@Data
public class ProjectPageQuery {

    /** 页码（从 1 开始） */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;

    /** 按名称模糊搜索（可选） */
    private String name;
}
