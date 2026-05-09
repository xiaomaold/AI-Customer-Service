<template>
  <div class="missed-dashboard">
    <div class="dashboard-header">
      <div>
        <h3>知识运营看板</h3>
        <p>先看未命中，再决定补什么知识。</p>
      </div>
      <el-button @click="loadDashboard">刷新</el-button>
    </div>

    <div class="metrics" v-loading="loading">
      <div class="metric-card">
        <div class="metric-label">未命中总量</div>
        <div class="metric-value">{{ dashboard?.totalCount ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">待处理</div>
        <div class="metric-value">{{ dashboard?.openCount ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">今日新增</div>
        <div class="metric-value">{{ dashboard?.todayCount ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">近 7 天新增</div>
        <div class="metric-value">{{ dashboard?.lastSevenDaysCount ?? 0 }}</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="panel">
        <div class="panel-title">按路由模式分布</div>
        <el-table :data="dashboard?.routeModeCounts || []" size="small" v-loading="loading">
          <el-table-column prop="key" label="模式" min-width="140" />
          <el-table-column prop="count" label="数量" width="100" />
        </el-table>
      </div>

      <div class="panel">
        <div class="panel-title">按未命中原因分布</div>
        <el-table :data="dashboard?.missReasonCounts || []" size="small" v-loading="loading">
          <el-table-column prop="key" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column prop="count" label="数量" width="100" />
        </el-table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-title">最近未命中问题</div>
      <el-table :data="dashboard?.recentMissedQuestions || []" v-loading="loading">
        <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="routeMode" label="模式" width="120" />
        <el-table-column prop="missReason" label="未命中原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminMissedQuestionDashboardVO } from '@/types'

const dashboard = ref<AdminMissedQuestionDashboardVO | null>(null)
const loading = ref(false)

onMounted(() => {
  loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  try {
    const res = await adminApi.getMissedQuestionDashboard()
    dashboard.value = res.data || null
  } finally {
    loading.value = false
  }
}

function formatDate(date?: string | null) {
  if (!date) return '-'
  return new Date(date).toLocaleString()
}
</script>

<style scoped lang="scss">
.missed-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    margin: 0 0 6px;
    font-size: 18px;
    color: #1f2a37;
  }

  p {
    margin: 0;
    color: #6b7f92;
    font-size: 13px;
  }
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card,
.panel {
  border: 1px solid #e5ebf3;
  border-radius: 14px;
  background: #fff;
  padding: 16px;
}

.metric-label {
  color: #6b7f92;
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  color: #1f2a37;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2a37;
}

@media (max-width: 900px) {
  .metrics,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
