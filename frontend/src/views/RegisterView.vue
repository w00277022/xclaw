<template>
  <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f5f7fa">
    <el-card style="width: 420px" shadow="always">
      <template #header>
        <div style="text-align: center; font-size: 20px; font-weight: bold">📝 注册 XClaw 账号</div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px" @keyup.enter="handleRegister">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-20位字母数字" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="选填，默认同用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少6位" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleRegister" :loading="loading" style="width: 100%">
            {{ loading ? '注册中...' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div style="text-align: center; font-size: 14px; color: #909399">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_, value, cb) => value === form.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' },
  ],
}

const handleRegister = async () => {
  try {
    await formRef.value.validate()
  } catch { return }
  loading.value = true
  try {
    const { data } = await authApi.register(form.username, form.displayName || form.username, form.password)
    localStorage.setItem('xclaw_token', data.token)
    localStorage.setItem('xclaw_user', JSON.stringify(data.user))
    ElMessage.success('注册成功！')
    router.push('/create')
  } catch (err) {
    const msg = err.response?.data?.message || '注册失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>
