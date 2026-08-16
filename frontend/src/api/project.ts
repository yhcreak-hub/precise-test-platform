import { http } from './request'

/** 构建类型 */
export type BuildType = 'maven' | 'gradle'

/** 项目状态（统一模型 VARCHAR 枚举） */
export type ProjectStatus = 'active' | 'disabled'

/** 被测项目（与后端 Project 实体字段一致） */
export interface Project {
  id: number
  name: string
  gitUrl: string
  branch: string
  buildType: string
  status: ProjectStatus
  baseUrl: string
  createdAt: string
}

/** MyBatis-Plus 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 新增项目参数 */
export interface ProjectCreateParams {
  name: string
  gitUrl: string
  branch?: string
  buildType?: string
}

/** 流水线触发响应（M1 占位：genTaskId=0） */
export interface PipelineTriggerResult {
  genTaskId: number
  message: string
}

/** 项目分页列表：GET /api/projects */
export function pageProjects(params: {
  page: number
  size: number
  name?: string
}): Promise<PageResult<Project>> {
  return http.get<PageResult<Project>>('/projects', params)
}

/** 新增项目：POST /api/projects */
export function createProject(data: ProjectCreateParams): Promise<Project> {
  return http.post<Project>('/projects', data)
}

/** 删除项目：DELETE /api/projects/{id} */
export function deleteProject(id: number): Promise<void> {
  return http.delete<void>(`/projects/${id}`)
}

/** 触发流水线：POST /api/projects/{id}/pipeline（M1 占位实现） */
export function triggerPipeline(id: number): Promise<PipelineTriggerResult> {
  return http.post<PipelineTriggerResult>(`/projects/${id}/pipeline`)
}
