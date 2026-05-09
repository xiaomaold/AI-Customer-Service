<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单管理</h2>
        <p class="page-subtitle">在后台创建订单、取消未付款订单，并手动把订单标记为已付款。</p>
      </div>
      <div class="toolbar">
        <el-button @click="loadData">刷新</el-button>
        <el-button type="primary" @click="createDialogVisible = true">新增订单</el-button>
      </div>
    </div>

    <section class="panel-card table-card">
      <el-table :data="orders" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column label="客户" min-width="140">
          <template #default="{ row }">{{ userNameMap[row.userId] || row.userId }}</template>
        </el-table-column>
        <el-table-column label="用户类型" min-width="110">
          <template #default="{ row }">{{ formatUserType(row.userType) }}</template>
        </el-table-column>
        <el-table-column prop="productNameSnapshot" label="产品" min-width="180" />
        <el-table-column prop="quantity" label="数量" min-width="80" />
        <el-table-column prop="totalAmount" label="总金额" min-width="100" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <span :class="buildStatusClass(row.status)">{{ formatOrderStatus(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="toolbar">
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="row.status !== 'UNPAID'"
                @click="markPaid(row.orderNo)"
              >
                标记已付款
              </el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="row.status !== 'UNPAID'"
                @click="openCancelDialog(row.orderNo, row.userId)"
              >
                取消订单
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增订单" width="520px">
      <el-form label-position="top">
        <el-form-item label="客户">
          <el-select v-model="createForm.userId" style="width: 100%">
            <el-option
              v-for="user in customers"
              :key="user.id"
              :label="`${user.displayName}（${formatUserType(user.userType)}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品">
          <el-select v-model="createForm.productNo" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.productNo"
              :label="`${product.productName}（${product.productNo}）`"
              :value="product.productNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="createForm.quantity" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitOrder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialogVisible" title="取消订单" width="480px">
      <el-form label-position="top">
        <el-form-item label="取消原因">
          <el-input v-model="cancelForm.cancelReason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="cancelDialogVisible = false">返回</el-button>
        <el-button type="danger" :loading="cancelling" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { cancelOrder, createOrder, listOrders, listProducts, listUsers, markOrderPaid } from '@/api/business'
import type { BusinessUser, Order, Product } from '@/types'
import { buildStatusClass, formatOrderStatus, formatUserType } from '@/utils/display'

const orders = ref<Order[]>([])
const customers = ref<BusinessUser[]>([])
const products = ref<Product[]>([])

const createDialogVisible = ref(false)
const cancelDialogVisible = ref(false)
const creating = ref(false)
const cancelling = ref(false)

const createForm = reactive({
  userId: 2001,
  productNo: 'P-1001',
  quantity: 1
})

const cancelForm = reactive({
  orderNo: '',
  userId: 0,
  cancelReason: ''
})

const userNameMap = computed<Record<number, string>>(() =>
  customers.value.reduce((map, user) => {
    map[user.id] = user.displayName
    return map
  }, {} as Record<number, string>)
)

async function loadData() {
  const [orderResponse, userResponse, productResponse] = await Promise.all([
    listOrders(),
    listUsers(),
    listProducts()
  ])
  orders.value = orderResponse.data
  customers.value = userResponse.data.filter((item) => item.userType === 'CUSTOMER')
  products.value = productResponse.data
}

async function submitOrder() {
  creating.value = true
  try {
    await createOrder({
      userId: createForm.userId,
      productNo: createForm.productNo,
      quantity: createForm.quantity,
      sourceChannel: 'MANUAL_BACKOFFICE'
    })
    ElMessage.success('订单创建成功')
    createDialogVisible.value = false
    await loadData()
  } finally {
    creating.value = false
  }
}

async function markPaid(orderNo: string) {
  await markOrderPaid(orderNo)
  ElMessage.success('订单已标记为已付款')
  await loadData()
}

function openCancelDialog(orderNo: string, userId: number) {
  cancelForm.orderNo = orderNo
  cancelForm.userId = userId
  cancelForm.cancelReason = ''
  cancelDialogVisible.value = true
}

async function submitCancel() {
  cancelling.value = true
  try {
    await cancelOrder(cancelForm.orderNo, {
      userId: cancelForm.userId,
      cancelReason: cancelForm.cancelReason
    })
    ElMessage.success('订单已取消')
    cancelDialogVisible.value = false
    await loadData()
  } finally {
    cancelling.value = false
  }
}

onMounted(loadData)
</script>
