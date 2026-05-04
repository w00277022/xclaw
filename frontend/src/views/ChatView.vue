<template>
  <div style="display: flex; height: 100%">
    <!-- Left: XClaw list -->
    <div style="width: 280px; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column">
      <div style="padding: 15px; border-bottom: 1px solid #e4e7ed; font-weight: bold">💬 选择 XClaw</div>
      <div style="flex: 1; overflow-y: auto">
        <div v-for="inst in instances" :key="inst.id"
          @click="selectInstance(inst)"
          :style="{ padding: '12px 15px', cursor: 'pointer', borderBottom: '1px solid #f0f0f0', background: selected?.id === inst.id ? '#ecf5ff' : '' }">
          <div style="font-weight: 500">{{ inst.name }}</div>
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            <el-tag size="small" :type="statusType(inst.status)">{{ inst.status }}</el-tag>
            <el-tag v-if="inst.errorMsg && (inst.errorMsg.includes('降级') || inst.errorMsg.includes('代理'))" size="small" type="warning" style="margin-left: 4px">代理</el-tag>
            <span style="margin-left: 8px">:{{ inst.port }}</span>
          </div>
        </div>
        <div v-if="!instances.length" style="padding: 30px; text-align: center; color: #999">暂无实例</div>
      </div>
    </div>
    <!-- Right: Chat area -->
    <div style="flex: 1; display: flex; flex-direction: column">
      <template v-if="selected">
        <div style="padding: 15px; border-bottom: 1px solid #e4e7ed; font-weight: bold">
          {{ selected.name }}
          <el-tag size="small" :type="statusType(selected.status)" style="margin-left: 10px">{{ selected.status }}</el-tag>
          <el-tag v-if="selected.errorMsg && (selected.errorMsg.includes('降级') || selected.errorMsg.includes('代理'))" size="small" type="warning" style="margin-left: 4px">代理模式</el-tag>
        </div>
        <!-- Messages -->
        <div ref="messagesRef" style="flex: 1; overflow-y: auto; padding: 20px">
          <div v-for="msg in messages" :key="msg.id" :style="{ marginBottom: '15px', display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }">
            <div :style="{
              maxWidth: '70%', padding: '10px 15px', borderRadius: '12px',
              background: msg.role === 'user' ? '#409EFF' : '#f4f4f5',
              color: msg.role === 'user' ? '#fff' : '#333'
            }">
              <div style="white-space: pre-wrap; word-break: break-word">{{ msg.content }}</div>
              <div style="font-size: 11px; margin-top: 5px; opacity: 0.6">{{ formatTime(msg.createdAt) }}</div>
            </div>
          </div>
        </div>
        <!-- Input -->
        <div style="padding: 15px; border-top: 1px solid #e4e7ed; display: flex; gap: 10px">
          <el-input v-model="inputText" placeholder="输入消息..." @keyup.enter="sendMessage" :disabled="selected.status !== 'RUNNING'" />
          <el-button type="primary" @click="sendMessage" :disabled="!inputText.trim() || selected.status !== 'RUNNING'">发送</el-button>
        </div>
      </template>
      <div v-else style="flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px">
        ← 请选择一个 XClaw 实例开始对话
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { xclawApi, chatApi } from '../api'

const instances = ref([])
const selected = ref(null)
const messages = ref([])
const inputText = ref('')
const messagesRef = ref(null)
let ws = null

const statusType = (s) => ({ RUNNING: 'success', STOPPED: 'info', ERROR: 'danger' }[s] || '')

const formatTime = (t) => t ? new Date(t).toLocaleTimeString() : ''

const loadData = async () => {
  const { data } = await xclawApi.list()
  instances.value = data
}

const selectInstance = async (inst) => {
  selected.value = inst
  // Load history
  const { data } = await chatApi.history(inst.id)
  messages.value = data
  await nextTick()
  scrollToBottom()
  // Connect WebSocket
  connectWs(inst.id)
}

const connectWs = (instanceId) => {
  if (ws) ws.close()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  // Build WebSocket URL relative to current host
  const wsUrl = `${protocol}//${location.host}/ws/chat/${instanceId}`
  console.log('[Chat] connecting to', wsUrl)
  ws = new WebSocket(wsUrl)
  ws.onopen = () => console.log('[Chat] WebSocket connected')
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      console.log('[Chat] received:', data)
      if (data.error) {
        const last = messages.value[messages.value.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          last.content = '❌ 对话出错'
          last.streaming = false
        } else {
          messages.value.push({ id: Date.now(), role: 'assistant', content: '❌ ' + data.content, createdAt: new Date().toISOString() })
        }
      } else if (data.stream) {
        // Streaming chunk: append to existing or create new streaming message
        const last = messages.value[messages.value.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          last.content += data.content
        } else {
          messages.value.push({ id: Date.now(), role: 'assistant', content: data.content, streaming: true, createdAt: new Date().toISOString() })
        }
      } else if (data.done) {
        // Stream complete: mark as done
        const last = messages.value[messages.value.length - 1]
        if (last && last.streaming) {
          last.streaming = false
        }
      } else {
        messages.value.push({ id: Date.now(), role: data.role || 'assistant', content: data.content, createdAt: new Date().toISOString() })
      }
      nextTick(scrollToBottom)
    } catch (e) { console.error('[Chat] parse error', e) }
  }
  ws.onerror = (e) => console.error('[Chat] WebSocket error', e)
  ws.onclose = (e) => console.log('[Chat] WebSocket closed', e.code, e.reason)
}

const sendMessage = () => {
  if (!inputText.value.trim() || !ws || ws.readyState !== WebSocket.OPEN) return
  const content = inputText.value.trim()
  messages.value.push({ id: Date.now(), role: 'user', content, createdAt: new Date().toISOString() })
  ws.send(JSON.stringify({ content }))
  inputText.value = ''
  nextTick(scrollToBottom)
}

const scrollToBottom = () => {
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

onMounted(loadData)
</script>
