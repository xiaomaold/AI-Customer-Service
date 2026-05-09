export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface BusinessUser {
  id: number
  username: string
  displayName: string
  userType: 'EMPLOYEE' | 'CUSTOMER'
}

export interface Product {
  id: number
  productNo: string
  productName: string
  price: number
  description?: string
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  userType: 'EMPLOYEE' | 'CUSTOMER'
  productId: number
  productNo: string
  productNameSnapshot: string
  unitPriceSnapshot: number
  quantity: number
  totalAmount: number
  status: 'UNPAID' | 'PAID' | 'CANCELLED'
  cancelReason?: string | null
  sourceChannel: 'AI_CHAT' | 'MANUAL_BACKOFFICE'
  createdTime: string
  updatedTime: string
}

export interface WorkOrder {
  id: number
  workOrderNo: string
  userId: number
  userType: 'EMPLOYEE' | 'CUSTOMER'
  workOrderType: 'LEAVE' | 'REFUND' | 'HUMAN_SERVICE'
  status: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED'
  title: string
  content?: string | null
  relatedOrderNo?: string | null
  extData?: Record<string, unknown>
  rejectReason?: string | null
  processedBy?: number | null
  processRemark?: string | null
  processedTime?: string | null
  sourceChannel: 'AI_CHAT' | 'MANUAL_BACKOFFICE'
  createdTime: string
  updatedTime: string
}
