export interface TemperatureSample {
  schemaVersion: 1
  protocolVersion?: 1 | 2
  temperature: number | null
  ambientTemperature: number | null
  valid: boolean
  updatedAt: number
  receivedAt?: number
  deviceTimeMs?: number | null
  bootId?: string | null
  sequence?: number | null
  discontinuity?: boolean
  source: 'device' | 'simulation' | 'public_thermocouple'
}
export interface CookingContext {
  schemaVersion: 1
  recipeId: string
  stepId: string
  method: string
  targetRange: [number, number] | null
}
export type TemperatureEvent = { type: 'ingredient_added' | 'probe_moved' | 'heat_off'; at: number }
export interface TemperatureAssessment {
  schemaVersion: 1
  quality: 'usable' | 'suspect' | 'invalid'
  phase: 'preheat' | 'drop' | 'recovery' | 'steady' | 'cooling' | 'unknown'
  phaseLabel: string
  risk: 'unknown' | 'observing' | 'warning' | 'danger'
  temperature: number | null
  slope: number | null
  reasons: string[]
  suggestion: string | null
  modelVersion: string
  alert: {at: number; risk: 'warning' | 'danger'} | null
  forecast: {seconds: number; low: number; median: number; high: number}[]
}
