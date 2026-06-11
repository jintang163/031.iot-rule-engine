import { useState, useEffect } from 'react'
import {
  Card, Button, Input, Select, Space, Tag, Modal, Form, message,
  Popconfirm, Switch, Slider, Row, Col, Checkbox, Radio
} from 'antd'
import {
  SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined,
  PoweroffOutlined, BulbOutlined, ThunderboltOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import {
  getDeviceList, createDevice, updateDevice, deleteDevice,
  controlDevice, getOnlineDevices
} from '../services/deviceApi'
import useAppStore from '../store/useAppStore'
import { formatDate, formatRelativeTime } from '../utils'

const { Option } = Select
const { Meta } = Card

const deviceTypeMap = {
  air_conditioner: { label: '空调', color: 'blue', icon: '❄️' },
  light: { label: '灯光', color: 'gold', icon: '💡' },
  temperature_sensor: { label: '温感', color: 'green', icon: '🌡️' },
  humidity_sensor: { label: '湿感', color: 'cyan', icon: '💧' },
  human_sensor: { label: '人体', color: 'purple', icon: '👤' }
}

const actionOptions = [
  { label: '开关控制', value: 'power' },
  { label: '温度调节', value: 'temperature' },
  { label: '亮度调节', value: 'brightness' },
  { label: '模式切换', value: 'mode' }
]

function DeviceManager() {
  const navigate = useNavigate()
  const { setLoading } = useAppStore()

  const [searchName, setSearchName] = useState('')
  const [searchType, setSearchType] = useState(null)
  const [searchOnline, setSearchOnline] = useState(null)
  const [data, setData] = useState([])
  const [stats, setStats] = useState({
    total: 0,
    online: 0,
    offline: 0,
    sensorCount: 0,
    actuatorCount: 0
  })
  const [modalOpen, setModalOpen] = useState(false)
  const [editData, setEditData] = useState(null)
  const [form] = Form.useForm()

  const fetchDeviceList = async () => {
    setLoading(true)
    try {
      const params = {
        name: searchName || undefined,
        type: searchType || undefined,
        online: searchOnline !== null ? searchOnline : undefined
      }
      const res = await getDeviceList(params)
      const list = res?.records || res?.list || res || []
      setData(list)

      const onlineList = list.filter((d) => d.online || d.status === 'online')
      const sensorList = list.filter((d) =>
        ['temperature_sensor', 'humidity_sensor', 'human_sensor'].includes(d.type)
      )
      const actuatorList = list.filter((d) =>
        ['air_conditioner', 'light'].includes(d.type)
      )
      setStats({
        total: list.length,
        online: onlineList.length,
        offline: list.length - onlineList.length,
        sensorCount: sensorList.length,
        actuatorCount: actuatorList.length
      })
    } catch (error) {
      console.error('获取设备列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDeviceList()
  }, [])

  const handleSearch = () => {
    fetchDeviceList()
  }

  const handleReset = () => {
    setSearchName('')
    setSearchType(null)
    setSearchOnline(null)
    setTimeout(fetchDeviceList, 0)
  }

  const handleAdd = () => {
    setEditData(null)
    form.resetFields()
    setModalOpen(true)
  }

  const handleEdit = (record) => {
    setEditData(record)
    form.setFieldsValue({
      id: record.id,
      name: record.name,
      type: record.type,
      actions: record.actions || []
    })
    setModalOpen(true)
  }

  const handleDelete = async (id) => {
    try {
      setLoading(true)
      await deleteDevice(id)
      message.success('删除成功')
      fetchDeviceList()
    } catch (error) {
      console.error('删除设备失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleModalSubmit = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      if (editData) {
        await updateDevice({ ...editData, ...values })
        message.success('更新成功')
      } else {
        await createDevice(values)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchDeviceList()
    } catch (error) {
      if (error.errorFields) return
      console.error('提交失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleControlPower = async (device, value) => {
    try {
      setLoading(true)
      await controlDevice(device.id, 'power', { on: value })
      message.success(value ? '已开启' : '已关闭')
      fetchDeviceList()
    } catch (error) {
      console.error('控制失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleTemperatureChange = async (device, value) => {
    try {
      setLoading(true)
      await controlDevice(device.id, 'temperature', { temperature: value })
      message.success(`温度已调节至 ${value}°C`)
      fetchDeviceList()
    } catch (error) {
      console.error('温度调节失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const renderDeviceStatus = (device) => {
    const typeInfo = deviceTypeMap[device.type] || { label: device.type, icon: '📦' }
    const statusData = device.data || device.statusData || {}

    switch (device.type) {
      case 'air_conditioner':
        return (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <Switch
                checked={statusData.on ?? device.on ?? false}
                onChange={(val) => handleControlPower(device, val)}
                checkedChildren="开"
                unCheckedChildren="关"
              />
              <Tag color="blue">模式: {statusData.mode || '制冷'}</Tag>
            </div>
            <div style={{ paddingLeft: 4, paddingRight: 8 }}>
              <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>
                温度: {statusData.temperature ?? 26}°C
              </div>
              <Slider
                min={16}
                max={30}
                value={statusData.temperature ?? 26}
                onChange={(val) => handleTemperatureChange(device, val)}
                disabled={!(statusData.on ?? device.on)}
              />
            </div>
          </div>
        )
      case 'light':
        return (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Switch
                checked={statusData.on ?? device.on ?? false}
                onChange={(val) => handleControlPower(device, val)}
                checkedChildren={<BulbOutlined />}
                unCheckedChildren={<BulbOutlined />}
              />
              <Tag color="gold">亮度: {statusData.brightness ?? 80}%</Tag>
            </div>
          </div>
        )
      case 'temperature_sensor':
        return (
          <div style={{ marginTop: 12, display: 'flex', gap: 16 }}>
            <Tag color="green">
              🌡️ 温度: {statusData.temperature ?? '--'}°C
            </Tag>
          </div>
        )
      case 'humidity_sensor':
        return (
          <div style={{ marginTop: 12, display: 'flex', gap: 16 }}>
            <Tag color="cyan">
              💧 湿度: {statusData.humidity ?? '--'}%
            </Tag>
          </div>
        )
      case 'human_sensor':
        return (
          <div style={{ marginTop: 12 }}>
            <Tag color={statusData.detected ? 'purple' : 'default'}>
              👤 {statusData.detected ? '有人' : '无人'}
            </Tag>
          </div>
        )
      default:
        return null
    }
  }

  const statCards = [
    { title: '总设备数', value: stats.total, color: '#1890ff' },
    { title: '在线数', value: stats.online, color: '#52c41a' },
    { title: '离线数', value: stats.offline, color: '#ff4d4f' },
    { title: '传感器数', value: stats.sensorCount, color: '#722ed1' },
    { title: '执行器数', value: stats.actuatorCount, color: '#fa8c16' }
  ]

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 16, marginBottom: 24 }}>
        {statCards.map((card, idx) => (
          <Card key={idx} bordered={false} style={{ borderRadius: 8 }}>
            <div style={{ fontSize: 14, color: '#888', marginBottom: 8 }}>{card.title}</div>
            <div style={{ fontSize: 28, fontWeight: 'bold', color: card.color }}>{card.value}</div>
          </Card>
        ))}
      </div>

      <Card bordered={false} style={{ borderRadius: 8, marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
          <h3 style={{ margin: 0 }}>设备管理</h3>
          <Space wrap>
            <Input
              placeholder="设备名称"
              prefix={<SearchOutlined />}
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Select
              placeholder="设备类型"
              value={searchType}
              onChange={(val) => setSearchType(val)}
              style={{ width: 140 }}
              allowClear
            >
              <Option value="air_conditioner">空调</Option>
              <Option value="light">灯光</Option>
              <Option value="temperature_sensor">温感</Option>
              <Option value="humidity_sensor">湿感</Option>
              <Option value="human_sensor">人体</Option>
            </Select>
            <Select
              placeholder="在线状态"
              value={searchOnline !== null ? String(searchOnline) : null}
              onChange={(val) => setSearchOnline(val === null ? null : val === 'true')}
              style={{ width: 120 }}
              allowClear
            >
              <Option value="true">在线</Option>
              <Option value="false">离线</Option>
            </Select>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button onClick={handleReset}>重置</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              新增设备
            </Button>
          </Space>
        </div>

        <Row gutter={[16, 16]}>
          {data.map((device) => {
            const typeInfo = deviceTypeMap[device.type] || { label: device.type, icon: '📦' }
            const isOnline = device.online || device.status === 'online'
            return (
              <Col xs={24} sm={12} md={8} lg={6} xl={6} key={device.id}>
                <Card
                  bordered
                  style={{
                    borderRadius: 8,
                    position: 'relative',
                    opacity: isOnline ? 1 : 0.7
                  }}
                  bodyStyle={{ padding: 16 }}
                  actions={[
                    <EditOutlined key="edit" style={{ color: '#1890ff' }} onClick={() => handleEdit(device)} />,
                    <Popconfirm
                      key="delete"
                      title="确认删除该设备？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => handleDelete(device.id)}
                    >
                      <DeleteOutlined style={{ color: '#ff4d4f' }} />
                    </Popconfirm>
                  ]}
                >
                  <div style={{
                    position: 'absolute',
                    top: 14,
                    right: 14,
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    backgroundColor: isOnline ? '#52c41a' : '#ff4d4f',
                    boxShadow: isOnline ? '0 0 6px #52c41a' : 'none'
                  }} />

                  <Meta
                    title={
                      <Space>
                        <span style={{ fontSize: 18 }}>{typeInfo.icon}</span>
                        <span style={{ fontSize: 16, fontWeight: 600 }}>{device.name}</span>
                      </Space>
                    }
                    description={
                      <div style={{ marginTop: 8 }}>
                        <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>
                          ID: {device.id}
                        </div>
                        <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
                        <Tag color={isOnline ? 'success' : 'default'}>
                          {isOnline ? '在线' : '离线'}
                        </Tag>
                        {renderDeviceStatus(device)}
                        <div style={{ fontSize: 12, color: '#aaa', marginTop: 12 }}>
                          最近上报: {device.lastReportTime ? formatRelativeTime(device.lastReportTime) : '-'}
                        </div>
                      </div>
                    }
                  />
                </Card>
              </Col>
            )
          })}
        </Row>

        {data.length === 0 && (
          <div style={{ textAlign: 'center', padding: 60, color: '#888' }}>
            暂无设备数据
          </div>
        )}
      </Card>

      <Modal
        title={editData ? '编辑设备' : '新增设备'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleModalSubmit}
        okText="确认"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="id"
            label="设备ID"
            rules={[{ required: true, message: '请输入设备ID' }]}
          >
            <Input placeholder="请输入设备ID" disabled={!!editData} />
          </Form.Item>
          <Form.Item
            name="name"
            label="设备名称"
            rules={[{ required: true, message: '请输入设备名称' }]}
          >
            <Input placeholder="请输入设备名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="设备类型"
            rules={[{ required: true, message: '请选择设备类型' }]}
          >
            <Select placeholder="请选择设备类型">
              <Option value="air_conditioner">空调</Option>
              <Option value="light">灯光</Option>
              <Option value="temperature_sensor">温感</Option>
              <Option value="humidity_sensor">湿感</Option>
              <Option value="human_sensor">人体</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="actions"
            label="支持动作"
          >
            <Checkbox.Group options={actionOptions} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default DeviceManager
