<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单管理</h2>
        <p class="page-subtitle">统一处理请假、退款、转人工这三类工单。</p>
      </div>
      <div class="toolbar">
        <el-button @click="loadData">刷新</el-button>
        <el-button type="primary" @click="createDialogVisible = true">新增工单</el-button>
      </div>
    </div>

    <section class="panel-card table-card">
      <el-table :data="workOrders" stripe>
        <el-table-column prop="workOrderNo" label="工单号" min-width="180" />
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">{{ userNameMap[row.userId] || row.userId }}</template>
        </el-table-column>
        <el-table-column label="用户类型" min-width="110">
          <template #default="{ row }">{{ formatUserType(row.userType) }}</template>
        </el-table-column>
        <el-table-column label="工单类型" min-width="130">
          <template #default="{ row }">{{ formatWorkOrderType(row.workOrderType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <span :class="buildStatusClass(row.status)">{{ formatWorkOrderStatus(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain type="primary" @click="openStatusDialog(row)">更新状态</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增工单" width="560px">
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-select v-model="createForm.userId" style="width: 100%">
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="`${user.displayName}（${formatUserType(user.userType)}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="工单类型">
          <el-select v-model="createForm.workOrderType" style="width: 100%">
            <el-option label="请假" value="LEAVE" />
            <el-option label="退款" value="REFUND" />
            <el-option label="转人工" value="HUMAN_SERVICE" />
          </el-select>
        </el-form-item>

        <el-form-item label="内容说明">
          <el-input v-model="createForm.content" type="textarea" :rows="3" />
        </el-form-item>

        <template v-if="createForm.workOrderType === 'LEAVE'">
          <el-form-item label="请假天数">
            <el-input-number v-model="createForm.leaveDays" :min="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="请假类型">
            <el-select v-model="createForm.leaveType" style="width: 100%">
              <el-option label="年假" value="ANNUAL" />
              <el-option label="病假" value="SICK" />
              <el-option label="事假" value="PERSONAL" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="createForm.workOrderType === 'REFUND'">
          <el-form-item label="关联订单">
            <el-select v-model="createForm.orderNo" style="width: 100%">
              <el-option
                v-for="order in refundableOrders"
                :key="order.orderNo"
                :label="`${order.orderNo}（${order.productNameSnapshot}）`"
                :value="order.orderNo"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitWorkOrder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusDialogVisible" title="更新工单状态" width="520px">
      <el-form label-position="top">
        <el-form-item label="状态">
          <el-select v-model="statusForm.status" style="width: 100%">
            <el-option label="未处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已处理" value="RESOLVED" />
            <el-option label="不予处理" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input-number v-model="statusForm.processedBy" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="statusForm.processRemark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="statusForm.status === 'REJECTED'" label="驳回原因">
          <el-input v-model="statusForm.rejectReason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="statusDialogVisible = false">返回</el-button>
        <el-button type="primary" :loading="updating" @click="submitStatus">更新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { createWorkOrder, listOrders, listUsers, listWorkOrders, updateWorkOrderStatus } from '@/api/business'
import type { BusinessUser, Order, WorkOrder } from '@/types'
import { buildStatusClass, formatUserType, formatWorkOrderStatus, formatWorkOrderType } from '@/utils/display'

const users = ref<BusinessUser[]>([])
const orders = ref<Order[]>([])
const workOrders = ref<WorkOrder[]>([])

const createDialogVisible = ref(false)
const statusDialogVisible = ref(false)
const creating = ref(false)
const updating = ref(false)

const createForm = reactive({
  userId: 1001,
  workOrderType: 'LEAVE' as 'LEAVE' | 'REFUND' | 'HUMAN_SERVICE',
  content: '',
  leaveDays: 1,
  leaveType: 'ANNUAL',
  orderNo: ''
})

const statusForm = reactive({
  workOrderNo: '',
  status: 'PROCESSING' as 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED',
  processedBy: 9001,
  processRemark: '',
  rejectReason: ''
})

const userNameMap = computed<Record<number, string>>(() =>
  users.value.reduce((map, user) => {
    map[user.id] = user.displayName
    return map
  }, {} as Record<number, string>)
)

const refundableOrders = computed(() =>
  orders.value.filter((order) => order.status === 'PAID' && order.userId === createForm.userId)
)

async function loadData() {
  const [userResponse, orderResponse, workOrderResponse] = await Promise.all([
    listUsers(),
    listOrders(),
    listWorkOrders()
  ])
  users.value = userResponse.data
  orders.value = orderResponse.data
  workOrders.value = workOrderResponse.data
}

async function submitWorkOrder() {
  creating.value = true
  try {
    const extData =
      createForm.workOrderType === 'LEAVE'
        ? {
            leaveDays: createForm.leaveDays,
            leaveType: createForm.leaveType
          }
        : createForm.workOrderType === 'REFUND'
          ? {
              orderNo: createForm.orderNo
            }
          : {}

    await createWorkOrder({
      userId: createForm.userId,
      workOrderType: createForm.workOrderType,
      content: createForm.content,
      extData,
      sourceChannel: 'MANUAL_BACKOFFICE'
    })
    ElMessage.success('工单创建成功')
    createDialogVisible.value = false
    await loadData()
  } finally {
    creating.value = false
  }
}

function openStatusDialog(workOrder: WorkOrder) {
  statusForm.workOrderNo = workOrder.workOrderNo
  statusForm.status = workOrder.status
  statusForm.processedBy = workOrder.processedBy ?? 9001
  statusForm.processRemark = workOrder.processRemark ?? ''
  statusForm.rejectReason = workOrder.rejectReason ?? ''
  statusDialogVisible.value = true
}

async function submitStatus() {
  updating.value = true
  try {
    await updateWorkOrderStatus(statusForm.workOrderNo, {
      status: statusForm.status,
      processedBy: statusForm.processedBy,
      processRemark: statusForm.processRemark,
      rejectReason: statusForm.status === 'REJECTED' ? statusForm.rejectReason : undefined
    })
    ElMessage.success('工单状态已更新')
    statusDialogVisible.value = false
    await loadData()
  } finally {
    updating.value = false
  }
}

onMounted(loadData)
</script>
