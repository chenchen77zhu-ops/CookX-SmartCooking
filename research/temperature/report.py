import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
D=ROOT/"docs/temperature"
r=json.loads((D/"experiment-results.json").read_text(encoding="utf-8"))
public=json.loads((D/"public-risk-results.json").read_text(encoding="utf-8"))
lines=["# 温度算法实验报告","","## 结论","",
"这是软件研究与集成版本。Transformer 在独立仿真过程上的阶段识别优于动态规则，但跨参数域的误报并未全面改善；NIST 外部风险测试也存在召回与误报的取舍。保持规则默认、模型实验开关，不将仿真成绩视为原型机实测。","","## 数据与方法","",
"100 个训练过程、24 个验证过程、30 个测试过程、30 个参数域外过程；各组种子互不重叠。窗口按完整过程分组。额外参数域使用 1100–1500 J/K 锅体热容与更长目标切换扰动；训练使用 500–1000 J/K。合成数据来自未做实机校准的两节点热过程模型，参数假设见 sources.json。","",
"主模型为两层四头、64 维 Transformer，共 78,096 参数；TCN 为 16,816 参数。各模型训练 10 轮，固定种子 42，验证集交叉熵选择权重。未来发生用户操作的预测跨度不参与预测损失。评估每 4 秒取一个因果窗口。质量门控和低模型概率可以拒绝判断。","",
"阶段 Macro F1 固定六类；unknown 是拒绝/不可识别类。另报拒绝率及接受样本错误率，避免把大量拒绝包装为高精度。事件为阶段转换，匹配正确类别且在真实转换后 0–20 秒内首次出现的预测；未匹配的预测转换计误报，未匹配的真实转换计漏报，延迟仅统计匹配事件。","",
"原固定温度页面没有六阶段语义，JSON 中的 fixed_threshold_proxy 仅用于说明其表达能力，不能视为同任务的公平分类基线；以下以动态规则为主要基线。","","## 阶段识别与消融","",
"| 方法 | 测试 Macro F1 | 域外 Macro F1 | 测试拒绝率 | 测试接受样本错误率 |","|---|---:|---:|---:|---:|"]
b=r["baselines"]
lines.append(f"| 动态规则 | {b['test']['dynamic_rules']['macro_f1']:.3f} | {b['ood']['dynamic_rules']['macro_f1']:.3f} | {b['test']['dynamic_rules']['abstention_fraction']:.1%} | {b['test']['dynamic_rules']['accepted_error_rate']:.1%} |")
for k,label in [("transformer","Transformer"),("tcn","TCN"),("without_context","去上下文"),("without_motion_training","去移动扰动训练")]:
    v=r["models"][k]["evaluation"];t=v["test"];o=v["ood"]
    lines.append(f"| {label} | {t['macro_f1']:.3f} | {o['macro_f1']:.3f} | {t['abstention_fraction']:.1%} | {t['accepted_error_rate']:.1%} |")
v=r["without_quality_gate"]
lines.append(f"| 去质量门控 | {v['test']['macro_f1']:.3f} | {v['ood']['macro_f1']:.3f} | {v['test']['abstention_fraction']:.1%} | {v['test']['accepted_error_rate']:.1%} |")
lines+=["","去质量门控会重新生成无门控分段的输入并直接使用模型分类，移除了质量与概率拒绝策略，属于整组质量处理的消融，不是单一阈值的因果归因。上下文消融只移除模型温区特征，规则分段与确认事件保持相同。","",
"## 温度预测与事件","", "| 数据域 | 5秒 MAE | 15秒 MAE | 30秒 MAE | 80%区间实际覆盖率（5/15/30秒） |","|---|---:|---:|---:|---|"]
for split in ["test","ood"]:
    v=r["models"]["transformer"]["evaluation"][split]
    a=v["forecast_mae_C"];c=v["interval_80_coverage"]
    lines.append(f"| {split} | {a[0]:.2f}℃ | {a[1]:.2f}℃ | {a[2]:.2f}℃ | {c[0]:.1%} / {c[1]:.1%} / {c[2]:.1%} |")
