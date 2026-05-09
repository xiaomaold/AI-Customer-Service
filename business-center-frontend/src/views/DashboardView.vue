<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">运营总览</h2>
        <p class="page-subtitle">这里展示独立业务模块当前的核心数据，方便先确认业务系统本身已经跑通。</p>
      </div>
      <el-button type="primary" @click="loadData">刷新</el-button>
    </div>

    <div class="grid-cards">
      <article class="panel-card metric-card">
        <div class="metric-label">产品数</div>
        <div class="metric-value">{{ products.length }}</div>
        <div class="metric-footnote">包含初始化产品和后台新增产品。</div>
      </article>
      <article class="panel-card metric-card">
        <div class="metric-label">订单数</div>
        <div class="metric-value">{{ orders.length }}</div>
        <div class="metric-footnote">订单独立管理，不和工单混在一起。</div>
      </article>
      <article class="panel-card metric-card">
        <div class="metric-label">待处理工单</div>
        <div class="metric-value">{{ pendingWorkOrderCount }}</div>
        <div class="metric-footnote">包含请假、退款、转人工三类工单。</div>
      </article>
      <article class="panel-card metric-card">
        <div class="metric-label">未付款订单</div>
        <div class="metric-value">{{ unpaidOrderCount }}</div>
        <div class="metric-footnote">未付款订单仍可在后台取消。</div>
      </article>
    </div>

    <div class="grid-cards">
      <section class="panel-card table-card">
        <div class="section-title">最新订单</div>
        <el-table :data="orders.slice(0, 5)" stripe>
          <el-table-column prop="orderNo" label="订单号" min-width="160" />
          <el-table-column prop="productNameSnapshot" label="产品" min-width="180" />
          <el-table-column prop="totalAmount" label="金额" min-width="100" />
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <span :class="buildStatusClass(row.status)">{{ formatOrderStatus(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel-card table-card">
        <div class="section-title">最新工单</div>
        <el-table :data="workOrders.slice(0, 5)" stripe>
          <el-table-column prop="workOrderNo" label="工单号" min-width="170" />
          <el-table-column label="类型" min-width="130">
            <template #default="{ row }">{{ formatWorkOrderType(row.workOrderType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <span :class="buildStatusClass(row.status)">{{ formatWorkOrderStatus(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { listOrders, listProducts, listWorkOrders } from '@/api/business'
import type { Order, Product, WorkOrder } from '@/types'
import {
  buildStatusClass,
  formatOrderStatus,
  formatWorkOrderStatus,
  formatWorkOrderType
} from '@/utils/display'

const products = ref<Product[]>([])
const orders = ref<Order[]>([])
const workOrders = ref<WorkOrder[]>([])

const unpaidOrderCount = computed(() => orders.value.filter((item) => item.status === 'UNPAID').length)
const pendingWorkOrderCount = computed(() => workOrders.value.filter((item) => item.status === 'PENDING').length)

async function loadData() {
  const [productResponse, orderResponse, workOrderResponse] = await Promise.all([
    listProducts(),
    listOrders(),
    listWorkOrders()
  ])
  products.value = productResponse.data
  orders.value = orderResponse.data
  workOrders.value = workOrderResponse.data
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.section-title {
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 700;
}
</style>
