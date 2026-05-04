<template>
  <div style="display: flex; height: 100%">
    <!-- Left: XClaw list (collapsible) -->
    <div
      :style="{
        width: isCollapsed ? '60px' : '280px',
        minWidth: isCollapsed ? '60px' : '280px',
        borderRight: '1px solid #e4e7ed',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        transition: 'width 0.3s ease, min-width 0.3s ease'
      }"
    >
      <div style="padding: 15px 10px; border-bottom: 1px solid #e4e7ed; font-weight: bold; display: flex; align-items: center; gap: 4px; min-height: 48px; white-space: nowrap">
        <template v-if="!isCollapsed">
          <span>💬 选择 XClaw</span>
          <el-button size="small" circle @click="toggleCollapse" style="margin-left: auto" title="收起列表">
            <el-icon><DArrowLeft /></el-icon>
          </el-button>
        </template>
        <el-button v-else size="small" circle @click="toggleCollapse" style="margin: 0 auto" title="展开列表">
          <el-icon><DArrowRight /></el-icon>
        </el-button>
      </div>
      <div style="flex: 1; overflow-y: auto">
        <div v-for="inst in instances" :key="inst.id">
          <!-- Instance header -->
          <div
            @click="selectInstance(inst)"
            :style="{ padding: isCollapsed ? '10px 8px' : '10px 15px 6px', cursor: 'pointer', borderBottom: '1px solid #f0f0f0', background: selected?.id === inst.id ? '#ecf5ff' : '', textAlign: isCollapsed ? 'center' : 'left' }"
          >
            <template v-if="!isCollapsed">
              <div style="font-weight: 600; font-size: 13px; display: flex; align-items: center; gap: 4px">
                <span style="flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ inst.name }}</span>
                <el-tag size="small" :type="statusType(inst.status)">{{ inst.status }}</el-tag>
              </div>
            </template>
            <template v-else>
              <div :style="{ fontWeight: selected?.id === inst.id ? 600 : 400, fontSize: '13px' }" :title="inst.name">{{ inst.name.slice(0, 3) }}</div>
              <div style="margin-top: 4px">
                <span :style="{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', background: statusColor(inst.status) }" :title="inst.status"></span>
              </div>
            </template>
          </div>
          <!-- Sessions under instance (only when expanded) -->
          <template v-if="!isCollapsed && selected?.id === inst.id">
            <div
              v-for="sess in (sessionsStore[inst.id]?.list || [])"
              :key="sess.id"
              @click="switchSession(inst, sess.id)"
              :style="{ padding: '7px 15px 7px 28px', cursor: 'pointer', fontSize: '13px', background: currentSessionId === sess.id ? '#d9ecff' : '#fafafa', borderBottom: '1px solid #f5f5f5', display: 'flex', alignItems: 'center', gap: 6 }"
            >
              <span style="flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #666">{{ sess.title || '新会话' }}</span>
              <span v-if="sessionsStore[inst.id].list.length > 1"
                @click.stop="deleteSession(inst.id, sess.id)"
                style="cursor: pointer; color: #ccc; font-size: 14px; flex-shrink: 0" title="删除会话">✕</span>
            </div>
            <div v-if="selected?.id === inst.id"
              @click="newSession(inst)"
              style="padding: 6px 15px 6px 28px; cursor: pointer; font-size: 13px; color: #409EFF; borderBottom: 1px solid #f0f0f0; display: flex; align-items: center; gap: 4px">
              <el-icon size="14"><Plus /></el-icon>
              <span>新建会话</span>
            </div>
          </template>
        </div>
        <div v-if="!instances.length" style="padding: 30px; text-align: center; color: #999">{{ isCollapsed ? '-' : '暂无实例' }}</div>
      </div>
    </div>

    <!-- Right: Chat area -->
    <div style="flex: 1; display: flex; flex-direction: column">
      <template v-if="selected">
        <!-- Header with session tabs -->
        <div style="padding: 8px 20px; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; gap: 8px">
          <span style="font-weight: 600">{{ selected.name }}</span>
          <el-tag size="small" :type="statusType(selected.status)">{{ selected.status }}</el-tag>
          <div style="flex: 1"></div>
          <el-button size="small" @click="newSession(selected)" :disabled="selected.status !== 'RUNNING'">
            <el-icon><Plus /></el-icon> 新建会话
          </el-button>
        </div>

        <!-- Messages -->
        <div ref="messagesRef" style="flex: 1; overflow-y: auto; padding: 20px">
          <div v-for="msg in messages" :key="msg.id"
            :style="{ marginBottom: '16px', display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }">
            <div
              @mouseenter="msg._hover = true" @mouseleave="msg._hover = false"
              :style="{
                maxWidth: '72%', borderRadius: '12px', position: 'relative', overflow: 'hidden',
                background: msg.role === 'user' ? '#409EFF' : '#f4f4f5',
                color: msg.role === 'user' ? '#fff' : '#333'
              }"
            >
              <div :style="{ padding: '10px 16px', minWidth: 0 }">
                <template v-if="msg.role === 'user'">
                  <div v-if="msg.attachment" style="margin-bottom: 8px; padding: 8px 10px; background: 'rgba(255,255,255,0.2)'; borderRadius: 8px; display: flex; align-items: center; gap: 8px">
                    <span>📎</span>
                    <span style="font-size: 13px; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ msg.attachment.fileName }}</span>
                    <span style="font-size: 11px; opacity: 0.7">{{ formatSize(msg.attachment.size) }}</span>
                  </div>
                  <div style="white-space: pre-wrap; word-break: break-all; overflow-wrap: break-word">{{ msg.content }}</div>
                </template>
                <div v-else class="markdown-body" v-html="renderMd(msg.content)" style="font-size: 14px; line-height: 1.6"></div>
                <div style="font-size: 11px; margin-top: 6px; opacity: 0.6">{{ formatTime(msg.createdAt) }}</div>
              </div>
              <div v-if="msg._hover" :style="{ position: 'absolute', top: '-8px', right: msg.role === 'user' ? 'auto' : '6px', left: msg.role === 'user' ? '6px' : 'auto', display: 'flex', gap: '4px' }">
                <el-button v-if="msg.role === 'assistant'" size="small" circle @click.stop="copyText(msg.content)" title="复制">
                  <el-icon size="14"><DocumentCopy /></el-icon>
                </el-button>
                <el-button size="small" circle @click.stop="deleteMsg(msg)" title="删除" type="danger">
                  <el-icon size="14"><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>

          <!-- Typing indicator -->
          <div v-if="waiting && streamingSessionId === currentSessionId" style="display: flex; align-items: flex-start; margin-bottom: 16px">
            <div style="background: #f4f4f5; border-radius: 12px; padding: 12px 18px; display: flex; gap: 4px; align-items: center">
              <span class="typing-dot" style="animation-delay: 0s"></span>
              <span class="typing-dot" style="animation-delay: 0.2s"></span>
              <span class="typing-dot" style="animation-delay: 0.4s"></span>
            </div>
          </div>
        </div>

        <!-- Input card -->
        <div style="padding: 10px 20px 16px; border-top: 1px solid #e4e7ed">
          <div style="border: 1px solid #dcdfe6; border-radius: 12px; padding: 8px 12px 6px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.04); transition: border-color 0.2s" class="input-card">
            <div v-if="uploadedFile" style="display: flex; align-items: center; gap: 8px; padding: 6px 8px; margin-bottom: 6px; background: #ecf5ff; border-radius: 8px">
              <span style="font-size: 16px">📎</span>
              <span style="flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ uploadedFile.fileName }}</span>
              <span style="font-size: 11px; color: #999">{{ formatSize(uploadedFile.size) }}</span>
              <el-button size="small" text @click="uploadedFile = null" type="danger" style="padding: 2px">✕</el-button>
            </div>
            <el-input
              v-model="inputText"
              placeholder="输入消息... Shift+Enter 换行"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 2, maxRows: 6 }"
              @keydown.enter.exact.prevent="sendMessage"
              @keydown.ctrl.enter.prevent="sendMessage"
              :disabled="selected.status !== 'RUNNING'"
              resize="none"
              class="chat-textarea input-naked"
            />
            <div style="display: flex; align-items: center; justify-content: space-between; margin-top: 4px">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleFileChange" accept="*">
                <el-button :disabled="selected.status !== 'RUNNING'" text title="上传附件" style="padding: 4px 8px; font-size: 13px; color: #909399">
                  <el-icon size="15"><Paperclip /></el-icon>
                  <span style="margin-left: 4px">附件</span>
                </el-button>
              </el-upload>
              <span style="font-size: 11px; color: #c0c4cc">Enter 发送</span>
              <el-button type="primary" size="small" round @click="sendMessage" :disabled="(!inputText.trim() && !uploadedFile) || selected.status !== 'RUNNING'">
                <span>发送</span>
                <el-icon size="14" style="margin-left: 2px"><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </template>
      <div v-else style="flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px">
        ← 请选择一个 XClaw 实例开始对话
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { xclawApi, chatApi } from '../api'

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) return hljs.highlight(code, { language: lang }).value
    return hljs.highlightAuto(code).value
  }
})

