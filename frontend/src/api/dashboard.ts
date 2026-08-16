import { http } from './request'

/** 工作台统计 */
export interface DashboardStats {
  projectCount: number
  apiCount: number
  caseCount: number
  mappingCount: number
  execRecordCount: number
  recentPassRate: number
  recentExecs: Array<{
    id: number
    source: string
    total: number
    passed: number
    costMs: number
    createdAt: string
  }>
}

/** 工作台统计：GET /api/projects/stats/summary */
export function getDashboardStats(): Promise<DashboardStats> {
  return http.get<DashboardStats>('/projects/stats/summary')
}
