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
        background: '#fafafa',
        overflow: 'hidden',
        transition: 'width 0.3s ease, min-width 0.3s ease'
      }"
    >
      <div style="padding: 15px 10px; border-bottom: 1px solid #e4e7ed; font-weight: bold; display: flex; align-items: center; gap: 8px; white-space: nowrap; min-height: 48px">
        <template v-if="!isCollapsed">
          <span>🌐</span>
          <span>选择 XClaw 实例</span>
          <el-button size="small" circle @click="loadData" style="margin-left: auto">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button size="small" circle @click="toggleCollapse">
            <el-icon><DArrowLeft /></el-icon>
          </el-button>
        </template>
        <el-button v-else size="small" circle @click="toggleCollapse" style="margin: 0 auto" title="展开实例列表">
          <el-icon><DArrowRight /></el-icon>
        </el-button>
      </div>

      <!-- Expanded list -->
      <div v-show="!isCollapsed" style="flex: 1; overflow-y: auto">
        <div
          v-for="inst in instances"
          :key="inst.id"
          @click="selectInstance(inst)"
          :style="{
            padding: '12px 15px', cursor: 'pointer',
            borderBottom: '1px solid #f0f0f0',
            background: selected?.id === inst.id ? '#ecf5ff' : '',
            transition: 'background 0.2s'
          }"
        >
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
            <div style="font-weight: 500; font-size: 14px; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap" :title="inst.name">{{ inst.name }}</div>
            <el-tag size="small" :type="statusType(inst.status)">{{ inst.status === 'RUNNING' ? '运行中' : inst.status === 'STOPPED' ? '已停止' : inst.status }}</el-tag>
          </div>
          <div style="font-size: 12px; color: #999; margin-top: 6px">
            <el-icon style="vertical-align: middle"><Monitor /></el-icon>
            <span style="margin-left: 4px">:{{ inst.port }}</span>
          </div>
        </div>
        <div v-if="!instances.length" style="padding: 40px 20px; text-align: center; color: #999">
          <div style="font-size: 32px; margin-bottom: 10px">📭</div>
          <div>暂无 XClaw 实例</div>
          <div style="font-size: 12px; margin-top: 5px">请先在「创建 XClaw」页面创建</div>
        </div>
      </div>

      <!-- Collapsed mini-list -->
      <div v-show="isCollapsed" style="flex: 1; overflow-y: auto">
        <div
          v-for="inst in instances"
          :key="inst.id"
          @click="selectInstance(inst)"
          :style="{
            padding: '10px 6px', cursor: 'pointer', textAlign: 'center',
            borderBottom: '1px solid #f0f0f0',
            background: selected?.id === inst.id ? '#ecf5ff' : '',
            fontSize: '13px', fontWeight: selected?.id === inst.id ? 600 : 400
          }"
          :title="inst.name"
        >
          <div style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ inst.name.slice(0, 3) }}</div>
          <span :style="{ display: 'inline-block', width: 7, height: 7, borderRadius: '50%', marginTop: 5, background: inst.status === 'RUNNING' ? '#67c23a' : inst.status === 'STOPPED' ? '#909399' : '#f56c6c' }" :title="inst.status"></span>
        </div>
        <div v-if="!instances.length" style="padding: 20px; text-align: center; color: #999; font-size: 12px">-</div>
      </div>
    </div>

    <!-- Right: Native Web UI via iframe -->
    <div style="flex: 1; display: flex; flex-direction: column; position: relative">
      <template v-if="selected">
        <!-- Info bar -->
        <div style="padding: 10px 20px; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; gap: 12px; background: #fff">
          <span style="font-weight: 600; font-size: 15px">{{ selected.name }}</span>
          <el-tag size="small" :type="statusType(selected.status)">{{ selected.status === 'RUNNING' ? '运行中' : selected.status }}</el-tag>
          <span style="color: #999; font-size: 13px; margin-left: auto">
            <el-icon><Link /></el-icon>
            http://localhost:{{ selected.port }}
          </span>
        </div>

        <!-- iframe (only when RUNNING) -->
        <div v-if="selected.status === 'RUNNING'" style="flex: 1; position: relative">
          <iframe
            :src="iframeUrl"
            style="width: 100%; height: 100%; border: none"
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-modals"
            allow="clipboard-read; clipboard-write"
            @load="onIframeLoad"
            @error="onIframeError"
          ></iframe>
        </div>

        <!-- Not running hint -->
        <div v-else style="flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #999; gap: 12px">
          <div style="font-size: 48px">⏸️</div>
          <div style="font-size: 16px">该实例当前未运行</div>
          <div style="font-size: 13px; color: #bbb">请先在「XClaw 管理」页面启动实例</div>
          <el-button type="primary" @click="goToManage" style="margin-top: 8px">前往管理</el-button>
        </div>
      </template>

      <!-- No selection -->
      <div v-else style="flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #999; gap: 12px">
        <div style="font-size: 56px">🌐</div>
        <div style="font-size: 17px; font-weight: 500; color: #666">XClaw Native 对话</div>
        <div style="font-size: 14px">← 请选择一个 XClaw 实例，查看其原生 Web UI</div>
      </div>

      <!-- Loading overlay -->
      <div v-if="iframeLoading && selected?.status === 'RUNNING'" style="position: absolute; inset: 50px 0 0 0; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.8); z-index: 10">
        <el-icon class="is-loading" style="font-size: 32px; color: #409EFF"><Loading /></el-icon>
        <span style="margin-left: 10px; color: #666">加载中...</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { xclawApi } from '../api'

const router = useRouter()
const instances = ref([])
const selected = ref(null)
const iframeLoading = ref(false)

// Sidebar collapse state, synced with main sidebar via localStorage
const isCollapsed = ref(localStorage.getItem('xclaw_sidebar_collapsed') === 'true')

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('xclaw_sidebar_collapsed', String(isCollapsed.value))
}

// Sync collapse state across tabs/components via storage event
const onStorage = (e) => {
  if (e.key === 'xclaw_sidebar_collapsed') {
    isCollapsed.value = e.newValue === 'true'
  }
}
// Storage listener (mount once, lives forever with keep-alive)
onMounted(() => window.addEventListener('storage', onStorage))

const iframeUrl = computed(() => {
  if (!selected.value) return ''
  return `http://localhost:${selected.value.port}`
})

const statusType = (s) => ({ RUNNING: 'success', STOPPED: 'info', ERROR: 'danger' }[s] || 'info')

const loadData = async () => {
  try {
    const { data } = await xclawApi.list()
    instances.value = data
    // Keep selected if still exists
    if (selected.value) {
      const found = data.find(i => i.id === selected.value.id)
      if (found) selected.value = found
    }
    // Remember last selected: auto-restore
    if (!selected.value) {
      const lastId = localStorage.getItem('xclaw_last_native_instance')
      if (lastId) {
        const inst = data.find(i => String(i.id) === lastId)
        if (inst) selectInstance(inst)
      }
    }
  } catch (e) {
    console.error('Failed to load instances:', e)
  }
}

const selectInstance = (inst) => {
  selected.value = inst
  localStorage.setItem('xclaw_last_native_instance', String(inst.id))
  iframeLoading.value = inst.status === 'RUNNING'
}

const onIframeLoad = () => {
  iframeLoading.value = false
}

const onIframeError = () => {
  iframeLoading.value = false
}

const goToManage = () => {
  router.push('/manage')
}

// Initial load handled by onActivated (keep-alive)
onActivated(loadData)
</script>
