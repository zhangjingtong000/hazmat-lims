import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'

// 路由级懒加载：首屏只加载布局与登录页，其余页面按访问时的 chunk 拉取。
// 打印页与大屏尤其值得拆开——它们带 Three.js / 打印样式，常规办公流程用不到。
const DashboardView = () => import('../views/DashboardView.vue')
const ChemInfoList = () => import('../views/chem/ChemInfoList.vue')
const ChemInfoDetail = () => import('../views/chem/ChemInfoDetail.vue')
const PurchaseList = () => import('../views/chem/PurchaseList.vue')
const StockInList = () => import('../views/chem/StockInList.vue')
const RequisitionList = () => import('../views/chem/RequisitionList.vue')
const RequisitionDetail = () => import('../views/chem/RequisitionDetail.vue')
const BatchList = () => import('../views/chem/BatchList.vue')
const TodoCenter = () => import('../views/chem/TodoCenter.vue')
const WasteList = () => import('../views/chem/WasteList.vue')
const WasteDetail = () => import('../views/chem/WasteDetail.vue')
const CheckList = () => import('../views/chem/CheckList.vue')
const CheckDetail = () => import('../views/chem/CheckDetail.vue')
const BaseDataView = () => import('../views/chem/BaseDataView.vue')
const StatsReport = () => import('../views/chem/StatsReport.vue')
const ScreenView = () => import('../views/screen/ScreenView.vue')
const UserList = () => import('../views/system/UserList.vue')
const RoleList = () => import('../views/system/RoleList.vue')
const MenuList = () => import('../views/system/MenuList.vue')
const OperLogList = () => import('../views/system/OperLogList.vue')
const NoticeList = () => import('../views/biz/NoticeList.vue')
const PurchasePrint = () => import('../views/print/PurchasePrint.vue')
const ReqPrint = () => import('../views/print/ReqPrint.vue')
const WastePrint = () => import('../views/print/WastePrint.vue')
const LabelPrint = () => import('../views/print/LabelPrint.vue')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/screen', component: ScreenView },
    { path: '/print/purchase/:id', component: PurchasePrint },
    { path: '/print/req/:id', component: ReqPrint },
    { path: '/print/waste/:id', component: WastePrint },
    { path: '/print/label/:id', component: LabelPrint },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: DashboardView },
        { path: 'chem/info', component: ChemInfoList },
        { path: 'chem/info/:id', component: ChemInfoDetail },
        { path: 'chem/purchase', component: PurchaseList },
        { path: 'chem/stockin', component: StockInList },
        { path: 'chem/requisition', component: RequisitionList },
        { path: 'chem/req/:id', component: RequisitionDetail },
        { path: 'chem/batch', component: BatchList },
        { path: 'chem/todo', component: TodoCenter },
        { path: 'chem/waste', component: WasteList },
        { path: 'chem/waste/:id', component: WasteDetail },
        { path: 'chem/check', component: CheckList },
        { path: 'chem/check/:id', component: CheckDetail },
        { path: 'chem/base', component: BaseDataView },
        { path: 'chem/stats', component: StatsReport },
        { path: 'system/user', component: UserList },
        { path: 'system/role', component: RoleList },
        { path: 'system/menu', component: MenuList },
        { path: 'system/operlog', component: OperLogList },
        { path: 'biz/notice', component: NoticeList }
      ]
    }
  ]
})

/** 登录门禁：无 token 一律回登录页（打印/大屏亦需会话，与后端拦截器口径一致） */
router.beforeEach(to => {
  const token = sessionStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
  return true
})

export default router
