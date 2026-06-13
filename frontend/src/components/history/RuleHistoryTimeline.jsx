import { useState, useEffect } from 'react'
import {
  Modal,
  Timeline,
  DatePicker,
  Button,
  Space,
  Card,
  Tag,
  Descriptions,
  Row,
  Col,
  Empty,
  Spin,
  Tooltip,
  Pagination,
  Typography,
  Alert
} from 'antd'
import {
  HistoryOutlined,
  ThunderboltOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  InfoCircleOutlined
} from '@ant-design/icons'
import moment from 'moment'
import { getRuleHistoryList, getRuleHistorySnapshot, isRuleHistoryEnabled } from '../services/ruleHistoryApi'

const { RangePicker } = DatePicker
const { Title, Text, Paragraph } = Typography

function formatTriggerTime(t) {
  if (!t) return '-'
  try {
    return moment(t.replace('T', ' ')).format('YYYY-MM-DD HH:mm:ss.SSS')
  } catch (e) {
    return String(t)
  }
}

function RuleHistoryTimeline({ ruleId, ruleName, open, onClose }) {
  const [loading, setLoading] = useState(false)
  const [historyEnabled, setHistoryEnabled] = useState(true)
  const [historyList, setHistoryList] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [timeRange, setTimeRange] = useState([moment().subtract(7, 'days'), moment()])
  const [selectedItem, setSelectedItem] = useState(null)
  const [snapshotDetail, setSnapshotDetail] = useState(null)
  const [snapshotLoading, setSnapshotLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)

  useEffect(() => {
    if (open) {
      checkEnabled()
      fetchHistory()
    }
  }, [open, ruleId])

  const checkEnabled = async () => {
    try {
      const res = await isRuleHistoryEnabled()
      setHistoryEnabled(res?.enabled === true)
    } catch (e) {
      setHistoryEnabled(false)
    }
  }

  const fetchHistory = async () => {
    if (!ruleId) return
    setLoading(true)
    try {
      const params = {
        page: pagination.current,
        size: pagination.pageSize,
        startTime: timeRange?.[0] ? timeRange[0].format('YYYY-MM-DDTHH:mm:ss') : undefined,
        endTime: timeRange?.[1] ? timeRange[1].format('YYYY-MM-DDTHH:mm:ss') : undefined
      }
      const res = await getRuleHistoryList(ruleId, params)
      const records = res?.records || []
      setHistoryList(records)
      setPagination(prev => ({
        ...prev,
        total: res?.total || 0,
        current: res?.current || prev.current,
        pageSize: res?.size || prev.pageSize
      }))
    } catch (e) {
      console.error('获取规则历史失败:', e)
      setHistoryList([])
    } finally {
      setLoading(false)
    }
  }

  const handleTimeRangeChange = (dates) => {
    setTimeRange(dates)
    setPagination(prev => ({ ...prev, current: 1 }))
    setTimeout(fetchHistory, 0)
  }

  const handlePageChange = (page, pageSize) => {
    setPagination(prev => ({ ...prev, current: page, pageSize }))
    setTimeout(() => fetchHistory(), 0)
  }

  const handleViewSnapshot = async (item) => {
    setSelectedItem(item)
    setDetailOpen(true)
    if (!item?.id) {
      setSnapshotDetail(item)
      return
    }
    setSnapshotLoading(true)
    try {
      const detail = await getRuleHistorySnapshot(item.id)
      setSnapshotDetail(detail || item)
    } catch (e) {
      console.error('获取快照详情失败:', e)
      setSnapshotDetail(item)
    } finally {
      setSnapshotLoading(false)
    }
  }

  const buildSnapshotSummary = (item) => {
    const snap = item?.deviceSnapshot || {}
    return (
      <Row gutter={16}>
        <Col xs={24} sm={12}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="设备ID">
              <Text copyable>{item.deviceId || snap.deviceId || '-'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="温度">
              <Tag color={snap.temperature > 30 ? 'red' : 'blue'}>
                {snap.temperature ?? item.temperature ?? '-'} {'\u00B0'}C
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="湿度">
              <Tag color="cyan">{snap.humidity ?? item.humidity ?? '-'} %</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="执行耗时">
              <Tag color="geekblue">{item.executionMs ?? '-'} ms</Tag>
            </Descriptions.Item>
          </Descriptions>
        </Col>
        <Col xs={24} sm={12}>
          <Card
            size="small"
            title={
              <Space>
                <ExperimentOutlined />
                <span>设备属性快照</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <pre style={{
              maxHeight: 220,
              overflow: 'auto',
              margin: 0,
              padding: 12,
              background: '#f6f8fa',
              borderRadius: 6,
              fontSize: 12,
              lineHeight: 1.6
            }}>
              {JSON.stringify(snap.attributes || snap, null, 2)}
            </pre>
          </Card>
        </Col>
      </Row>
    )
  }

  const buildActionsList = (item) => {
    const actions = item?.actions || []
    if (actions.length === 0) {
      return <Alert type="info" showIcon message="本次触发无执行动作" />
    }
    return (
      <div style={{ marginTop: 12 }}>
        <Title level={5} style={{ marginTop: 0, marginBottom: 12 }}>
          <ThunderboltOutlined style={{ color: '#faad14', marginRight: 6 }} />
          执行动作 ({actions.length})
        </Title>
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          {actions.map((act, idx) => (
            <Card
              key={idx}
              size="small"
              style={{ borderRadius: 6 }}
            >
              <Row gutter={12} align="middle">
                <Col xs={24} md={6}>
                  <Tag color={act.success ? 'green' : 'red'} icon={act.success ? <CheckCircleOutlined /> : <InfoCircleOutlined />}>
                    {act.actionType || 'unknown'}
                  </Tag>
                </Col>
                <Col xs={24} md={6}>
                  <Text type="secondary">目标: </Text>
                  <Text copyable>{act.targetDeviceId || '-'}</Text>
                </Col>
                <Col xs={24} md={12}>
                  <Text type="secondary">参数: </Text>
                  <Text code style={{ fontSize: 12 }}>
                    {JSON.stringify(act.params || {})}
                  </Text>
                </Col>
              </Row>
              {!act.success && act.errorMsg && (
                <Alert
                  style={{ marginTop: 8 }}
                  type="error"
                  showIcon
                  message={act.errorMsg}
                />
              )}
            </Card>
          ))}
        </Space>
      </div>
    )
  }

  const buildTimelineItems = () => {
    if (historyList.length === 0) {
      return [
        {
          color: 'gray',
          dot: <HistoryOutlined />,
          children: <Empty description="暂无触发记录" style={{ padding: '32px 0' }} />
        }
      ]
    }
    return historyList.map(item => {
      const dotColor = item.engineType === 'aviator' ? 'blue' : 'purple'
      const actionCount = item.actions?.length || 0
      return {
        color: dotColor,
        dot: <ClockCircleOutlined />,
        children: (
          <Card
            size="small"
            hoverable
            style={{ marginBottom: 8, borderRadius: 6 }}
            onClick={() => handleViewSnapshot(item)}
            title={
              <Space>
                <Tag color={dotColor}>
                  {item.engineType?.toUpperCase()}
                </Tag>
                <Text strong>{formatTriggerTime(item.triggerTime)}</Text>
                <Text type="secondary">#{item.id?.slice(0, 8) || ''}</Text>
              </Space>
            }
            extra={
              <Tooltip title="查看详情/回放快照">
                <Button type="link" size="small" icon={<ExperimentOutlined />}>
                  回放快照
                </Button>
              </Tooltip>
            }
          >
            <Space size={[16, 8]} wrap>
              <Space>
                <InfoCircleOutlined />
                <Text type="secondary">设备:</Text>
                <Text copyable>{item.deviceId || '-'}</Text>
              </Space>
              <Space>
                <ThunderboltOutlined />
                <Text type="secondary">动作:</Text>
                <Tag color="orange">{actionCount}个</Tag>
              </Space>
              <Space>
                <ClockCircleOutlined />
                <Text type="secondary">耗时:</Text>
                <Tag color="geekblue">{item.executionMs || 0}ms</Tag>
              </Space>
            </Space>
            {item.matchedExpression && (
              <Paragraph
                style={{
                  marginTop: 10,
                  marginBottom: 0,
                  padding: 8,
                  background: '#f6ffed',
                  borderRadius: 4,
                  fontSize: 12,
                  fontFamily: 'Consolas, Monaco, monospace'
                }}
                ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
              >
                <Text type="secondary" style={{ marginRight: 6 }}>条件:</Text>
                {item.matchedExpression}
              </Paragraph>
            )}
          </Card>
        )
      }
    })
  }

  return (
    <>
      <Modal
        title={
          <Space>
            <HistoryOutlined style={{ color: '#1890ff' }} />
            <span>规则历史轨迹</span>
            <Tag color="blue">{ruleName || `规则#${ruleId}`}</Tag>
          </Space>
        }
        open={open}
        onCancel={onClose}
        footer={null}
        width={960}
        destroyOnClose
      >
        {!historyEnabled && (
          <Alert
            style={{ marginBottom: 16 }}
            type="warning"
            showIcon
            message="Elasticsearch 历史轨迹功能未启用"
            description="当前后端未启用ES存储或ES不可用，仅展示空列表。请联系管理员将 application.yml 中 rule.history.enabled 设置为 true 并启动 ES 服务。"
          />
        )}

        <Card size="small" style={{ marginBottom: 16, borderRadius: 6 }}>
          <Row gutter={12} align="middle" justify="space-between">
            <Col xs={24} sm={16}>
              <Space wrap>
                <Text type="secondary">时间范围:</Text>
                <RangePicker
                  showTime
                  value={timeRange}
                  onChange={handleTimeRangeChange}
                  format="YYYY-MM-DD HH:mm"
                  allowClear
                  ranges={{
                    '最近1小时': [moment().subtract(1, 'hours'), moment()],
                    '今日': [moment().startOf('day'), moment()],
                    '最近7天': [moment().subtract(7, 'days'), moment()],
                    '最近30天': [moment().subtract(30, 'days'), moment()],
                    '最近90天': [moment().subtract(90, 'days'), moment()]
                  }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={8} style={{ textAlign: 'right' }}>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchHistory}
                loading={loading}
              >
                刷新
              </Button>
            </Col>
          </Row>
          <Row style={{ marginTop: 12 }}>
            <Col span={24}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                数据保留 {historyEnabled ? '90' : 0} 天，共 {pagination.total} 条记录
              </Text>
            </Col>
          </Row>
        </Card>

        <Spin spinning={loading}>
          <div style={{
            maxHeight: 520,
            overflow: 'auto',
            padding: '8px 16px 8px 8px',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            background: '#fafafa'
          }}>
            <Timeline
              mode="left"
              items={buildTimelineItems()}
            />
          </div>
        </Spin>

        {pagination.total > 0 && (
          <div style={{ textAlign: 'right', marginTop: 16 }}>
            <Pagination
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={pagination.total}
              showSizeChanger
              showQuickJumper
              showTotal={t => `共 ${t} 条`}
              pageSizeOptions={['20', '50', '100']}
              onChange={handlePageChange}
              onShowSizeChange={handlePageChange}
            />
          </div>
        )}
      </Modal>

      <Modal
        title={
          <Space>
            <ExperimentOutlined style={{ color: '#52c41a' }} />
            <span>回放设备数据快照</span>
            <Tag color="geekblue">{formatTriggerTime(selectedItem?.triggerTime)}</Tag>
          </Space>
        }
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false)
          setSnapshotDetail(null)
        }}
        footer={[
          <Button key="close" onClick={() => {
            setDetailOpen(false)
            setSnapshotDetail(null)
          }}>
            关闭
          </Button>
        ]}
        width={900}
        destroyOnClose
      >
        <Spin spinning={snapshotLoading}>
          {snapshotDetail && (
            <div>
              <Card
                size="small"
                style={{ marginBottom: 16, borderRadius: 6 }}
                type="inner"
                title={
                  <Space>
                    <InfoCircleOutlined />
                    <span>触发概要</span>
                  </Space>
                }
              >
                <Descriptions column={2} size="small" bordered>
                  <Descriptions.Item label="规则ID">{snapshotDetail.ruleId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="规则名称">{snapshotDetail.ruleName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="触发时间">{formatTriggerTime(snapshotDetail.triggerTime)}</Descriptions.Item>
                  <Descriptions.Item label="引擎类型">
                    <Tag color={snapshotDetail.engineType === 'aviator' ? 'blue' : 'purple'}>
                      {snapshotDetail.engineType?.toUpperCase() || '-'}
                    </Tag>
                  </Descriptions.Item>
                </Descriptions>
                {snapshotDetail.matchedExpression && (
                  <div style={{ marginTop: 12 }}>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>命中条件表达式:</Text>
                    <pre style={{
                      margin: 0,
                      padding: 10,
                      background: '#fffbe6',
                      border: '1px solid #ffe58f',
                      borderRadius: 4,
                      fontSize: 12,
                      fontFamily: 'Consolas, Monaco, monospace',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all'
                    }}>
                      {snapshotDetail.matchedExpression}
                    </pre>
                  </div>
                )}
              </Card>

              <Card
                size="small"
                style={{ marginBottom: 16, borderRadius: 6 }}
                type="inner"
                title={
                  <Space>
                    <HistoryOutlined />
                    <span>设备数据快照</span>
                  </Space>
                }
              >
                {buildSnapshotSummary(snapshotDetail)}
              </Card>

              <Card
                size="small"
                style={{ borderRadius: 6 }}
                type="inner"
              >
                {buildActionsList(snapshotDetail)}
              </Card>
            </div>
          )}
        </Spin>
      </Modal>
    </>
  )
}

export default RuleHistoryTimeline
