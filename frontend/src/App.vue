<template>
  <el-container style="height: 100vh; flex-direction: column">
    <!-- Top Header -->
    <el-header v-if="currentUser" style="height: 50px; background: #304156; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; border-bottom: 1px solid #4a5a6a">
      <div style="display: flex; align-items: center; gap: 16px">
        <el-icon
          style="color: #bfcbd9; font-size: 20px; cursor: pointer"
          @click="toggleCollapse"
        >
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
        <div style="color: #fff; font-size: 18px; font-weight: bold; display: flex; align-items: center; gap: 8px">
          <span style="font-size: 22px">🦞</span>
          <span v-show="!isCollapsed">XClaw 管理平台</span>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 12px">
        <el-icon style="color: #bfcbd9"><UserFilled /></el-icon>
        <span style="color: #bfcbd9; font-size: 14px">{{ currentUser.displayName || currentUser.username }}</span>
        <el-tag :type="currentUser.role === 'ADMIN' ? 'danger' : 'info'" size="small">
          {{ currentUser.role === 'ADMIN' ? '管理员' : '普通用户' }}
        </el-tag>
        <el-button size="small" text style="color: #f56c6c; margin-left: 4px" @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <!-- Body: Sidebar + Content -->
    <el-container style="flex: 1; overflow: hidden">
      <el-aside v-if="currentUser" :width="isCollapsed ? '64px' : '220px'" style="background: #304156; overflow-y: auto; overflow-x: hidden; transition: width 0.3s ease">
        <el-menu
          :default-active="route.path"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          :collapse="isCollapsed"
        >
          <el-menu-item index="/create">
            <el-icon><Plus /></el-icon>
            <span>创建 XClaw</span>
          </el-menu-item>
          <el-menu-item index="/manage">
            <el-icon><Setting /></el-icon>
            <span>XClaw 管理</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>XClaw 对话</span>
          </el-menu-item>
          <el-menu-item index="/native">
            <el-icon><Monitor /></el-icon>
            <span>XClaw Native</span>
          </el-menu-item>
          <el-menu-item v-if="adminCheck" index="/admin">
            <el-icon><User /></el-icon>
            <span>管理员配置</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="padding: 0; overflow: hidden">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const currentUser = ref(null)
const adminCheck = ref(false)

// Sidebar collapse state, persisted to localStorage
const isCollapsed = ref(localStorage.getItem('xclaw_sidebar_collapsed') === 'true')

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('xclaw_sidebar_collapsed', String(isCollapsed.value))
}

const syncUser = () => {
  try {
    const raw = localStorage.getItem('xclaw_user')
    currentUser.value = raw ? JSON.parse(raw) : null
    adminCheck.value = currentUser.value?.role === 'ADMIN'
  } catch {
    currentUser.value = null
    adminCheck.value = false
  }
}

watch(() => route.path, syncUser, { immediate: true })

const handleLogout = () => {
  localStorage.removeItem('xclaw_token')
  localStorage.removeItem('xclaw_user')
  currentUser.value = null
  adminCheck.value = false
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style>
body { margin: 0; }
.el-header { --el-header-height: 50px; }
</style>
