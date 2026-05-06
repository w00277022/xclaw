<template>
  <div style="padding: 16px">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
      <h2 style="margin: 0; font-size: 18px">🖥️ 节点管理</h2>
      <el-button type="primary" @click="openAddDialog">
        <el-icon style="margin-right: 5px"><Plus /></el-icon>新增节点
      </el-button>
    </div>

    <el-table :data="nodes" stripe border style="width: 100%" size="small" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="host" label="主机" width="160" />
      <el-table-column prop="port" label="SSH端口" width="90" align="center" />
      <el-table-column prop="sshUser" label="SSH用户" width="120" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ONLINE' ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="本机" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isLocal ? '' : 'info'" size="small">
            {{ row.isLocal ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="250">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            @click="openEditDialog(row)"
          >编辑</el-button>
          <el-button
            size="small"
            type="success"
            @click="handleTest(row)"
            :loading="testingId === row.id"
          >测试连接</el-button>
          <el-popconfirm
            :title="row.isLocal ? '本机节点不可删除' : '确定删除该节点吗？'"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button
                size="small"
                type="danger"
                :disabled="row.isLocal"
                :loading="loadingId === row.id"
              >删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!nodes.length && !loading" style="text-align: center; padding: 30px; color: #999">
      <div style="font-size: 32px; margin-bottom: 10px">📭</div>
      <div>暂无节点数据</div>
    </div>

    <!-- Add / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑节点' : '新增节点'" width="480px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px" size="small">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="节点显示名称" />
        </el-form-item>
        <el-form-item label="主机" prop="host">
          <el-input v-model="form.host" placeholder="IP 地址或域名" />
        </el-form-item>
        <el-form-item label="SSH端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="SSH用户" prop="sshUser">
          <el-input v-model="form.sshUser" placeholder="例如 root" />
        </el-form-item>
        <el-form-item label="SSH私钥" prop="sshKey">
          <el-input
            v-model="form.sshKey"
            type="textarea"
            :rows="4"
            placeholder="粘贴 SSH 私钥内容（可选，留空则保持不变）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          {{ isEditing ? '保存修改' : '确定添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { nodeApi } from '../api'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const nodes = ref([])
const loading = ref(false)
const loadingId = ref(null)
const testingId = ref(null)

const loadNodes = async () => {
  loading.value = true
  try {
    const { data } = await nodeApi.list()
    nodes.value = data || []
  } finally {
    loading.value = false
  }
}

// -- Dialog --
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const submitLoading = ref(false)
const formRef = ref(null)

const defaultForm = () => ({ name: '', host: '', port: 22, sshUser: 'root', sshKey: '' })

const form = reactive(defaultForm())

const rules = {
  name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入SSH端口', trigger: 'blur' }],
  sshUser: [{ required: true, message: '请输入SSH用户', trigger: 'blur' }],
}

const openAddDialog = () => {
  isEditing.value = false
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEditing.value = true
  editingId.value = row.id
  form.name = row.name
  form.host = row.host
  form.port = row.port || 22
  form.sshUser = row.sshUser || ''
  form.sshKey = ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try { await formRef.value.validate() } catch { return }
  submitLoading.value = true
  try {
    const payload = {
      name: form.name,
      host: form.host,
      port: form.port,
      sshUser: form.sshUser,
    }
    // Only send sshKey if provided (backend will keep existing if empty)
    if (form.sshKey) payload.sshKey = form.sshKey

    if (isEditing.value) {
      await nodeApi.update(editingId.value, payload)
      ElMessage.success('节点已更新')
    } else {
      payload.sshKey = form.sshKey // required for create
      await nodeApi.create(payload)
      ElMessage.success('节点已创建')
    }
    dialogVisible.value = false
    loadNodes()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// -- Test --
const handleTest = async (row) => {
  testingId.value = row.id
  try {
    const { data } = await nodeApi.test(row.id)
    if (data.connected) {
      ElMessage.success('连接成功 — 节点状态已更新为在线')
    } else {
      ElMessage.warning('连接失败 — 节点状态已更新为离线')
    }
    loadNodes()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '测试失败')
  } finally {
    testingId.value = null
  }
}

// -- Delete --
const handleDelete = async (row) => {
  if (row.isLocal) {
    ElMessage.warning('本机节点不可删除')
    return
  }
  loadingId.value = row.id
  try {
    await nodeApi.delete(row.id)
    ElMessage.success('节点已删除')
    loadNodes()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  } finally {
    loadingId.value = null
  }
}

onMounted(loadNodes)
</script>
