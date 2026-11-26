export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface EngineeringStatus {
  component: string
  phase: 'ENGINEERING_SCAFFOLD'
}

export async function getEngineeringStatus(): Promise<ApiResponse<EngineeringStatus>> {
  const response = await fetch('/api/v1/system/status')
  if (!response.ok) throw new Error(`网关不可用（HTTP ${response.status}）`)
  const body: ApiResponse<EngineeringStatus> = await response.json()
  if (body.code !== 'OK' || body.data.phase !== 'ENGINEERING_SCAFFOLD') {
    throw new Error('网关返回了无法识别的工程状态')
  }
  return body
}
