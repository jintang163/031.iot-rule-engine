import { useState, useEffect, useMemo } from 'react'
import {
  Card, Row, Col, Statistic, DatePicker, Button, Table, Tag,
  Typography, Space, Dropdown, Progress, Alert, Empty, message, Tooltip, Divider
} from 'antd'
import {
  ThunderboltOutlined, RocketOutlined, ClockCircleOutlined,
  DollarOutlined, DownloadOutlined, FileExcelOutlined, FileJsonOutlined,
  DashboardOutlined, BulbOutlined, RiseOutlined, WarningOutlined,
  InfoCircleOutlined, AlertOutlined, CheckCircleOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { getStatsOverview, exportStatsCsv, exportStatsJson, downloadBlob } from '../services/statsApi'

const { Title, Text, Paragraph } = Typography
const { RangePicker } = DatePicker

const levelIconMap = {
  high: <WarningOutlined style={{ color: '#ff4d4f' }} />,
  medium: <AlertOutlined style={{ color: '#faad14' }} />,
  info: <InfoCircleOutlined style={{ color: '#1890ff' }} />
}

const levelColorMap = {
  high: 'red',
  medium: 'orange',
  info: 'blue'
}

function CostBreakdownChart({ data }) {
  if (!data) return null
  const items = [
    { key: 'airConditioning', label: '空调', value: data.airConditioning || 0, color: '#1890ff' },
    { key: 'lighting', label: '照明', value: data.lighting || 0, color: '#faad14' },
    { key: 'heating', label: '采暖', value: data.heating || 0, color: '#eb2f96' },
    { key: 'other', label: '其他', value: data.other || 0, color: '#8c8c8c' }
  ]
  const total = items.reduce((s, i) => s + Number(i.value), 0) || 1

  return (
    <div>
      <div style={{ height: 14, display: 'flex', borderRadius: 7, overflow: 'hidden', marginBottom: 12 }}>
        {items.map(i => (
          <div
            key={i.key}
            title={`${i.label} ¥${Number(i.value).toFixed(2)} (${((Number(i.value) / total) * 100).toFixed(1)}%)`}
            style={{
              background: i.color,
              width: `${(Number(i.value) / total) * 100}%`,
              minWidth: Number(i.value) > 0 ? 3 : 0
            }}
          />
        ))}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
        {items.map(i => (
          <div key={i.key} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 10, height: 10, background: i.color, borderRadius: 2, display: 'inline-block' }} />
            <Text style={{ fontSize: 12, color: '#595959' }}>{i.label}</Text>
            <Text strong style={{ fontSize: 12 }}>¥{Number(i.value).toFixed(2)}</Text>
          </div>
        ))}
      </div>
    </div>
  )
}

