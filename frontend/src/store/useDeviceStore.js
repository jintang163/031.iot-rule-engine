import { create } from 'zustand'

const useDeviceStore = create((set, get) => ({
  devices: [],
  selectedDevice: null,
  deviceDataMap: {},
  loading: false,
  pagination: {
    current: 1,
    pageSize: 10,
    total: 0
  },
  filters: {},

  setDevices: (devices) => set({ devices }),

  setSelectedDevice: (device) => set({ selectedDevice: device }),

  addDevice: (device) => set((state) => ({
    devices: [device, ...state.devices],
    pagination: { ...state.pagination, total: state.pagination.total + 1 }
  })),

  updateDevice: (id, data) => set((state) => ({
    devices: state.devices.map((d) => (d.id === id ? { ...d, ...data } : d)),
    selectedDevice: state.selectedDevice?.id === id
      ? { ...state.selectedDevice, ...data }
      : state.selectedDevice
  })),

  deleteDevice: (id) => set((state) => ({
    devices: state.devices.filter((d) => d.id !== id),
    selectedDevice: state.selectedDevice?.id === id ? null : state.selectedDevice,
    pagination: { ...state.pagination, total: state.pagination.total - 1 }
  })),

  updateDeviceStatus: (id, status) => set((state) => ({
    devices: state.devices.map((d) =>
      d.id === id ? { ...d, status, lastSeen: new Date().toLocaleString() } : d
    )
  })),

  setDeviceData: (deviceId, data) => set((state) => ({
    deviceDataMap: {
      ...state.deviceDataMap,
      [deviceId]: {
        ...state.deviceDataMap[deviceId],
        ...data,
        updatedAt: new Date().toLocaleString()
      }
    }
  })),

  setLoading: (loading) => set({ loading }),

  setPagination: (pagination) => set((state) => ({
    pagination: { ...state.pagination, ...pagination }
  })),

  setFilters: (filters) => set((state) => ({
    filters: { ...state.filters, ...filters },
    pagination: { ...state.pagination, current: 1 }
  })),

  clearFilters: () => set({ filters: {}, pagination: { current: 1, pageSize: 10, total: 0 } })
}))

export default useDeviceStore
