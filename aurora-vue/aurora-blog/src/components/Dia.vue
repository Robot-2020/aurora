<template>
  <transition name="fade-bounce-y" mode="out-in">
    <div v-show="showDia" id="bot-container">
      <div id="Aurora-Dia--body" :style="cssVariables">
        <div id="Aurora-Dia--tips-wrapper">
          <div id="Aurora-Dia--tips" class="Aurora-Dia--tips">{{ currentTip }}</div>
        </div>
        <div id="Aurora-Dia" class="Aurora-Dia" @click="openDialog">
          <div id="Aurora-Dia--eyes" class="Aurora-Dia--eyes">
            <div id="Aurora-Dia--left-eye" class="Aurora-Dia--eye left"></div>
            <div id="Aurora-Dia--right-eye" class="Aurora-Dia--eye right"></div>
          </div>
        </div>
        <div class="Aurora-Dia--platform"></div>
      </div>
    </div>
  </transition>

  <el-dialog v-model="dialogVisible" title="Dia AI 助手" width="500px">
    <div class="chat-dialog">
      <!-- 欢迎语区域 -->
      <div v-if="messages.length === 0" class="welcome-section">
        <div class="welcome-message">
          <h3>👋 你好！我是你的AI助手 Dia</h3>
          <p>我可以帮你解答各种问题，有什么想了解的吗？</p>
        </div>
      </div>
      
      <!-- 聊天消息区域 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-for="(message, index) in messages" :key="index" 
             :class="['message', message.type]">
          <div class="message-content">
            <div v-if="message.type === 'bot' && message.isStreaming" class="streaming-dots">
              <span></span><span></span><span></span>
            </div>
            <div v-else-if="message.type === 'bot'" class="message-text" v-html="formatMessage(message.text)"></div>
            <div v-else class="message-text">{{ message.text }}</div>
          </div>
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="input-section">
        <el-input
          v-model="userInput"
          type="textarea"
          :rows="2"
          placeholder="请输入您的问题..."
          @keydown.enter.prevent="handleSendMessage"
          :disabled="isLoading"
        ></el-input>
        <el-button 
          type="primary" 
          @click="handleSendMessage" 
          :loading="isLoading"
          class="send-button"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script lang="ts">
// @ts-nocheck
import { computed, defineComponent, onMounted, ref, nextTick } from 'vue'
import { useDiaStore } from '@/stores/dia'
import { useAppStore } from '@/stores/app'
import { ElMessage } from 'element-plus'
import api from '@/api/api'

