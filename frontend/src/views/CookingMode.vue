<template>
  <div class="cooking-nav-page">
    <!-- 1. 顶部标题 -->
    <div class="nav-header">
      <el-tag type="success" effect="dark">步骤 {{ currentIndex + 1 }} / {{ recipe.steps.length }}</el-tag>
      <div class="voice-indicator">🎙️ 语音控制中</div>
    </div>

    <!-- 2. 步骤文本区域 -->
    <div class="step-content-box">
      <h2 class="step-text-display">{{ currentStep.text }}</h2>
    </div>

    <!-- 3. 一键提醒按钮（如果有时间） -->
    <div class="action-section" v-if="currentStep.time_estimate >= 60">
       <el-button type="warning" icon="AlarmClock" round @click="setReminder">一键提醒</el-button>
    </div>

    <!-- 4. 进度条区域 -->
    <div class="progress-section">
      <el-progress
        type="circle"
        :percentage="progress"
        :stroke-width="12"
        :width="180"
        color="#67C23A"
      >
        <div class="timer-display">
          <span class="time-val">{{ formatTime(timeLeft) }}</span>
          <span class="time-label">剩余时间</span>
        </div>
      </el-progress>

      <div class="time-adjust-btns">
        <el-button size="small" @click="timeLeft += 60">+1分</el-button>
        <el-button size="small" @click="timeLeft = Math.max(0, timeLeft - 60)">-1分</el-button>
      </div>
    </div>

    <!-- 5. 底部控制栏 -->
    <div class="footer-controls">
      <el-button size="large" circle @click="pause = !pause">
        <el-icon><VideoPause v-if="!pause"/><VideoPlay v-else/></el-icon>
      </el-button>

      <el-button type="primary" size="large" class="next-btn" @click="nextStep">
        {{ currentIndex === recipe.steps.length - 1 ? '完成烹饪' : '下一步' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
/* 1. 弹窗内容整体容器：强制垂直堆叠并居中 */
.nav-content {
  display: flex;
  flex-direction: column;
  align-items: center; /* ✅ 所有子组件水平居中 */
  text-align: center;
  padding: 10px 0 20px;
}

/* 2. 顶部状态栏：步骤标签和语音状态 */
.step-header {
  width: 100%;
  display: flex;
  justify-content: center; /* 改为居中，原为 space-between */
  align-items: center;
  gap: 15px; /* 两个标签之间的距离 */
  margin-bottom: 25px; /* 与下方文字的距离 */
}

/* 3. 步骤描述文字：大字加粗，增加行高 */
.nav-step-text {
  font-size: 20px;
  font-weight: bold;
  line-height: 1.6;
  color: #2c3e50;
  margin: 0 0 25px 0; /* 下方留出较多空隙 */
  padding: 0 10px;
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 4. 一键提醒按钮区域 */
.reminder-action {
  margin-bottom: 30px; /* 与进度条拉开距离 */
}

/* 5. 计时器/进度条区域 */
.timer-box {
  margin-bottom: 35px; /* 与底部切换按钮拉开距离 */
  display: flex;
  flex-direction: column;
  align-items: center;
}

.timer-text {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.timer-text .seconds {
  font-size: 26px;
  font-weight: 800;
  color: #409EFF;
}

.timer-text .unit {
  font-size: 12px;
  color: #999;
}

/* 6. 时间微调按钮 (+1/-1分) */
.time-adjust {
  display: flex;
  gap: 12px;
  margin-top: 15px;
}

/* 7. 底部控制按钮 (上一步/下一步) */
.nav-controls {
  display: flex;
  justify-content: center;
  gap: 20px;
  width: 100%;
  margin-top: 10px;
}

/* 让“下一步”按钮稍微宽一点，突出主要操作 */
.nav-controls .el-button--primary {
  padding-left: 30px;
  padding-right: 30px;
}

/* 8. 底部提示语 */
.hint {
  margin-top: 25px;
  font-size: 12px;
  color: #abbac1;
}

/* 语音状态的小呼吸灯动画 */
.voice-status.active {
  color: #67C23A;
  font-weight: bold;
  animation: blink 2s infinite;
}

@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}
</style>