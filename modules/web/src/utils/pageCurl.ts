/**
 * 仿真翻页：把屏幕上的正文栏复制一份盖在页面上，页面瞬间跳到新位置后，
 * 让这份副本像纸一样从右下角卷起翻走（往后翻），或从左侧翻回来盖住旧页（往前翻）。
 * 不改变正文的排版和滚动方式，只是在翻页时多一层动画。
 *
 * 几何：纸张右下角 C 被拉到 P，折痕是 CP 的中垂线；折痕靠 C 一侧的部分翻折到另一侧，
 * 翻折部分显示纸张背面（淡淡透出镜像文字），露出的区域显示下面的新页。
 */

type Point = { x: number; y: number }
type Box = { left: number; top: number; width: number; height: number }

/** 正文栏在屏幕上可见的部分 */
const visibleBox = (paper: HTMLElement): Box => {
  const rect = paper.getBoundingClientRect()
  const viewHeight = document.documentElement.clientHeight
  const top = Math.max(rect.top, 0)
  const bottom = Math.min(rect.bottom, viewHeight)
  return { left: rect.left, top, width: rect.width, height: Math.max(bottom - top, 0) }
}

/** 正文栏当前画面的副本：一个和可见区域一样大的方块，内部是正文栏的克隆 */
const makePage = (paper: HTMLElement, box: Box) => {
  const rect = paper.getBoundingClientRect()
  const page = document.createElement('div')
  page.style.cssText = `position:absolute;left:0;top:0;width:${box.width}px;height:${box.height}px;overflow:hidden;`
  const clone = paper.cloneNode(true) as HTMLElement
  clone.style.position = 'absolute'
  clone.style.left = '0'
  clone.style.top = rect.top - box.top + 'px'
  clone.style.margin = '0'
  clone.style.pointerEvents = 'none'
  page.appendChild(clone)
  return page
}

/** 矩形 [0,w]x[0,h] 与半平面 side*(X-M)·n >= 0 的交集（凸多边形顶点） */
const clipRect = (w: number, h: number, m: Point, n: Point, side: 1 | -1) => {
  const corners: Point[] = [
    { x: 0, y: 0 },
    { x: w, y: 0 },
    { x: w, y: h },
    { x: 0, y: h },
  ]
  const f = (p: Point) => side * ((p.x - m.x) * n.x + (p.y - m.y) * n.y)
  const result: Point[] = []
  for (let i = 0; i < corners.length; i++) {
    const a = corners[i]
    const b = corners[(i + 1) % corners.length]
    const fa = f(a)
    const fb = f(b)
    if (fa >= 0) result.push(a)
    if ((fa >= 0) !== (fb >= 0)) {
      const t = fa / (fa - fb)
      result.push({ x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t })
    }
  }
  return result
}

const toPolygon = (points: Point[]) =>
  points.length < 3
    ? 'polygon(0 0, 0 0, 0 0)'
    : `polygon(${points.map(p => `${p.x.toFixed(1)}px ${p.y.toFixed(1)}px`).join(',')})`

/** 沿法线方向、从折痕起算的线性渐变 */
const foldGradient = (w: number, h: number, m: Point, n: Point, stops: [number, string][]) => {
  const angle = Math.atan2(n.x, -n.y)
  const length = Math.abs(w * Math.sin(angle)) + Math.abs(h * Math.cos(angle))
  const start = { x: w / 2 - (n.x * length) / 2, y: h / 2 - (n.y * length) / 2 }
  const foldAt = (m.x - start.x) * n.x + (m.y - start.y) * n.y
  return `linear-gradient(${angle}rad, ${stops
    .map(([offset, color]) => `${color} ${(foldAt + offset).toFixed(1)}px`)
    .join(',')})`
}

const easeInOut = (t: number) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2)

export type PageTurn = {
  /** 页面已经定位到新位置后调用，播放翻页动画 */
  play: (duration: number) => Promise<void>
  /** 取消动画，移除覆盖层 */
  cancel: () => void
}

/**
 * 开始一次仿真翻页：立即用当前画面的副本盖住页面，之后页面可以随意跳转或重新加载，
 * 定位好后调用 play 播放动画
 * @param host 覆盖层的父元素（需在正文样式作用域内，副本才能保持原样式）
 * @param paper 正文栏元素
 * @param forward true 往后翻（当前页卷起翻走），false 往前翻（上一页从左侧翻回来）
 */
