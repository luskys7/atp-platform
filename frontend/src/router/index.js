import { createRouter, createWebHistory } from 'vue-router'
import { pageTitle } from '@/config/app'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/wallboard',
    name: 'ExecutionWallboard',
    component: () => import('@/views/ExecutionWallboard.vue'),
    meta: { title: '执行大屏' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页概览' }
      },
      {
        path: 'public-assets',
        name: 'PublicAssets',
        component: () => import('@/views/PublicAssets.vue'),
        meta: { title: '公共组件' }
      },
      {
        path: 'project-hub',
        name: 'ProjectHub',
        component: () => import('@/views/ProjectHub.vue'),
        meta: { title: '项目管理' }
      },
      {
        path: 'settings-hub',
        name: 'SettingsHub',
        component: () => import('@/views/SettingsHub.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('@/views/Devices.vue'),
        meta: { title: '设备管理' }
      },
      {
        path: 'devices/:id/screen',
        name: 'DeviceScreen',
        component: () => import('@/views/DeviceScreen.vue'),
        meta: { title: '远程投屏' }
      },
      {
        path: 'element-picker',
        name: 'ElementPickerHub',
        component: () => import('@/views/ElementPicker.vue'),
        meta: { title: '控件拾取' }
      },
      {
        path: 'element-picker/:id',
        name: 'ElementPicker',
        component: () => import('@/views/ElementPicker.vue'),
        meta: { title: '控件拾取' }
      },
      {
        path: 'cases',
        name: 'Cases',
        component: () => import('@/views/Cases.vue'),
        meta: { title: '测试用例' }
      },
      {
        path: 'suites',
        name: 'Suites',
        component: () => import('@/views/Suites.vue'),
        meta: { title: '测试套件' }
      },
      {
        path: 'platform-config',
        name: 'PlatformConfig',
        component: () => import('@/views/PlatformConfig.vue'),
        meta: { title: '平台配置' }
      },
      {
        path: 'app-packages',
        name: 'AppPackages',
        component: () => import('@/views/AppPackages.vue'),
        meta: { title: 'APP 包仓库' }
      },
      {
        path: 'cases/editor',
        name: 'VisualCaseEditor',
        component: () => import('@/views/VisualCaseEditor.vue'),
        meta: { title: '可视化用例编辑' }
      },
      {
        path: 'cases/editor/:id',
        name: 'VisualCaseEditorEdit',
        component: () => import('@/views/VisualCaseEditor.vue'),
        meta: { title: '编辑可视化用例' }
      },
      {
        path: 'cases/:id/debug',
        name: 'CaseDebug',
        component: () => import('@/views/CaseDebugWorkspace.vue'),
        meta: { title: '同屏调试' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/Tasks.vue'),
        meta: { title: '任务执行' }
      },
      {
        path: 'tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/TaskDetail.vue'),
        meta: { title: '任务详情' }
      },
      {
        path: 'recordings',
        name: 'Recordings',
        component: () => import('@/views/Recordings.vue'),
        meta: { title: '录屏回放' }
      },
      {
        path: 'recordings/review/:id',
        name: 'RecordCaseReview',
        component: () => import('@/views/RecordCaseReview.vue'),
        meta: { title: '录制审阅' }
      },
      {
        path: 'recording-quality',
        name: 'RecordingQuality',
        component: () => import('@/views/RecordingQuality.vue'),
        meta: { title: '录制质量' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '测试报告' }
      },
      {
        path: 'reports/:taskId',
        name: 'ReportDetail',
        component: () => import('@/views/ReportDetail.vue'),
        meta: { title: '报告详情' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'controls',
        name: 'Controls',
        component: () => import('@/views/Controls.vue'),
        meta: { title: '控件池管理' }
      },
      {
        path: 'controls/batch-validate',
        name: 'ControlBatchValidate',
        component: () => import('@/views/ControlBatchValidate.vue'),
        meta: { title: '控件批量校验' }
      },
      {
        path: 'controls/locator-failures',
        name: 'LocatorFailureReport',
        component: () => import('@/views/LocatorFailureReport.vue'),
        meta: { title: '定位失败报表' }
      },
      {
        path: 'ci',
        name: 'CiSettings',
        component: () => import('@/views/CiSettings.vue'),
        meta: { title: 'CI/CD 配置', admin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? pageTitle(to.meta.title) : pageTitle()
  let token = localStorage.getItem('token')
  // 清除无效 token，避免登录态异常导致白屏
  if (token === 'undefined' || token === 'null') {
    localStorage.removeItem('token')
    token = ''
  }
  if (!to.meta.public && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
