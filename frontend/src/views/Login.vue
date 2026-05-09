<template>
  <div class="login-page">
    <div class="login-backdrop login-backdrop--left"></div>
    <div class="login-backdrop login-backdrop--right"></div>

    <div class="login-shell">
      <section class="login-hero">
        <div class="hero-badge">AI RAG Workspace</div>
        <h1>企业知识问答与客服协同平台</h1>
        <p>统一接入知识库检索、文档分析、客服规范查询与智能会话处理。</p>

        <div class="hero-points">
          <div class="hero-point">
            <strong>知识问答</strong>
            <span>让客服与业务人员在同一入口快速获得可靠答案。</span>
          </div>
          <div class="hero-point">
            <strong>文档处理</strong>
            <span>上传文档后由 AI 自动总结、抽取信息并推荐知识库归档。</span>
          </div>
          <div class="hero-point">
            <strong>权限协同</strong>
            <span>按角色管理知识库访问与维护权限，适合企业内部协作。</span>
          </div>
        </div>
      </section>

      <section class="login-card">
        <div class="brand">
          <div class="brand-mark">AI</div>
          <div class="brand-copy">
            <div class="brand-title">欢迎回来</div>
            <div class="brand-subtitle">登录后即可进入 AI 工作台</div>
          </div>
        </div>

        <el-form class="login-form" :model="form" @submit.prevent="handleLogin">
          <el-form-item>
            <el-input v-model="form.username" placeholder="用户名" size="large" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              size="large"
              @keydown.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="submit-btn" :loading="authStore.loading" @click="handleLogin">
            登录
          </el-button>
        </el-form>

        <div class="login-tip">建议使用企业账号登录，以同步你的会话与知识库权限。</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: ''
})

async function handleLogin() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码。')
    return
  }

  try {
    await authStore.login(form.username.trim(), form.password)
    router.push('/')
  } catch {
    // handled by interceptor
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #f7f9fc;
  padding: 28px;
}

.login-backdrop {
  position: absolute;
  border-radius: 999px;
  filter: blur(18px);
  opacity: 0.9;
}

.login-backdrop--left {
  width: 360px;
  height: 360px;
  left: -80px;
  top: 12%;
  background: rgba(191, 219, 254, 0.55);
}

.login-backdrop--right {
  width: 440px;
  height: 440px;
  right: -120px;
  bottom: 5%;
  background: rgba(229, 237, 246, 0.88);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 440px);
  gap: 24px;
  align-items: stretch;
}

.login-hero,
.login-card {
  border-radius: 32px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  box-shadow: var(--app-shadow-lg);
  backdrop-filter: blur(18px);
}

.login-hero {
  padding: 36px;
  background: #ffffff;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(111, 151, 194, 0.1);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.login-hero h1 {
  margin: 22px 0 14px;
  font-size: 42px;
  line-height: 1.12;
  color: var(--app-text);
}

.login-hero p {
  max-width: 520px;
  color: var(--app-text-secondary);
  font-size: 16px;
  line-height: 1.8;
}

.hero-points {
  display: grid;
  gap: 14px;
  margin-top: 34px;
}

.hero-point {
  padding: 18px 18px 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: var(--app-shadow-sm);
}

.hero-point strong {
  display: block;
  font-size: 16px;
  color: var(--app-text);
}

.hero-point span {
  display: block;
  margin-top: 8px;
  line-height: 1.75;
  color: var(--app-text-secondary);
}

.login-card {
  padding: 30px;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: #7ea3cb;
  color: #fff;
  font-weight: 800;
  letter-spacing: 0.08em;
  box-shadow: 0 8px 18px rgba(126, 163, 203, 0.16);
}

.brand-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--app-text);
}

.brand-subtitle {
  margin-top: 4px;
  color: var(--app-text-secondary);
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-input__wrapper) {
    min-height: 52px;
    box-shadow: none;
    border: 1px solid rgba(148, 163, 184, 0.22);
    background: rgba(248, 251, 255, 0.96);
  }
}

.submit-btn {
  width: 100%;
  min-height: 50px;
  margin-top: 6px;
  box-shadow: 0 12px 22px rgba(111, 151, 194, 0.16);
}

.login-tip {
  margin-top: 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-tertiary);
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-hero {
    padding: 28px;
  }

  .login-hero h1 {
    font-size: 34px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 16px;
  }

  .login-hero,
  .login-card {
    border-radius: 24px;
    padding: 22px;
  }

  .login-hero h1 {
    font-size: 28px;
  }
}
</style>
