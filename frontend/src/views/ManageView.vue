<template>
  <div style="padding: 16px">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 实例列表 Tab -->
      <el-tab-pane label="实例列表" name="instances">
        <el-table :data="instances" stripe border style="width: 100%" size="small" v-loading="loading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="名称" width="150" />
          <el-table-column prop="type" label="类型" width="140">
            <template #default="{ row }">
              <el-tag :type="row.type === 'hermes' ? 'warning' : 'primary'" effect="dark" round>
                {{ row.type === 'hermes' ? '🤖 Hermes' : '🦞 OpenClaw' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="150">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="port" label="端口" width="100" />
          <el-table-column prop="containerId" label="容器ID" width="200" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="handleSync(row)" :icon="Refresh">同步</el-button>
              <el-button v-if="row.status === 'STOPPED'" size="small" type="success" @click="handleStart(row)" :icon="VideoPlay">启动</el-button>
              <el-button v-if="row.status === 'RUNNING'" size="small" type="warning" @click="handleStop(row)" :icon="VideoPause">停止</el-button>
              <el-popconfirm title="确定删除？" @confirm="handleDelete(row)"><template #reference><el-button size="small" type="danger" :icon="Delete">删除</el-button></template></el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 审批管理 Tab (仅管理员可见) -->
      <el-tab-pane v-if="isAdmin()" label="审批管理" name="approvals">
        <div v-if="approvals.length === 0 && !approvalLoading" style="text-align: center; padding: 40px; color: #999">
          <div style="font-size: 32px; margin-bottom: 10px">✅</div>
          <div>暂无待审批的实例</div>
        </div>
        <el-table v-else :data="approvals" stripe border style="width: 100%" size="small" v-loading="approvalLoading">
          <el-table-column prop="instanceName" label="实例名称" width="150" />
          <el-table-column prop="instanceDescription" label="实例描述" min-width="150" show-overflow-tooltip />
          <el-table-column prop="requesterName" label="申请人" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="approvalStatusType(row.status)">{{ approvalStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="adminName" label="审批人" width="120" />
          <el-table-column prop="rejectReason" label="拒绝原因" width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" size="small" type="success" @click="handleApprove(row)" :loading="approvingId === row.id">批准</el-button>
              <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="handleReject(row)" :loading="rejectingId === row.id">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 拒绝原因弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝审批" width="380px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝原因" size="small" />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="rejectingId">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { xclawApi, approvalApi, isAdmin } from '../api'
import { ElMessage } from 'element-plus'
import { Refresh, VideoPlay, VideoPause, Delete } from '@element-plus/icons-vue'

const activeTab = ref('instances')
const instances = ref([])
const approvals = ref([])
const loading = ref(false)
const approvalLoading = ref(false)
const approvingId = ref(null)
const rejectingId = ref(null)

const loadData = async () => {
  loading.value = true
  try {
    const res = await xclawApi.list()
    instances.value = res.data || []
  } catch (e) {
    console.error('Failed to load instances:', e)
  } finally {
    loading.value = false
  }

  if (isAdmin()) {
    approvalLoading.value = true
    try {
      const res = await approvalApi.list()
      approvals.value = res.data || []
      console.log('Approvals loaded:', approvals.value.length)
    } catch (e) {
      console.error('Failed to load approvals:', e, e.response?.data)
    } finally {
      approvalLoading.value = false
    }
  }
}

const statusType = (s) => {
  const map = { RUNNING: 'success', STOPPED: 'info', ERROR: 'danger', CREATING: 'warning', DELETING: 'warning', PENDING_APPROVAL: 'warning', REJECTED: 'danger' }
  return map[s] || ''
}

const statusLabel = (s) => {
  const map = { RUNNING: '运行中', STOPPED: '已停止', ERROR: '异常', CREATING: '创建中', DELETING: '删除中', PENDING_APPROVAL: '待审批', REJECTED: '已拒绝' }
  return map[s] || s
}

const approvalStatusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || '')
const approvalStatusLabel = (s) => ({ PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝' }[s] || s)

const handleSync = async (row) => {
  await xclawApi.sync(row.id)
  ElMessage.success('状态已同步')
  loadData()
}

const handleStart = async (row) => {
  await xclawApi.start(row.id)
  ElMessage.success('实例已启动')
  loadData()
}

const handleStop = async (row) => {
  await xclawApi.stop(row.id)
  ElMessage.success('实例已停止')
  loadData()
}

const handleDelete = async (row) => {
  await xclawApi.delete(row.id)
  ElMessage.success('实例已删除')
  loadData()
}

const handleApprove = async (row) => {
  approvingId.value = row.id
  try {
    await approvalApi.approve(row.id)
    ElMessage.success('已批准')
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '审批失败')
  } finally {
    approvingId.value = null
  }
}

// Reject with reason dialog
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const currentRejectRow = ref(null)

const handleReject = (row) => {
  currentRejectRow.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const confirmReject = async () => {
  rejectingId.value = currentRejectRow.value.id
  try {
    await approvalApi.reject(currentRejectRow.value.id, rejectReason.value)
    ElMessage.success('已拒绝')
    rejectDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    rejectingId.value = null
  }
}

onMounted(loadData)
</script>
