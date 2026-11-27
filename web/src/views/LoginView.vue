<template>
  <div class="hcl-root">
    <!-- ===================== 左侧 · 品牌叙事区 ===================== -->
    <aside class="hcl-brand">
      <canvas ref="netCanvas" class="hcl-net" aria-hidden="true"></canvas>
      <div class="hcl-brand-inner">
        <!-- 徽标 + 系统名 -->
        <div class="hcl-logo-row">
          <div class="hcl-logo" aria-hidden="true">
            <svg viewBox="0 0 40 40" fill="none">
              <path d="M20 3.5 34.5 11.2v14.4L20 36.5 5.5 25.6V11.2L20 3.5Z"
                    stroke="currentColor" stroke-width="2.4" stroke-linejoin="round"/>
              <path d="M13.5 26.5c0-2.8 2.4-4.6 6.5-6.6 4.1-2 6.5-3.8 6.5-6.6"
                    stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/>
              <circle cx="20" cy="3.5" r="2.6" fill="currentColor"/>
              <circle cx="34.5" cy="25.6" r="2.6" fill="currentColor"/>
              <circle cx="5.5" cy="25.6" r="2.6" fill="currentColor"/>
            </svg>
          </div>
          <div class="hcl-logo-text">
            <span class="hcl-logo-en">LAB&nbsp;EHS&nbsp;·&nbsp;HAZMAT</span>
            <span class="hcl-logo-cn">实验室安全信息化平台</span>
          </div>
        </div>

        <h1 class="hcl-title">
          高校实验室<br/>
          <em>危险化学品</em><br/>
          全流程管理系统
        </h1>
        <p class="hcl-sub">
          申购 · 审批 · 出入库 · 领用 · 处置 · 盘点 —— 账实相符，全程留痕。
        </p>

        <!-- 全流程时间线 -->
        <ol class="hcl-flow" aria-label="危化品全流程环节">
          <li v-for="(s, i) in flowSteps" :key="s.t" class="hcl-flow-item"
              :style="{ '--d': (0.55 + i * 0.09) + 's' }">
            <span class="hcl-flow-dot">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
                   v-html="s.icon"></svg>
            </span>
            <span class="hcl-flow-label">{{ s.t }}</span>
            <span class="hcl-flow-desc">{{ s.d }}</span>
          </li>
        </ol>

        <!-- 合规徽章 -->
        <div class="hcl-badges">
          <span v-for="b in badges" :key="b" class="hcl-badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 2 4 5.5v5.2c0 5 3.4 9.4 8 10.8 4.6-1.4 8-5.8 8-10.8V5.5L12 2Z"/>
              <path d="m8.8 12 2.2 2.2 4.2-4.4"/>
            </svg>
            {{ b }}
          </span>
        </div>
      </div>

      <!-- 底部装饰条文 -->
      <footer class="hcl-brand-foot">
        <span>GB 15603 通则</span><i></i>
        <span>《危险化学品安全管理条例》</span><i></i>
        <span>高校实验室安全规范</span>
      </footer>
    </aside>

    <!-- ===================== 右侧 · 登录卡片区 ===================== -->
    <main class="hcl-panel">
      <div class="hcl-card" :class="{ 'hcl-card--shake': shaking }">
        <header class="hcl-card-head">
          <h2>欢迎回来</h2>
          <p>请使用实验室统一账号登录系统</p>
        </header>

        <form class="hcl-form" novalidate @submit.prevent="onSubmit">
          <!-- 用户名 -->
          <label class="hcl-field" :class="{ 'hcl-field--focus': focusField === 'user', 'hcl-field--filled': username }">
            <span class="hcl-field-label">用户名</span>
            <span class="hcl-field-box">
              <svg class="hcl-field-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="8" r="3.6"/>
                <path d="M5 20c.8-3.4 3.6-5.2 7-5.2s6.2 1.8 7 5.2"/>
              </svg>
              <input v-model.trim="username" type="text" autocomplete="username"
                     placeholder="请输入用户名" @focus="focusField = 'user'" @blur="focusField = ''"/>
            </span>
          </label>

          <!-- 密码 -->
          <label class="hcl-field" :class="{ 'hcl-field--focus': focusField === 'pwd', 'hcl-field--filled': password }">
            <span class="hcl-field-label">密码</span>
            <span class="hcl-field-box">
              <svg class="hcl-field-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="4.5" y="10" width="15" height="10" rx="2.2"/>
                <path d="M8 10V7.6a4 4 0 0 1 8 0V10"/>
                <circle cx="12" cy="15" r="1.4" fill="currentColor" stroke="none"/>
              </svg>
              <input v-model="password" :type="showPwd ? 'text' : 'password'"
                     autocomplete="current-password" placeholder="请输入密码"
                     @focus="focusField = 'pwd'" @blur="focusField = ''"/>
              <button type="button" class="hcl-eye" :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                      @click="showPwd = !showPwd">
                <svg v-if="!showPwd" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z"/>
                  <circle cx="12" cy="12" r="2.8"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 4.5 20 19.5"/>
                  <path d="M9.9 6.2A9.4 9.4 0 0 1 12 5.8c6 0 9.5 6.2 9.5 6.2a17 17 0 0 1-3 3.7M6.3 8.4A16.4 16.4 0 0 0 2.5 12S6 18.2 12 18.2c1 0 2-.2 2.8-.5"/>
                </svg>
              </button>
            </span>
          </label>

          <!-- 错误提示 -->
          <p v-if="errorMsg" class="hcl-error" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" aria-hidden="true">
              <circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5"/><circle cx="12" cy="16.6" r=".4" fill="currentColor"/>
            </svg>
            {{ errorMsg }}
          </p>

          <!-- 登录按钮 -->
          <button type="submit" class="hcl-submit" :disabled="loading">
            <span v-if="!loading" class="hcl-submit-text">
              登 录
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M4 12h15"/><path d="m13.5 6 6 6-6 6"/>
              </svg>
            </span>
            <span v-else class="hcl-spinner" aria-label="正在登录"></span>
          </button>
        </form>

        <!-- 今日值班表：点姓名即代入该岗位账号（密码统一 123456） -->
        <div class="hcl-roster">
          <div class="hcl-roster-head">
            <span class="hcl-roster-title">今 日 值 班</span>
            <span class="hcl-roster-en">DUTY ROSTER</span>
          </div>
          <div class="hcl-roster-list">
            <button
              v-for="p in persons" :key="p.u" type="button" class="hcl-roster-item"
              :title="`${p.name} · ${p.role} · 账号 ${p.u}`" @click="fill(p)">
              <span class="hcl-rname">{{ p.name }}</span>
              <span class="hcl-rrole">{{ p.role }}</span>
              <span class="hcl-rno">{{ p.no }}</span>
            </button>
          </div>
          <p class="hcl-roster-tip">点值班人姓名，代入其岗位账号</p>
        </div>

        <!-- 制度告知 -->
        <p class="hcl-legal">
          登录即视为已阅
          <a href="javascript:void(0)" @click.prevent>《实验室危险化学品安全管理制度》</a>
        </p>
      </div>

      <p class="hcl-panel-foot">高校实验室安全与环境管理处 · 信息化运维支持</p>
    </main>

    <!-- ===================== 登录成功覆盖层 ===================== -->
    <transition name="hcl-fade">
      <div v-if="success" class="hcl-success" role="status">
        <div class="hcl-success-card">
          <span class="hcl-success-ring">
            <svg viewBox="0 0 52 52" aria-hidden="true">
              <circle class="hcl-success-circle" cx="26" cy="26" r="24" fill="none"/>
              <path class="hcl-success-check" d="M15 27l7.5 7.5L37 19" fill="none"/>
            </svg>
          </span>
          <h3>登录成功</h3>
          <p>安全核验已通过，正在进入危化品全流程管理工作台…</p>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../store/auth'

