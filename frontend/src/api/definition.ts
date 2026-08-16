import { http } from './request'

/** 接口定义（与后端 ApiDefinition 实体字段一致） */
export interface ApiDefinition {
  id: number
  projectId: number
  apiPath: string
  httpMethod: string
  paramSchemaJson: string
  responseSchemaJson: string
  controllerClass: string
  controllerMethod: string
  filePath: string
  lineNo: number
  status: string
  createdAt: string
}

/** 查询项目的接口清单：GET /api/projects/{id}/apis */
export function getProjectApis(projectId: number): Promise<ApiDefinition[]> {
  return http.get<ApiDefinition[]>(`/projects/${projectId}/apis`)
}
