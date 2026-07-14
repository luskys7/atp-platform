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
    user: readStoredUser()
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
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.user))
      return res.data
    },

    async fetchProfile() {
      const res = await authApi.profile()
      this.user = res.data
      localStorage.setItem('user', JSON.stringify(res.data))
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
