import { useState, useEffect } from 'react'
import {
  Modal, Form, InputNumber, Button, Space, Card, Tag, Statistic, Row, Col,
  Switch, message, Typography, Divider
} from 'antd'
import { PlayCircleOutlined, PauseCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  startSimulator, stopSimulator, getSimulatorStatus
} from '../../services/deviceApi'
import { formatDateTime } from '../../utils'

const { Title, Text } = Typography

function DeviceSimulator({ device, visible, onClose, onRefresh }) {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState(null)
  const [refreshInterval, setRefreshInterval] = useState(null)

  const fetchStatus = async () => {
    if (!device) return
    try {
      const res = await getSimulatorStatus(device.deviceId)
      setStatus(res)
    } catch (error) {
      console.error('获取模拟器状态失败:', error)
    }
  }

  useEffect(() => {
    if (visible && device) {
      fetchStatus()
      const interval = setInterval(fetchStatus, 2000)
      setRefreshInterval(interval)
      form.setFieldsValue({
        intervalSeconds: 5,
        minTemperature: 15,
        maxTemperature: 40,
        minHumidity: 30,
        maxHumidity: 90
      })
    }
    return () => {
      if (refreshInterval) {
        clearInterval(refreshInterval)
        setRefreshInterval(null)
      }
    }
  }, [visible, device])

  const handleStart = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      await startSimulator({
        deviceId: device.deviceId,
        deviceType: device.type,
        ...values
      })
      message.success('模拟器已启动')
      fetchStatus()
    } catch (error) {
      if (error.errorFields) return
      console.error('启动模拟器失败:', error)
      message.error('启动模拟器失败')
    } finally {
      setLoading(false)
    }
  }

  const handleStop = async () => {
    try {
      setLoading(true)
      await stopSimulator(device.deviceId)
      message.success('模拟器已停止')
      fetchStatus()
    } catch (error) {
      console.error('停止模拟器失败:', error)
      message.error('停止模拟器失败')
    } finally {
      setLoading(false)
    }
  }

  if (!visible || !device) return null

  return (
    <Modal
      title={
        <Space>
          <span>设备模拟器</span>
          <Tag color={status?.running ? 'success' : 'default'}>
            {status?.running ? '运行中' : '已停止'}
          </Tag>
        </Space>
      }
      open={visible}
      onCancel={onClose}
      footer={null}
      width={600}
      destroyOnClose
    >
      <div>
        <Card bordered={false} style={{ marginBottom: 16, background: '#f5f5f5' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Title level={5} style={{ margin: 0 }}>{device.name}</Title>
              <Text type="secondary">ID: {device.deviceId}</Text>
            </div>
            <Tag color="blue">{device.type}</Tag>
          </div>
        </Card>

        {status && (
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={8}>
              <Card bordered={false} style={{ textAlign: 'center' }}>
                <Statistic
                  title="温度"
                  value={status.lastTemperature ?? '--'}
                  suffix="°C"
                  precision={1}
                  valueStyle={{ color: status.running ? '#cf1322' : '#999' }}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card bordered={false} style={{ textAlign: 'center' }}>
                <Statistic
                  title="湿度"
                  value={status.lastHumidity ?? '--'}
                  suffix="%"
                  precision={1}
                  valueStyle={{ color: status.running ? '#1890ff' : '#999' }}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card bordered={false} style={{ textAlign: 'center' }}>
                <Statistic
                  title="上报次数"
                  value={status.reportCount ?? 0}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
          </Row>
        )}

        {status?.lastReportTime && (
          <div style={{ textAlign: 'center', marginBottom: 16, color: '#888' }}>
            最后上报: {formatDateTime(status.lastReportTime)}
          </div>
        )}

        <Divider orientation="left">模拟器配置</Divider>

        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="intervalSeconds"
                label="上报间隔（秒）"
                rules={[{ required: true, message: '请输入上报间隔' }]}
              >
                <InputNumber min={1} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left" plain>温度范围</Divider>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="minTemperature"
                label="最低温度（°C）"
                rules={[{ required: true, message: '请输入最低温度' }]}
              >
                <InputNumber min={-40} max={80} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="maxTemperature"
                label="最高温度（°C）"
                rules={[{ required: true, message: '请输入最高温度' }]}
              >
                <InputNumber min={-40} max={80} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left" plain>湿度范围</Divider>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="minHumidity"
                label="最低湿度（%）"
                rules={[{ required: true, message: '请输入最低湿度' }]}
              >
                <InputNumber min={0} max={100} step={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="maxHumidity"
                label="最高湿度（%）"
                rules={[{ required: true, message: '请输入最高湿度' }]}
              >
                <InputNumber min={0} max={100} step={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <div style={{ marginTop: 24, textAlign: 'center' }}>
            <Space>
              {status?.running ? (
                <Button
                  type="primary"
                  danger
                  icon={<PauseCircleOutlined />}
                  onClick={handleStop}
                  loading={loading}
                  size="large"
                >
                  停止模拟
                </Button>
              ) : (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={handleStart}
                  loading={loading}
                  size="large"
                >
                  开始模拟
                </Button>
              )}
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchStatus}
                size="large"
              >
                刷新状态
              </Button>
            </Space>
          </div>
        </Form>
      </div>
    </Modal>
  )
}

export default DeviceSimulator