const route = useRoute()
const router = useRouter()

/* ---------- 表单状态 ---------- */
const username = ref('')
const password = ref('')
const showPwd = ref(false)
const focusField = ref('')
const loading = ref(false)
const success = ref(false)
const errorMsg = ref('')
const shaking = ref(false)

/* 今日值班表：五位值班人对应五个岗位账号，点姓名即代入（密码统一 123456） */
const persons = [
  { name: '陈老师', role: '申请人', no: '01', u: 'chen', p: '123456' },
  { name: '张教授', role: '导师', no: '02', u: 'zhang', p: '123456' },
  { name: '李安全员', role: '安全员', no: '03', u: 'li', p: '123456' },
  { name: '王院长', role: '学院', no: '04', u: 'wang', p: '123456' },
  { name: '刘同学', role: '学生', no: '05', u: 'liu', p: '123456' }
]

function fill (p) {
  username.value = p.u
  password.value = p.p
  errorMsg.value = ''
}

async function onSubmit () {
  if (loading.value || success.value) return
  if (!username.value || !password.value) {
    errorMsg.value = '请输入用户名和密码后再登录'
    shaking.value = true
    setTimeout(() => { shaking.value = false }, 500)
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await login(username.value, password.value)
    success.value = true
    setTimeout(() => { router.replace(route.query.redirect || '/dashboard') }, 900)
  } catch (e) {
    loading.value = false
    errorMsg.value = (e && e.message) ? e.message : '用户名或密码不正确'
    shaking.value = true
    setTimeout(() => { shaking.value = false }, 500)
  }
}

