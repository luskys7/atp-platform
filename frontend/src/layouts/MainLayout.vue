<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapse ? '72px' : '240px'" class="sidebar">
      <div class="logo" :class="{ collapsed: isCollapse }">
        <div class="logo-icon">
          <el-icon :size="26"><Monitor /></el-icon>
        </div>
        <div v-if="!isCollapse" class="logo-text">
          <span class="logo-title">{{ APP_NAME }}</span>
          <span class="logo-sub">Test Automation Flow</span>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <nav class="nav-group" v-for="group in menuGroups" :key="group.label">
          <div v-if="!isCollapse" class="nav-group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!isCollapse" class="nav-label">{{ item.title }}</span>
          </router-link>
        </nav>
      </el-scrollbar>

      <div class="sidebar-footer" v-if="!isCollapse">
        <div class="version-tag">{{ APP_NAME }} v1.0</div>
      </div>
    </el-aside>

    <el-container class="main-area">
      <el-header class="header">
        <div class="header-left">
          <button class="icon-btn" @click="isCollapse = !isCollapse">
            <el-icon :size="18"><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <button
            v-if="activeScreenCount"
            class="screen-pill"
            @click="goActiveScreen"
          >
            <span class="pill-dot" />
            投屏中 · {{ activeScreenCount }}
          </button>
          <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99" class="msg-badge">
            <button class="icon-btn" @click="showMessages = true; loadMessages()">
              <el-icon :size="18"><Bell /></el-icon>
            </button>
          </el-badge>
          <el-tag class="role-tag" effect="plain" round>{{ roleLabel }}</el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-block">
              <div class="avatar">{{ avatarLetter }}</div>
              <span class="username">{{ userStore.user?.display_name || userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component, route: r }">
          <transition name="fade" mode="out-in">
            <keep-alive include="DeviceScreen">
              <component
                :is="Component"
                :key="r.name === 'DeviceScreen' ? `screen-${r.params.id}` : r.fullPath"
              />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <el-drawer v-model="showMessages" title="站内消息" size="400px">
      <div style="margin-bottom:12px;text-align:right">
        <el-button size="small" plain @click="markAllRead">全部已读</el-button>
      </div>
      <div v-for="m in messages" :key="m.id" class="msg-item" :class="{ unread: !m.read_at }" @click="openMessage(m)">
        <div class="msg-title">{{ m.title }}</div>
        <div class="msg-body">{{ m.content }}</div>
        <div class="msg-time">{{ fmtMsgTime(m.created_at) }}</div>
      </div>
      <el-empty v-if="!messages.length" description="暂无消息" />
    </el-drawer>

    <ScreenStreamDock />
    <QuickRecordFab v-if="userStore.canEdit && recordingV2" />
    <RecordingStatusBar
      v-if="recordingV2"
      @pause="onGlobalPause"
      @resume="onGlobalResume"
      @mark-segment="onGlobalMarkSegment"
      @finish="onGlobalFinish"
      @cancel="onGlobalCancel"
    />
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { roleLabels } from '@/utils/status'
import { APP_NAME } from '@/config/app'
import { messageApi, recordApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import ScreenStreamDock from '@/components/ScreenStreamDock.vue'
import QuickRecordFab from '@/components/QuickRecordFab.vue'
import RecordingStatusBar from '@/components/RecordingStatusBar.vue'
import { activeScreenSessions, syncActiveScreenSessions } from '@/composables/useScreenStream'
import { operationRecordingState, invokeRecordingAction } from '@/composables/useOperationRecording'
import {
  loadDraftMeta, loadDraftBlob, clearDraft, draftReasonLabel, uploadDraftVideo
} from '@/composables/useRecordingRecovery'
import { useRecordingFeatures } from '@/composables/useRecordingFeatures'

const { features: recordingFeatures, loadFeatures } = useRecordingFeatures()
const recordingV2 = computed(() => recordingFeatures.value.recording_v2 !== false)

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const activeScreenCount = computed(() => activeScreenSessions.value.length)

function goActiveScreen() {
  const first = activeScreenSessions.value[0]
  if (first) router.push(`/devices/${first.deviceId}/screen`)
}

function goRecordingScreen() {
  if (operationRecordingState.deviceId) {
    router.push(`/devices/${operationRecordingState.deviceId}/screen`)
  }
}

function onGlobalPause() {
  if (!invokeRecordingAction('pause')) goRecordingScreen()
}

function onGlobalResume() {
  if (!invokeRecordingAction('resume')) goRecordingScreen()
}

function onGlobalMarkSegment() {
  if (!invokeRecordingAction('markSegment')) goRecordingScreen()
}

function onGlobalFinish() {
  if (!invokeRecordingAction('finish')) goRecordingScreen()
}

function onGlobalCancel() {
  if (!invokeRecordingAction('cancel')) goRecordingScreen()
}

const currentTitle = computed(() => route.meta.title || '')
const roleLabel = computed(() => roleLabels[userStore.role] || '')
const avatarLetter = computed(() => {
  const name = userStore.user?.display_name || userStore.user?.username || '?'
  return name.charAt(0).toUpperCase()
})

const menuGroups = computed(() => {
  const groups = [
    {
      label: '工作台',
      items: [
        { path: '/dashboard', title: '首页概览', icon: 'Odometer' },
        { path: '/wallboard', title: '执行大屏', icon: 'DataBoard' }
      ]
    },
    {
      label: '用例资产',
      items: [
        { path: '/cases', title: '测试用例', icon: 'DocumentCopy' },
        { path: '/suites', title: '测试套件', icon: 'Collection' },
        { path: '/platform-config', title: '平台配置', icon: 'Setting' },
        { path: '/app-packages', title: 'APP 包仓库', icon: 'Box' }
      ]
    },
    {
      label: '测试中心',
      items: [
        { path: '/devices', title: '设备管理', icon: 'Iphone' },
        { path: '/element-picker', title: '控件拾取', icon: 'Aim' },
        { path: '/tasks', title: '测试任务', icon: 'List' },
        { path: '/recordings', title: '录屏回放', icon: 'VideoCamera' },
        { path: '/recording-quality', title: '录制质量', icon: 'DataAnalysis' },
        { path: '/reports', title: '测试报告', icon: 'Document' }
      ]
    }
  ]
  if (userStore.isAdmin) {
    groups.push({
      label: '系统管理',
      items: [
        { path: '/controls', title: '控件池', icon: 'Grid' },
        { path: '/ci', title: 'CI/CD', icon: 'Connection' }
      ]
    })
  }
  groups.push({
    label: '账户',
    items: [{ path: '/profile', title: '个人中心', icon: 'User' }]
  })
  return groups
})

function isActive(path) {
  if (path === '/cases') return route.path.startsWith('/cases')
  if (path === '/suites') return route.path.startsWith('/suites')
  if (path === '/platform-config') return route.path === '/platform-config'
  if (path === '/app-packages') return route.path === '/app-packages'
  if (path === '/tasks') return route.path.startsWith('/tasks') || route.path.startsWith('/cases/editor')
  if (path === '/devices') return route.path.startsWith('/devices')
  if (path === '/element-picker') return route.path.startsWith('/element-picker')
  if (path === '/reports') return route.path.startsWith('/reports')
  if (path === '/recording-quality') return route.path === '/recording-quality'
  return route.path === path || route.path.startsWith(path + '/')
}

function handleCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}

const showMessages = ref(false)
const messages = ref([])
const unreadCount = ref(0)
let msgTimer = null

async function loadUnreadCount() {
  try {
    const res = await messageApi.unreadCount()
    unreadCount.value = res.data?.count ?? res.data ?? 0
  } catch { /* ignore */ }
}

async function loadMessages() {
  try {
    const res = await messageApi.list()
    messages.value = res.data || []
  } catch { /* ignore */ }
}

async function markAllRead() {
  await messageApi.markAllRead()
  unreadCount.value = 0
  messages.value = messages.value.map(m => ({ ...m, read_at: m.read_at || new Date().toISOString() }))
  ElMessage.success('已全部标记为已读')
}

async function openMessage(m) {
  if (!m.read_at) {
    await messageApi.markRead([m.id])
    m.read_at = new Date().toISOString()
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (m.related_task_id) {
    showMessages.value = false
    router.push(`/tasks/${m.related_task_id}`)
  }
}

function fmtMsgTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

async function checkGlobalRecoveryDraft() {
  if (operationRecordingState.active) return
  const draft = loadDraftMeta()
  if (!draft?.session_id) return
  const blob = await loadDraftBlob(draft.session_id)
  if (!blob) {
    await clearDraft(draft.session_id)
    return
  }
  const reasonText = draftReasonLabel(draft.reason)
  const isUploadFail = draft.reason === 'upload_failed'
  try {
    await ElMessageBox.confirm(
      `检测到未完成的录制草稿：会话 #${draft.session_id}，${draft.step_count || 0} 步（${reasonText}）`,
      '恢复录制草稿',
      {
        confirmButtonText: isUploadFail ? '重试上传' : '前往审阅',
        cancelButtonText: '丢弃',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    )
    if (isUploadFail) {
      const operator = userStore.user?.display_name || userStore.user?.username || ''
      await uploadDraftVideo(draft, recordApi, operator)
      ElMessage.success('视频上传成功')
    }
    router.push(`/recordings/review/${draft.session_id}`)
  } catch (action) {
    if (action === 'cancel') {
      await clearDraft(draft.session_id)
      ElMessage.info('已丢弃录制草稿')
    }
  }
}

onMounted(() => {
  loadFeatures()
  loadUnreadCount()
  syncActiveScreenSessions()
  msgTimer = setInterval(loadUnreadCount, 60000)
  screenSyncTimer = setInterval(syncActiveScreenSessions, 1500)
  featuresPollTimer = setInterval(() => loadFeatures(true), 60000)
  document.addEventListener('visibilitychange', onVisibilityChange)
  if (userStore.canEdit) checkGlobalRecoveryDraft()
})

let screenSyncTimer = null
let featuresPollTimer = null

function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadFeatures(true)
}

onUnmounted(() => {
  if (msgTimer) clearInterval(msgTimer)
  if (screenSyncTimer) clearInterval(screenSyncTimer)
  if (featuresPollTimer) clearInterval(featuresPollTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped lang="scss">
.main-layout {
  height: 100vh;
  background: var(--atp-bg);
}

.sidebar {
  background: linear-gradient(180deg, #070B14 0%, var(--atp-sidebar) 50%, #111827 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  border-right: 1px solid rgba(56, 189, 248, 0.08);
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  &.collapsed {
    justify-content: center;
    padding: 20px 12px;
  }
}

.logo-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--atp-brand-400), var(--atp-primary));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 14px rgba(8, 145, 178, 0.35);
}

.logo-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.logo-title {
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.logo-sub {
  color: rgba(255, 255, 255, 0.45);
  font-size: 10px;
  margin-top: 2px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.menu-scroll {
  flex: 1;
  padding: 12px 10px;
}

.nav-group {
  margin-bottom: 20px;
}

.nav-group-label {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 0 12px 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  margin-bottom: 4px;
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 14px;
  transition: all 0.2s ease;
  position: relative;

  .el-icon {
    font-size: 18px;
    flex-shrink: 0;
  }

  &:hover {
    background: var(--atp-sidebar-hover);
    color: #fff;
  }

  &.active {
    background: var(--atp-sidebar-active);
    color: #fff;
    font-weight: 500;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      background: var(--atp-brand-400);
      border-radius: 0 3px 3px 0;
    }
  }
}

.sidebar-footer {
  padding: 16px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.version-tag {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  text-align: center;
}

.main-area {
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 60px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--atp-border-neutral);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--atp-border);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--atp-text-secondary);
  transition: all 0.2s;

  &:hover {
    border-color: var(--atp-brand-300);
    color: var(--atp-primary);
    background: var(--atp-brand-50);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.role-tag {
  border-color: var(--atp-border) !important;
  color: var(--atp-text-secondary) !important;
}

.user-block {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 24px;
  transition: background 0.2s;

  &:hover {
    background: var(--atp-border-light);
  }
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--atp-brand-400), var(--atp-primary));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.username {
  font-size: 14px;
  color: var(--atp-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  padding: 0;
  overflow-y: auto;
  background: var(--atp-bg-page);
}

.main-content:has(.page-overview) {
  background:
    radial-gradient(ellipse at 20% 0%, rgba(56, 189, 248, 0.07) 0%, transparent 50%),
    var(--atp-bg);
}

.msg-badge { margin-right: 8px; }

.screen-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--atp-success), #059669);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 12px rgba(16, 185, 129, 0.3);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 16px rgba(108, 212, 178, 0.45);
  }
}

.pill-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.msg-item {
  padding: 12px;
  border-bottom: 1px solid var(--atp-border-light, #eee);
  cursor: pointer;
  transition: background 0.15s;
}
.msg-item:hover { background: var(--atp-brand-50); }
.msg-item.unread { background: rgba(8, 145, 178, 0.06); }
.msg-item.unread .msg-title::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--atp-warning);
  margin-right: 6px;
  vertical-align: middle;
}
.msg-title { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
.msg-body { font-size: 13px; color: var(--atp-text-secondary); line-height: 1.5; }
.msg-time { font-size: 12px; color: var(--atp-text-placeholder); margin-top: 6px; }
</style>
