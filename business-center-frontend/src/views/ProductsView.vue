<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">产品管理</h2>
        <p class="page-subtitle">维护客户提交订单时可选择的产品目录。</p>
      </div>
      <div class="toolbar">
        <el-button @click="loadProducts">刷新</el-button>
        <el-button type="primary" @click="dialogVisible = true">新增产品</el-button>
      </div>
    </div>

    <section class="panel-card table-card">
      <el-table :data="products" stripe>
        <el-table-column prop="productNo" label="产品号" min-width="140" />
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column prop="price" label="价格" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="220" />
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" title="新增产品" width="520px">
      <el-form label-position="top">
        <el-form-item label="产品号">
          <el-input v-model="form.productNo" placeholder="例如：P-1003" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="form.productName" placeholder="例如：机械键盘" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.price" :min="1" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProduct">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { createProduct, listProducts } from '@/api/business'
import type { Product } from '@/types'

const products = ref<Product[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  productNo: '',
  productName: '',
  price: 100,
  description: ''
})

async function loadProducts() {
  const response = await listProducts()
  products.value = response.data
}

function resetForm() {
  form.productNo = ''
  form.productName = ''
  form.price = 100
  form.description = ''
}

async function submitProduct() {
  submitting.value = true
  try {
    await createProduct({
      productNo: form.productNo.trim(),
      productName: form.productName.trim(),
      price: Number(form.price),
      description: form.description.trim()
    })
    ElMessage.success('产品创建成功')
    dialogVisible.value = false
    resetForm()
    await loadProducts()
  } finally {
    submitting.value = false
  }
}

onMounted(loadProducts)
</script>
