import mqtt from 'mqtt'

class MqttService {
  constructor() {
    this.client = null
    this.messageHandlers = new Map()
    this.isConnected = false
  }

  connect(options = {}) {
    const defaultOptions = {
      host: 'localhost',
      port: 8083,
      protocol: 'ws',
      username: '',
      password: '',
      clientId: `web_${Date.now()}_${Math.random().toString(16).substr(2, 8)}`,
      clean: true,
      reconnectPeriod: 5000,
      connectTimeout: 10000,
      ...options
    }

    const url = `${defaultOptions.protocol}://${defaultOptions.host}:${defaultOptions.port}/mqtt`

    return new Promise((resolve, reject) => {
      try {
        this.client = mqtt.connect(url, defaultOptions)

        this.client.on('connect', () => {
          this.isConnected = true
          console.log('MQTT连接成功')
          resolve(this.client)
        })

        this.client.on('error', (error) => {
          console.error('MQTT连接错误:', error)
          reject(error)
        })

        this.client.on('reconnect', () => {
          console.log('MQTT重连中...')
        })

        this.client.on('close', () => {
          this.isConnected = false
          console.log('MQTT连接已断开')
        })

        this.client.on('message', (topic, message) => {
          const payload = JSON.parse(message.toString())
          const handlers = this.messageHandlers.get(topic) || []
          handlers.forEach((handler) => {
            try {
              handler(topic, payload)
            } catch (e) {
              console.error('消息处理错误:', e)
            }
          })
        })
      } catch (error) {
        reject(error)
      }
    })
  }

  subscribe(topic, handler, options = { qos: 0 }) {
    if (!this.client || !this.isConnected) {
      console.warn('MQTT未连接，无法订阅')
      return
    }

    if (!this.messageHandlers.has(topic)) {
      this.messageHandlers.set(topic, [])
    }
    this.messageHandlers.get(topic).push(handler)

    this.client.subscribe(topic, options, (error) => {
      if (error) {
        console.error(`订阅主题失败: ${topic}`, error)
      } else {
        console.log(`订阅主题成功: ${topic}`)
      }
    })
  }

  unsubscribe(topic, handler) {
    if (!this.client) return

    if (handler) {
      const handlers = this.messageHandlers.get(topic) || []
      const index = handlers.indexOf(handler)
      if (index > -1) {
        handlers.splice(index, 1)
      }
      if (handlers.length === 0) {
        this.client.unsubscribe(topic)
        this.messageHandlers.delete(topic)
      }
    } else {
      this.client.unsubscribe(topic)
      this.messageHandlers.delete(topic)
    }
  }

  publish(topic, message, options = { qos: 0, retain: false }) {
    if (!this.client || !this.isConnected) {
      console.warn('MQTT未连接，无法发布消息')
      return
    }

    const payload = typeof message === 'string' ? message : JSON.stringify(message)
    this.client.publish(topic, payload, options, (error) => {
      if (error) {
        console.error(`发布消息失败: ${topic}`, error)
      }
    })
  }

  disconnect() {
    if (this.client) {
      this.client.end()
      this.client = null
      this.isConnected = false
      this.messageHandlers.clear()
    }
  }

  getConnectionStatus() {
    return this.isConnected
  }
}

const mqttService = new MqttService()

export default mqttService