const instances = ref([])
const selected = ref(null)
const inputText = ref('')
const messagesRef = ref(null)
const uploadedFile = ref(null)
const waiting = ref(false)
const currentSessionId = ref(null)
const streamingSessionId = ref(null) // Locks target session during streaming
let ws = null

// Sessions store: { [instanceId]: { list: [{id,title,createdAt,sessionKey}], messages: { [sessionId]: [...] } } }
const sessionsStore = ref({})

const SESSIONS_STORAGE_KEY = 'xclaw_sessions_store'

function saveSessionsToStorage() {
  // Only persist metadata, not messages (messages go to DB)
  const meta = {}
  for (const [instId, s] of Object.entries(sessionsStore.value)) {
    meta[instId] = { list: s.list.map(x => ({ id: x.id, title: x.title, createdAt: x.createdAt, sessionKey: x.sessionKey })) }
  }
  localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(meta))
}

function loadSessionsFromStorage() {
  try {
    const raw = localStorage.getItem(SESSIONS_STORAGE_KEY)
    if (!raw) return
    const meta = JSON.parse(raw)
    for (const [instId, data] of Object.entries(meta)) {
      if (!sessionsStore.value[instId]) {
        sessionsStore.value[instId] = { list: [], messages: {} }
      }
      const s = sessionsStore.value[instId]
      for (const item of (data.list || [])) {
        // Avoid duplicates
        if (s.list.find(x => x.id === item.id)) continue
        s.list.push({ ...item })
        s.messages[item.id] = []
      }
    }
  } catch (e) { console.error('Failed to load sessions from storage', e) }
}