function DailyTrendChart({ data }) {
  if (!data || data.length === 0) {
    return <Empty description="暂无趋势数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }
  const maxTriggers = Math.max(...data.map(d => d.triggerCount || 0), 1)
  const maxCost = Math.max(...data.map(d => Number(d.estimatedCostYuan) || 0), 0.01)

  return (
    <div style={{ padding: '12px 4px 4px 4px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 140 }}>
        {data.map((d, idx) => (
          <div key={idx} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
            <div style={{ height: 110, display: 'flex', alignItems: 'flex-end', gap: 2 }}>
              <Tooltip title={`触发: ${d.triggerCount || 0}`}>
                <div
                  style={{
                    width: 10,
                    background: 'linear-gradient(180deg, #69b1ff 0%, #1677ff 100%)',
                    borderRadius: '2px 2px 0 0',
                    height: `${((d.triggerCount || 0) / maxTriggers) * 100}%`,
                    minHeight: d.triggerCount > 0 ? 3 : 0
                  }}
                />
              </Tooltip>
              <Tooltip title={`电费: ¥${Number(d.estimatedCostYuan || 0).toFixed(2)}`}>
                <div
                  style={{
                    width: 10,
                    background: 'linear-gradient(180deg, #ffd666 0%, #faad14 100%)',
                    borderRadius: '2px 2px 0 0',
                    height: `${(Number(d.estimatedCostYuan || 0) / maxCost) * 100}%`,
                    minHeight: Number(d.estimatedCostYuan || 0) > 0 ? 3 : 0
                  }}
                />
              </Tooltip>
            </div>
            <Text style={{ fontSize: 10, color: '#8c8c8c' }}>{(d.date || '').slice(5)}</Text>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 16, marginTop: 8, justifyContent: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ width: 10, height: 10, background: '#1677ff', borderRadius: 2 }} />
          <Text style={{ fontSize: 11, color: '#595959' }}>触发次数</Text>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ width: 10, height: 10, background: '#faad14', borderRadius: 2 }} />
          <Text style={{ fontSize: 11, color: '#595959' }}>预估电费</Text>
        </div>
      </div>
    </div>
  )
}

function RuleStatsPage() {
  const [loading, setLoading] = useState(false)
  const [overview, setOverview] = useState(null)
  const [dateRange, setDateRange] = useState([dayjs().subtract(7, 'day'), dayjs()])

  const fetchData = async () => {
    setLoading(true)
    try {
      const params = {}
      if (dateRange && dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange && dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')
      const res = await getStatsOverview(params)
      setOverview(res)
    } catch (err) {
      message.error('获取统计数据失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [dateRange])

  const handleExportCsv = async () => {
    try {
      const params = {}
      if (dateRange && dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange && dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')
      const blob = await exportStatsCsv(params)
      downloadBlob(blob, `rule_stats_${dayjs().format('YYYYMMDD_HHmmss')}.csv`)
      message.success('CSV 报表导出成功')
    } catch (err) {
      message.error('CSV 报表导出失败')
    }
  }

  const handleExportJson = async () => {
    try {
      const params = {}
      if (dateRange && dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange && dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')
      const blob = await exportStatsJson(params)
      downloadBlob(blob, `rule_stats_${dayjs().format('YYYYMMDD_HHmmss')}.json`)
      message.success('JSON 报表导出成功')
    } catch (err) {
      message.error('JSON 报表导出失败')
    }
  }

  const ruleColumns = useMemo(() => ([
    {
      title: '规则',
      dataIndex: 'ruleName',
      key: 'ruleName',
      render: (text, record) => (
        <Space>
          <Text strong>{text || `Rule #${record.ruleId}`}</Text>
        </Space>
      )
    },
    {
      title: '触发次数',
      dataIndex: 'triggerCount',
      key: 'triggerCount',
      sorter: (a, b) => (a.triggerCount || 0) - (b.triggerCount || 0),
      defaultSortOrder: 'descend',
      render: v => <Tag color="blue">{v || 0}</Tag>
    },
    {
      title: '动作次数',
      dataIndex: 'actionCount',
      key: 'actionCount',
      sorter: (a, b) => (a.actionCount || 0) - (b.actionCount || 0),
      render: v => <Tag color="purple">{v || 0}</Tag>
    },
    {
      title: '平均耗时',
      dataIndex: 'avgExecutionMs',
      key: 'avgExecutionMs',
      sorter: (a, b) => (Number(a.avgExecutionMs) || 0) - (Number(b.avgExecutionMs) || 0),
      render: v => `${Number(v || 0).toFixed(0)} ms`
    },
    {
      title: '最大耗时',
      dataIndex: 'maxExecutionMs',
      key: 'maxExecutionMs',
      render: v => `${Number(v || 0).toFixed(0)} ms`
    },
    {
      title: '预估电费',
      dataIndex: 'estimatedCostYuan',
      key: 'estimatedCostYuan',
      sorter: (a, b) => (Number(a.estimatedCostYuan) || 0) - (Number(b.estimatedCostYuan) || 0),
      render: v => <span style={{ color: '#fa541c', fontWeight: 600 }}>¥{Number(v || 0).toFixed(2)}</span>
    }
  ]), [])

  return (
    <div style={{ minHeight: '100%' }}>
      <Card
        style={{ marginBottom: 16, borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: '16px 20px' }}
        bordered={false}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              <DashboardOutlined style={{ color: '#1677ff', marginRight: 8 }} />
              规则执行统计与成本分析
            </Title>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {overview?.startDate && overview?.endDate && (
                <span>统计周期: {overview.startDate} ~ {overview.endDate}</span>
              )}
            </Text>
          </div>
          <Space>
            <RangePicker
              value={dateRange}
              onChange={setDateRange}
              allowClear={false}
              ranges={{
                '近7天': [dayjs().subtract(7, 'day'), dayjs()],
                '近30天': [dayjs().subtract(30, 'day'), dayjs()],
                '本月': [dayjs().startOf('month'), dayjs()]
              }}
            />
            <Dropdown
              menu={{
                items: [
                  { key: 'csv', icon: <FileExcelOutlined />, label: '导出 CSV', onClick: handleExportCsv },
                  { key: 'json', icon: <FileJsonOutlined />, label: '导出 JSON', onClick: handleExportJson }
                ]
              }}
              placement="bottomRight"
            >
              <Button type="primary" icon={<DownloadOutlined />}>
                导出报表
              </Button>
            </Dropdown>
          </Space>
        </div>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <Statistic
              title="总触发次数"
              value={overview?.totalTriggers || 0}
              prefix={<ThunderboltOutlined style={{ color: '#1677ff' }} />}
              loading={loading}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <Statistic
              title="总动作次数"
              value={overview?.totalActions || 0}
              prefix={<RocketOutlined style={{ color: '#722ed1' }} />}
              loading={loading}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <Statistic
              title="平均执行耗时"
              value={Number(overview?.avgExecutionMs || 0).toFixed(0)}
              suffix="ms"
              prefix={<ClockCircleOutlined style={{ color: '#fa8c16' }} />}
              loading={loading}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <Statistic
              title="预估电费合计"
              value={Number(overview?.totalCostYuan || 0).toFixed(2)}
              prefix={<DollarOutlined style={{ color: '#fa541c' }} />}
              suffix="元"
              loading={loading}
              valueStyle={{ color: '#fa541c' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card
            bordered={false}
            style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)', height: '100%' }}
            title={<span style={{ fontSize: 14 }}><BulbOutlined style={{ color: '#faad14', marginRight: 6 }} />能耗分类</span>}
            loading={loading}
          >
            {overview?.costBreakdown ? (
              <CostBreakdownChart data={overview.costBreakdown} />
            ) : <Empty description="暂无能耗数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />}

            {overview?.costConfig && (
              <>
                <Divider style={{ margin: '16px 0' }} />
                <Text type="secondary" style={{ fontSize: 11 }}>
                  电价: ¥{Number(overview.costConfig.electricityPricePerKwh).toFixed(2)}/度 · 估算每次触发运行 {Number(overview.costConfig.avgRuntimeMinutesPerTrigger).toFixed(0)} 分钟
                </Text>
              </>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            bordered={false}
            style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)', height: '100%' }}
            title={<span style={{ fontSize: 14 }}><RiseOutlined style={{ color: '#1677ff', marginRight: 6 }} />每日趋势</span>}
            loading={loading}
          >
            <DailyTrendChart data={overview?.dailyTrend} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={16}>
          <Card
            bordered={false}
            style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
            title={<span style={{ fontSize: 14 }}><DashboardOutlined style={{ color: '#722ed1', marginRight: 6 }} />规则执行排名</span>}
            loading={loading}
            bodyStyle={{ padding: 12 }}
          >
            <Table
              rowKey="ruleId"
              size="small"
              columns={ruleColumns}
              dataSource={overview?.ruleRankings || []}
              pagination={{ pageSize: 8, size: 'small' }}
              locale={{ emptyText: '暂无规则执行数据' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card
            bordered={false}
            style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
            title={<span style={{ fontSize: 14 }}><WarningOutlined style={{ color: '#faad14', marginRight: 6 }} />优化建议</span>}
            loading={loading}
          >
            {overview?.optimizationTips && overview.optimizationTips.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {overview.optimizationTips.map((tip, idx) => (
                  <Alert
                    key={idx}
                    type={tip.level === 'high' ? 'error' : tip.level === 'medium' ? 'warning' : 'info'}
                    showIcon
                    icon={levelIconMap[tip.level] || <InfoCircleOutlined />}
                    message={
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <span>
                          <Tag color={levelColorMap[tip.level]} style={{ margin: 0 }}>{tip.title}</Tag>
                          {tip.ruleName && (
                            <Text style={{ fontSize: 12, marginLeft: 6 }}>{tip.ruleName}</Text>
                          )}
                        </span>
                        {tip.estimatedSavings && (
                          <Tooltip title="优化后预计可节省">
                            <Tag color="green" style={{ margin: 0 }}>
                              约省 ¥{Number(tip.estimatedSavings).toFixed(2)}
                            </Tag>
                          </Tooltip>
                        )}
                      </Space>
                    }
                    description={tip.detail}
                    style={{ padding: 8, fontSize: 12 }}
                  />
                ))}
              </div>
            ) : <Empty description="暂无优化建议" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default RuleStatsPage
