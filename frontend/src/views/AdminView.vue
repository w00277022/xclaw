<template>
  <div style="padding: 30px">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>👥 用户管理</h2>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon style="margin-right: 5px"><Plus /></el-icon>添加用户
      </el-button>
    </div>

    <el-table :data="users" stripe border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="displayName" label="显示名称" width="150" />
      <el-table-column prop="role" label="角色" width="150">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
            {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <el-button
            v-if="row.role === 'USER'"
            size="small"
            type="warning"
            @click="handleChangeRole(row, 'ADMIN')"
            :loading="loadingId === row.id"
          >设为管理员</el-button>
          <el-button
            v-if="row.role === 'ADMIN'"
            size="small"
            type="info"
            @click="handleChangeRole(row, 'USER')"
            :loading="loadingId === row.id"
            :disabled="row.username === 'admin'"
          >取消管理员</el-button>
          <el-popconfirm
            v-if="row.username !== 'admin'"
            title="确定删除该用户吗？"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button size="small" type="danger" :loading="loadingId === row.id">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!users.length && !loading" style="text-align: center; padding: 60px; color: #999">
      <div style="font-size: 32px; margin-bottom: 10px">📭</div>
      <div>暂无用户数据</div>
    </div>

    <!-- Add User Dialog -->
    <el-dialog v-model="showAddDialog" title="添加用户" width="420px" :close-on-click-modal="false">
      <el-form :model="addForm" :rules="addRules" ref="addFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" placeholder="3-20位字母数字" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="addForm.displayName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" placeholder="至少6位" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="addForm.role">
            <el-radio value="USER">普通用户</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddUser" :loading="addLoading">确定添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { userApi } from '../api'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const users = ref([])
const loading = ref(false)
const loadingId = ref(null)

const loadUsers = async () => {
  loading.value = true
  try {
    const { data } = await userApi.list()
    users.value = data || []
  } finally {
    loading.value = false
  }
}

const handleChangeRole = async (row, newRole) => {
  loadingId.value = row.id
  try {
    await userApi.updateRole(row.id, newRole)
    ElMessage.success(`已${newRole === 'ADMIN' ? '设为' : '取消'}管理员`)
    loadUsers()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '操作失败')
  } finally {
    loadingId.value = null
  }
}

const handleDelete = async (row) => {
  loadingId.value = row.id
  try {
    await userApi.delete(row.id)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  } finally {
    loadingId.value = null
  }
}

// Add user
const showAddDialog = ref(false)
const addLoading = ref(false)
const addFormRef = ref(null)
const addForm = reactive({ username: '', displayName: '', password: '', role: 'USER' })
const addRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20位字母/数字/下划线', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '至少6位', trigger: 'blur' }],
}

const handleAddUser = async () => {
  try { await addFormRef.value.validate() } catch { return }
  addLoading.value = true
  try {
    await userApi.create({ ...addForm, displayName: addForm.displayName || addForm.username })
    ElMessage.success('用户创建成功')
    showAddDialog.value = false
    Object.assign(addForm, { username: '', displayName: '', password: '', role: 'USER' })
    loadUsers()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    addLoading.value = false
  }
}

onMounted(loadUsers)
</script>
