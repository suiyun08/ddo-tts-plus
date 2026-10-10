# 离线语音引擎与上架可行性调研

调研时间：2026-10-10。结论基于当时的公开资料和常识判断，标注"未核实"的内容在使用前需要再确认。

## 一、结论

- 想在 App 里内置离线中文朗读，首选 **sherpa-onnx**（Apache-2.0，提供 Android Java/Kotlin 接口）。
- 离线方案的音质和速度目前都不如现在用的 Edge TTS，入门机上尤其明显，听长篇小说要先实测。
- 上架版建议分两步：
  1. 默认用**手机系统 TTS**（「阅读」已经支持），App 不内置模型，合规风险最低。
  2. 需要开箱即用的好音色时，再集成 sherpa-onnx，模型首次使用时下载。

## 二、可选引擎

| 方案 | 许可 | 中文效果 | 说明 |
|---|---|---|---|
| **sherpa-onnx** | Apache-2.0（引擎） | 取决于模型 | 离线推理框架，有 Android 接口和可装成系统朗读引擎的示例 App；已支持 Kokoro（含中文 v1.1-zh）、Matcha、MeloTTS/VITS；新版本支持指定中文词语读音 |
| Piper | 新版 GPL-3.0 | 中文音色少、质量一般 | 原仓库 rhasspy/piper 于 2025-10 归档，迁到 OHF-Voice/piper1-gpl |
| espeak-ng | GPL-3.0 | 很机械 | 体积小，不适合听书 |
| CosyVoice / F5-TTS / Fish-Speech 等 | 各异，部分禁止商用 | 好 | 对手机太重，不适合端侧 |

## 三、sherpa-onnx 可用的中文模型

| 模型 | 特点 | 注意 |
|---|---|---|
| Kokoro v1.1-zh | 音质最好，约 100 个中文男女声 | 第三方 App 估算：旗舰机生成 1 分钟语音约 20–40 秒，入门机约 2–3 分钟，**入门机跟不上实时朗读**；模型约一两百 MB |
| Matcha / VITS 中文模型 | 快，低端机可实时 | 偏机械，长句语调平 |
| MeloTTS | 中英混读较好 | 音质中等 |

## 四、许可证要单独核实（上架前必做）

- 引擎许可和模型许可是分开的，每个模型、每个音色都要单独看。
- Kokoro v1.1-zh：第三方页面标注 Apache-2.0，**未看到官方模型页原文（未核实）**。训练数据据称来自龙猫数据（LongMaoData）的 100 位专业中文发音人。
- 不少中文 VITS / Matcha 模型用标贝（Data Baker）BZNSYP 音库训练。据一般了解该音库只授权非商业用途，**未查到授权原文（未核实）**，商用前需联系标贝。

## 五、去掉书源、只做本地电子书阅读的上架要点

| 事项 | 说明 |
|---|---|
| 电子书来源 | 用户导入本地文件、自己的网盘/WebDAV、公版书：可行；内置从网站搜索下载小说：和书源同样有版权问题 |
| Edge TTS | 调用的是 Edge"大声朗读"的非公开接口，商用分发可能违反微软条款，且随时可能失效。上架版改用系统 TTS 或官方付费接口（Azure、小米、豆包等开放平台） |
| App 备案 | 2023 年起国内分发的 App 需要备案，个人可办，商店审核时核对备案号 |
| 软件著作权 | 华为、小米、OPPO、vivo、应用宝大多要求软著或电子版权证明，个人可申请，周期数周到一两个月 |
| 隐私合规 | 隐私政策、权限用途说明、第三方 SDK 清单；去掉 Firebase 统计 |
| 名称与协议 | 不能再用「阅读 / Legado」名称和图标；GPL-3.0 可以上架，但必须公开完整源码 |
| 工作量 | 书源和搜索、发现、订阅、规则引擎深度耦合；先隐藏界面入口，再逐步清理代码 |

## 六、"中转服务让地址永久不变"的结论

- 技术上可行：手机和电脑都主动连同一台云服务器，由它转发请求（ngrok、Cloudflare Tunnel 同理）。
- 想让服务器"看不到数据"，需要端到端加密（扫码配对交换密钥，中转只转发密文）。
- 现有 Web 服务没有任何身份校验，暴露到公网前必须先加配对和访问令牌。
- 做成公开服务还涉及 ICP 备案、转发侵权内容的连带责任、个人信息保护、带宽成本。
- 只给自己用的中转复杂度可控；公开上架应先确认商店审核和版权能走通。

## 七、下一步（如果要推进）

1. 定 TTS 方案：系统 TTS（免费、音色一般）还是付费官方接口（音质好、有持续费用）。
2. 在目标商店后台查个人开发者上架「阅读类 / 工具类」应用的具体要求。
3. 想试离线音质：先在手机上装 sherpa-onnx 官方 TTS 引擎 App，用 Kokoro 中文模型听一段小说，确认速度和音质。

## 参考来源

- [sherpa_onnx 更新日志（pub.dev 镜像）](https://pub-web-s1.flutter-io.cn/packages/sherpa_onnx/versions/1.10.43/changelog)
- [react-native-sherpa-onnx TTS 模型概览](https://www.mintlify.com/XDcobra/react-native-sherpa-onnx/models/tts/overview)
- [VoxSherpa TTS（基于 sherpa-onnx 的 Android App）](https://github.com/CodeBySonu95/VoxSherpa-TTS)
- [Kokoro-82M-v1.1-zh（Mixpeek）](https://mixpeek.com/model/hexgrad/Kokoro-82M-v1.1-zh)
- [Kokoro-82M-v1.1-zh（PromptLayer）](https://www.promptlayer.com/models/kokoro-82m-v11-zh)
- [Kokoro-82M-zh-MLX](https://huggingface.co/1038lab/Kokoro-82M-zh-MLX)
- [Piper 安装与许可说明](https://www.qwe.edu.pl/ai-tools/install-piper-tts-open-source/)
- [Piper TTS 评测](https://www.promptquorum.com/power-local-llm/piper-tts-review)
- [piper1-gpl（Gitee 镜像）](https://gitee.com/sieding/piper1-gpl)
