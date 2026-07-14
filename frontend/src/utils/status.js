export const deviceStatusMap = {
  online: { label: '在线', type: 'success' },
  offline: { label: '离线', type: 'info' },
  busy: { label: '占用', type: 'warning' },
  maintenance: { label: '维护', type: 'info' },
  error: { label: '异常', type: 'danger' }
}

export const taskStatusMap = {
  pending: { label: '等待中', type: 'info' },
  queued: { label: '排队中', type: 'info' },
  running: { label: '运行中', type: 'primary' },
  success: { label: '通过', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  timeout: { label: '超时', type: 'danger' },
  paused: { label: '已暂停', type: 'info' },
  waiting_manual: { label: '待人工', type: 'warning' }
}

export const roleLabels = {
  super_admin: '超级管理员',
  test_admin: '测试管理员',
  tester: '测试人员',
  developer_readonly: '研发只读'
}

export function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}
