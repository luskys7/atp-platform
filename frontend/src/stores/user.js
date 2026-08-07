import { defineStore } from 'pinia'
import { authApi } from '@/api'

function readStoredUser() {
  try {
    const raw = localStorage.getItem('user')
    if (!raw || raw === 'undefined' || raw === 'null') return null
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem('user')
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: readStoredUser(),
    preferences: null
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    canEdit: (state) => ['super_admin', 'test_admin', 'tester'].includes(state.user?.role),
    isAdmin: (state) => ['super_admin', 'test_admin'].includes(state.user?.role),
    isReadonly: (state) => state.user?.role === 'developer_readonly'
  },

  actions: {
    async login(credentials) {
      const res = await authApi.login(credentials)
      this.token = res.data.token
      this.user = res.data.user
      this.preferences = res.data.preferences || null
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.user))
      return res.data
    },

    async fetchProfile() {
      const res = await authApi.profile()
      // 兼容旧结构：data 直接是 user，或 { user, preferences }
      const payload = res.data || {}
      this.user = payload.user || payload
      this.preferences = payload.preferences || this.preferences
      localStorage.setItem('user', JSON.stringify(this.user))
      return payload
    },

    logout() {
      this.token = ''
      this.user = null
      this.preferences = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
