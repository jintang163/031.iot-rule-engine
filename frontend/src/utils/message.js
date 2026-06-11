import { message, notification } from 'antd'

export const showMessage = (type, content, duration = 3) => {
  message[type]({
    content,
    duration
  })
}

export const showSuccess = (content) => showMessage('success', content)
export const showError = (content) => showMessage('error', content)
export const showWarning = (content) => showMessage('warning', content)
export const showInfo = (content) => showMessage('info', content)

export const showNotification = (type, title, description, duration = 4.5) => {
  notification[type]({
    message: title,
    description,
    duration,
    placement: 'topRight'
  })
}

export const notifySuccess = (title, description) =>
  showNotification('success', title, description)
export const notifyError = (title, description) =>
  showNotification('error', title, description)
export const notifyWarning = (title, description) =>
  showNotification('warning', title, description)
export const notifyInfo = (title, description) =>
  showNotification('info', title, description)

export const handleApiError = (error, defaultMessage = '操作失败') => {
  const msg = error?.response?.data?.message || error?.message || defaultMessage
  showError(msg)
  return msg
}

export default {
  showMessage,
  showSuccess,
  showError,
  showWarning,
  showInfo,
  showNotification,
  notifySuccess,
  notifyError,
  notifyWarning,
  notifyInfo,
  handleApiError
}
