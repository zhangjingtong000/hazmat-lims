<template>
  <el-container class="layout-root">
    <!-- ═══ 顶部导航（差异化签名：白底顶栏 + 安全橙 + 分组下拉，非侧边栏） ═══ -->
    <el-header class="top-bar" height="56px">
      <div class="brand" @click="$router.push('/dashboard')">
        <svg class="brand-logo" viewBox="0 0 40 40" aria-hidden="true">
  <rect x="-2" y="-2" width="44" height="44" rx="10" fill="url(#brandG)"/>
  <defs>
    <linearGradient id="brandG" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#F59E0B"/>
      <stop offset="1" stop-color="#B45309"/>
    </linearGradient>
  </defs>
  <g stroke="#fff" fill="none"><path d="M20 3.5 34.5 11.2v14.4L20 36.5 5.5 25.6V11.2L20 3.5Z"
                     stroke-width="2.4" stroke-linejoin="round"/>
              <path d="M13.5 26.5c0-2.8 2.4-4.6 6.5-6.6 4.1-2 6.5-3.8 6.5-6.6"
                     stroke-width="2.4" stroke-linecap="round"/>
              <circle cx="20" cy="3.5" r="2.6" fill="#fff"/>
              <circle cx="34.5" cy="25.6" r="2.6" fill="#fff"/>
              <circle cx="5.5" cy="25.6" r="2.6" fill="#fff"/></g>
</svg>
        <div class="brand-text">
          <span class="brand-name">实验室危化品全流程管理</span>
          <span class="brand-sub">HAZMAT · 五双制度 · 先到期先出</span>
        </div>
      </div>

      <nav class="nav-center">
        <router-link class="nav-item" :class="{ active: isActive('/dashboard') }" to="/dashboard">首页</router-link>

        <el-dropdown trigger="hover" @command="go">
          <span class="nav-item" :class="{ active: inGroup('/chem/info', '/chem/purchase') }">
            化学品管理 <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu class="nav-group-menu">
              <el-dropdown-item command="/chem/info">化学品台账</el-dropdown-item>
              <el-dropdown-item command="/chem/purchase">申购管理</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="hover" @command="go">
          <span class="nav-item" :class="{ active: inGroup('/chem/stockin', '/chem/requisition', '/chem/batch', '/chem/todo', '/chem/waste', '/chem/check') }">
            库存与监管 <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu class="nav-group-menu">
              <el-dropdown-item command="/chem/stockin">入库双签</el-dropdown-item>
              <el-dropdown-item command="/chem/requisition">领用与归还</el-dropdown-item>
              <el-dropdown-item command="/chem/batch">批次监管</el-dropdown-item>
              <el-dropdown-item command="/chem/todo" divided>审批中心</el-dropdown-item>
              <el-dropdown-item command="/chem/waste">废弃处置</el-dropdown-item>
              <el-dropdown-item command="/chem/check">账物盘点</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="hover" @command="go">
          <span class="nav-item" :class="{ active: inGroup('/chem/base', '/chem/stats') }">
            系统 <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu class="nav-group-menu">
              <el-dropdown-item command="/chem/base">基础数据</el-dropdown-item>
              <el-dropdown-item command="/chem/stats">统计报表</el-dropdown-item>
              <el-dropdown-item command="/system/user" divided>用户管理</el-dropdown-item>
              <el-dropdown-item command="/system/role">角色管理</el-dropdown-item>
              <el-dropdown-item command="/system/menu">菜单管理</el-dropdown-item>
              <el-dropdown-item command="/system/operlog">操作日志</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <router-link class="nav-item" :class="{ active: isActive('/biz/notice') }" to="/biz/notice">信息</router-link>
      </nav>

      <div class="nav-right">
        <!-- G6 站内信：未读徽标 + 消息面板（定时任务/业务事件主动触达） -->
        <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadNotify">
          <template #reference>
            <el-badge :value="notifyUnread" :hidden="!notifyUnread" :max="99" class="notify-badge">
              <el-icon class="notify-bell" aria-label="站内信" :size="18"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="notify-panel">
            <div class="notify-head">
              <span class="notify-title">站内信</span>
              <el-button text size="small" type="primary" :disabled="!notifyUnread" @click="doReadAll">全部已读</el-button>
            </div>
            <el-empty v-if="!notifies.length" description="暂无消息" :image-size="50" />
            <div v-for="n in notifies" :key="n.id" class="notify-item" :class="{ unread: !n.readFlag }"
              @click="doRead(n)">
              <div class="notify-item-title">{{ n.title }}</div>
              <div class="notify-item-body">{{ n.content }}</div>
              <div class="notify-item-time">{{ n.createTime }}</div>
            </div>
          </div>
        </el-popover>
        <el-button
          v-if="hasPerm('hzm:ai:ask')"
          class="ai-entry" text
          aria-label="智能检索"
          data-testid="ai-entry"
          @click="aiVisible = true">
          <el-icon><MagicStick /></el-icon> 智能查询
        </el-button>
        <router-link class="nav-item screen-link" to="/screen">
          <el-icon><Monitor /></el-icon> 数据大屏
        </router-link>
        <el-dropdown trigger="click" @command="onUserCmd">
          <span class="user-chip">
            <el-icon><UserFilled /></el-icon>
            {{ auth.user?.nickname || auth.user?.username || '未登录' }}
            <el-tag v-if="roleLabel" size="small" effect="plain" round>{{ roleLabel }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="layout-main" :class="{ 'belt-on': showRelay }">
      <!-- 上手引导：签批接力带（只在单据类页面出现；带在上、规程在下双层） -->
      <RelayBelt v-if="showRelay" />
      <router-view :key="$route.fullPath" />
    </el-main>

    <el-dialog v-model="pwdVisible" title="修改密码（自助）" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" label-width="90px">
        <el-form-item label="旧密码" prop="oldPassword" :rules="R.required('旧密码')">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword" :rules="R.password()">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8~64 位，含两类字符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="doChangePwd">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 智能查询（防御性 AI 层：意图解析/缓存回放/法规检索） -->
    <AiAskDialog v-model="aiVisible" />
  </el-container>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Bell, MagicStick, Monitor, UserFilled } from '@element-plus/icons-vue'
