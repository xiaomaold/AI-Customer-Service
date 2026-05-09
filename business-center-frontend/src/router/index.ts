import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import ProductsView from '@/views/ProductsView.vue'
import OrdersView from '@/views/OrdersView.vue'
import WorkOrdersView from '@/views/WorkOrdersView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView },
        { path: 'products', name: 'products', component: ProductsView },
        { path: 'orders', name: 'orders', component: OrdersView },
        { path: 'work-orders', name: 'work-orders', component: WorkOrdersView }
      ]
    }
  ]
})

export default router
