import { create } from 'zustand'

const useAppStore = create((set, get) => ({
  collapsed: false,
  theme: 'light',
  loading: false,
  notificationList: [],
  wsConnected: false,
  mqttConnected: false,
  mqttClient: null,
  currentUser: null,

  toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),

  setTheme: (theme) => set({ theme }),

  setLoading: (loading) => set({ loading }),

  addNotification: (notification) => set((state) => ({
    notificationList: [
      {
        id: Date.now(),
        timestamp: new Date().toLocaleString(),
        read: false,
        ...notification
      },
      ...state.notificationList
    ]
  })),

  markNotificationRead: (id) => set((state) => ({
    notificationList: state.notificationList.map((n) =>
      n.id === id ? { ...n, read: true } : n
    )
  })),

  markAllRead: () => set((state) => ({
    notificationList: state.notificationList.map((n) => ({ ...n, read: true }))
  })),

  clearNotifications: () => set({ notificationList: [] }),

  setWsConnected: (connected) => set({ wsConnected: connected }),

  setMqttConnected: (connected) => set({ mqttConnected: connected }),

  setMqttClient: (client) => set({ mqttClient: client }),

  setCurrentUser: (user) => set({ currentUser: user })
}))

export default useAppStore
