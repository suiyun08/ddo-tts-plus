// 模拟「阅读」手机端 Web 服务：不装 APK 也能在浏览器里预览、调试网页端
// 用法: node mock/server.mjs [dist目录，默认 dist] [端口，默认 18080]
// 自动化测试且浏览器面板隐藏时，设置环境变量 MOCK_POLYFILL=1 注入动画帧/相交检测的模拟实现
// 调试接口：/__infinite?on=&anim=&dur=  /__setProgress?idx=&pos=  /__delay?shelf=&progress=  /__readChapters?set=
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const [distArg, portArg] = process.argv.slice(2)
const distDir = distArg || new URL('../dist', import.meta.url).pathname
const port = Number(portArg || 18080)
const indent = '　　'

const book = {
  name: '测试之书',
  author: '佚名',
  bookUrl: 'local://test-book',
  tocUrl: '',
  origin: 'loc_book',
  originName: 'test.txt',
  type: 0,
  group: 0,
  latestChapterTime: 0,
  lastCheckTime: 0,
  lastCheckCount: 0,
  totalChapterNum: 60,
  durChapterTitle: '第一章 春天',
  durChapterIndex: 0,
  durChapterPos: 0,
  durChapterTime: Date.now(),
  canUpdate: false,
  order: 0,
  originOrder: 0,
  syncTime: 0,
}

const chapters = [
  {
    title: '第一章 春天',
    paras: [
      '京口瓜洲一水间，钟山只隔数重山。春风又绿江南岸，明月何时照我还。',
      '他笑了笑，没有说话，转身走进了雨里。街上的行人很少，只有几盏昏黄的路灯。',
      '这是第三段，用来拉开距离。'.repeat(6),
      '这是第四段，同样用来拉开距离。'.repeat(6),
      '老人看着远方。他笑了笑，说这条路他走了四十年，每一块石板都认得。',
      '最后一段，故事暂时告一段落。',
      ...Array.from({ length: 30 }, (_, i) => `春天补充第${i + 1}段。风从南边吹来，带着湿润的气息，田里的秧苗一天比一天高，远处有人在唱歌。`),
    ],
  },
  {
    title: '第二章 夏天',
    paras: Array.from(
      { length: 12 },
      (_, i) => `夏天第${i + 1}段。蝉鸣从早到晚不停，空气里都是青草和泥土的味道，河边的孩子们光着脚追逐打闹。`,
    ),
  },
  {
    title: '第三章 秋天',
    paras: ['秋天来了。', '叶子落了一地。'],
  },
]

for (let n = 4; n <= 60; n++) {
  chapters.push({
    title: `第${n}章 冬日${n}`,
    paras: Array.from({ length: 6 + (n % 5) * 4 }, (_, i) => `第${n}章第${i + 1}段。雪落了一整夜，屋檐下挂满了冰凌，炉火噼啪作响，窗外一片白茫茫。`),
  })
}
const contentOf = i => chapters[i].paras.map(p => indent + p).join('\n')

// 手机端口径的位置：标题 + 换行，再加上各段（含缩进）和换行
const appPos = (chapterIndex, paraIndex, offsetInPara) => {
  let pos = chapters[chapterIndex].title.length + 1
  for (let i = 0; i < paraIndex; i++) pos += indent.length + chapters[chapterIndex].paras[i].length + 1
  return pos + indent.length + offsetInPara
}

const longText = chapters[1].paras.slice(3, 9).join('')
let readConfig = null
// 模拟手机响应慢：书架接口、进度保存的延迟（毫秒）
let shelfDelay = 0
let progressDelay = 0
let readChapters = []
const sleep = ms => new Promise(r => setTimeout(r, ms))
let bookmarks = [
  {
    time: 1760000000000,
    bookName: book.name,
    bookAuthor: book.author,
    chapterIndex: 0,
    chapterPos: appPos(0, 0, chapters[0].paras[0].indexOf('春风又绿江南岸')),
    chapterName: chapters[0].title,
    bookText: '春风又绿江南岸',
    content: '手机端添加的选中文字书签',
  },
  {
    time: 1760000001000,
    bookName: book.name,
    bookAuthor: book.author,
    chapterIndex: 0,
    chapterPos: appPos(0, 4, chapters[0].paras[4].indexOf('他笑了笑')),
    chapterName: chapters[0].title,
    bookText: '他笑了笑',
    content: '重复短语，应标在第五段（老人那段）',
  },
  {
    time: 1760000002000,
    bookName: book.name,
    bookAuthor: book.author,
    chapterIndex: 1,
    chapterPos: appPos(1, 3, 0),
    chapterName: chapters[1].title,
    bookText: longText,
    content: '手机端整页书签，网页不画线',
  },
]