// Current messages = messages of current session
const messages = computed(() => {
  if (!selected.value || !currentSessionId.value) return []
  return sessionsStore.value[selected.value.id]?.messages[currentSessionId.value] || []
})

// Ensure instance has at least one session
function ensureSession(instId) {
  if (!sessionsStore.value[instId]) {
    sessionsStore.value[instId] = { list: [], messages: {} }
  }
  const s = sessionsStore.value[instId]
  if (s.list.length === 0) {
    const sid = Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
    s.list.push({ id: sid, title: '新会话', createdAt: new Date().toISOString(), sessionKey: 'agent:main:main' })
    s.messages[sid] = []
    saveSessionsToStorage()
  }
}

function getCurrentSessionKey() {
  if (!selected.value || !currentSessionId.value) return 'agent:main:main'
  const s = sessionsStore.value[selected.value.id]
  if (!s) return 'agent:main:main'
  const item = s.list.find(x => x.id === currentSessionId.value)
  return item?.sessionKey || 'agent:main:main'
}

function newSession(inst) {
  ensureSession(inst.id)
  // Request a real OpenClaw session
  if (ws && ws.readyState === WebSocket.OPEN) {
    const label = '新会话'
    ws.send(JSON.stringify({ newSession: true, label }))
    waiting.value = true // wait for session creation
  } else {
    // Fallback: create local-only session if not connected
    const s = sessionsStore.value[inst.id]
    const sid = Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
    s.list.unshift({ id: sid, title: '新会话', createdAt: new Date().toISOString(), sessionKey: 'agent:main:main' })
    saveSessionsToStorage()
    s.messages[sid] = []
    currentSessionId.value = sid; localStorage.setItem("xclaw_last_session_" + (selected.value?.id || instId), currentSessionId.value)
    waiting.value = false
    nextTick(scrollToBottom)
  }
}

