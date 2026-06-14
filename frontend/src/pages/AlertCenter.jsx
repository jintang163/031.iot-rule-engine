import { useState, useEffect, useMemo, useCallback } from 'react'
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Button,
  Input,
  Select,
  DatePicker,
  Space,
  Tooltip,
  message,
  Popconfirm,
  Typography,
  Modal,
  Form,
  Switch,
  Badge,
  Statistic,
  Tabs,
  Drawer,
  Descriptions,
  Divider
} from 'antd'
import {
  AlertOutlined,
  ReloadOutlined,
  SearchOutlined,
  CheckCircleOutlined,
  ClearOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  BellOutlined,
  SettingOutlined,
  DingtalkOutlined,
  MailOutlined,
  MessageOutlined,
  EyeOutlined,
  PieChartOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  getAlertList,
  getAlertStatistics,
  acknowledgeAlert,
  clearAlert,
  batchAcknowledge,
  batchClear,
  getNotifyConfigList,
  saveNotifyConfig,
  updateNotifyConfig
} from '../services/alertApi'

const { Title, Text } = Typography
const { RangePicker } = DatePicker
const { Option } = Select

const LEVEL_CONFIG = {
  critical: { color: '#ff4d4f', icon: <ExclamationCircleOutlined />, label: '严重', tagColor: 'error' },
  warning: { color: '#faad14', icon: <WarningOutlined />, label: '警告', tagColor: 'warning' },
  info: { color: '#1677ff', icon: <InfoCircleOutlined />, label: '提示', tagColor: 'processing' }
}

const STATUS_CONFIG = {
  pending: { color: '#faad14', label: '待处理', tagColor: 'warning' },
  acknowledged: { color: '#1677ff', label: '已确认', tagColor: 'processing' },
  cleared: { color: '#52c41a', label: '已清除', tagColor: 'success' }
}

const CHANNEL_CONFIG = {
  dingtalk: { label: '钉钉', icon: <DingtalkOutlined />, color: '#1677ff' },
  wecom: { label: '企业微信', icon: <MessageOutlined />, color: '#07c160' },
  email: { label: '邮件', icon: <MailOutlined />, color: '#fa8c16' }
}