import { auth, loadProfile, logout, changePassword, hasPerm } from '../store/auth'
import { listNotify, notifyUnread as fetchUnread, readNotify, readAllNotify } from '../api/system'
import AiAskDialog from '../components/AiAskDialog.vue'
import RelayBelt from '../components/RelayBelt.vue'
import R from '../utils/validation'

const route = useRoute()
const router = useRouter()

const aiVisible = ref(false)
let notifyTimer = null

const ROLE_LABELS = {
  admin: '管理员', advisor: '导师', safety: '安全员', college: '学院', campus: '校级', teacher: '教师', student: '研究生'
}
const roleLabel = computed(() => ROLE_LABELS[auth.roles[0]] || auth.roles[0] || '')

const isActive = p => route.path === p
const inGroup = (...prefixes) => prefixes.some(p => route.path.startsWith(p))
const go = cmd => router.push(cmd)

/* 引导带只在单据类页面出现（台账/基础数据/系统页不出现） */
const DOC_PATH_RE = /^\/chem\/(purchase|stockin|requisition|req|todo|waste|check)(\/\d+)?$/
const showRelay = computed(() => DOC_PATH_RE.test(route.path))

const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const pwdFormRef = ref(null)

/* ═══ G6 站内信（未读轮询 + 面板） ═══ */
const notifyUnread = ref(0)
const notifies = ref([])

async function refreshUnread() {
  try {
    const data = await fetchUnread()
    notifyUnread.value = Number(data?.count || 0)
  } catch { /* 静默：通知不影响主流程 */ }
}

async function loadNotify() {
  try {
    const data = await listNotify({ page: 1, size: 20 })
    notifies.value = data.records || []
    notifyUnread.value = Number((data.records || []).filter(n => !n.readFlag).length)
    await refreshUnread()
  } catch { /* 静默 */ }
}

async function doRead(item) {
  if (item.readFlag) return
  item.readFlag = 1
  notifyUnread.value = Math.max(0, notifyUnread.value - 1)
  await readNotify(item.id).catch(() => {})
}

async function doReadAll() {
  await readAllNotify().catch(() => {})
  notifies.value.forEach(n => { n.readFlag = 1 })
  notifyUnread.value = 0
  ElMessage.success('站内信已全部标记为已读')
}

