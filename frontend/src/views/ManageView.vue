<template>
  <div style="padding: 30px">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>⚙️ XClaw 管理</h2>
      <el-button type="primary" @click="loadData" :icon="Refresh">刷新</el-button>
    </div>
    <el-table :data="instances" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="status" label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          <el-tag v-if="row.errorMsg && (row.errorMsg.includes('降级') || row.errorMsg.includes('代理'))" size="small" type="warning" style="margin-left: 4px">代理</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="port" label="端口" width="100" />
      <el-table-column prop="containerId" label="容器ID" width="200" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleSync(row)" :icon="Refresh">同步</el-button>
          <el-button size="small" type="success" @click="handleStart(row)" v-if="row.status==='STOPPED'" :icon="VideoPlay">启动</el-button>
          <el-button size="small" type="warning" @click="handleStop(row)" v-if="row.status==='RUNNING'" :icon="VideoPause">停止</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)" :icon="Delete">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { xclawApi } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoPlay, VideoPause, Delete } from '@element-plus/icons-vue'

const instances = ref([])

const loadData = async () => {
  const { data } = await xclawApi.list()
  instances.value = data
}

const statusType = (s) => ({ RUNNING: 'success', STOPPED: 'info', ERROR: 'danger', CREATING: 'warning', DELETING: 'warning' }[s] || '')

const handleSync = async (row) => {
  await xclawApi.sync(row.id)
  ElMessage.success('状态已同步')
  loadData()
}

const handleStart = async (row) => {
  await xclawApi.start(row.id)
  ElMessage.success('启动中...')
  loadData()
}

const handleStop = async (row) => {
  await xclawApi.stop(row.id)
  ElMessage.success('停止中...')
  loadData()
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除 XClaw "${row.name}"？此操作不可恢复！`, '警告', { type: 'warning' })
  await xclawApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>
