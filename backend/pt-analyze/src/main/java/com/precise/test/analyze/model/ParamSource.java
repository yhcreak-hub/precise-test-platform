package com.precise.test.analyze.model;

/**
 * 参数来源（Spring MVC 的参数绑定方式）。
 *
 * <p>与参数注解的对应关系：
 * <ul>
 *   <li>{@link #QUERY}：@RequestParam（Query 参数 / 表单字段）</li>
 *   <li>{@link #PATH}：@PathVariable（路径变量，如 /user/{id}）</li>
 *   <li>{@link #BODY}：@RequestBody（请求体，通常为 DTO 对象）</li>
 *   <li>{@link #HEADER}：@RequestHeader（请求头）</li>
 *   <li>{@link #NONE}：无参数注解（Spring 默认按 Query/ModelAttribute 绑定，
 *       引擎不做进一步推断，仅标记来源未知）</li>
 * </ul>
 */
public enum ParamSource {

    QUERY,
    PATH,
    BODY,
    HEADER,
    NONE
}
