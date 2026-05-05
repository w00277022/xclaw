<template>
  <el-container v-if="currentUser" style="height: 100vh; flex-direction: row; overflow: hidden">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" style="background: #304156; display: flex; flex-direction: column; overflow: hidden; transition: width 0.3s ease">
      <!-- Sidebar Top: Logo + Toggle -->
      <div style="display: flex; align-items: center; padding: 12px 16px; border-bottom: 1px solid #4a5a6a; gap: 8px; height: 48px; flex-shrink: 0">
        <el-icon
          style="color: #bfcbd9; font-size: 20px; cursor: pointer; flex-shrink: 0"
          @click="toggleCollapse"
        >
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
        <span style="color: #fff; font-size: 16px; font-weight: bold; white-space: nowrap; overflow: hidden">
          <span style="font-size: 20px">🦞</span>
          <span v-show="!isCollapsed"> XClaw</span>
        </span>
      </div>

      <!-- Navigation Menu -->
      <el-menu
        :default-active="route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        :collapse="isCollapsed"
        style="flex: 1; border-right: none; overflow-y: auto"
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

      <!-- Sidebar Bottom: User Info + Logout (hover to reveal) -->
      <div
        class="sidebar-user-area"
        style="border-top: 1px solid #4a5a6a; padding: 12px; flex-shrink: 0"
        :style="{ display: 'flex', flexDirection: isCollapsed ? 'column' : 'row', alignItems: 'center', gap: isCollapsed ? '6px' : '8px', justifyContent: 'center' }"
      >
        <div :style="{ display: 'flex', alignItems: 'center', gap: '6px', overflow: 'hidden' }">
          <el-icon style="color: #bfcbd9; flex-shrink: 0"><UserFilled /></el-icon>
          <span v-show="!isCollapsed" style="color: #bfcbd9; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">
            {{ currentUser.displayName || currentUser.username }}
          </span>
        </div>
        <el-tag v-show="!isCollapsed" :type="currentUser.role === 'ADMIN' ? 'danger' : 'info'" size="small" style="flex-shrink: 0">
          {{ currentUser.role === 'ADMIN' ? '管理员' : '普通用户' }}
        </el-tag>
        <el-button
          class="logout-btn"
          size="small"
          text
          style="color: #f56c6c; flex-shrink: 0"
          :style="{ minWidth: isCollapsed ? 'auto' : 'auto' }"
          @click="handleLogout"
        >
          {{ isCollapsed ? '退' : '退出' }}
        </el-button>
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-main style="padding: 0; overflow: hidden; flex: 1">
      <router-view v-slot="{ Component }">
        <keep-alive include="ChatView,NativeView">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </el-main>
  </el-container>

  <!-- Login / Register pages (no layout) -->
  <router-view v-if="!currentUser" v-slot="{ Component }">
    <component :is="Component" />
  </router-view>
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

/* Bottom logout button: hidden by default, shown on hover */
.sidebar-user-area .logout-btn {
  display: none;
}
.sidebar-user-area:hover .logout-btn {
  display: inline-flex;
}
</style>
