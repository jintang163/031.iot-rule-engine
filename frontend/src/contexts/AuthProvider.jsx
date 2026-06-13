import { createContext, useContext, useEffect, useState, useMemo } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { message } from 'antd'
import {
  login as loginApi,
  logout as logoutApi,
  getCurrentUser,
  setToken,
  getToken,
  removeToken,
  setUser,
  getUser
} from '../services/authApi'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const initAuth = async () => {
      const token = getToken()
      if (!token) {
        setLoading(false)
        return
      }
      const cachedUser = getUser()
      if (cachedUser) {
        setUserState(cachedUser)
      }
      setLoading(false)
    }
    initAuth()
  }, [])

  const login = async (form) => {
    const res = await loginApi(form)
    setToken(res.token)
    const userInfo = {
      userId: res.userId,
      tenantId: res.tenantId,
      tenantName: res.tenantName,
      username: res.username,
      nickname: res.nickname,
      avatar: res.avatar,
      roles: res.roles,
      permissions: res.permissions,
      isAdmin: res.isAdmin
    }
    setUser(userInfo)
    setUserState(userInfo)
    message.success(`欢迎, ${userInfo.nickname || userInfo.username}`)
    return res
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (e) {
    } finally {
      removeToken()
      setUserState(null)
      message.success('已退出登录')
    }
  }

  const hasPerm = (perm) => {
    if (!user || !user.permissions) return false
    if (user.isAdmin) return true
    return user.permissions.includes(perm) || user.permissions.includes('*')
  }

  const hasRole = (role) => {
    if (!user || !user.roles) return false
    if (user.isAdmin) return true
    return Array.isArray(role)
      ? role.some(r => user.roles.includes(r))
      : user.roles.includes(role)
  }

  const value = useMemo(() => ({
    user,
    loading,
    login,
    logout,
    hasPerm,
    hasRole
  }), [user, loading])

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

export function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return null
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return children
}
