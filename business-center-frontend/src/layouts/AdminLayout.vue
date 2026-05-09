<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="brand-block">
        <p class="brand-kicker">业务中心</p>
        <h1 class="brand-title">管理后台</h1>
        <p class="brand-copy">先把订单、产品、工单后台跑通，后续再由 AI 客服统一接入。</p>
      </div>

      <el-menu :default-active="activePath" class="side-menu" router>
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>总览</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon>
          <span>产品</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单</span>
        </el-menu-item>
        <el-menu-item index="/work-orders">
          <el-icon><Service /></el-icon>
          <span>工单</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="admin-main">
      <header class="topbar panel-card">
        <div>
          <p class="topbar-label">独立模块</p>
          <strong class="topbar-title">业务中心后台运营台</strong>
        </div>
        <el-tag type="primary" effect="dark">后端 8091</el-tag>
      </header>

      <section class="admin-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { DataBoard, Goods, Service, Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const activePath = computed(() => route.path)
</script>

<style scoped lang="scss">
.admin-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: 100vh;
}

.admin-sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 20px;
  border-right: 1px solid rgba(217, 226, 242, 0.8);
  background:
    linear-gradient(180deg, rgba(18, 31, 63, 0.98), rgba(23, 44, 87, 0.96)),
    linear-gradient(180deg, #12203f 0%, #1f3770 100%);
  color: #f4f7ff;
}

.brand-block {
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.04);
}

.brand-kicker {
  margin: 0 0 10px;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.66);
}

.brand-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.1;
}

.brand-copy {
  margin: 12px 0 0;
  line-height: 1.7;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

:deep(.side-menu) {
  border-right: none;
  background: transparent;
}

:deep(.side-menu .el-menu-item) {
  margin-bottom: 8px;
  border-radius: 14px;
  color: rgba(255, 255, 255, 0.76);
}

:deep(.side-menu .el-menu-item.is-active) {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}

.admin-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 22px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
}

.topbar-label {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--bc-text-soft);
}

.topbar-title {
  font-size: 16px;
}

.admin-content {
  min-width: 0;
  padding-top: 20px;
}

@media (max-width: 1080px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    border-right: none;
    border-bottom: 1px solid rgba(217, 226, 242, 0.8);
  }
}
</style>
