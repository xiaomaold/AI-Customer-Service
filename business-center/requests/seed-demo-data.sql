SET NAMES utf8mb4;

INSERT INTO bc_order (
    id, order_no, user_id, user_type, product_id, product_no, product_name_snapshot,
    unit_price_snapshot, quantity, total_amount, status, cancel_reason, source_channel,
    created_time, updated_time
)
SELECT
    4001, 'ORD202604220001', 2001, 'CUSTOMER', 3001, 'P-1001', '商务笔记本电脑',
    5999.00, 1, 5999.00, 'UNPAID', NULL, 'AI_CHAT', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM bc_order WHERE order_no = 'ORD202604220001'
);

INSERT INTO bc_order (
    id, order_no, user_id, user_type, product_id, product_no, product_name_snapshot,
    unit_price_snapshot, quantity, total_amount, status, cancel_reason, source_channel,
    created_time, updated_time
)
SELECT
    4002, 'ORD202604220002', 2001, 'CUSTOMER', 3002, 'P-1002', '无线鼠标',
    129.00, 2, 258.00, 'PAID', NULL, 'MANUAL_BACKOFFICE', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM bc_order WHERE order_no = 'ORD202604220002'
);

INSERT INTO bc_work_order (
    id, work_order_no, user_id, user_type, work_order_type, status, title, content,
    related_order_no, ext_json, reject_reason, processed_by, process_remark,
    processed_time, source_channel, created_time, updated_time
)
SELECT
    5001, 'WO202604220001', 1001, 'EMPLOYEE', 'LEAVE', 'PENDING',
    '员工演示账号请假申请', '下周需要请假处理个人事务', NULL,
    '{"leaveDays":3,"leaveType":"ANNUAL"}',
    NULL, NULL, NULL, NULL, 'AI_CHAT', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM bc_work_order WHERE work_order_no = 'WO202604220001'
);

INSERT INTO bc_work_order (
    id, work_order_no, user_id, user_type, work_order_type, status, title, content,
    related_order_no, ext_json, reject_reason, processed_by, process_remark,
    processed_time, source_channel, created_time, updated_time
)
SELECT
    5002, 'WO202604220002', 2001, 'CUSTOMER', 'HUMAN_SERVICE', 'PROCESSING',
    '客户演示账号转人工申请', '客户希望人工跟进发货问题', NULL,
    '{"sessionId":12345,"lastQuestion":"我的订单什么时候发货","aiSummary":"客户咨询发货进度，希望人工接入"}',
    NULL, 9001, '已分配客服跟进', NOW(), 'AI_CHAT', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM bc_work_order WHERE work_order_no = 'WO202604220002'
);

INSERT INTO bc_work_order (
    id, work_order_no, user_id, user_type, work_order_type, status, title, content,
    related_order_no, ext_json, reject_reason, processed_by, process_remark,
    processed_time, source_channel, created_time, updated_time
)
SELECT
    5003, 'WO202604220003', 2001, 'CUSTOMER', 'REFUND', 'RESOLVED',
    '客户演示账号退款申请', '客户申请退款', 'ORD202604220002',
    '{"orderNo":"ORD202604220002"}',
    NULL, 9001, '已处理退款申请', NOW(), 'MANUAL_BACKOFFICE', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM bc_work_order WHERE work_order_no = 'WO202604220003'
);

SELECT order_no, status, total_amount FROM bc_order ORDER BY id;
SELECT work_order_no, work_order_type, status, related_order_no FROM bc_work_order ORDER BY id;
