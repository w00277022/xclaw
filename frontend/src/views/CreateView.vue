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
          <template v-if="allowedTypes.openclaw">
            <el-radio-button value="openclaw">
              <span style="display: inline-flex; align-items: center; gap: 4px">🦞 OpenClaw</span>
            </el-radio-button>
          </template>
          <template v-else>
            <el-radio-button value="openclaw" disabled>
              <span style="display: inline-flex; align-items: center; gap: 4px; opacity: 0.5">🦞 OpenClaw (无权限)</span>
            </el-radio-button>
          </template>
          <template v-if="allowedTypes.hermes">
            <el-radio-button value="hermes">
              <span style="display: inline-flex; align-items: center; gap: 4px">🤖 Hermes-Agent</span>
            </el-radio-button>
          </template>
          <template v-else>
            <el-radio-button value="hermes" disabled>
              <span style="display: inline-flex; align-items: center; gap: 4px; opacity: 0.5">🤖 Hermes-Agent (无权限)</span>
            </el-radio-button>
          </template>
        </el-radio-group>
        <div v-if="!allowedTypes.openclaw && !allowedTypes.hermes" style="color: #f56c6c; font-size: 12px; margin-top: 8px">
          您当前没有创建任何类型实例的权限，请联系管理员
        </div>
      </el-form-item>
      <el-form-item label="部署节点">
        <el-select v-model="form.nodeId" placeholder="选择部署节点" clearable style="width: 100%">
          <el-option v-for="n in nodes" :key="n.id" :label="n.name + (n.isLocal ? ' (本机)' : ' - ' + n.host)" :value="n.id" />
        </el-select>
        <div style="font-size: 12px; color: #909399; margin-top: 4px">留空则部署到本机</div>
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
        <el-button
          type="primary"
          @click="handleCreate"
          :loading="loading"
          :disabled="!allowedTypes.openclaw && !allowedTypes.hermes"
        >创建</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { xclawApi, getCurrentUser } from '../api'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = reactive({ name: '', description: '', configJson: '', type: 'openclaw', nodeId: null })
const nodes = ref([])

const user = computed(() => getCurrentUser())
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const allowedTypes = reactive({ openclaw: true, hermes: false })

// Load allowed types from API
const loadAllowedTypes = async () => {
  try {
    const { data } = await xclawApi.allowedTypes()
    if (Array.isArray(data)) {
      for (const t of data) {
        allowedTypes[t.type] = t.allowed
      }
      // Auto-select first allowed type if current selection not allowed
      if (!allowedTypes[form.type]) {
        if (allowedTypes.openclaw) form.type = 'openclaw'
        else if (allowedTypes.hermes) form.type = 'hermes'
      }
    }
  } catch {
    // Fallback: use localStorage user data
    if (user.value) {
      allowedTypes.openclaw = user.value.canCreateOpenclaw !== false
      allowedTypes.hermes = user.value.canCreateHermes === true
    }
    if (isAdmin.value) {
      allowedTypes.openclaw = true
      allowedTypes.hermes = true
    }
    if (!allowedTypes[form.type]) {
      if (allowedTypes.openclaw) form.type = 'openclaw'
      else if (allowedTypes.hermes) form.type = 'hermes'
    }
  }
}

const handleCreate = async () => {
  if (!form.name) { ElMessage.warning('请输入实例名称'); return }
  if (!allowedTypes[form.type]) {
    ElMessage.error('您没有创建该类型实例的权限')
    return
  }
  loading.value = true
  try {
    const { data } = await xclawApi.create({ ...form })
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

onMounted(loadAllowedTypes)

// Load nodes
xclawApi.nodes().then(({ data }) => { nodes.value = data || [] }).catch(() => {})
</script>