export const beginPageTurn = (
  host: HTMLElement,
  paper: HTMLElement,
  forward: boolean,
): PageTurn => {
  const box = visibleBox(paper)
  const layer = document.createElement('div')
  layer.className = 'page-curl-layer'
  layer.style.cssText = `position:fixed;left:${box.left}px;top:${box.top}px;width:${box.width}px;height:${box.height}px;z-index:60;`
  // 先盖上当前画面，遮住接下来的跳转和加载
  const oldPage = makePage(paper, box)
  layer.appendChild(oldPage)
  host.appendChild(layer)

  let cancelled = false
  let frame = 0
  const cancel = () => {
    cancelled = true
    cancelAnimationFrame(frame)
    layer.remove()
  }

  const play = (duration: number) =>
    new Promise<void>(resolve => {
      if (cancelled) return resolve()
      const w = box.width
      const h = box.height
      if (w <= 0 || h <= 0 || duration <= 0) {
        cancel()
        return resolve()
      }
      // 往后翻：卷起的是旧页，下面露出真实页面；往前翻：翻回来的是新页，下面垫着旧页
      const sheetSource = forward ? null : makePage(paper, box)
      if (forward) oldPage.remove()
      const background = getComputedStyle(paper).background

      const sheet = forward ? oldPage : (sheetSource as HTMLElement)
      const shade = document.createElement('div')
      shade.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;'
      const flapWrap = document.createElement('div')
      flapWrap.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;'
      const flap = forward ? (oldPage.cloneNode(true) as HTMLElement) : makePage(paper, box)
      flap.style.transformOrigin = '0 0'
      // 纸张背面：纸色盖住大部分文字，只透出淡淡的镜像字迹
      const back = document.createElement('div')
      back.style.cssText = `position:absolute;inset:0;background:${background};opacity:0.9;`
      const backShade = document.createElement('div')
      backShade.style.cssText = 'position:absolute;inset:0;'
      flap.append(back, backShade)
      flapWrap.appendChild(flap)
      layer.append(shade, sheet, flapWrap)

      const render = (progress: number) => {
        // 右下角沿弧线移到左侧，途中略微抬起，形成斜向卷角
        const c = { x: w, y: h }
        const p = {
          x: w - 2 * w * progress,
          y: h - Math.sin(progress * Math.PI) * h * 0.3,
        }
        const dx = c.x - p.x
        const dy = c.y - p.y
        const length = Math.hypot(dx, dy)
        if (length < 0.5) {
          sheet.style.clipPath = ''
          flap.style.display = 'none'
          shade.style.display = 'none'
          return
        }
        flap.style.display = ''
        shade.style.display = ''
        const n = { x: dx / length, y: dy / length }
        const m = { x: (c.x + p.x) / 2, y: (c.y + p.y) / 2 }
        const folded = clipRect(w, h, m, n, 1)
        sheet.style.clipPath = toPolygon(clipRect(w, h, m, n, -1))
        // 翻折部分：以折痕为轴镜像
        const d = m.x * n.x + m.y * n.y
        const a = 1 - 2 * n.x * n.x
        const b = -2 * n.x * n.y
        const dd = 1 - 2 * n.y * n.y
        flap.style.transform = `matrix(${a},${b},${b},${dd},${2 * d * n.x},${2 * d * n.y})`
        flap.style.clipPath = toPolygon(folded)
        backShade.style.background = foldGradient(w, h, m, n, [
          [0, 'rgba(0,0,0,0.16)'],
          [18, 'rgba(255,255,255,0.18)'],
          [Math.max(length / 2, 40), 'rgba(0,0,0,0.04)'],
        ])
        // 快翻完时翻折部分已到正文栏左侧，逐渐淡出
        flapWrap.style.opacity = progress > 0.75 ? String((1 - progress) / 0.25) : '1'
        flapWrap.style.filter = `drop-shadow(${(-n.x * 4).toFixed(1)}px ${(-n.y * 4).toFixed(1)}px 8px rgba(0,0,0,0.28))`
        // 露出的下一页在折痕附近有一道阴影
        shade.style.clipPath = toPolygon(folded)
        shade.style.background = foldGradient(w, h, m, n, [
          [0, 'rgba(0,0,0,0.22)'],
          [36, 'rgba(0,0,0,0)'],
        ])
      }

      const start = performance.now()
      const step = (now: number) => {
        if (cancelled) return resolve()
        const t = Math.min((now - start) / duration, 1)
        const eased = easeInOut(t)
        render(forward ? eased : 1 - eased)
        if (t < 1) {
          frame = requestAnimationFrame(step)
        } else {
          layer.remove()
          resolve()
        }
      }
      render(forward ? 0 : 1)
      frame = requestAnimationFrame(step)
    })

  return { play, cancel }
}