lines+=["","覆盖率尚未达到各场景一致的标称 80%，不作为真实置信保证。以上预测误差在有有效目标的仿真窗口计算，含被分类拒绝的窗口；不只挑选已接受的好预测。","",
"| 方法与域 | 事件误报 | 事件漏报 | 已匹配事件平均延迟 |","|---|---:|---:|---:|"]
for split in ["test","ood"]:
    for name,v in [("动态规则",r["baselines"][split]["dynamic_rules"]),("Transformer",r["models"]["transformer"]["evaluation"][split])]:
        lines.append(f"| {name} / {split} | {v['event_false_positives']} | {v['event_misses']} | {v['mean_event_delay_s']:.2f} 秒 |")
lines+=["","## NIST 外部数据验证","",
"实际下载官方原始 XLSX 并记录 SHA256。原始文件含 60 次实验，除了排风管传感器，还有锅面 Type-K 热电偶。外部评估仅使用 Pan TC center 和原始 Pre-Ignition 标签；没有用于训练或调阈值。保留 4 秒采样，不伪装成 2 Hz 实测；只在评估器将允许采样间隔设为 5 秒。无阶段真值，因此不报告外部六阶段 F1。","",
"| 方法 | 阳性样本召回 | 正常样本误报率 | 误报事件数 | 异常过程召回 | 已检出过程平均延迟 |","|---|---:|---:|---:|---:|---:|"]
for k,v in public["models"].items():
    lines.append(f"| {k} | {v['positiveSampleRecall']:.1%} | {v['negativeSampleFalsePositiveRate']:.1%} | {v['falseAlarmEpisodes']} | {v['eventRecall']:.1%} | {v['meanDetectionDelaySeconds']:.2f} 秒 |")
lines+=["","45 次实验含 Pre-Ignition 阳性区间；该标签是超出正常烹饪条件，不等同于已经起火。原数据中实际发生起火的实验数也不是该事件数。未知/超传感器范围的样本计拒绝，阳性时计漏报；没有裁剪高温值以制造高召回。事件召回按阳性区间内至少一次报警计，不能替代逐样本召回。","",
"新规则的正常样本误报率下降，但阳性样本召回下降、平均延迟上升，误报事件数也未减少。不能宣称预警全面优于旧方案。器件类型、测点、采样率与烹饪条件均与原型不同；这是外部风险研究，不是硬件安全测试。模型不会覆盖规则风险，故没有虚构“Transformer 风险召回提升”。","",
"## 部署与验证","",
f"模型文件 {r['model_bytes']:,} 字节，低于 2 MB。Python 与 ONNX 最大绝对误差 {r['onnx_max_abs_error']:.3g}；桌面 ONNX CPU 推理 P95 {r['desktop_onnx_p95_ms']:.3f} ms。桌面结果不能当作手机性能。"]
if (D/"wasm-results.json").exists():
    w=json.loads((D/"wasm-results.json").read_text())
    lines += [f"桌面 Chromium WASM 连续 100 次推理 P95 {w['wasmP95Ms']:.3f} ms，最大绝对误差 {w['maxAbsError']:.3g}，测试中外部网络请求为 {w['externalNetworkRequests']}。"]
lines+=["","前端单元测试、仿真测试、浏览器回放/模型失效测试及编译结果见 validation.json。实机蓝牙、真实移动测温和 Android 手机 P95 未测试；发布门槛尚未满足，模型保持实验状态。","",
"## 来源","",
"- [NIST 原始数据与说明](https://doi.org/10.18434/M32171)","- [MLX90614 厂商资料](https://www.melexis.com/en/documents/documentation/datasheets/datasheet-mlx90614)","- [热过程仿真方法参考](https://doi.org/10.1016/j.jfoodeng.2023.111697)"]
(D/"EXPERIMENTS.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