function deleteSession(instId, sessionId) {
  const s = sessionsStore.value[instId]
  if (!s || s.list.length <= 1) { ElMessage.warning('至少保留一个会话'); return }
  delete s.messages[sessionId]
  s.list = s.list.filter(x => x.id !== sessionId)
  saveSessionsToStorage()
  if (currentSessionId.value === sessionId) {
    currentSessionId.value = s.list[0].id; localStorage.setItem("xclaw_last_session_" + (selected.value?.id || instId), currentSessionId.value)
  }
}

async function switchSession(inst, sessionId) {
  if (selected.value?.id !== inst.id) {
    selectInstance(inst, sessionId)
    return
  }
  currentSessionId.value = sessionId; localStorage.setItem("xclaw_last_session_" + (selected.value?.id || instId), currentSessionId.value)
  // Load persisted history for this session
  try {
    const sessKey = getCurrentSessionKey()
    if (sessKey && sessKey !== 'agent:main:main') {
      const { data } = await chatApi.history(inst.id, sessKey)
      if (data.length) {
        sessionsStore.value[inst.id].messages[sessionId] = data
        const firstUser = data.find(m => m.role === 'user')
        if (firstUser) autoTitle(inst.id, sessionId, firstUser.content)
      }
    }
  } catch (e) { console.error('Load session history error:', e) }
  await nextTick()
  scrollToBottom()
}

// Auto-title a session from first user message
function autoTitle(instId, sessionId, content) {
  const s = sessionsStore.value[instId]
  if (!s) return
  const item = s.list.find(x => x.id === sessionId)
  if (item && item.title === '新会话') {
    item.title = content.slice(0, 20) + (content.length > 20 ? '…' : '')
  }
}

const isCollapsed = ref(localStorage.getItem('xclaw_sidebar_collapsed') === 'true')
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('xclaw_sidebar_collapsed', String(isCollapsed.value))
}
const onStorage = (e) => { if (e.key === 'xclaw_sidebar_collapsed') isCollapsed.value = e.newValue === 'true' }
onMounted(() => window.addEventListener('storage', onStorage))

