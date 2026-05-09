package com.airag.modules.chat.routing;

import java.util.List;

public final class QuestionIntentKeywordCatalog {

    public static final List<String> GENERAL_GENERATION_ALIASES = List.of(
            "帮我写", "帮我生成", "帮我做", "帮我弄", "给我写", "给我生成", "给我做", "给我弄",
            "写一份", "写一个", "生成一份", "生成一个", "做一份", "做一个", "弄一份", "弄一个",
            "润色", "改写", "重写", "扩写", "缩写", "总结", "翻译", "整理",
            "模板", "表格", "表单", "请假条", "请假表", "邮件", "申请书", "通知"
    );

    public static final List<String> KNOWLEDGE_REQUIRED_ALIASES = List.of(
            "根据公司", "按公司", "按照公司", "根据制度", "按制度", "按照制度",
            "根据知识库", "按知识库", "按照知识库", "根据规定", "按规定", "按照规定",
            "审批流程", "报销流程", "请假制度", "公司制度", "公司流程", "员工手册",
            "知识库", "文档", "文件", "资料", "政策", "规则", "流程"
    );

    public static final List<String> BUSINESS_PROCESS_ALIASES = List.of(
            "请假", "报销", "退款", "售后", "审批", "工单", "合同", "发票", "客服", "申请"
    );

    public static final List<String> PRE_SALES_ALIASES = List.of(
            "价格", "报价", "多少钱", "购买", "试用", "演示", "方案", "功能", "产品", "适用场景"
    );

    public static final List<String> AFTER_SALES_ALIASES = List.of(
            "售后", "故障", "报修", "维修", "支持", "修复", "问题处理", "服务范围"
    );

    public static final List<String> REFUND_ALIASES = List.of(
            "退款", "退货", "换货", "退换", "退回", "售后政策"
    );

    public static final List<String> COMPLAINT_ALIASES = List.of(
            "投诉", "差评", "不满", "纠纷", "升级处理", "抱怨", "客服态度"
    );

    public static final List<String> CONTACT_CHANNEL_ALIASES = List.of(
            "联系方式", "客服电话", "电话", "热线", "邮箱", "地址", "工单入口", "服务时间", "联系我们"
    );

    public static final List<String> ORDER_PAYMENT_ALIASES = List.of(
            "订单", "支付", "付款", "发票", "账单", "合同", "报价", "扣款", "到账", "到帐"
    );

    public static final List<String> ACCOUNT_PERMISSION_ALIASES = List.of(
            "账号", "账户", "登录", "注册", "密码", "权限", "开通", "禁用", "用户", "身份"
    );

    public static final List<String> HR_ADMIN_ALIASES = List.of(
            "请假", "报销", "事假", "病假", "年假", "制度", "流程", "员工手册", "人事", "行政"
    );

    public static final List<String> HUMAN_SERVICE_ALIASES = List.of(
            "人工", "人工客服", "转人工", "专员", "人工处理", "客服介入"
    );

    public static final List<String> WORK_ORDER_ALIASES = List.of(
            "工单", "提交申请", "发起申请", "提交记录", "登记", "售后申请", "投诉单"
    );

    public static final List<String> STATUS_QUERY_ALIASES = List.of(
            "进度", "状态", "到哪一步", "处理到哪", "审核到哪", "到账了吗", "到帐了吗"
    );

    public static final List<String> KNOWLEDGE_TOPIC_SUFFIXES = List.of(
            "规则", "流程", "制度", "政策", "规范", "办法", "说明", "指引", "要求", "标准"
    );

    public static final List<String> DOCUMENT_DISCOVERY_ALIASES = List.of(
            "有哪些", "哪几份", "列出", "列表", "清单", "目录", "哪些文档", "什么文档",
            "哪些资料", "哪些文件", "有哪些报告", "有哪些实验报告"
    );

    public static final List<String> DOCUMENT_TARGET_ALIASES = List.of(
            "文档", "文件", "资料", "报告", "手册", "合同", "简历", "pdf"
    );

    public static final List<String> DOCUMENT_UPLOAD_ACTION_ALIASES = List.of(
            "上传文档", "上传文件", "上传资料", "放到知识库", "入库", "导入知识库", "帮我上传"
    );