async function doChangePwd() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('旧密码与新密码均不能为空')
    return
  }
  await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
  ElMessage.success('密码已更新，请牢记新口令')
  pwdVisible.value = false
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
}

async function onUserCmd(cmd) {
  if (cmd === 'pwd') {
    pwdVisible.value = true
    nextTick(() => pwdFormRef.value?.clearValidate())
  } else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    logout()
    router.replace('/login')
  }
}

onMounted(() => {
  if (auth.token && !auth.loaded) loadProfile()
  // 站内信未读数轮询（30s；轻量接口，失败静默）
  refreshUnread()
  notifyTimer = setInterval(refreshUnread, 30000)
})

onUnmounted(() => {
  if (notifyTimer) clearInterval(notifyTimer)
})
</script>

<style scoped>
.layout-root { min-height: 100vh; background: var(--c-page); }
.top-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  background: #fff;
  border-bottom: 1px solid var(--c-line);
  padding: 0 22px;
  position: sticky;
  top: 0;
  z-index: 50;
}
.brand { display: flex; align-items: center; gap: 10px; cursor: pointer; flex: none; }
.brand-logo { width: 34px; height: 34px; border-radius: 8px; box-shadow: 0 3px 8px rgba(217, 119, 6, 0.28); }
.brand-text { display: flex; flex-direction: column; line-height: 1.25; }
.brand-name { font-weight: 800; color: var(--c-graphite-deep); font-size: 1rem; letter-spacing: 0.03em; }
.brand-sub { font-size: 0.64rem; color: var(--c-safe); letter-spacing: 0.12em; font-weight: 600; }
.nav-center { display: flex; align-items: center; gap: 4px; flex: 1; }
.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 7px 14px;
  border-radius: var(--r);
  color: var(--c-graphite);
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.15s;
  outline: none;
}
.nav-item { position: relative; border-radius: var(--r-sm); }
.nav-item:hover { color: var(--c-safe-deep); background: #FAF6EE; }
.nav-item.active { color: var(--c-safe-deep); background: transparent; }
.nav-item.active::after {
  content: '';
  position: absolute;
  left: 12px; right: 12px; bottom: -1px;
  height: 2px;
  background: var(--c-safe);
  border-radius: 1px;
}
.nav-right { display: flex; align-items: center; gap: 10px; flex: none; }
.screen-link { color: var(--c-safe-deep); }
.ai-entry {
  color: var(--c-safe-deep);
  font-weight: 600;
  padding: 7px 12px;
  border: 1px solid var(--c-safe-line);
  border-radius: var(--r-sm);
  background: var(--c-safe-bg);
}
.ai-entry:hover { color: var(--c-safe-deep); background: var(--c-safe-bg); border-color: var(--c-safe); }
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--c-graphite);
  font-size: 0.9rem;
  padding: 5px 8px;
  border-radius: var(--r);
  outline: none;
}
.user-chip:hover { background: var(--c-safe-bg); }
.layout-main { padding: 18px 26px 36px; max-width: 1440px; width: 100%; margin: 0 auto; }
/* 单据页：页面内原有 regimen-bar 由接力带的规程层接管，避免同一屏两条规程 */
.belt-on :deep(.regimen-bar) { display: none; }
.notify-badge { display: inline-flex; align-items: center; }
.notify-bell { color: var(--c-graphite); cursor: pointer; padding: 6px; border-radius: var(--r-sm); }
.notify-bell:hover { color: var(--c-safe-deep); background: var(--c-safe-bg); }
.notify-panel { max-height: 420px; overflow-y: auto; }
.notify-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.notify-title { font-weight: 700; color: var(--c-graphite-deep); }
.notify-item { padding: 8px 10px; border-radius: var(--r-sm); cursor: pointer; border-left: 3px solid transparent; }
.notify-item:hover { background: var(--c-safe-bg); }
.notify-item.unread { border-left-color: var(--c-safe); background: #FFFBF3; }
.notify-item-title { font-size: 0.86rem; font-weight: 600; color: var(--c-graphite-deep); }
.notify-item-body { font-size: 0.78rem; color: var(--c-graphite); margin-top: 2px; line-height: 1.5; }
.notify-item-time { font-size: 0.7rem; color: var(--c-graphite-soft); margin-top: 3px; }
</style>