const statusType = (s) => ({ RUNNING: 'success', STOPPED: 'info', ERROR: 'danger' }[s] || '')
const statusColor = (s) => ({ RUNNING: '#67c23a', STOPPED: '#909399', ERROR: '#f56c6c' }[s] || '#909399')
const formatTime = (t) => t ? new Date(t).toLocaleTimeString() : ''
const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return bytes.toFixed(1) + ' ' + units[i]
}
const renderMd = (text) => {
  try { return marked.parse(text || '') } catch { return text }
}
const copyText = async (text) => {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') } catch { ElMessage.warning('复制失败') }
}
const deleteMsg = async (msg) => {
  try { await chatApi.deleteMessage(msg.id); ElMessage.success('已删除') } catch (e) { ElMessage.error('删除失败') }
  // Remove from current session
  if (selected.value && currentSessionId.value) {
    const msgs = sessionsStore.value[selected.value.id]?.messages[currentSessionId.value]
    if (msgs) {
      const idx = msgs.findIndex(m => m.id === msg.id)
      if (idx !== -1) msgs.splice(idx, 1)
    }
  }
}
const handleFileChange = (uf) => {
  const file = uf.raw
  if (!file) return
  uploadedFile.value = { fileName: file.name, size: file.size, mimeType: file.type, file }
}
const uploadAndGetInfo = async () => {
  if (!uploadedFile.value) return null
  try { const { data } = await chatApi.upload(uploadedFile.value.file); return data } catch { ElMessage.error('文件上传失败'); return null }
}

const loadData = async () => {
  const { data } = await xclawApi.list()
  instances.value = data
  // Restore persisted sessions
  loadSessionsFromStorage()
  const lastId = localStorage.getItem('xclaw_last_chat_instance')
  if (lastId) {
    const inst = data.find(i => String(i.id) === lastId)
    if (inst) selectInstance(inst)
  }
}

const selectInstance = async (inst, sessionId) => {
  const changing = selected.value?.id !== inst.id
  selected.value = inst
  localStorage.setItem('xclaw_last_chat_instance', String(inst.id))
  ensureSession(inst.id)

  if (sessionId) {
    currentSessionId.value = sessionId; localStorage.setItem("xclaw_last_session_" + (selected.value?.id || instId), currentSessionId.value)
  } else if (!currentSessionId.value || !sessionsStore.value[inst.id]?.messages[currentSessionId.value]) {
    currentSessionId.value = sessionsStore.value[inst.id].list[0].id
  }

  if (changing) {
    // Load persisted history for the default session
    try {
      const defaultSid = sessionsStore.value[inst.id].list[0].id
      const defaultSessKey = sessionsStore.value[inst.id].list[0].sessionKey || 'agent:main:main'
      const { data } = await chatApi.history(inst.id, defaultSessKey)
      if (data.length) {
        sessionsStore.value[inst.id].messages[defaultSid] = data
        const firstUser = data.find(m => m.role === 'user')
        if (firstUser) autoTitle(inst.id, defaultSid, firstUser.content)
      }
    } catch (e) { console.error('Load history error:', e) }
    connectWs(inst.id)
  }

  await nextTick()
  scrollToBottom()
}

