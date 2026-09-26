// Presentation only: never fill gaps, extrapolate samples, or manufacture forecasts.
export function cleanChartPoints(points = []) {
  const times = new Map()
  for (const point of points) {
    if (Number.isFinite(point?.at) && Number.isFinite(point?.t)) times.set(point.at, { at: point.at, t: point.t })
  }
  return [...times.values()].sort((a, b) => a.at - b.at)
}

export function chartGeometry(points, prediction = [], min = 50, max = 250) {
  const measured = cleanChartPoints(points)
  const last = measured.at(-1)
  const forecast = last ? cleanChartPoints(prediction).filter(p => p.at > last.at) : []
  const all = [...measured, ...forecast]
  const low = Math.min(min, ...all.map(p => Math.floor(p.t / 50) * 50))
  const high = Math.max(max, low + 50, ...all.map(p => Math.ceil(p.t / 50) * 50))
  const end = all.at(-1)?.at ?? 0
  const start = Math.min(all[0]?.at ?? 0, end - 60000)
  const x = at => 3 + 90 * (at - start) / (end - start)
  const y = t => 18 + 76 * (high - t) / (high - low)
  const coordinates = list => list.map(p => ({ x: x(p.at), y: y(p.t) }))
  return { measured, forecast, low, high, start, end, y, coordinates,
    endpoint: measured.length > 1 ? { x: x(last.at), y: y(last.t), value: Math.round(last.t) } : null }
}

// Smooth each measured segment with horizontal tangents. Every segment stays
// between its two observed endpoints; unlike an unconstrained spline it cannot overshoot.
export function smoothChartPath(points) {
  if (points.length < 2) return ''
  const n = value => value.toFixed(3)
  let path = `M ${n(points[0].x)} ${n(points[0].y)}`
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i], mid = (a.x + b.x) / 2
    path += ` C ${n(mid)} ${n(a.y)}, ${n(mid)} ${n(b.y)}, ${n(b.x)} ${n(b.y)}`
  }
  return path
}