export default defineComponent({
  name: 'Dia',
  setup() {
    const diaStore = useDiaStore()
    const appStore = useAppStore()
    const showDia = ref(false)
    const dialogVisible = ref(false)
    const userInput = ref('')
    const isLoading = ref(false)
    const messages = ref([] as Array<{type: string, text: string, isStreaming?: boolean}>)
    const messagesContainer = ref<HTMLElement>()
    
    onMounted(() => {
      initializeBot()
    })

    const initializeBot = () => {
      if (!appStore.aurora_bot_enable) return
      diaStore.initializeBot({
        locale: diaStore.aurora_bot.locale,
        tips: diaStore.aurora_bot.tips
      })
      setTimeout(() => {
        showDia.value = true
      }, 1000)
    }

    const openDialog = () => {
      dialogVisible.value = true
      // 清空之前的对话
      messages.value = []
    }

    const handleSendMessage = async () => {
      if (!userInput.value.trim() || isLoading.value) return

      const question = userInput.value.trim()
      userInput.value = ''
      
      // 添加用户消息
      messages.value.push({
        type: 'user',
        text: question
      })
      
      // 添加机器人消息（流式输出）
      const botMessageIndex = messages.value.push({
        type: 'bot',
        text: '',
        isStreaming: true
      }) - 1
      
      isLoading.value = true
      
      try {
        // 调用后端API - 这里是模拟调用，您需要根据实际后端接口调整
        await simulateStreamingResponse(botMessageIndex, question)
      } catch (error) {
        console.error('对话失败:', error)
        messages.value[botMessageIndex] = {
          type: 'bot',
          text: '抱歉，我目前无法处理您的请求。请稍后重试。'
        }
        ElMessage({
          message: '对话请求失败',
          type: 'error',
          duration: 3000
        })
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    const simulateStreamingResponse = async (messageIndex: number, question: string) => {
      // 模拟流式输出效果
      const responses = {
        '你好': '你好！我是Dia，很高兴为你服务。有什么我可以帮助你的吗？',
        '博客': '这是一个基于Vue的现代化博客系统，包含前台博客展示和后台管理系统。',
        '帮助': '我可以帮你解答关于这个博客系统的各种问题，或者聊天交流。',
        '技术': '这个项目使用了Vue 3、TypeScript、Element Plus等现代前端技术栈。',
        '默认': '这个问题很有趣！虽然我现在还不能提供具体的答案，但您可以尝试查阅相关文档或在博客中进行搜索。'
      }
      
      const response = responses[question] || responses['默认']
      const chars = response.split('')
      let currentText = ''
      
      return new Promise<void>((resolve) => {
        const interval = setInterval(() => {
          if (chars.length === 0) {
            clearInterval(interval)
            messages.value[messageIndex] = {
              type: 'bot',
              text: currentText
            }
            resolve()
            return
          }
          
          currentText += chars.shift()
          messages.value[messageIndex] = {
            type: 'bot',
            text: currentText,
            isStreaming: chars.length > 0
          }
          
          scrollToBottom()
        }, 50)
      })
    }

    const formatMessage = (text: string) => {
      // 简单的消息格式化，支持换行
      return text.replace(/\n/g, '<br>')
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    const currentTip = computed(() => {
      const hour = new Date().getHours()
      if (hour >= 5 && hour < 12) return '早上好！点击我聊天～'
      if (hour >= 12 && hour < 18) return '下午好！有什么问题吗？'
      return '晚上好！需要帮助吗？'
    })

    return {
      cssVariables: computed(() => {
        return `
          --aurora-dia--linear-gradient: ${appStore.themeConfig.header_gradient_css};
          --aurora-dia--linear-gradient-hover: linear-gradient(
            to bottom,
            ${appStore.themeConfig.gradient.color_2},
            ${appStore.themeConfig.gradient.color_3}
          );
          --aurora-dia--platform-light: ${appStore.themeConfig.gradient.color_3};
        `
      }),
      showDia,
      dialogVisible,
      userInput,
      isLoading,
      messages,
      messagesContainer,
      currentTip,
      openDialog,
      handleSendMessage,
      formatMessage,
      scrollToBottom
    }
  }
})
</script>

<style lang="scss" scoped>
#bot-container {
  position: fixed;
  left: 20px;
  bottom: 0;
  z-index: 1000;
  width: 70px;
  height: 60px;
}
#Aurora-Dia--body {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  width: 100%;
  height: 100%;
  --auora-dia--width: 65px; /* 110px */
  --auora-dia--height: 50px; /* 95px */
  --auora-dia--hover-height: 60px; /* 105px */
  --auora-dia--jump-1: 55px; /* 95px */
  --auora-dia--jump-2: 60px; /* 100px */
  --auora-dia--jump-3: 45px; /* 85px */
  --auora-dia--eye-top: 10px; /* 25px */
  --auora-dia--eye-height: 15px; /* 25px */
  --auora-dia--eye-width: 8px; /* 15px */
  --auora-dia--eye-top: 10px; /* 20px */
  --auora-dia--platform-size: var(--auora-dia--jump-2); /* 100px */
  --auora-dia--platform-size-shake-1: 75px; /* 115px */
  --auora-dia--platform-size-shake-2: 45px; /* 115px */
  --auora-dia--platform-top: -15px; /* 0 */
  --aurora-dia--linear-gradient: var(--main-gradient); /* linear-gradient(to bottom, #5fc, #1a8) */
  --aurora-dia--linear-gradient-hover: linear-gradient(to bottom, #25b0cc, #3f60de);
  --aurora-dia--platform-light: #b712ac;
}
.Aurora-Dia {
  position: absolute;
  bottom: 30px;
  width: var(--auora-dia--width);
  height: var(--auora-dia--height);
  border-radius: 45%;
  border: 4px solid var(--background-secondary);
  // box-shadow: 0 0 5px rgba(0, 0, 0, 0.5);
  animation: breathe-and-jump 3s linear infinite;
  cursor: pointer;
  z-index: 1;
}
.Aurora-Dia::before {
  content: '';
  position: absolute;
  top: -1px;
  left: -1px;
  width: calc(100% + 3px);
  height: calc(100% + 2px);
  background-color: #2cdcff;
  background: var(--aurora-dia--linear-gradient);
  border-radius: 45%;
  opacity: 0;
  opacity: 1;
  transition: 0.3s linear all;
}
.Aurora-Dia.active {
  animation: deactivate 0.75s linear, bounce-then-breathe 5s linear infinite;
}

.Aurora-Dia--eyes > .Aurora-Dia--eye {
  position: absolute;
  top: var(--auora-dia--eye-top);
  width: var(--auora-dia--eye-width);
  height: var(--auora-dia--eye-height);
  border-radius: 15px;
  background-color: #fff;
  box-shadow: 0 0 7px rgba(255, 255, 255, 0.5);
  animation: blink 5s linear infinite;
}
.Aurora-Dia--eyes > .Aurora-Dia--eye.left {
  left: 25%;
}
.Aurora-Dia--eyes > .Aurora-Dia--eye.right {
  right: 25%;
}
.Aurora-Dia--eyes.moving > .Aurora-Dia--eye {
  animation: none;
}

.Aurora-Dia--platform {
  position: relative;
  top: 0;
  transform: rotateX(70deg);
  width: var(--auora-dia--platform-size);
  height: var(--auora-dia--platform-size);
  box-shadow: 0 0 var(--auora-dia--platform-size) var(--aurora-dia--platform-light),
    0 0 15px var(--aurora-dia--platform-light) inset;
  animation: jump-pulse 3s linear infinite;
}

.Aurora-Dia--platform {
  border-radius: 50%;
  transition: 0.2s linear all;
}

.Aurora-Dia:hover {
  animation: shake-to-alert 0.5s linear;
  height: var(--auora-dia--hover-height);
  transform: translateY(-7px);
}
.Aurora-Dia:hover::before {
  background: var(--aurora-dia--linear-gradient-hover);
}
.Aurora-Dia:hover,
.Aurora-Dia:hover > .Aurora-Dia--eyes > .Aurora--Dia-eye {
  border-color: var(--text-accent);
  box-shadow: 0 0 5px var(--text-accent);
}
.Aurora-Dia:hover + .Aurora-Dia--platform {
  box-shadow: 0 0 var(--auora-dia--platform-size) var(--text-accent), 0 0 15px var(--text-accent) inset;
  animation: shake-pulse 0.5s linear;
}

#Aurora-Dia--tips-wrapper {
  position: absolute;
  bottom: 80px;
  right: -120px;
  width: 200px;
  min-height: 60px;
  background: var(--aurora-dia--linear-gradient);
  color: var(--text-normal);
  padding: 0.2rem;
  border-radius: 8px;
  opacity: 0;
  animation: tips-breathe 3s linear infinite;
  transition: 0.3s linear opacity;
}

#Aurora-Dia--tips-wrapper.active {
  opacity: 0.86;
}

