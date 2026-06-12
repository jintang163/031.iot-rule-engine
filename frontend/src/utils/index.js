export const generateId = (prefix = 'id') => {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

export const generateNodeId = (type = 'node') => {
  return `${type}_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`
}

export const formatDate = (date, format = 'YYYY-MM-DD HH:mm:ss') => {
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

export const formatRelativeTime = (date) => {
  const now = new Date()
  const target = new Date(date)
  const diff = now.getTime() - target.getTime()

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}天前`
  if (hours > 0) return `${hours}小时前`
  if (minutes > 0) return `${minutes}分钟前`
  if (seconds > 0) return `${seconds}秒前`
  return '刚刚'
}

export const debounce = (func, wait) => {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

export const throttle = (func, limit) => {
  let inThrottle
  return function (...args) {
    if (!inThrottle) {
      func.apply(this, args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}

export const deepClone = (obj) => {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime())
  if (obj instanceof Array) return obj.map((item) => deepClone(item))
  const cloned = {}
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      cloned[key] = deepClone(obj[key])
    }
  }
  return cloned
}

export const validateExpression = (expression) => {
  if (!expression || !expression.trim()) return false
  try {
    new Function(`return (${expression})`)
    return true
  } catch (e) {
    return false
  }
}

export const validateMqttTopic = (topic) => {
  if (!topic || !topic.trim()) return false
  const regex = /^[a-zA-Z0-9_+#\/$\-\.]+$/
  return regex.test(topic)
}

export const bytesToSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

export const getStatusColor = (status) => {
  const colorMap = {
    online: '#52c41a',
    offline: '#ff4d4f',
    active: '#52c41a',
    inactive: '#8c8c8c',
    draft: '#faad14',
    running: '#1890ff',
    error: '#ff4d4f',
    warning: '#faad14',
    success: '#52c41a'
  }
  return colorMap[status] || '#8c8c8c'
}

export const formatDateTime = (date) => {
  if (!date) return '-'
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export default {
  generateId,
  generateNodeId,
  formatDate,
  formatDateTime,
  formatRelativeTime,
  debounce,
  throttle,
  deepClone,
  validateExpression,
  validateMqttTopic,
  bytesToSize,
  getStatusColor,
  downloadBlob
}