function AlertCenter() {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState({})
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  const [levelFilter, setLevelFilter] = useState(null)
  const [statusFilter, setStatusFilter] = useState(null)
  const [deviceIdFilter, setDeviceIdFilter] = useState('')
  const [keywordFilter, setKeywordFilter] = useState('')
  const [dateRange, setDateRange] = useState(null)

  const [notifyConfigs, setNotifyConfigs] = useState([])
  const [configModalVisible, setConfigModalVisible] = useState(false)
  const [editingConfig, setEditingConfig] = useState(null)
  const [configForm] = Form.useForm()

  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false)
  const [currentAlert, setCurrentAlert] = useState(null)

  const [activeTab, setActiveTab] = useState('list')

  const fetchData = useCallback(async (page = pagination.current, pageSize = pagination.pageSize) => {
    setLoading(true)
    try {
      const params = {
        pageNum: page,
        pageSize,
        level: levelFilter,
        status: statusFilter,
        deviceId: deviceIdFilter || undefined,
        keyword: keywordFilter || undefined,
        startTime: dateRange?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
        endTime: dateRange?.[1]?.format('YYYY-MM-DD HH:mm:ss')
      }
      const result = await getAlertList(params)
      setData(result.records || [])
      setPagination(prev => ({
        ...prev,
        current: result.current || page,
        total: result.total || 0,
        pageSize: result.size || pageSize
      }))
    } catch (e) {
      message.error('加载告警列表失败')
    } finally {
      setLoading(false)
    }
  }, [levelFilter, statusFilter, deviceIdFilter, keywordFilter, dateRange, pagination.current, pagination.pageSize])

  const fetchStats = useCallback(async () => {
    try {
      const result = await getAlertStatistics()
      setStats(result || {})
    } catch (e) {
      console.error('加载统计数据失败', e)
    }
  }, [])

  const fetchNotifyConfigs = useCallback(async () => {
    try {
      const result = await getNotifyConfigList()
      setNotifyConfigs(result || [])
    } catch (e) {
      console.error('加载通知配置失败', e)
    }
  }, [])

  useEffect(() => {
    fetchData()
    fetchStats()
    fetchNotifyConfigs()
  }, [])

  useEffect(() => {
    fetchData(1)
    fetchStats()
  }, [levelFilter, statusFilter, deviceIdFilter, keywordFilter, dateRange])

  const handleAcknowledge = async (id) => {
    try {
      await acknowledgeAlert(id)
      message.success('告警已确认')
      fetchData()
      fetchStats()
    } catch (e) {
      message.error('确认失败')
    }
  }

  const handleClear = async (id) => {
    try {
      await clearAlert(id)
      message.success('告警已清除')
      fetchData()
      fetchStats()
    } catch (e) {
      message.error('清除失败')
    }
  }

  const handleBatchAcknowledge = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要确认的告警')
      return
    }
    try {
      await batchAcknowledge(selectedRowKeys)
      message.success(`已确认 ${selectedRowKeys.length} 条告警`)
      setSelectedRowKeys([])
      fetchData()
      fetchStats()
    } catch (e) {
      message.error('批量确认失败')
    }
  }

  const handleBatchClear = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要清除的告警')
      return
    }
    try {
      await batchClear(selectedRowKeys)
      message.success(`已清除 ${selectedRowKeys.length} 条告警`)
      setSelectedRowKeys([])
      fetchData()
      fetchStats()
    } catch (e) {
      message.error('批量清除失败')
    }
  }

  const handleReset = () => {
    setLevelFilter(null)
    setStatusFilter(null)
    setDeviceIdFilter('')
    setKeywordFilter('')
    setDateRange(null)
    setSelectedRowKeys([])
  }

  const handleRefresh = () => {
    fetchData()
    fetchStats()
  }

  const handleSaveConfig = async () => {
    try {
      const values = await configForm.validateFields()
      const configData = {
        ...editingConfig,
        ...values,
        config: JSON.stringify(values.configData || {})
      }
      delete configData.configData

      if (configData.id) {
        await updateNotifyConfig(configData)
        message.success('通知配置已更新')
      } else {
        await saveNotifyConfig(configData)
        message.success('通知配置已创建')
      }
      setConfigModalVisible(false)
      setEditingConfig(null)
      fetchNotifyConfigs()
    } catch (e) {
      console.error('保存通知配置失败', e)
    }
  }

  const openConfigModal = (config = null) => {
    if (config) {
      setEditingConfig(config)
      const configData = config.config ? JSON.parse(config.config) : {}
      configForm.setFieldsValue({
        name: config.name,
        channel: config.channel,
        enabledLevels: config.enabledLevels ? config.enabledLevels.split(',') : [],
        enabled: config.enabled === 1,
        configData
      })
    } else {
      setEditingConfig(null)
      configForm.resetFields()
    }
    setConfigModalVisible(true)
  }

  const showDetail = (record) => {
    setCurrentAlert(record)
    setDetailDrawerVisible(true)
  }

  const levelChartData = useMemo(() => {
    const chartData = [
      { level: '严重', count: stats.criticalCount || 0, color: '#ff4d4f' },
      { level: '警告', count: stats.warningCount || 0, color: '#faad14' },
      { level: '提示', count: stats.infoCount || 0, color: '#1677ff' }
    ]
    return chartData
  }, [stats])

  const statusChartData = useMemo(() => {
    return [
      { status: '待处理', count: stats.pendingCount || 0, color: '#faad14' },
      { status: '已确认', count: stats.acknowledgedCount || 0, color: '#1677ff' },
      { status: '已清除', count: stats.clearedCount || 0, color: '#52c41a' }
    ]
  }, [stats])

  const renderBarChart = (data, labelKey, valueKey, colorKey) => {
    const maxCount = Math.max(...data.map(d => d[valueKey]), 1)
    return (
      <div style={{ padding: '8px 0' }}>
        {data.map((item, index) => (
          <div key={index} style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <Text style={{ fontSize: 13 }}>{item[labelKey]}</Text>
              <Text strong style={{ color: item[colorKey] }}>{item[valueKey]}</Text>
            </div>
            <div style={{
              height: 8,
              borderRadius: 4,
              background: '#f0f0f0',
              overflow: 'hidden'
            }}>
              <div style={{
                height: '100%',
                borderRadius: 4,
                background: item[colorKey],
                width: `${(item[valueKey] / maxCount) * 100}%`,
                transition: 'width 0.5s ease',
                minWidth: item[valueKey] > 0 ? 8 : 0
              }} />
            </div>
          </div>
        ))}
      </div>
    )
  }

  const columns = [
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 80,
      fixed: 'left',
      render: (level) => {
        const cfg = LEVEL_CONFIG[level] || LEVEL_CONFIG.info
        return (
          <Tag icon={cfg.icon} color={cfg.tagColor} style={{ margin: 0 }}>
            {cfg.label}
          </Tag>
        )
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status) => {
        const cfg = STATUS_CONFIG[status] || STATUS_CONFIG.pending
        return <Tag color={cfg.tagColor}>{cfg.label}</Tag>
      }
    },
    {
      title: '告警消息',
      dataIndex: 'message',
      key: 'message',
      width: 240,
      ellipsis: { showTitle: false },
      render: (text, record) => (
        <Tooltip title={text}>
          <span
            style={{ cursor: 'pointer', color: '#1677ff' }}
            onClick={() => showDetail(record)}
          >
            {text}
          </span>
        </Tooltip>
      )
    },
    {
      title: '关联规则',
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 150,
      ellipsis: true,
      render: (text) => text || <span style={{ color: '#bfbfbf' }}>-</span>
    },
    {
      title: '关联设备',
      dataIndex: 'deviceId',
      key: 'deviceId',
      width: 130,
      render: (text) => text ? <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{text}</span> : <span style={{ color: '#bfbfbf' }}>-</span>
    },
    {
      title: '通知渠道',
      dataIndex: 'notifyChannels',
      key: 'notifyChannels',
      width: 130,
      render: (channels, record) => {
        if (!channels) return <span style={{ color: '#bfbfbf' }}>-</span>
        const channelList = channels.split(',').filter(Boolean)
        return (
          <Space size={4}>
            {channelList.map(ch => {
              const cfg = CHANNEL_CONFIG[ch]
              return cfg ? (
                <Tooltip key={ch} title={cfg.label}>
                  <Tag icon={cfg.icon} style={{ margin: 0, color: cfg.color, borderColor: cfg.color }}>
                    {cfg.label}
                  </Tag>
                </Tooltip>
              ) : null
            })}
            {record.notifyStatus === 3 && <Tag color="error" style={{ margin: 0 }}>发送失败</Tag>}
          </Space>
        )
      }
    },
    {
      title: '告警时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (text) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => showDetail(record)} />
          </Tooltip>
          {record.status === 'pending' && (
            <Popconfirm title="确认此告警？" onConfirm={() => handleAcknowledge(record.id)} okText="确认" cancelText="取消">
              <Button type="link" size="small" icon={<CheckCircleOutlined />} style={{ color: '#1677ff' }}>
                确认
              </Button>
            </Popconfirm>
          )}
          {record.status !== 'cleared' && (
            <Popconfirm title="清除此告警？" onConfirm={() => handleClear(record.id)} okText="清除" cancelText="取消">
              <Button type="link" size="small" icon={<ClearOutlined />} style={{ color: '#52c41a' }}>
                清除
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ]

  const renderConfigForm = () => {
    const channel = configForm.getFieldValue('channel') || editingConfig?.channel
    return (
      <Form form={configForm} layout="vertical">
        <Form.Item name="name" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
          <Input placeholder="请输入配置名称" />
        </Form.Item>
        <Form.Item name="channel" label="通知渠道" rules={[{ required: true, message: '请选择通知渠道' }]}>
          <Select placeholder="请选择通知渠道" disabled={!!editingConfig}>
            <Option value="dingtalk">
              <DingtalkOutlined style={{ marginRight: 8, color: '#1677ff' }} />
              钉钉
            </Option>
            <Option value="wecom">
              <MessageOutlined style={{ marginRight: 8, color: '#07c160' }} />
              企业微信
            </Option>
            <Option value="email">
              <MailOutlined style={{ marginRight: 8, color: '#fa8c16' }} />
              邮件
            </Option>
          </Select>
        </Form.Item>
        <Form.Item name="enabledLevels" label="启用通知的告警级别">
          <Select mode="multiple" placeholder="请选择告警级别">
            <Option value="critical">严重</Option>
            <Option value="warning">警告</Option>
            <Option value="info">提示</Option>
          </Select>
        </Form.Item>
        <Form.Item name="enabled" label="是否启用" valuePropName="checked">
          <Switch />
        </Form.Item>

        {(channel === 'dingtalk' || (!editingConfig && !channel)) && (
          <>
            <Divider orientation="left" plain style={{ fontSize: 13 }}>钉钉配置</Divider>
            <Form.Item name={['configData', 'webhookUrl']} label="Webhook URL">
              <Input placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
            </Form.Item>
            <Form.Item name={['configData', 'secret']} label="加签密钥">
              <Input placeholder="SEC... (可选)" />
            </Form.Item>
            <Form.Item name={['configData', 'messageType']} label="消息类型">
              <Select placeholder="请选择消息类型">
                <Option value="markdown">Markdown</Option>
                <Option value="text">纯文本</Option>
              </Select>
            </Form.Item>
          </>
        )}

        {channel === 'wecom' && (
          <>
            <Divider orientation="left" plain style={{ fontSize: 13 }}>企业微信配置</Divider>
            <Form.Item name={['configData', 'webhookUrl']} label="Webhook URL">
              <Input placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..." />
            </Form.Item>
          </>
        )}

        {channel === 'email' && (
          <>
            <Divider orientation="left" plain style={{ fontSize: 13 }}>邮件配置</Divider>
            <Form.Item name={['configData', 'host']} label="SMTP服务器">
              <Input placeholder="smtp.example.com" />
            </Form.Item>
            <Form.Item name={['configData', 'port']} label="端口">
              <Input type="number" placeholder="465" />
            </Form.Item>
            <Form.Item name={['configData', 'username']} label="用户名">
              <Input placeholder="alert@example.com" />
            </Form.Item>
            <Form.Item name={['configData', 'password']} label="密码/授权码">
              <Input.Password placeholder="请输入密码或授权码" />
            </Form.Item>
            <Form.Item name={['configData', 'from']} label="发件人地址">
              <Input placeholder="alert@example.com" />
            </Form.Item>
            <Form.Item name={['configData', 'to']} label="收件人地址">
              <Input placeholder="admin@example.com (多个用逗号分隔)" />
            </Form.Item>
          </>
        )}
      </Form>
    )
  }

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div style={{
                width: 44, height: 44, borderRadius: 10,
                background: '#fff2f0', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                color: '#ff4d4f', fontSize: 20
              }}>
                <ExclamationCircleOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>严重告警</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#ff4d4f' }}>
                  {stats.criticalCount || 0}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div style={{
                width: 44, height: 44, borderRadius: 10,
                background: '#fffbe6', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                color: '#faad14', fontSize: 20
              }}>
                <WarningOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>待处理</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#faad14' }}>
                  {stats.pendingCount || 0}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div style={{
                width: 44, height: 44, borderRadius: 10,
                background: '#e6f4ff', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                color: '#1677ff', fontSize: 20
              }}>
                <CheckCircleOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>已确认</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#1677ff' }}>
                  {stats.acknowledgedCount || 0}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div style={{
                width: 44, height: 44, borderRadius: 10,
                background: '#f6ffed', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                color: '#52c41a', fontSize: 20
              }}>
                <AlertOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>今日告警</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#52c41a' }}>
                  {stats.todayCount || 0}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card className="log-search-bar" bordered={false}>
        <Row gutter={[16, 12]} align="bottom">
          <Col xs={24} sm={12} md={4}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>告警级别</div>
            <Select
              placeholder="全部"
              style={{ width: '100%' }}
              value={levelFilter}
              onChange={setLevelFilter}
              allowClear
            >
              <Option value="critical">严重</Option>
              <Option value="warning">警告</Option>
              <Option value="info">提示</Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>告警状态</div>
            <Select
              placeholder="全部"
              style={{ width: '100%' }}
              value={statusFilter}
              onChange={setStatusFilter}
              allowClear
            >
              <Option value="pending">待处理</Option>
              <Option value="acknowledged">已确认</Option>
              <Option value="cleared">已清除</Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>设备ID</div>
            <Input
              placeholder="请输入设备ID"
              value={deviceIdFilter}
              onChange={(e) => setDeviceIdFilter(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={4}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>关键词</div>
            <Input
              placeholder="搜索告警消息/规则"
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              value={keywordFilter}
              onChange={(e) => setKeywordFilter(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>时间范围</div>
            <RangePicker
              style={{ width: '100%' }}
              value={dateRange}
              onChange={setDateRange}
              showTime
              allowClear
            />
          </Col>
          <Col xs={24} md={3}>
            <Space>
              <Button onClick={handleReset}>重置</Button>
              <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading} />
              </Tooltip>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card
        bordered={false}
        style={{ borderRadius: 10 }}
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          tabBarExtraContent={
            <Space>
              {selectedRowKeys.length > 0 && (
                <>
                  <Button
                    icon={<CheckCircleOutlined />}
                    onClick={handleBatchAcknowledge}
                    style={{ color: '#1677ff', borderColor: '#1677ff' }}
                  >
                    批量确认 ({selectedRowKeys.length})
                  </Button>
                  <Button
                    icon={<ClearOutlined />}
                    onClick={handleBatchClear}
                    style={{ color: '#52c41a', borderColor: '#52c41a' }}
                  >
                    批量清除 ({selectedRowKeys.length})
                  </Button>
                </>
              )}
              <Button
                icon={<SettingOutlined />}
                onClick={() => openConfigModal(null)}
              >
                通知配置
              </Button>
            </Space>
          }
          items={[
            {
              key: 'list',
              label: (
                <Space>
                  <AlertOutlined />
                  告警列表
                  <Badge count={stats.pendingCount || 0} size="small" />
                </Space>
              ),
              children: (
                <Table
                  columns={columns}
                  dataSource={data}
                  rowKey="id"
                  loading={loading}
                  scroll={{ x: 1200 }}
                  rowSelection={{
                    selectedRowKeys,
                    onChange: setSelectedRowKeys,
                    getCheckboxProps: (record) => ({
                      disabled: record.status === 'cleared'
                    })
                  }}
                  pagination={{
                    ...pagination,
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 条记录`,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    onChange: (page, pageSize) => fetchData(page, pageSize)
                  }}
                />
              )
            },
            {
              key: 'charts',
              label: (
                <Space>
                  <PieChartOutlined />
                  告警图表
                </Space>
              ),
              children: (
                <Row gutter={[24, 24]} style={{ padding: '16px 0' }}>
                  <Col xs={24} md={12}>
                    <Card title="告警级别分布" bordered={false} style={{ borderRadius: 8 }}>
                      {renderBarChart(levelChartData, 'level', 'count', 'color')}
                      <Divider style={{ margin: '12px 0' }} />
                      <Row gutter={16}>
                        <Col span={8}>
                          <Statistic
                            title="严重"
                            value={stats.criticalCount || 0}
                            valueStyle={{ color: '#ff4d4f', fontSize: 18 }}
                          />
                        </Col>
                        <Col span={8}>
                          <Statistic
                            title="警告"
                            value={stats.warningCount || 0}
                            valueStyle={{ color: '#faad14', fontSize: 18 }}
                          />
                        </Col>
                        <Col span={8}>
                          <Statistic
                            title="提示"
                            value={stats.infoCount || 0}
                            valueStyle={{ color: '#1677ff', fontSize: 18 }}
                          />
                        </Col>
                      </Row>
                    </Card>
                  </Col>
                  <Col xs={24} md={12}>
                    <Card title="告警状态分布" bordered={false} style={{ borderRadius: 8 }}>
                      {renderBarChart(statusChartData, 'status', 'count', 'color')}
                      <Divider style={{ margin: '12px 0' }} />
                      <Row gutter={16}>
                        <Col span={8}>
                          <Statistic
                            title="待处理"
                            value={stats.pendingCount || 0}
                            valueStyle={{ color: '#faad14', fontSize: 18 }}
                          />
                        </Col>
                        <Col span={8}>
                          <Statistic
                            title="已确认"
                            value={stats.acknowledgedCount || 0}
                            valueStyle={{ color: '#1677ff', fontSize: 18 }}
                          />
                        </Col>
                        <Col span={8}>
                          <Statistic
                            title="已清除"
                            value={stats.clearedCount || 0}
                            valueStyle={{ color: '#52c41a', fontSize: 18 }}
                          />
                        </Col>
                      </Row>
                    </Card>
                  </Col>
                  <Col xs={24}>
                    <Card title="告警总览" bordered={false} style={{ borderRadius: 8 }}>
                      <Row gutter={24}>
                        <Col span={6}>
                          <Statistic title="告警总数" value={stats.totalCount || 0} />
                        </Col>
                        <Col span={6}>
                          <Statistic title="今日新增" value={stats.todayCount || 0} valueStyle={{ color: '#1677ff' }} />
                        </Col>
                        <Col span={6}>
                          <Statistic
                            title="处理率"
                            value={stats.totalCount > 0
                              ? (((stats.acknowledgedCount || 0) + (stats.clearedCount || 0)) / stats.totalCount * 100).toFixed(1)
                              : 0}
                            suffix="%"
                            valueStyle={{ color: '#52c41a' }}
                          />
                        </Col>
                        <Col span={6}>
                          <Statistic
                            title="严重告警占比"
                            value={stats.totalCount > 0
                              ? ((stats.criticalCount || 0) / stats.totalCount * 100).toFixed(1)
                              : 0}
                            suffix="%"
                            valueStyle={{ color: '#ff4d4f' }}
                          />
                        </Col>
                      </Row>
                    </Card>
                  </Col>
                </Row>
              )
            }
          ]}
        />
      </Card>

      <Drawer
        title="告警详情"
        placement="right"
        width={480}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
      >
        {currentAlert && (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="告警ID">{currentAlert.id}</Descriptions.Item>
              <Descriptions.Item label="级别">
                {(() => {
                  const cfg = LEVEL_CONFIG[currentAlert.level] || LEVEL_CONFIG.info
                  return <Tag icon={cfg.icon} color={cfg.tagColor}>{cfg.label}</Tag>
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {(() => {
                  const cfg = STATUS_CONFIG[currentAlert.status] || STATUS_CONFIG.pending
                  return <Tag color={cfg.tagColor}>{cfg.label}</Tag>
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="告警消息">{currentAlert.message}</Descriptions.Item>
              <Descriptions.Item label="关联规则">
                {currentAlert.ruleName || '-'}
                {currentAlert.ruleId && (
                  <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                    (ID: {currentAlert.ruleId})
                  </Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="关联设备">
                {currentAlert.deviceId || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="通知渠道">
                {currentAlert.notifyChannels || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="告警时间">
                {currentAlert.createTime ? dayjs(currentAlert.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>
              {currentAlert.acknowledgedBy && (
                <Descriptions.Item label="确认人">{currentAlert.acknowledgedBy}</Descriptions.Item>
              )}
              {currentAlert.acknowledgedTime && (
                <Descriptions.Item label="确认时间">
                  {dayjs(currentAlert.acknowledgedTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
              )}
              {currentAlert.clearedBy && (
                <Descriptions.Item label="清除人">{currentAlert.clearedBy}</Descriptions.Item>
              )}
              {currentAlert.clearedTime && (
                <Descriptions.Item label="清除时间">
                  {dayjs(currentAlert.clearedTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
              )}
            </Descriptions>

            {currentAlert.detail && (
              <>
                <Divider orientation="left" style={{ marginTop: 20 }}>详细信息</Divider>
                <div className="action-params-json" style={{ maxHeight: 200, overflow: 'auto' }}>
                  {(() => {
                    try {
                      return JSON.stringify(JSON.parse(currentAlert.detail), null, 2)
                    } catch {
                      return currentAlert.detail
                    }
                  })()}
                </div>
              </>
            )}

            <Divider orientation="left" style={{ marginTop: 20 }}>操作</Divider>
            <Space>
              {currentAlert.status === 'pending' && (
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={() => {
                    handleAcknowledge(currentAlert.id)
                    setDetailDrawerVisible(false)
                  }}
                >
                  确认告警
                </Button>
              )}
              {currentAlert.status !== 'cleared' && (
                <Button
                  icon={<ClearOutlined />}
                  onClick={() => {
                    handleClear(currentAlert.id)
                    setDetailDrawerVisible(false)
                  }}
                >
                  清除告警
                </Button>
              )}
            </Space>
          </div>
        )}
      </Drawer>

      <Modal
        title={editingConfig ? '编辑通知配置' : '新增通知配置'}
        open={configModalVisible}
        onOk={handleSaveConfig}
        onCancel={() => { setConfigModalVisible(false); setEditingConfig(null) }}
        width={600}
        destroyOnClose
      >
        {renderConfigForm()}
      </Modal>

      <Modal
        title="告警通知渠道配置"
        open={false}
        footer={null}
        width={700}
      >
        <Row gutter={[16, 16]}>
          {notifyConfigs.map(config => {
            const channelCfg = CHANNEL_CONFIG[config.channel] || {}
            return (
              <Col xs={24} sm={12} key={config.id}>
                <Card
                  size="small"
                  title={
                    <Space>
                      {channelCfg.icon}
                      <span>{config.name}</span>
                      {config.enabled === 1 ? (
                        <Tag color="success" style={{ margin: 0 }}>已启用</Tag>
                      ) : (
                        <Tag color="default" style={{ margin: 0 }}>已禁用</Tag>
                      )}
                    </Space>
                  }
                  extra={
                    <Button type="link" size="small" onClick={() => openConfigModal(config)}>
                      编辑
                    </Button>
                  }
                  style={{ borderRadius: 8 }}
                >
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      通知级别: {config.enabledLevels || '全部'}
                    </Text>
                  </div>
                </Card>
              </Col>
            )
          })}
          <Col xs={24} sm={12}>
            <Card
              size="small"
              style={{
                borderRadius: 8,
                border: '2px dashed #d9d9d9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                minHeight: 80,
                cursor: 'pointer'
              }}
              onClick={() => openConfigModal(null)}
            >
              <Space style={{ color: '#8c8c8c' }}>
                <SettingOutlined />
                <span>添加通知渠道</span>
              </Space>
            </Card>
          </Col>
        </Row>
      </Modal>
    </div>
  )
}

export default AlertCenter