.Aurora-Dia--tips {
  position: relative;
  height: 100%;
  width: 100%;
  min-height: 60px;
  border-radius: 6px;
  padding: 0.2rem 0.5rem;
  font-size: 0.8rem;
  font-weight: 800;
  background: var(--background-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
}

.Aurora-Dia--tips > span {
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  padding: 0 0.1rem;
  color: #7aa2f7;
  background-color: #7aa2f7;
  background-image: var(--strong-gradient);
}

@keyframes deactivate {
  0% {
    border-color: var(--text-sub-accent);
  }
  20%,
  60% {
    border-color: var(--text-accent);
  }
  40%,
  80%,
  100% {
    border-color: var(--background-secondary);
  }
}

@keyframes tips-breathe {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-5px);
  }
}

@keyframes bounce-then-breathe {
  0%,
  5%,
  10%,
  15% {
    transform: translateY(0);
  }
  2.5%,
  7.5%,
  12.5% {
    transform: translateY(-15px);
  }
  20%,
  40%,
  60%,
  80%,
  100% {
    height: var(--auora-dia--jump-1);
    transform: translateY(0);
  }
  30%,
  50%,
  70%,
  90% {
    height: var(--auora-dia--jump-2);
    transform: translateY(-5px);
  }
}

@keyframes breathe-and-jump {
  0%,
  40%,
  80%,
  100% {
    height: var(--auora-dia--jump-1);
    transform: translateY(0);
  }
  20%,
  60%,
  70%,
  90% {
    height: var(--auora-dia--jump-2);
    transform: translateY(-5px);
  }
  85% {
    height: var(--auora-dia--jump-3);
    transform: translateY(-20px);
  }
}