const ok = data => JSON.stringify({ isSuccess: true, errorMsg: '', data })
const fail = msg => JSON.stringify({ isSuccess: false, errorMsg: msg, data: null })

const readBody = req =>
  new Promise(resolve => {
    let body = ''
    req.on('data', c => (body += c))
    req.on('end', () => resolve(body))
  })

const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.ico': 'image/x-icon', '.svg': 'image/svg+xml', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf' }

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, `http://localhost:${port}`)
    const api = url.pathname
    res.setHeader('Access-Control-Allow-Origin', '*')
    const json = body => {
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(body)
    }
    if (req.method === 'POST') {
      const body = await readBody(req)
      console.log('POST', api, body.slice(0, 200))
      if (api === '/saveBookmark') {
        const bm = JSON.parse(body)
        if (!bm.time) bm.time = Date.now()
        bookmarks = bookmarks.filter(b => b.time !== bm.time).concat([bm])
        return json(ok(bm))
      }
      if (api === '/saveBookProgress') {
        const p = JSON.parse(body)
        await sleep(progressDelay)
        Object.assign(book, p)
        return json(ok(''))
      }
      if (api === '/saveReadConfig') {
        readConfig = JSON.parse(body)
        return json(ok(''))
      }
      if (api === '/saveReadChapters') {
        const d = JSON.parse(body)
        for (const u of d.urls) if (!readChapters.includes(u)) readChapters.push(u)
        return json(ok(''))
      }
      if (api === '/deleteBookmark') {
        const bm = JSON.parse(body)
        bookmarks = bookmarks.filter(b => b.time !== bm.time)
        return json(ok(''))
      }
      return json(ok(''))
    }
    switch (api) {
      case '/getReadConfig':
        return readConfig ? json(ok(JSON.stringify(readConfig))) : json(fail('无配置'))
      case '/__infinite':
        readConfig = { theme: 0, font: 0, fontSize: 18, readWidth: 800, infiniteLoading: url.searchParams.get('on') === '1', customFontName: '', jumpDuration: Number(url.searchParams.get('dur') ?? 300), pageAnim: url.searchParams.get('anim') ?? 'curl', spacing: { paragraph: 1, line: 0.8, letter: 0 } }
        return json(ok(readConfig))
      case '/__delay':
        shelfDelay = Number(url.searchParams.get('shelf') ?? 0)
        progressDelay = Number(url.searchParams.get('progress') ?? 0)
        return json(ok({ shelfDelay, progressDelay }))
      case '/getReadChapters':
        return json(ok(readChapters))
      case '/__readChapters':
        if (url.searchParams.has('set')) readChapters = url.searchParams.get('set').split(',').filter(Boolean)
        return json(ok(readChapters))
      case '/__test_polyfill.js':
        res.setHeader('Content-Type', 'text/javascript')
        return res.end(fs.readFileSync(new URL('./test-polyfill.js', import.meta.url)))
      case '/getBookshelf': {
        // 先读出当时的进度再延迟返回，模拟手机读库后网络传输慢
        const snapshot = ok([{ ...book }])
        await sleep(shelfDelay)
        return json(snapshot)
      }
      case '/getChapterList':
        return json(ok(chapters.map((c, i) => ({ url: 'c' + i, title: c.title, isVolume: false, baseUrl: '', bookUrl: book.bookUrl, index: i, isVip: false, isPay: false }))))
      case '/getBookContent':
        return json(ok(contentOf(Number(url.searchParams.get('index')))))
      case '/getBookmarks':
        console.log('GET getBookmarks', url.searchParams.get('name'))
        return json(ok([...bookmarks].sort((a, b) => a.chapterIndex - b.chapterIndex || a.chapterPos - b.chapterPos)))
      case '/__setProgress':
        // 模拟手机端翻到别处
        book.durChapterIndex = Number(url.searchParams.get('idx'))
        book.durChapterPos = Number(url.searchParams.get('pos'))
        book.durChapterTitle = chapters[book.durChapterIndex].title
        book.durChapterTime = Date.now()
        return json(ok(book))
      case '/__book':
        return json(ok(book))
      case '/__bookmarks':
        return json(JSON.stringify(bookmarks))
      case '/cover':
        res.statusCode = 404
        return res.end()
    }
    const file = path.resolve(distDir, api === '/' ? 'index.html' : '.' + api)
    if (!file.startsWith(path.resolve(distDir)) || !fs.existsSync(file)) {
      res.statusCode = 404
      return res.end('not found')
    }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream')
    if (file.endsWith('index.html') && process.env.MOCK_POLYFILL === '1') {
      return res.end(fs.readFileSync(file, 'utf8').replace('<head>', '<head><script src="./__test_polyfill.js"></script>'))
    }
    fs.createReadStream(file).pipe(res)
  })
  .listen(port, () => console.log('mock legado on', port))