    public static final List<String> DOCUMENT_ANALYZE_ACTION_ALIASES = List.of(
            "解析文档", "解析文件", "分析文档", "分析文件", "提取文档", "提取文件",
            "总结文档", "总结文件", "识别文档", "识别文件"
    );

    public static final List<String> PRODUCT_QUERY_ACTION_ALIASES = List.of(
            "产品信息", "商品信息", "产品详情", "商品详情", "查看产品", "查看商品",
            "查询产品", "查询商品", "产品介绍", "商品介绍", "产品资料", "商品资料"
    );

    public static final List<String> ORDER_SUBMISSION_ACTION_ALIASES = List.of(
            "帮我下单", "帮我提交订单", "我要下单", "我要买", "我要购买",
            "创建订单", "提交订单", "生成订单", "买下来", "帮我买"
    );

    public static final List<String> REFUND_REQUEST_ACTION_ALIASES = List.of(
            "帮我退款", "我要退款", "申请退款", "发起退款", "帮我申请退款", "帮我发起退款"
    );

    public static final List<String> PHONE_ALIASES = List.of(
            "客服电话", "客服热线", "联系电话", "售后电话", "商务电话", "电话", "热线"
    );

    public static final List<String> EMAIL_ALIASES = List.of(
            "邮箱", "邮件", "联系邮箱", "客服邮箱", "商务邮箱", "合作邮箱", "销售邮箱", "电子邮箱"
    );

    public static final List<String> ADDRESS_ALIASES = List.of(
            "地址", "公司地址", "办公地址", "联系地址", "位置", "在哪里"
    );

    public static final List<String> CHAT_ALIASES = List.of(
            "你好", "您好", "hi", "hello", "早上好", "下午好", "晚上好", "谢谢", "感谢"
    );

    public static final List<String> FOLLOW_UP_GENERATION_ALIASES = List.of(
            "再简短一点", "再详细一点", "再正式一点", "再口语一点", "换个说法", "换一种说法",
            "改成表格", "改成列表", "改成邮件", "改成通知", "改成模板", "润色一下",
            "重写一下", "精简一下", "扩写一下", "继续", "接着写", "补充一下"
    );

    public static final List<String> KNOWLEDGE_BASE_LIST_ALIASES = List.of(
            "我能看什么知识库",
            "我能查看什么知识库",
            "我能看哪些知识库",
            "我能查看哪些知识库",
            "有哪些知识库",
            "列出知识库",
            "知识库列表"
    );

    public static final List<String> KNOWLEDGE_BASE_LIST_EXCLUSIONS = List.of(
            "哪个知识库", "在哪个知识库", "知识库有几个文档", "哪个库"
    );

    public static final List<String> COMPANY_SUBJECT_ALIASES = List.of(
            "公司", "企业"
    );

    public static final List<String> COMPANY_INTRO_ALIASES = List.of(
            "介绍", "是什么"
    );

    public static final List<String> DOCUMENT_STOP_WORDS = List.of(
            "这份", "这个", "请", "文档", "文件", "里面", "里", "是谁的", "是谁",
            "写了什么", "讲了什么", "是什么内容", "内容", "包含哪些信息", "联系方式",
            "手机号", "电话", "邮箱", "姓名", "：", ",", "。", "，", "?"
    );

    public static final List<String> DOCUMENT_ENUMERATION_ALIASES = List.of(
            "哪些", "全部", "所有", "分别", "列出", "完整", "都有", "项目", "经历"
    );

    public static final List<String> DOCUMENT_CONTACT_ALIASES = List.of(
            "姓名", "谁", "联系方式"
    );

    public static final List<String> STRUCTURED_FACT_DOCUMENT_ALIASES = List.of(
            "联系方式", "联系我们", "公司简介", "企业介绍", "售后政策", "退款与售后政策", "客服"
    );

    public static final List<String> PHONE_FACT_QUERIES = List.of(
            "联系电话", "客服电话", "客服热线", "售后电话"
    );

    public static final List<String> EMAIL_FACT_QUERIES = List.of(
            "联系邮箱", "客服邮箱", "电子邮箱"
    );

    public static final List<String> ADDRESS_FACT_QUERIES = List.of(
            "公司地址", "办公地址", "联系地址"
    );

    private QuestionIntentKeywordCatalog() {
    }
}