const connectWs = (instanceId) => {
  if (ws) ws.close()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/chat/${instanceId}`)
  ws.onopen = () => console.log('[Chat] WS connected')
  ws.onmessage = async (event) => {
    try {
      const data = JSON.parse(event.data)

      // Handle session creation response
      if (data.sessionCreated) {
        waiting.value = false
        const instId = selected.value?.id
        if (!instId) return
        const s = sessionsStore.value[instId]
        const sid = Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
        const label = data.label || '新会话'
        s.list.unshift({ id: sid, title: label, createdAt: new Date().toISOString(), sessionKey: data.sessionKey })
        saveSessionsToStorage()
        s.messages[sid] = []
        currentSessionId.value = sid; localStorage.setItem("xclaw_last_session_" + (selected.value?.id || instId), currentSessionId.value)
        await nextTick()
        scrollToBottom()
        return
      }

      waiting.value = false
      const targetSid = streamingSessionId.value || currentSessionId.value; const msgs = sessionsStore.value[selected.value?.id]?.messages[targetSid]
      if (!msgs) return

      if (data.error) {
        const last = msgs[msgs.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          last.content = '❌ ' + data.content; last.streaming = false
        } else {
          msgs.push({ id: Date.now(), role: 'assistant', content: '❌ ' + data.content, createdAt: new Date().toISOString() })
        }
        ElMessage.error(data.content)
        streamingSessionId.value = null
      } else if (data.stream) {
        if (!data.content) return
        const last = msgs[msgs.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          last.content += data.content
        } else {
          msgs.push({ id: Date.now(), role: 'assistant', content: data.content, streaming: true, createdAt: new Date().toISOString() })
        }
      } else if (data.done) {
        streamingSessionId.value = null
        const last = msgs[msgs.length - 1]
        if (last && last.streaming) last.streaming = false
      } else if (!data.stream) {
        msgs.push({ id: data.id || Date.now(), role: data.role || 'assistant', content: data.content, createdAt: data.createdAt || new Date().toISOString() })
      }
      nextTick(scrollToBottom)
    } catch (e) { console.error('[Chat] parse error', e) }
  }
  ws.onerror = (e) => console.error('[Chat] WS error', e)
  ws.onclose = (e) => console.log('[Chat] WS closed', e.code)
}

const sendMessage = async () => {
  if ((!inputText.value.trim() && !uploadedFile.value) || !ws || ws.readyState !== WebSocket.OPEN) return
  if (!selected.value || !currentSessionId.value) return

  let fileInfo = null
  if (uploadedFile.value) fileInfo = await uploadAndGetInfo()
  const content = inputText.value.trim() || (fileInfo ? '[附件]' : '')

  const userMsg = { id: Date.now(), role: 'user', content, createdAt: new Date().toISOString() }
  if (fileInfo) userMsg.attachment = { fileName: fileInfo.fileName, size: fileInfo.size, mimeType: fileInfo.mimeType }

  const msgs = sessionsStore.value[selected.value.id].messages[currentSessionId.value]
  msgs.push(userMsg)

  // Auto-title the session
  autoTitle(selected.value.id, currentSessionId.value, content)

  const wsPayload = { content, sessionKey: getCurrentSessionKey() }
  if (fileInfo) wsPayload.attachment = { fileName: fileInfo.fileName, mimeType: fileInfo.mimeType, fileKey: fileInfo.fileKey, size: fileInfo.size }
  ws.send(JSON.stringify(wsPayload))

  streamingSessionId.value = currentSessionId.value
  waiting.value = true
  inputText.value = ''
  uploadedFile.value = null
  nextTick(scrollToBottom)
}

const scrollToBottom = () => {
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

onActivated(loadData)
</script>

<style>
.input-card:hover { border-color: #c0c4cc !important; }
.input-card:focus-within { border-color: #409EFF !important; }
.input-naked .el-textarea__inner {
  border: none !important; box-shadow: none !important;
  padding: 0 !important; resize: none !important; background: transparent !important;
}
.typing-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: #909399;
  animation: typingBounce 1.4s ease-in-out infinite;
}
@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.chat-textarea .el-textarea__inner {
  width: 100% !important; max-width: 100% !important;
  resize: none !important; box-sizing: border-box;
}
.chat-textarea { width: 100% !important; display: block; }
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
}
.markdown-body pre {
  background: #1e1e1e; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 8px 0;
}
.markdown-body pre code { background: transparent; padding: 0; color: #d4d4d4; font-size: 13px; font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace; }
.markdown-body code { background: rgba(175,184,193,0.2); padding: 2px 6px; border-radius: 4px; font-size: 0.9em; font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace; }
.markdown-body table { border-collapse: collapse; width: 100%; margin: 8px 0; }
.markdown-body th, .markdown-body td { border: 1px solid #d0d7de; padding: 6px 13px; }
.markdown-body th { background: #f6f8fa; font-weight: 600; }
.markdown-body blockquote { border-left: 3px solid #d0d7de; padding: 0 1em; color: #656d76; margin: 8px 0; }
.markdown-body ul, .markdown-body ol { padding-left: 2em; }
.markdown-body img { max-width: 100%; }
.markdown-body a { color: #0969da; }
</style>
