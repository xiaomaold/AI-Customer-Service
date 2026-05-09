export function formatOrderStatus(status: string) {
  const map: Record<string, string> = {
    UNPAID: '未付款',
    PAID: '已付款',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

export function formatWorkOrderStatus(status: string) {
  const map: Record<string, string> = {
    PENDING: '未处理',
    PROCESSING: '处理中',
    RESOLVED: '已处理',
    REJECTED: '不予处理'
  }
  return map[status] || status
}

export function formatWorkOrderType(type: string) {
  const map: Record<string, string> = {
    LEAVE: '请假',
    REFUND: '退款',
    HUMAN_SERVICE: '转人工'
  }
  return map[type] || type
}

export function formatUserType(type: string) {
  const map: Record<string, string> = {
    EMPLOYEE: '员工',
    CUSTOMER: '客户'
  }
  return map[type] || type
}

export function formatLeaveType(type: string) {
  const map: Record<string, string> = {
    ANNUAL: '年假',
    SICK: '病假',
    PERSONAL: '事假',
    OTHER: '其他'
  }
  return map[type] || type
}

export function buildStatusClass(status: string) {
  return `status-pill status-pill--${status.toLowerCase().replace('_', '-')}`
}
