import { useState, useEffect, useRef } from 'react'
import {
  Card, Button, Input, Select, Space, Tag, Modal, Form, message,
  Popconfirm, Switch, Slider, Row, Col, Checkbox, Radio, Upload, Table
} from 'antd'
import {
  SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined,
  PoweroffOutlined, BulbOutlined, ThunderboltOutlined, ExportOutlined,
  ImportOutlined, DownloadOutlined, RobotOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import {
  getDeviceList, createDevice, updateDevice, deleteDevice,
  controlDevice, getOnlineDevices, exportDevices, importDevices,
  downloadImportTemplate
} from '../services/deviceApi'
import useAppStore from '../store/useAppStore'
import { formatDate, formatRelativeTime, downloadBlob } from '../utils'
import DeviceForm from '../components/device/DeviceForm'
import DeviceSimulator from '../components/device/DeviceSimulator'

const { Option } = Select
const { Meta } = Card

const deviceTypeMap = {
  aircon: { label: '空调', color: 'blue', icon: '❄️' },
  light: { label: '灯光', color: 'gold', icon: '💡' },
  sensor_temp: { label: '温感', color: 'green', icon: '🌡️' },
  sensor_humidity: { label: '湿感', color: 'cyan', icon: '💧' },
  sensor_presence: { label: '人体', color: 'purple', icon: '👤' }
}

const protocolMap = {
  MQTT: { color: 'blue' },
  HTTP: { color: 'orange' }
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
  const fileInputRef = useRef(null)

  const [searchName, setSearchName] = useState('')
  const [searchType, setSearchType] = useState(null)
  const [searchOnline, setSearchOnline] = useState(null)
  const [searchRoom, setSearchRoom] = useState('')
  const [searchProtocol, setSearchProtocol] = useState(null)
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
  const [simulatorVisible, setSimulatorVisible] = useState(false)
  const [simulatorDevice, setSimulatorDevice] = useState(null)
  const [viewMode, setViewMode] = useState('card')

  const fetchDeviceList = async () => {
    setLoading(true)
    try {
      const params = {
        name: searchName || undefined,
        type: searchType || undefined,
        online: searchOnline !== null ? searchOnline : undefined,
        room: searchRoom || undefined,
        protocol: searchProtocol || undefined
      }
      const res = await getDeviceList(params)
      const list = res?.records || res?.list || res || []
      setData(list)

      const onlineList = list.filter((d) => d.online === 1 || d.online === true)
      const sensorList = list.filter((d) =>
        ['sensor_temp', 'sensor_humidity', 'sensor_presence'].includes(d.type)
      )
      const actuatorList = list.filter((d) =>
        ['aircon', 'light'].includes(d.type)
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
    setSearchRoom('')
    setSearchProtocol(null)
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
      deviceId: record.deviceId,
      name: record.name,
      type: record.type,
      room: record.room,
      protocol: record.protocol,
      location: record.location,
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

  const handleModalSubmit = async (values) => {
    try {
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
      message.error('提交失败')
    } finally {
      setLoading(false)
    }
  }

  const handleControlPower = async (device, value) => {
    try {
      setLoading(true)
      await controlDevice(device.deviceId, 'power', { on: value })
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
      await controlDevice(device.deviceId, 'temperature', { temperature: value })
      message.success(`温度已调节至 ${value}°C`)
      fetchDeviceList()
    } catch (error) {
      console.error('温度调节失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleExport = async () => {
    try {
      setLoading(true)
      const blob = await exportDevices()
      downloadBlob(blob, `设备列表_${formatDate(new Date(), 'YYYYMMDDHHmmss')}.xlsx`)
      message.success('导出成功')
    } catch (error) {
      console.error('导出失败:', error)
      message.error('导出失败')
    } finally {
      setLoading(false)
    }
  }

  const handleDownloadTemplate = async () => {
    try {
      setLoading(true)
      const blob = await downloadImportTemplate()
      downloadBlob(blob, '设备导入模板.xlsx')
      message.success('模板下载成功')
    } catch (error) {
      console.error('模板下载失败:', error)
      message.error('模板下载失败')
    } finally {
      setLoading(false)
    }
  }

  const handleImport = async (file) => {
    try {
      setLoading(true)
      const result = await importDevices(file)
      const { successCount, failCount, errors } = result
      if (failCount > 0) {
        Modal.warning({
          title: '导入完成',
          content: (
            <div>
              <p>成功: {successCount} 条</p>
              <p>失败: {failCount} 条</p>
              {errors && errors.length > 0 && (
                <div style={{ marginTop: 12, maxHeight: 200, overflow: 'auto' }}>
                  {errors.map((err, idx) => (
                    <p key={idx} style={{ color: '#ff4d4f', margin: '4px 0' }}>{err}</p>
                  ))}
                </div>
              )}
            </div>
          )
        })
      } else {
        message.success(`导入成功，共 ${successCount} 条`)
      }
      fetchDeviceList()
    } catch (error) {
      console.error('导入失败:', error)
      message.error('导入失败')
    } finally {
      setLoading(false)
    }
    return false
  }

  const handleOpenSimulator = (device) => {
    setSimulatorDevice(device)
    setSimulatorVisible(true)
  }

  const renderDeviceStatus = (device) => {
    const typeInfo = deviceTypeMap[device.type] || { label: device.type, icon: '📦' }
    const statusData = device.data || device.statusData || {}
    const status = device.status ? JSON.parse(device.status) : {}

    switch (device.type) {
      case 'aircon':
        return (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <Switch
                checked={status.power === 'on' || status.on}
                onChange={(val) => handleControlPower(device, val)}
                checkedChildren="开"
                unCheckedChildren="关"
              />
              <Tag color="blue">模式: {status.mode || '制冷'}</Tag>
            </div>
            <div style={{ paddingLeft: 4, paddingRight: 8 }}>
              <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>
                温度: {status.temperature ?? 26}°C
              </div>
              <Slider
                min={16}
                max={30}
                value={status.temperature ?? 26}
                onChange={(val) => handleTemperatureChange(device, val)}
                disabled={!(status.power === 'on' || status.on)}
              />
            </div>
          </div>
        )
      case 'light':
        return (
          <div style={{ marginTop: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Switch
                checked={status.power === 'on' || status.on}
                onChange={(val) => handleControlPower(device, val)}
                checkedChildren={<BulbOutlined />}
                unCheckedChildren={<BulbOutlined />}
              />
              <Tag color="gold">亮度: {status.brightness ?? 80}%</Tag>
            </div>
          </div>
        )
      case 'sensor_temp':
        return (
          <div style={{ marginTop: 12, display: 'flex', gap: 16 }}>
            <Tag color="green">
              🌡️ 温度: {status.temperature ?? '--'}°C
            </Tag>
            {status.humidity && (
              <Tag color="cyan">
                💧 湿度: {status.humidity}%
              </Tag>
            )}
          </div>
        )
      case 'sensor_humidity':
        return (
          <div style={{ marginTop: 12, display: 'flex', gap: 16 }}>
            <Tag color="cyan">
              💧 湿度: {status.humidity ?? '--'}%
            </Tag>
          </div>
        )
      case 'sensor_presence':
        return (
          <div style={{ marginTop: 12 }}>
            <Tag color={status.presence ? 'purple' : 'default'}>
              👤 {status.presence ? '有人' : '无人'}
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

  const tableColumns = [
    {
      title: '设备ID',
      dataIndex: 'deviceId',
      key: 'deviceId',
      width: 150
    },
    {
      title: '设备名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '设备类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type) => {
        const info = deviceTypeMap[type] || { label: type, icon: '📦', color: 'default' }
        return (
          <Space>
            <span>{info.icon}</span>
            <Tag color={info.color}>{info.label}</Tag>
          </Space>
        )
      }
    },
    {
      title: '所属房间',
      dataIndex: 'room',
      key: 'room',
      width: 100
    },
    {
      title: '通信协议',
      dataIndex: 'protocol',
      key: 'protocol',
      width: 100,
      render: (protocol) => {
        const info = protocolMap[protocol] || { color: 'default' }
        return <Tag color={info.color}>{protocol || '-'}</Tag>
      }
    },
    {
      title: '安装位置',
      dataIndex: 'location',
      key: 'location'
    },
    {
      title: '在线状态',
      dataIndex: 'online',
      key: 'online',
      width: 100,
      render: (online) => (
        <Tag color={online === 1 ? 'success' : 'default'}>
          {online === 1 ? '在线' : '离线'}
        </Tag>
      )
    },
    {
      title: '最后活跃时间',
      dataIndex: 'lastOnlineTime',
      key: 'lastOnlineTime',
      width: 180,
      render: (time) => time ? formatRelativeTime(time) : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <a onClick={() => handleOpenSimulator(record)}>
            <RobotOutlined /> 模拟
          </a>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <Popconfirm
            title="确认删除该设备？"
            onConfirm={() => handleDelete(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <a style={{ color: '#ff4d4f' }}>删除</a>
          </Popconfirm>
        </Space>
      )
    }
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
              style={{ width: 160 }}
              allowClear
            />
            <Input
              placeholder="所属房间"
              value={searchRoom}
              onChange={(e) => setSearchRoom(e.target.value)}
              style={{ width: 120 }}
              allowClear
            />
            <Select
              placeholder="设备类型"
              value={searchType}
              onChange={(val) => setSearchType(val)}
              style={{ width: 120 }}
              allowClear
            >
              <Option value="aircon">空调</Option>
              <Option value="light">灯光</Option>
              <Option value="sensor_temp">温感</Option>
              <Option value="sensor_humidity">湿感</Option>
              <Option value="sensor_presence">人体</Option>
            </Select>
            <Select
              placeholder="通信协议"
              value={searchProtocol}
              onChange={(val) => setSearchProtocol(val)}
              style={{ width: 120 }}
              allowClear
            >
              <Option value="MQTT">MQTT</Option>
              <Option value="HTTP">HTTP</Option>
            </Select>
            <Select
              placeholder="在线状态"
              value={searchOnline !== null ? String(searchOnline) : null}
              onChange={(val) => setSearchOnline(val === null ? null : val === '1' ? 1 : 0)}
              style={{ width: 100 }}
              allowClear
            >
              <Option value="1">在线</Option>
              <Option value="0">离线</Option>
            </Select>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button onClick={handleReset}>重置</Button>
            <Radio.Group value={viewMode} onChange={(e) => setViewMode(e.target.value)}>
              <Radio.Button value="card">卡片</Radio.Button>
              <Radio.Button value="table">列表</Radio.Button>
            </Radio.Group>
          </Space>
        </div>

        <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增设备
          </Button>
          <Button icon={<ExportOutlined />} onClick={handleExport}>
            导出Excel
          </Button>
          <Upload
            beforeUpload={handleImport}
            showUploadList={false}
            accept=".xlsx,.xls"
          >
            <Button icon={<ImportOutlined />}>
              导入Excel
            </Button>
          </Upload>
          <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
            下载模板
          </Button>
        </div>

        {viewMode === 'card' ? (
          <Row gutter={[16, 16]}>
            {data.map((device) => {
              const typeInfo = deviceTypeMap[device.type] || { label: device.type, icon: '📦' }
              const isOnline = device.online === 1 || device.online === true
              const protocolInfo = protocolMap[device.protocol] || { color: 'default' }
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
                      <RobotOutlined
                        key="simulator"
                        style={{ color: '#722ed1' }}
                        onClick={() => handleOpenSimulator(device)}
                      />,
                      <EditOutlined
                        key="edit"
                        style={{ color: '#1890ff' }}
                        onClick={() => handleEdit(device)}
                      />,
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
                            ID: {device.deviceId}
                          </div>
                          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 4 }}>
                            <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
                            {device.room && <Tag color="geekblue">{device.room}</Tag>}
                            <Tag color={protocolInfo.color}>{device.protocol || 'MQTT'}</Tag>
                            <Tag color={isOnline ? 'success' : 'default'}>
                              {isOnline ? '在线' : '离线'}
                            </Tag>
                          </div>
                          {renderDeviceStatus(device)}
                          <div style={{ fontSize: 12, color: '#aaa', marginTop: 12 }}>
                            最近活跃: {device.lastOnlineTime ? formatRelativeTime(device.lastOnlineTime) : '-'}
                          </div>
                          {device.location && (
                            <div style={{ fontSize: 12, color: '#aaa', marginTop: 2 }}>
                              位置: {device.location}
                            </div>
                          )}
                        </div>
                      }
                    />
                  </Card>
                </Col>
              )
            })}
          </Row>
        ) : (
          <Table
            columns={tableColumns}
            dataSource={data}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 1000 }}
          />
        )}

        {data.length === 0 && (
          <div style={{ textAlign: 'center', padding: 60, color: '#888' }}>
            暂无设备数据
          </div>
        )}
      </Card>

      <DeviceForm
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onSubmit={handleModalSubmit}
        editData={editData}
      />

      <DeviceSimulator
        device={simulatorDevice}
        visible={simulatorVisible}
        onClose={() => setSimulatorVisible(false)}
        onRefresh={fetchDeviceList}
      />
    </div>
  )
}

export default DeviceManager
