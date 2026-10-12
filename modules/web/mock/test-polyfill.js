// 仅用于本地测试：预览面板隐藏时浏览器暂停动画帧和相交检测，这里用定时器模拟
(function () {
  window.requestAnimationFrame = cb => setTimeout(() => cb(performance.now()), 16)
  class IO {
    constructor(cb, opts = {}) {
      this.cb = cb
      this.m = (opts.rootMargin || '0px').trim().split(/\s+/)
      this.els = new Map()
      IO.all.add(this)
    }
    parse(v, size) { return v.endsWith('%') ? (parseFloat(v) / 100) * size : parseFloat(v) || 0 }
    observe(el) { this.els.set(el, undefined); setTimeout(() => this.check(true), 0) }
    unobserve(el) { this.els.delete(el) }
    disconnect() { this.els.clear(); IO.all.delete(this) }
    check(force) {
      const h = innerHeight
      let [t, r, b] = this.m
      if (r === undefined) r = t
      if (b === undefined) b = t
      const top = -this.parse(t, h), bottom = h + this.parse(b, h)
      const entries = []
      for (const [el, prev] of this.els) {
        const rc = el.getBoundingClientRect()
        const inter = rc.bottom > top && rc.top < bottom
        if (force || prev !== inter) {
          this.els.set(el, inter)
          entries.push({ target: el, isIntersecting: inter, boundingClientRect: rc })
        }
      }
      if (entries.length) this.cb(entries, this)
    }
  }
  IO.all = new Set()
  window.IntersectionObserver = IO
  const tick = () => IO.all.forEach(o => o.check(false))
  addEventListener('scroll', tick, { passive: true })
  setInterval(tick, 200)
})()