@keyframes blink {
  0%,
  100% {
    transform: scale(1, 0.05);
  }
  5%,
  95% {
    transform: scale(1, 1);
  }
}

@keyframes jump-pulse {
  0%,
  40%,
  80%,
  100% {
    box-shadow: 0 0 30px var(--aurora-dia--platform-light), 0 0 45px var(--aurora-dia--platform-light) inset;
  }
  20%,
  60%,
  70%,
  90% {
    box-shadow: 0 0 70px var(--aurora-dia--platform-light), 0 0 25px var(--aurora-dia--platform-light) inset;
  }
  85% {
    box-shadow: 0 0 100px var(--aurora-dia--platform-light), 0 0 15px var(--aurora-dia--platform-light) inset;
  }
}

@keyframes shake-to-alert {
  0%,
  20%,
  40%,
  60%,
  80%,
  100% {
    transform: rotate(0) translateY(-8px);
  }
  10%,
  25%,
  35%,
  50%,
  65% {
    transform: rotate(7deg) translateY(-8px);
  }
  15%,
  30%,
  45%,
  55%,
  70% {
    transform: roate(-7deg) translateY(-8px);
  }
}

@keyframes shake-pulse {
  0%,
  20%,
  40%,
  60%,
  80%,
  100% {
    box-shadow: 0 0 var(--auora-dia--platform-size) #2cdcff, 0 0 15px #2cdcff inset;
  }
  10%,
  25%,
  35%,
  50%,
  65% {
    box-shadow: 0 0 var(--auora-dia--platform-size-shake-1) #2cdcff, 0 0 15px #2cdcff inset;
  }
  15%,
  30%,
  45%,
  55%,
  70% {
    box-shadow: 0 0 var(--auora-dia--platform-size-shake-2) #2cdcff, 0 0 15px #2cdcff inset;
  }
}
</style>

<style lang="scss">
.Aurora-Dia--tips > span {
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  padding: 0 0.05rem;
  color: #7aa2f7;
  background-color: #7aa2f7;
  background-image: var(--strong-gradient);
}

/* 聊天对话框样式 */
.chat-dialog {
  height: 400px;
  display: flex;
  flex-direction: column;
}

.welcome-section {
  text-align: center;
  padding: 20px;
}

.welcome-message h3 {
  margin: 0 0 10px 0;
  color: var(--text-accent);
}

.welcome-message p {
  margin: 0;
  color: var(--text-normal);
  opacity: 0.8;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  margin: 20px 0;
  max-height: 250px;
  padding: 10px;
  background: var(--background-secondary);
  border-radius: 8px;
}

.message {
  margin-bottom: 15px;
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-content {
  background: var(--main-gradient);
  color: white;
  border-radius: 15px 15px 0 15px;
}

.message.bot .message-content {
  background: var(--background-primary);
  color: var(--text-normal);
  border: 1px solid var(--background-secondary);
  border-radius: 15px 15px 15px 0;
}

.message-content {
  max-width: 80%;
  padding: 10px 15px;
  word-wrap: break-word;
}

.message-text {
  line-height: 1.4;
}

.streaming-dots {
  display: flex;
  align-items: center;
  padding: 5px 0;
}

.streaming-dots span {
  height: 8px;
  width: 8px;
  margin: 0 2px;
  background-color: var(--text-normal);
  border-radius: 50%;
  display: inline-block;
  animation: bounce 1.5s infinite ease-in-out;
}

.streaming-dots span:nth-child(1) {
  animation-delay: -0.3s;
}

.streaming-dots span:nth-child(2) {
  animation-delay: -0.15s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-section {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-section :deep(.el-textarea) {
  flex: 1;
}

.input-section :deep(.el-textarea .el-textarea__inner) {
  background: var(--background-secondary);
  border-color: var(--border-color);
  color: var(--text-normal);
  resize: none;
}

.send-button {
  height: 56px;
}

:deep(.el-dialog) {
  background: var(--background-primary) !important;
  border-radius: 12px;
  
  .el-dialog__header {
    border-bottom: 1px solid var(--background-secondary);
    
    .el-dialog__title {
      color: var(--text-accent);
    }
  }
  
  .el-dialog__body {
    background: var(--background-primary) !important;
    padding: 20px;
  }
}
</style>
