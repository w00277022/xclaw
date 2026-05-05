<template>
  <div style="padding: 40px; max-width: 600px; margin: 0 auto">
    <h2>🚀 创建 XClaw 实例</h2>

    <!-- Role hint -->
    <el-alert
      v-if="!isAdmin"
      title="审批提示"
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 20px"
    >
      <template #default>
        <p style="margin: 0; line-height: 1.8">
          您创建的实例需要<strong>管理员审批</strong>后才能启动。<br/>
          创建后请在"XClaw 管理"页面查看审批状态。
        </p>
      </template>
    </el-alert>

    <el-form :model="form" label-width="100px" style="margin-top: 30px">
      <el-form-item label="实例类型">
        <el-radio-group v-model="form.type">
          <el-radio-button value="openclaw">
            <span style="display: inline-flex; align-items: center; gap: 4px">🦞 OpenClaw</span>
          </el-radio-button>
          <el-radio-button value="hermes">
            <span style="display: inline-flex; align-items: center; gap: 4px">🤖 Hermes-Agent</span>
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="实例名称">
        <el-input v-model="form.name" placeholder="输入实例名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选描述" />
      </el-form-item>
      <el-form-item label="自定义配置">
        <el-input v-model="form.configJson" type="textarea" :rows="4" placeholder='可选，JSON 格式，如 {"model": "ark/glm-5.1"}' />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleCreate" :loading="loading">创建</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { xclawApi, isAdmin } from '../api'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = ref({ name: '', description: '', configJson: '', type: 'openclaw' })

const handleCreate = async () => {
  if (!form.value.name) { ElMessage.warning('请输入实例名称'); return }
  loading.value = true
  try {
    const { data } = await xclawApi.create(form.value)
    if (data.status === 'PENDING_APPROVAL') {
      ElMessage.info('创建成功！实例需等待管理员审批后启动')
    } else if (data.status === 'RUNNING' && data.errorMsg) {
      if (data.errorMsg.includes('降级') || data.errorMsg.includes('代理')) {
        ElMessage.success('实例已创建（代理模式）：Docker 不可用，已自动切换为统一后端代理，可直接对话')
      } else {
        ElMessage.success('实例创建成功！')
      }
    } else {
      ElMessage.success('创建请求已提交，正在启动...')
    }
    router.push('/manage')
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}
</script>
