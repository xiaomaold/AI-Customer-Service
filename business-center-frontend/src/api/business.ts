import request from '@/utils/request'
import type { BusinessUser, Order, Product, WorkOrder } from '@/types'

export function listUsers() {
  return request.get<unknown, { data: BusinessUser[] }>('/users')
}

export function listProducts() {
  return request.get<unknown, { data: Product[] }>('/products')
}

export function createProduct(payload: {
  productNo: string
  productName: string
  price: number
  description?: string
}) {
  return request.post<unknown, { data: Product }>('/products', payload)
}

export function listOrders() {
  return request.get<unknown, { data: Order[] }>('/orders')
}

export function createOrder(payload: {
  userId: number
  productNo: string
  quantity: number
  sourceChannel: 'AI_CHAT' | 'MANUAL_BACKOFFICE'
}) {
  return request.post<unknown, { data: Order }>('/orders', payload)
}

export function cancelOrder(orderNo: string, payload: { userId: number; cancelReason: string }) {
  return request.post<unknown, { data: Order }>(`/orders/${orderNo}/cancel`, payload)
}

export function markOrderPaid(orderNo: string) {
  return request.post<unknown, { data: Order }>(`/orders/${orderNo}/mark-paid`)
}

export function listWorkOrders() {
  return request.get<unknown, { data: WorkOrder[] }>('/work-orders')
}

export function createWorkOrder(payload: {
  userId: number
  workOrderType: 'LEAVE' | 'REFUND' | 'HUMAN_SERVICE'
  content?: string
  extData?: Record<string, unknown>
  sourceChannel: 'AI_CHAT' | 'MANUAL_BACKOFFICE'
}) {
  return request.post<unknown, { data: WorkOrder }>('/work-orders', payload)
}

export function updateWorkOrderStatus(
  workOrderNo: string,
  payload: {
    status: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED'
    processedBy?: number
    processRemark?: string
    rejectReason?: string
  }
) {
  return request.post<unknown, { data: WorkOrder }>(`/work-orders/${workOrderNo}/status`, payload)
}
