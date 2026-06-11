import { useState, useMemo } from 'react'
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
  Typography
} from 'antd'
import {
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  SearchOutlined,
  RetweetOutlined,
  FileTextOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'

const { Title } = Typography
const { RangePicker } = DatePicker
const { Option } = Select

const generateMockLogs = () => {
  const actions = ['turn_on_light', 'turn_off_light', 'turn_on_aircon', 'send_alert', 'send_notification']
  const actionNames = {
    turn_on_light: '开灯',
    turn_off_light: '关灯',
    turn_on_aircon: '开空调',
    send_alert: '推送告警',
    send_notification: '发送通知'
  }
  const rules = [
    { id: 'R001', name: '高温告警规则' },
    { id: 'R002', name: '设备离线告警' },
    { id: 'R003', name: '湿度异常处理' },
    { id: 'R004', name: '烟雾报警联动' },
    { id: 'R005', name: '定时数据采集' }
  ]
  const devices = ['DEV001', 'DEV002', 'DEV003', 'DEV004', 'DEV005', 'DEV006', 'DEV007', 'DEV008']
  const logs = []

  for (let i = 1; i <= 56; i++) {
    const rule = rules[Math.floor(Math.random() * rules.length)]
    const action = actions[Math.floor(Math.random() * actions.length)]
    const isSuccess = Math.random() > 0.25
    const retryCount = isSuccess ? 0 : Math.floor(Math.random() * 3)
    const hoursAgo = Math.floor(Math.random() * 72)

    logs.push({
      id: `LOG${String(i).padStart(6, '0')}`,
      ruleId: rule.id,
      ruleName: rule.name,
      actionType: action,
      actionName: actionNames[action],
      deviceId: devices[Math.floor(Math.random() * devices.length)],
      params: {
        temperature: Math.floor(Math.random() * 30) + 10,
        level: ['info', 'warning', 'error'][Math.floor(Math.random() * 3)],
        message: '设备状态变更触发'
      },
      result: isSuccess ? 'success' : 'failed',
      retryCount,
      errorMessage: isSuccess
        ? ''
        : ['连接超时', '设备离线', '参数错误', '权限不足'][Math.floor(Math.random() * 4)],
      executeTime: dayjs().subtract(hoursAgo, 'hour').format('YYYY-MM-DD HH:mm:ss'),
      executeDuration: Math.floor(Math.random() * 2000) + 100
    })
  }

  return logs.sort((a, b) => dayjs(b.executeTime).valueOf() - dayjs(a.executeTime).valueOf())
}

const mockLogs = generateMockLogs()

function ActionLog() {
  const [data, setData] = useState(mockLogs)
  const [loading, setLoading] = useState(false)
  const [ruleIdSearch, setRuleIdSearch] = useState('')
  const [deviceIdSearch, setDeviceIdSearch] = useState('')
  const [resultFilter, setResultFilter] = useState(null)
  const [dateRange, setDateRange] = useState(null)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: mockLogs.length
  })

  const stats = useMemo(() => {
    const total = data.length
    const success = data.filter((item) => item.result === 'success').length
    const failed = total - success
    const successRate = total > 0 ? ((success / total) * 100).toFixed(1) : '0.0'
    return { total, success, failed, successRate }
  }, [data])

  const filteredData = useMemo(() => {
    return data.filter((item) => {
      if (ruleIdSearch && !item.ruleId.toLowerCase().includes(ruleIdSearch.toLowerCase())) {
        return false
      }
      if (
        deviceIdSearch &&
        !item.deviceId.toLowerCase().includes(deviceIdSearch.toLowerCase())
      ) {
        return false
      }
      if (resultFilter && item.result !== resultFilter) {
        return false
      }
      if (dateRange && dateRange.length === 2) {
        const itemTime = dayjs(item.executeTime)
        if (
          itemTime.isBefore(dayjs(dateRange[0]).startOf('day')) ||
          itemTime.isAfter(dayjs(dateRange[1]).endOf('day'))
        ) {
          return false
        }
      }
      return true
    })
  }, [data, ruleIdSearch, deviceIdSearch, resultFilter, dateRange])

  const handleRetry = (record) => {
    message.loading({ content: `正在重试 ${record.id}...`, key: 'retry-log' })
    setTimeout(() => {
      setData((prev) =>
        prev.map((item) =>
          item.id === record.id
            ? {
                ...item,
                result: 'success',
                retryCount: item.retryCount + 1,
                errorMessage: '',
                executeTime: dayjs().format('YYYY-MM-DD HH:mm:ss')
              }
            : item
        )
      )
      message.success({ content: `日志 ${record.id} 重试成功`, key: 'retry-log' })
    }, 1000)
  }

  const handleRefresh = () => {
    setLoading(true)
    setTimeout(() => {
      setLoading(false)
      message.success('日志列表已刷新')
    }, 600)
  }

  const handleSearch = () => {
    setPagination({ ...pagination, current: 1, total: filteredData.length })
  }

  const handleReset = () => {
    setRuleIdSearch('')
    setDeviceIdSearch('')
    setResultFilter(null)
    setDateRange(null)
    setPagination({ current: 1, pageSize: 10, total: data.length })
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 120,
      fixed: 'left',
      render: (text) => (
        <span style={{ fontFamily: 'monospace', color: '#1677ff' }}>{text}</span>
      )
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 140,
      render: (text, record) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontWeight: 500 }}>{text}</span>
          <span style={{ fontSize: 12, color: '#8c8c8c', fontFamily: 'monospace' }}>
            {record.ruleId}
          </span>
        </Space>
      )
    },
    {
      title: '动作类型',
      dataIndex: 'actionName',
      key: 'actionName',
      width: 100,
      render: (text, record) => (
        <Tag color="blue" style={{ margin: 0 }}>
          {text}
        </Tag>
      )
    },
    {
      title: '设备ID',
      dataIndex: 'deviceId',
      key: 'deviceId',
      width: 100,
      render: (text) => <span style={{ fontFamily: 'monospace' }}>{text}</span>
    },
    {
      title: '参数',
      dataIndex: 'params',
      key: 'params',
      width: 260,
      render: (params) => (
        <div className="action-params-json">{JSON.stringify(params, null, 2)}</div>
      )
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      width: 90,
      render: (result) =>
        result === 'success' ? (
          <Tag
            icon={<CheckCircleOutlined />}
            color="success"
            style={{ padding: '2px 10px', margin: 0 }}
          >
            成功
          </Tag>
        ) : (
          <Tag
            icon={<CloseCircleOutlined />}
            color="error"
            style={{ padding: '2px 10px', margin: 0 }}
          >
            失败
          </Tag>
        )
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 90,
      align: 'center',
      render: (count) => (
        <span style={{ color: count > 0 ? '#fa8c16' : '#8c8c8c', fontWeight: 500 }}>
          {count}
        </span>
      )
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      width: 120,
      ellipsis: { showTitle: false },
      render: (text) =>
        text ? (
          <Tooltip title={text}>
            <span style={{ color: '#ff4d4f' }}>{text}</span>
          </Tooltip>
        ) : (
          <span style={{ color: '#bfbfbf' }}>-</span>
        )
    },
    {
      title: '执行时间',
      dataIndex: 'executeTime',
      key: 'executeTime',
      width: 170
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_, record) =>
        record.result === 'failed' ? (
          <Popconfirm
            title="确认重试此执行？"
            description={`将重新执行日志 ${record.id}`}
            onConfirm={() => handleRetry(record)}
            okText="确认重试"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              icon={<RetweetOutlined />}
              style={{ padding: 0 }}
            >
              重试
            </Button>
          </Popconfirm>
        ) : (
          <span style={{ color: '#bfbfbf' }}>-</span>
        )
    }
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 10,
                  background: '#e6f4ff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#1677ff',
                  fontSize: 20
                }}
              >
                <FileTextOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>执行总数</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#1677ff' }}>
                  {stats.total}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 10,
                  background: '#f6ffed',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#52c41a',
                  fontSize: 20
                }}
              >
                <CheckCircleOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>成功</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#52c41a' }}>
                  {stats.success}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 10,
                  background: '#fff2f0',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#ff4d4f',
                  fontSize: 20
                }}
              >
                <CloseCircleOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>失败</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#ff4d4f' }}>
                  {stats.failed}
                </div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card className="stat-card" bordered={false}>
            <Space align="center" size={12}>
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 10,
                  background: '#fffbe6',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#faad14',
                  fontSize: 20
                }}
              >
                <ExclamationCircleOutlined />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>成功率</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#faad14' }}>
                  {stats.successRate}%
                </div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card className="log-search-bar" bordered={false}>
        <Row gutter={[16, 12]} align="bottom">
          <Col xs={24} sm={12} md={5}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>规则ID</div>
            <Input
              placeholder="请输入规则ID"
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              value={ruleIdSearch}
              onChange={(e) => setRuleIdSearch(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>设备ID</div>
            <Input
              placeholder="请输入设备ID"
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              value={deviceIdSearch}
              onChange={(e) => setDeviceIdSearch(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={4}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>执行结果</div>
            <Select
              placeholder="全部"
              style={{ width: '100%' }}
              value={resultFilter}
              onChange={setResultFilter}
              allowClear
            >
              <Option value="success">成功</Option>
              <Option value="failed">失败</Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>时间范围</div>
            <RangePicker
              style={{ width: '100%' }}
              value={dateRange}
              onChange={setDateRange}
              allowClear
            />
          </Col>
          <Col xs={24} md={4}>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
              <Tooltip title="刷新列表">
                <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading} />
              </Tooltip>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card
        bordered={false}
        title={
          <Space>
            <Title level={5} style={{ margin: 0 }}>
              执行日志
            </Title>
            <Tag color="blue" style={{ marginLeft: 8 }}>
              共 {filteredData.length} 条
            </Tag>
          </Space>
        }
        style={{ borderRadius: 10 }}
      >
        <Table
          columns={columns}
          dataSource={filteredData}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            ...pagination,
            total: filteredData.length,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (page, pageSize) =>
              setPagination({ ...pagination, current: page, pageSize })
          }}
        />
      </Card>
    </div>
  )
}

export default ActionLog
