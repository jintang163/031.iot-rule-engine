import useAppStore from '../store/useAppStore'

class WsService {
  constructor() {
    this.ws = null
    this.subscriptions = new Map()
    this.reconnectTimer = null
    this.reconnectCount = 0
    this.maxReconnectCount = 10
    this.reconnectInterval = 3000
  }

  connect(url = '/api/ws') {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}${url}`

    try {
      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        console.log('WebSocket 连接成功')
        this.reconnectCount = 0
        useAppStore.getState().setWsConnected(true)
      }

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          const destination = message.destination || message.topic
          if (destination && this.subscriptions.has(destination)) {
            const callbacks = this.subscriptions.get(destination)
            callbacks.forEach((cb) => cb(message.body || message.payload || message))
          }
        } catch (e) {
          console.warn('WebSocket 消息解析失败:', e)
        }
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket 错误:', error)
      }

      this.ws.onclose = (event) => {
        console.log('WebSocket 连接关闭:', event.code, event.reason)
        useAppStore.getState().setWsConnected(false)
        this.tryReconnect(url)
      }
    } catch (error) {
      console.error('WebSocket 连接失败:', error)
      this.tryReconnect(url)
    }
  }

  tryReconnect(url) {
    if (this.reconnectCount >= this.maxReconnectCount) {
      console.warn('WebSocket 重连次数已达上限，停止重连')
      return
    }
    if (this.reconnectTimer) return

    this.reconnectCount++
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      console.log(`WebSocket 第 ${this.reconnectCount} 次重连...`)
      this.connect(url)
    }, this.reconnectInterval)
  }

  subscribeDeviceStatus(callback) {
    this.subscribe('/topic/device/status', callback)
  }

  subscribeDeviceData(callback) {
    this.subscribe('/topic/device/data', callback)
  }

  subscribeAlerts(callback) {
    this.subscribe('/topic/alerts', callback)
  }

  subscribe(topic, callback) {
    if (!this.subscriptions.has(topic)) {
      this.subscriptions.set(topic, [])
    }
    const callbacks = this.subscriptions.get(topic)
    if (!callbacks.includes(callback)) {
      callbacks.push(callback)
    }
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'subscribe', destination: topic }))
    }
  }

  unsubscribe(topic, callback) {
    if (this.subscriptions.has(topic)) {
      const callbacks = this.subscriptions.get(topic)
      const index = callbacks.indexOf(callback)
      if (index > -1) {
        callbacks.splice(index, 1)
      }
      if (callbacks.length === 0) {
        this.subscriptions.delete(topic)
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          this.ws.send(JSON.stringify({ type: 'unsubscribe', destination: topic }))
        }
      }
    }
  }

  send(destination, body) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ destination, body }))
    } else {
      console.warn('WebSocket 未连接，消息发送失败')
    }
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.subscriptions.clear()
    useAppStore.getState().setWsConnected(false)
  }
}

const wsService = new WsService()

export default wsService
