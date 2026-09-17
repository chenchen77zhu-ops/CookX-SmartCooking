# 2026 国赛 C 题提交版代码说明

## 当前结论

当前工作区 E:\ICAN\SmartCooking 是“智感鲜烹”应用项目。经对项目内全部非依赖 Python
源码、所有文件名、Git 全历史及全部分支进行盘点，未发现 2026 国赛 C 题的题目附件、标准化
数据、结果目录或四问核心求解源码。

因此，本次不能在“不改变模型、参数、计算口径和结果”的前提下生成以下文件：

- data_preprocessing.py
- question1.py
- question2.py
- question3.py
- question4.py

以上文件未创建。创建空壳或依据论文、结果表反推实现都会造成不可验证的伪提交，违反“找不到
问题一或问题二完整核心求解代码时不得臆造/补写模型”以及“所有输出必须由真实计算产生”的要求。

## 预期文件用途与当前状态

| 预期文件 | 预期用途 | 当前状态 |
| --- | --- | --- |
| data_preprocessing.py | 附件读取校验、时间轴统一、10 分钟尺度转换、缺失值处理 | 缺少赛题附件和原预处理源码，无法整合 |
| question1.py | 问题一完整优化建模、求解和结果输出 | 未找到主求解源码，已停止整合 |
| question2.py | 预测、误差风险修正、日前优化、紧急购电评价 | 未找到主求解源码，已停止整合 |
| question3.py | 多节点预测更新、滚动优化、调整费用 | 未找到 question3_rolling.py 或等价源码 |
| question4.py | 实时电价预测及 Q4-2/Q4-3 模型 | 未找到 question4_realtime_price.py 或等价源码 |

## 原始脚本盘点与分类

已完整读取当前项目中发现的 14 个非依赖 Python 文件。

| 原始脚本 | 分类 | 与国赛 C 题的关系 / 排除原因 |
| --- | --- | --- |
| app/main.py | 智能厨房 Web/API 业务代码 | 不包含电力优化、SOC、预测或实时电价模型 |
| app/models/user.py | 用户 JSON 数据访问 | 与赛题无关 |
| app/models/food_detector.py | YOLO 食材识别 | 与赛题无关 |
| app/services/deepseek_service.py | 菜谱大模型服务 | 与赛题无关 |
| app/services/qwen_service.py | 图像食材识别服务 | 与赛题无关 |
| app/services/sms_service.py | 短信验证码模拟 | 与赛题无关 |
| app/services/tts_service.py | 语音合成 | 与赛题无关 |
| app/core/config.py | 智能厨房应用配置 | 与赛题无关 |
| test_model.py | YOLO 临时测试/诊断 | 非赛题核心代码 |
| train_yolo/train.py | 食材识别模型训练 | 非赛题核心代码 |
| app/__init__.py | 空包初始化文件 | 无计算逻辑 |
| app/api/__init__.py | 空包初始化文件 | 无计算逻辑 |
| app/api/inventory.py | 空文件 | 无计算逻辑 |
| app/api/recipe.py | 空文件 | 无计算逻辑 |

项目根目录中的 PPT、图片及 .inspect.ndjson 文件属于“智感鲜烹”答辩材料或演示资产，
不属于 2026 国赛 C 题的数据、论文或结果，未纳入提交版。

## 核心源文件映射

| 提交版目标 | 应有原始来源 | 实际找到的来源 |
| --- | --- | --- |
| 数据预处理 | 附件读取/预处理主脚本 | 无 |
| 问题一 | 问题一完整主求解脚本 | 无 |
| 问题二 | 问题二预测、风险修正与日前优化主脚本 | 无 |
| 问题三 | question3_rolling.py 或论文最终使用版本 | 无 |
| 问题四 | question4_realtime_price.py 或论文最终使用版本 | 无 |

无法判断同名脚本的最终版本，也无法建立“原始脚本 → 提交版脚本”的真实映射。

## 被排除的非核心内容

- 智能厨房 FastAPI、用户、库存、短信、语音和菜谱服务：业务领域不符。
- YOLO 食材识别训练及诊断脚本：模型目标与赛题不符。
- PPT、图片、布局检查和答辩材料：展示资产，不是计算源码。
- .venv、Android/前端构建目录和第三方依赖：生成物或外部依赖，不属于项目核心源码。

未发现可供分类的国赛绘图脚本、结果验证脚本、Word/Excel 论文生成脚本、旧版本或消融脚本。

## 输入附件与输出文件

当前缺失：

- 2026 国赛 C 题原始附件及字段说明；
- 时间序列的起止时间、原始时间粒度和单位说明；
- result1、result2、result3、result4 或等价最终结果；
- 四问最终论文实际引用的代码版本；
- 原求解器及版本、随机种子和求解容差信息。

由于输入与模型源码均缺失，不能定义可信的输入文件名、输出字段或单位。

## 依赖库

无法从当前 SmartCooking 项目的依赖推断赛题依赖。提交版应以原始赛题脚本实际导入项为准，
不能擅自假设使用 NumPy、pandas、SciPy、PuLP、Pyomo、Gurobi 或其他求解器。

## 运行命令

当前没有可安全生成和运行的赛题脚本，因此以下预期命令暂不可用：

    python data_preprocessing.py --data-dir <附件目录> --output-dir <结果目录>
    python question1.py --data-dir <附件目录> --output-dir <结果目录>
    python question2.py --data-dir <附件目录> --output-dir <结果目录>
    python question3.py --data-dir <附件目录> --output-dir <结果目录>
    python question4.py --data-dir <附件目录> --output-dir <结果目录>

## 验证结果

| 验证项 | 结果 | 原因 |
| --- | --- | --- |
| --help | 无法验证 | 五个核心脚本因源文件缺失未创建 |
| 数据维度、NaN/inf | 无法验证 | 缺少附件与预处理源码 |
| SOC、供需平衡、非负性 | 无法验证 | 缺少优化模型源码 |
| 与原始最终结果逐项比较 | 无法验证 | 缺少最终结果及原始可运行版本 |
| 差异不超过 1e-6 | 无法声明 | 没有可比较的计算输出 |

本报告没有伪造任何运行成功、约束通过或结果一致结论。

## 当前无法独立复现项

问题一、问题二、问题三、问题四及公共数据预处理均无法独立复现。恢复工作至少需要提供：

1. 完整赛题代码目录，尤其是问题一、问题二主求解脚本；
2. question3_rolling.py、question4_realtime_price.py；
3. 全部赛题附件；
4. 最终结果目录及论文实际引用版本；
5. 求解环境或依赖清单。

收到上述材料后，才能按模型原貌整理五个独立脚本，并执行逐项 1e-6 结果比对。
