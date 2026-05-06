<template>
  <div style="min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%)">
    <el-card style="width: 420px; border-radius: 12px">
      <template #header>
        <div style="text-align: center; font-size: 24px; font-weight: bold; color: #303133">
          🤖 XClaw 管理平台
        </div>
      </template>
      <el-form :model="form" label-width="0" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" @click="handleLogin" :loading="loading" style="width: 100%">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div style="text-align: center; font-size: 14px">
        没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { authApi } from '../api'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const { data } = await authApi.login(form.value.username, form.value.password)
    localStorage.setItem('xclaw_token', data.token)
    localStorage.setItem('xclaw_user', JSON.stringify(data.user))
    ElMessage.success('登录成功')
    router.push('/create')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
