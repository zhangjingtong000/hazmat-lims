import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import ChemInfoList from '../views/chem/ChemInfoList.vue'
import ChemInfoDetail from '../views/chem/ChemInfoDetail.vue'
import PurchaseList from '../views/chem/PurchaseList.vue'
import StockInList from '../views/chem/StockInList.vue'
import RequisitionList from '../views/chem/RequisitionList.vue'
import RequisitionDetail from '../views/chem/RequisitionDetail.vue'
import BatchList from '../views/chem/BatchList.vue'
import TodoCenter from '../views/chem/TodoCenter.vue'
import WasteList from '../views/chem/WasteList.vue'
import WasteDetail from '../views/chem/WasteDetail.vue'
import CheckList from '../views/chem/CheckList.vue'
import CheckDetail from '../views/chem/CheckDetail.vue'
import BaseDataView from '../views/chem/BaseDataView.vue'
import StatsReport from '../views/chem/StatsReport.vue'
import ScreenView from '../views/screen/ScreenView.vue'
import UserList from '../views/system/UserList.vue'
import RoleList from '../views/system/RoleList.vue'
import MenuList from '../views/system/MenuList.vue'
import OperLogList from '../views/system/OperLogList.vue'
import NoticeList from '../views/biz/NoticeList.vue'
import PurchasePrint from '../views/print/PurchasePrint.vue'
import ReqPrint from '../views/print/ReqPrint.vue'
import WastePrint from '../views/print/WastePrint.vue'
import LabelPrint from '../views/print/LabelPrint.vue'

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
