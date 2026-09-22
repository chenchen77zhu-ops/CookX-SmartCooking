# 人员二第二批交付跟踪

后端基线 main 3bc9da2；先普通合并进旧分支链，再从 B4 建新分支。禁止云端合并、强推或推送 integration 分支。

| 功能 | 分支 | 状态 |
|---|---|---|
| 库存收尾 | feat/p2-inventory-edit-completion | 开发中 |
| B9 | feat/p2-recipe-validation | 待开发 |
| B5 | feat/p2-cooking-session | 待开发 |
| B6 | feat/p2-voice-commands | 待开发 |
| B7 | feat/p2-cooking-adjustments | 待开发 |
| B8 | feat/p2-cooking-reminders | 待开发 |
| 设备诊断 | feat/p2-device-diagnostics | 待开发 |

后端按名称扣减、无幂等键、小数保质期批次 key 转整数，以及旧字段回退仍由主人负责。测试替身与真机验收分开记录。
