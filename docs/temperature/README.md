# CookX 温度时序模块

## 状态与边界

已接入 ESP32 → JDY-31 → Android Capacitor → Vue 温度模块。规则判断为默认，Transformer 为用户主动开启的实验模式。所有模型训练数据为明确标记的物理仿真；没有原型机烹饪实测，不能将实验分数称为设备识别准确率或安全认证。

模块区分测量质量、烹饪阶段、温度风险三件事。单点红外曲线可能同时对应投料和探头移开，无法保证识别真实动作；不确定时抑制操作建议，用户可确认投料、探头移动或关火。没有执行器，不控制灶具，不估计内部熟度。

## 目录与协作

- `firmware/cookx_sense`：独立固件，保留现有接线。
- `frontend/src/temperature`：协议之外的纯算法、特征契约、Worker、会话控制与类型。前端组件只负责展示。
- `research/temperature`：仿真、训练、公开数据核验、基线和浏览器实验。
- `frontend/public/temperature`：模型、哈希清单与明确来源的回放。WASM 文件由安装依赖后构建时复制。
- `docs/temperature`：接口、来源、机器可读结果和实验解释。

推荐、鲜度、视觉模块无需修改。菜谱团队继续提供原有 steps；温度适配器接受明确的摄氏温区（例如 `160–180 ℃` 或数值数组）。模糊的“中火”“180”“180°F”不会转换为安全目标。可以直接调用 `setContext(CookingContext)` 接入结构化字段。目标温区来自菜谱，不代表已经完成实验校准。

## 数据与协议

一行一帧，ASCII，LF 或 CRLF。旧格式 `TEMP:168.5`、纯数字 `168.5` 均可用。旧固件上报值可能已经做过均值滤波；无法还原原始值，不虚构环境温度或采样时钟。

新版：
```text
CX2,abcdef01,12,6000,168.50,26.10,1
CX2,abcdef01,13,6500,,26.10,0
```

字段依次为版本、8 位十六进制启动标识、uint32 序号、设备毫秒时间、原始目标温度、传感器环境温度、有效位。无效目标值必须留空；环境温度可缺失。V2 目标范围 −70～380℃、环境 −40～125℃仅作器件数据范围校验，不等于全量程精度。MLX90614 环境温度反映传感器自身热环境，并非食物或冰箱温度。

单帧上限 256 字符。重复或乱序帧丢弃；启动标识变化、缺序和计时回绕标记中断。算法默认超过 1.5 秒采样间隔重建窗口；5 秒无数据进入失效。单次 12℃ 以上突变、每秒 12℃ 以上变化、较大残差触发可疑状态，这些为待校准工程参数，不是通用物理定律。

稳定积累至少约 5 秒后再判断；阶段切换保持约 2 秒。持续超目标上限 10℃或超过通用 230℃观察阈值触发提醒，持续 10 秒升级，重复提醒冷却 30 秒。缺温区时只给趋势与通用高温关注提示。230℃不是着火点或通用安全线。

## 模型与离线运行

输入为 120×8 的因果历史：归一化温度、截断温升率、采样间隔、有效掩码、可用环境温度、温区下限、上限、上下文存在标记。每 10 点分块形成 12 个 token；两层、四头、64 维 Transformer，共 78,096 个参数。输出六阶段 logits、5/15/30 秒的 0.1/0.5/0.9 分位预测及测量质量 logit。

训练窗口由产品自身的 JS engine 和 features 生成，避免 Python/JS 两套预处理漂移。推理使用历史窗口，不读取未来；训练时未来值仅作为标签，发生新操作的预测跨度不参与损失。输出区间只代表仿真域学习到的分位数，实际覆盖率见报告。

模型在 Worker 中运行，WASM 单线程；模型和运行文件随 App 打包，加载时校验 SHA256。每秒最多一次推理，同步使用会话代次和有效性校验，拒绝重连、步骤变化、操作确认或超时后的过期结果。模型不覆盖规则风险输出；实验阶段估计与默认阶段并列显示。加载/推理失败回退规则，不能显示旧预测。

## 使用

进入 AI 厨房并开始一道菜谱，在 CookX Sense 卡片查看趋势。展开“算法与记录”可开启实验模型、导出数据或在断开设备后运行仿真回放。回放不冒充蓝牙连接；连接真实设备会结束回放。用户确认事件随会话保存。

默认仅本地记录，按登录用户 ID 区分存储键，保留当前最近 2400 点，每约 10 秒保存；“导出本次”和“导出保存记录”均输出 JSON。设备、仿真切换创建新会话，不混入同一记录。浏览器限制存储时提示导出，不发送云端。事件与上下文是人工或软件记录，不能作为自动识别真值。

## 复现与检查

前端：
```shell
cd frontend
npm ci --registry=https://registry.npmjs.org
npm test
npm run build
npx cap sync android
```

训练（Python 3.12，使用独立虚拟环境）：
```shell
python -m pip install -r research/temperature/requirements.txt
python research/temperature/train.py --epochs 10 --train-runs 100
python -m unittest discover -s research/temperature -p "test_*.py"
python research/temperature/fetch_public.py
python research/temperature/evaluate_public.py
```

需要 Node 可执行文件在 PATH 中。训练固定种子 42，完整过程种子分组；100 个训练过程、24 个验证、30 个测试、30 个额外参数域测试。生成的大数据文件在 gitignore 中；模型、配置与结果入库。缺少公开数据时来源核验脚本明确记录失败，不伪造下载或评估成功。

浏览器测试先运行 `npm run dev`，安装 Playwright（或将 `COOKX_PLAYWRIGHT` 指向已安装模块），可用 `COOKX_CHROME` 指定测试浏览器：
```shell
node research/temperature/browser-smoke.cjs
node research/temperature/browser-parity.cjs
```

Android 构建使用 JDK 21、SDK 36、build-tools 35、Gradle 8.14.3。设置本机 JAVA_HOME/ANDROID_HOME 后，在 `frontend/android` 运行 `gradlew assembleDebug`。中文路径在 Windows Android/ESP32 工具中可能受限；使用 ASCII 输出目录或 ASCII 工作副本。仓库不保存本机 SDK 路径或工具链。

固件编译（不自动烧写）：
```shell
arduino-cli compile --fqbn esp32:esp32:esp32 --build-path <ASCII临时目录> firmware/cookx_sense
```
验证使用 ESP32 core 3.3.11、Adafruit MLX90614 2.1.6、BusIO 1.17.4。烧写前由硬件负责人核对开发板型号、供电与原接线。

## 后续实机验收

固定与移动安装分别记录锅具、测距、视角、传感器完整型号、参考测温、操作时刻和场景。测试真实缺测、遮挡、投料及干扰，不以故意起火采样。得到独立实机数据后再校准质量阈值、温区与模型置信度，并重新进行模型发布评估。当前版本不能替代人工看护或独立安全保护装置。