/* ---------- 品牌区内容 ---------- */
const flowSteps = [
  { t: '分类申购', d: '按 1—4 级分类提交', icon: '<path d="M6 3h9l4 4v14H6z"/><path d="M15 3v4h4"/><path d="M9 12h6M9 16h6"/>' },
  { t: '逐级审批', d: '剧毒品类校级终审', icon: '<path d="M12 3l7 3v5c0 4.4-3 8.4-7 10-4-1.6-7-5.6-7-10V6z"/><path d="m9 12 2 2 4-4"/>' },
  { t: '双人签认', d: '出入库双人复核', icon: '<circle cx="9" cy="8" r="3"/><circle cx="16.5" cy="9.5" r="2.4"/><path d="M3.5 19c.7-3 2.9-4.6 5.5-4.6s4.8 1.6 5.5 4.6"/><path d="M15 15.6c2.2.3 3.8 1.6 4.4 3.9"/>' },
  { t: '先到期先出', d: 'FEFO 规则发放', icon: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.6 2.6"/><path d="M9 2.5h6"/>' },
  { t: '废弃处置', d: '处置全程留痕', icon: '<path d="M4 7h16"/><path d="M9 7V4.5h6V7"/><path d="M6.5 7l1 13h9l1-13"/><path d="M10 11v5M14 11v5"/>' },
  { t: '账实盘点', d: '定期对账核销', icon: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 9h8M8 13h5"/><path d="m14.5 16.5 1.5 1.5 3-3"/>' }
]
const badges = ['GB 15603 合规', '危化品条例 aligned', '全流程电子留痕']

/* ---------- 分子网络背景（Canvas，无外部资源） ---------- */
const netCanvas = ref(null)
let rafId = 0

onMounted(() => {
  const canvas = netCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const DPR = Math.min(window.devicePixelRatio || 1, 2)
  let W = 0, H = 0, pts = []

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  function resize () {
    const r = canvas.parentElement.getBoundingClientRect()
    W = r.width; H = r.height
    canvas.width = W * DPR; canvas.height = H * DPR
    canvas.style.width = W + 'px'; canvas.style.height = H + 'px'
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0)
    const n = Math.round((W * H) / 26000)
    pts = Array.from({ length: n }, () => ({
      x: Math.random() * W,
      y: Math.random() * H,
      vx: (Math.random() - 0.5) * 0.28,
      vy: (Math.random() - 0.5) * 0.28,
      r: 1.2 + Math.random() * 1.8
    }))
  }

  function draw () {
    ctx.clearRect(0, 0, W, H)
    const LINK = 130
    for (let i = 0; i < pts.length; i++) {
      const p = pts[i]
      if (!reduced) {
        p.x += p.vx; p.y += p.vy
        if (p.x < -20) p.x = W + 20; if (p.x > W + 20) p.x = -20
        if (p.y < -20) p.y = H + 20; if (p.y > H + 20) p.y = -20
      }
      for (let j = i + 1; j < pts.length; j++) {
        const q = pts[j]
        const dx = p.x - q.x, dy = p.y - q.y
        const d = Math.hypot(dx, dy)
        if (d < LINK) {
          ctx.strokeStyle = `rgba(150,110,50,${(1 - d / LINK) * 0.11})`
          ctx.lineWidth = 1
          ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(q.x, q.y); ctx.stroke()
        }
      }
      ctx.fillStyle = 'rgba(140,105,45,0.32)'
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill()
    }
    if (!reduced) rafId = requestAnimationFrame(draw)
  }

  resize()
  draw()
  window.addEventListener('resize', resize)
  onBeforeUnmount(() => {
    cancelAnimationFrame(rafId)
    window.removeEventListener('resize', resize)
  })
})
</script>

<style scoped>
/* ============ 设计令牌 ============ */
.hcl-root {
  --paper: #faf7f2;          /* 暖纸底 */
  --paper-deep: #f3ede3;
  --ink: #292524;            /* 暖石灰墨色 */
  --ink-2: #57534e;
  --ink-3: #8a8378;
  --line: #e7dfd2;
  --brand: #d97706;          /* 安全橙主色 */
  --brand-deep: #b45309;
  --brand-soft: #fef3e2;
  --teal: #0f766e;           /* 补色·青玉（成功/合规） */
  --card: #ffffff;
  --shadow-lg: 0 24px 60px -18px rgba(87, 62, 24, 0.22), 0 6px 18px -8px rgba(87, 62, 24, 0.12);
  --radius: 14px;

  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", "Helvetica Neue", Arial, sans-serif;
  color: var(--ink);
  background: var(--paper);
  -webkit-font-smoothing: antialiased;
}

/* ============ 左侧品牌区 ============ */
.hcl-brand {
  position: relative;
  flex: 1.15;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(28px, 4.5vw, 56px);
  overflow: hidden;
  background:
    radial-gradient(1100px 620px at 12% -8%, #fde9c8 0%, transparent 55%),
    radial-gradient(900px 700px at 108% 112%, #f6e3c4 0%, transparent 52%),
    linear-gradient(160deg, var(--paper) 0%, var(--paper-deep) 100%);
  border-right: 1px solid var(--line);
}
.hcl-brand::before { /* 安全橙细带 */
  content: "";
  position: absolute; inset: 0 auto 0 0;
  width: 5px;
  background: linear-gradient(180deg, var(--brand) 0%, #f59e0b 55%, var(--brand-deep) 100%);
}
.hcl-net { position: absolute; inset: 0; pointer-events: none; }

.hcl-brand-inner { position: relative; z-index: 1; max-width: 560px; }

.hcl-logo-row { display: flex; align-items: center; gap: 14px; animation: hcl-rise .7s cubic-bezier(.22,.9,.3,1) both; }
.hcl-logo {
  width: 52px; height: 52px; flex: none;
  display: grid; place-items: center;
  color: #fff;
  background: linear-gradient(145deg, #f59e0b, var(--brand) 55%, var(--brand-deep));
  border-radius: 14px;
  box-shadow: 0 10px 22px -8px rgba(217, 119, 6, .55), inset 0 1px 0 rgba(255,255,255,.35);
}
.hcl-logo svg { width: 32px; height: 32px; }
.hcl-logo-text { display: flex; flex-direction: column; gap: 2px; }
.hcl-logo-en { font-size: 11px; letter-spacing: .22em; color: var(--brand-deep); font-weight: 700; }
.hcl-logo-cn { font-size: 13px; color: var(--ink-3); letter-spacing: .12em; }

.hcl-title {
  margin: clamp(26px, 4vh, 44px) 0 0;
  font-size: clamp(30px, 3.4vw, 44px);
  line-height: 1.28;
  font-weight: 800;
  letter-spacing: .02em;
  animation: hcl-rise .7s .08s cubic-bezier(.22,.9,.3,1) both;
}
.hcl-title em {
  font-style: normal;
  background: linear-gradient(100deg, var(--brand-deep), var(--brand) 60%, #f59e0b);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hcl-sub {
  margin: 14px 0 0;
  font-size: 15px; line-height: 1.8; color: var(--ink-2);
  animation: hcl-rise .7s .16s cubic-bezier(.22,.9,.3,1) both;
}

/* 全流程时间线 */
.hcl-flow {
  list-style: none; margin: clamp(24px, 4vh, 40px) 0 0; padding: 0;
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
}
.hcl-flow-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px 1fr;
  grid-template-rows: auto auto;
  column-gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, .55);
  border: 1px solid rgba(231, 223, 210, .9);
  backdrop-filter: blur(4px);
  transition: transform .25s ease, box-shadow .25s ease, border-color .25s ease;
  animation: hcl-rise .6s var(--d, .55s) cubic-bezier(.22,.9,.3,1) both;
}
.hcl-flow-item:hover {
  transform: translateY(-3px);
  border-color: #C9BFA8;
  box-shadow: 0 12px 24px -12px rgba(180, 110, 20, .35);
}
.hcl-flow-dot {
  grid-row: 1 / 3;
  width: 38px; height: 38px;
  display: grid; place-items: center;
  color: #7A6A4F;
  background: #F5F1E8;
  border: 1px solid #E7E0D2;
  border-radius: 8px;
}
.hcl-flow-dot svg { width: 20px; height: 20px; }
.hcl-flow-label { font-size: 14px; font-weight: 700; }
.hcl-flow-desc { font-size: 12px; color: var(--ink-3); }

/* 合规徽章 */
.hcl-badges { display: flex; flex-wrap: wrap; gap: 10px; margin-top: clamp(22px, 3.5vh, 34px); animation: hcl-rise .7s 1.05s cubic-bezier(.22,.9,.3,1) both; }
.hcl-badge {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 12px;
  font-size: 12px; font-weight: 600; letter-spacing: .04em;
  color: var(--teal);
  background: rgba(15, 118, 110, .07);
  border: 1px solid rgba(15, 118, 110, .22);
  border-radius: 999px;
}
.hcl-badge svg { width: 14px; height: 14px; }

.hcl-brand-foot {
  position: relative; z-index: 1;
  display: flex; align-items: center; gap: 14px;
  padding-top: 18px;
  font-size: 12px; color: var(--ink-3); letter-spacing: .06em;
  border-top: 1px dashed var(--line);
}
.hcl-brand-foot i { width: 4px; height: 4px; border-radius: 50%; background: var(--brand); opacity: .6; }

/* ============ 右侧登录区 ============ */
.hcl-panel {
  flex: 1;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 22px;
  padding: clamp(24px, 4vw, 48px);
  background:
    radial-gradient(700px 400px at 85% 8%, #fdf1de 0%, transparent 60%),
    var(--paper);
}
.hcl-card {
  width: min(420px, 100%);
  padding: clamp(28px, 3.2vw, 40px);
  background: var(--card);
  border: 1px solid rgba(231, 223, 210, .9);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  animation: hcl-card-in .8s .15s cubic-bezier(.22,.9,.3,1) both;
}
.hcl-card--shake { animation: hcl-shake .45s ease; }

.hcl-card-head h2 { margin: 0; font-size: 26px; font-weight: 800; letter-spacing: .02em; }
.hcl-card-head p { margin: 8px 0 0; font-size: 13.5px; color: var(--ink-3); }

.hcl-form { margin-top: 26px; display: flex; flex-direction: column; gap: 18px; }

.hcl-field { display: flex; flex-direction: column; gap: 8px; cursor: text; }
.hcl-field-label {
  font-size: 13px; font-weight: 600; color: var(--ink-2);
  transition: color .2s ease;
}
.hcl-field--focus .hcl-field-label { color: var(--brand-deep); }
.hcl-field-box {
  display: flex; align-items: center; gap: 10px;
  height: 50px; padding: 0 14px;
  background: #fdfcfa;
  border: 1.5px solid var(--line);
  border-radius: 12px;
  transition: border-color .2s ease, box-shadow .2s ease, background .2s ease;
}
.hcl-field--focus .hcl-field-box {
  border-color: var(--brand);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(217, 119, 6, .14);
}
.hcl-field-icon { width: 19px; height: 19px; flex: none; color: var(--ink-3); transition: color .2s ease; }
.hcl-field--focus .hcl-field-icon { color: var(--brand); }
.hcl-field-box input {
  flex: 1; min-width: 0;
  border: 0; outline: 0; background: transparent;
  font-size: 15px; color: var(--ink);
  font-family: inherit;
}
.hcl-field-box input::placeholder { color: #b8b0a2; }
.hcl-eye {
  flex: none; display: grid; place-items: center;
  width: 34px; height: 34px;
  border: 0; border-radius: 8px;
  background: transparent; color: var(--ink-3);
  cursor: pointer;
  transition: background .2s ease, color .2s ease;
}
.hcl-eye:hover { background: var(--brand-soft); color: var(--brand-deep); }
.hcl-eye svg { width: 19px; height: 19px; }

.hcl-error {
  display: flex; align-items: center; gap: 8px;
  margin: -4px 0 0;
  font-size: 13px; color: #b91c1c;
  animation: hcl-rise .3s ease both;
}
.hcl-error svg { width: 16px; height: 16px; flex: none; }

.hcl-submit {
  position: relative;
  height: 52px; margin-top: 4px;
  border: 0; border-radius: 12px;
  font-size: 16px; font-weight: 700; letter-spacing: .3em; text-indent: .3em;
  color: #fff; font-family: inherit;
  background: linear-gradient(120deg, #f59e0b 0%, var(--brand) 45%, var(--brand-deep) 100%);
  background-size: 160% 100%;
  box-shadow: 0 14px 28px -10px rgba(217, 119, 6, .55), inset 0 1px 0 rgba(255,255,255,.28);
  cursor: pointer;
  transition: background-position .35s ease, transform .15s ease, box-shadow .25s ease, filter .2s ease;
}
.hcl-submit:hover:not(:disabled) { background-position: 90% 0; box-shadow: 0 18px 34px -10px rgba(217, 119, 6, .65), inset 0 1px 0 rgba(255,255,255,.28); }
.hcl-submit:active:not(:disabled) { transform: scale(.975); }
.hcl-submit:disabled { cursor: wait; filter: saturate(.85); }
.hcl-submit-text { display: inline-flex; align-items: center; gap: 10px; }
.hcl-submit-text svg { width: 19px; height: 19px; }
.hcl-spinner {
  width: 22px; height: 22px; display: inline-block;
  border: 2.5px solid rgba(255,255,255,.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: hcl-spin .7s linear infinite;
}

/* 今日值班表：5 列等宽、透明底 + 虚线边、上下紧凑 */
.hcl-roster { margin-top: 12px; }
.hcl-roster-head {
  display: flex; justify-content: space-between; align-items: baseline;
  border-top: 1px solid rgba(90, 80, 65, 0.14); padding-top: 10px;
}
.hcl-roster-title { font-size: 0.7rem; letter-spacing: 0.22em; color: #8a8378; }
.hcl-roster-en { font-size: 0.54rem; letter-spacing: 0.18em; color: #b3aca1; font-family: var(--f-mono, monospace); }
.hcl-roster-list { display: grid; grid-template-columns: repeat(5, 1fr); gap: 5px; margin-top: 7px; }
.hcl-roster-item {
  display: flex; flex-direction: column; align-items: center; gap: 0;
  padding: 3px 2px 4px;
  background: transparent;
  border: 1px dashed rgba(120, 108, 90, 0.32); border-radius: 4px;
  cursor: pointer; transition: all 0.14s ease;
  font-family: inherit;
}
.hcl-roster-item:hover { background: rgba(217, 119, 6, 0.07); border-color: rgba(217, 119, 6, 0.55); }
.hcl-roster-item:focus-visible { outline: 2px solid rgba(217, 119, 6, 0.5); outline-offset: 1px; }
.hcl-rname { font-size: 0.72rem; color: #3f3a33; font-weight: 600; letter-spacing: 0.02em; }
.hcl-rrole { font-size: 0.54rem; color: #8a8378; }
.hcl-rno { font-size: 0.5rem; color: #b3aca1; font-family: var(--f-mono, monospace); letter-spacing: 0.04em; }
.hcl-roster-tip { margin: 6px 0 0; font-size: 0.6rem; color: #a29b90; letter-spacing: 0.04em; }

.hcl-legal { margin: 16px 0 0; font-size: 12.5px; color: var(--ink-3); text-align: center; line-height: 1.7; }
.hcl-legal a { color: var(--brand-deep); text-decoration: none; border-bottom: 1px solid rgba(217,119,6,.35); transition: border-color .2s ease; }
.hcl-legal a:hover { border-bottom-color: var(--brand-deep); }

.hcl-panel-foot { margin: 0; font-size: 12px; color: var(--ink-3); letter-spacing: .08em; }

/* ============ 成功覆盖层 ============ */
.hcl-success {
  position: fixed; inset: 0; z-index: 50;
  display: grid; place-items: center;
  background: rgba(250, 247, 242, .82);
  backdrop-filter: blur(10px);
}
.hcl-success-card {
  display: flex; flex-direction: column; align-items: center;
  padding: 44px 56px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 20px;
  box-shadow: var(--shadow-lg);
  text-align: center;
  animation: hcl-pop .5s cubic-bezier(.2,1.2,.3,1) both;
}
.hcl-success-ring svg { width: 84px; height: 84px; }
.hcl-success-circle {
  stroke: var(--teal); stroke-width: 3;
  stroke-dasharray: 151; stroke-dashoffset: 151;
  animation: hcl-draw .6s .1s ease-out forwards;
}
.hcl-success-check {
  stroke: var(--teal); stroke-width: 3.4; stroke-linecap: round; stroke-linejoin: round;
  stroke-dasharray: 34; stroke-dashoffset: 34;
  animation: hcl-draw .4s .55s ease-out forwards;
}
.hcl-success-card h3 { margin: 18px 0 0; font-size: 22px; font-weight: 800; }
.hcl-success-card p { margin: 8px 0 0; font-size: 13.5px; color: var(--ink-3); }

.hcl-fade-enter-active { transition: opacity .35s ease; }
.hcl-fade-leave-active { transition: opacity .3s ease; }
.hcl-fade-enter-from, .hcl-fade-leave-to { opacity: 0; }

/* ============ 动效关键帧 ============ */
@keyframes hcl-rise { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }
@keyframes hcl-card-in { from { opacity: 0; transform: translateY(22px) scale(.985); } to { opacity: 1; transform: none; } }
@keyframes hcl-pop { from { opacity: 0; transform: scale(.85); } to { opacity: 1; transform: scale(1); } }
@keyframes hcl-spin { to { transform: rotate(360deg); } }
@keyframes hcl-draw { to { stroke-dashoffset: 0; } }
@keyframes hcl-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-9px); } 40% { transform: translateX(8px); }
  60% { transform: translateX(-5px); } 80% { transform: translateX(4px); }
}

/* ============ 响应式 ============ */
@media (max-width: 960px) {
  .hcl-root { flex-direction: column; }
  .hcl-brand { flex: none; border-right: 0; border-bottom: 1px solid var(--line); }
  .hcl-brand::before { inset: 0 0 auto 0; width: auto; height: 5px;
    background: linear-gradient(90deg, var(--brand), #f59e0b 55%, var(--brand-deep)); }
  .hcl-flow { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hcl-brand-foot { display: none; }
}
@media (max-width: 560px) {
  .hcl-flow { grid-template-columns: 1fr; }
  .hcl-card { padding: 26px 20px; }
}

/* ============ 降低动效偏好 ============ */
@media (prefers-reduced-motion: reduce) {
  .hcl-root *, .hcl-root *::before, .hcl-root *::after {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: .01ms !important;
  }
}
</style>
