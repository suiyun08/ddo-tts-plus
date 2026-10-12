export type webReadConfig = {
  theme: number
  font: number
  fontSize: number
  readWidth: number
  infiniteLoading: boolean
  customFontName: string
  jumpDuration: number
  /** 翻页方式：仿真翻页、平滑滚动、无动画 */
  pageAnim: 'curl' | 'scroll' | 'none'
  spacing: {
    paragraph: number
    line: number
    letter: number
  }
}
