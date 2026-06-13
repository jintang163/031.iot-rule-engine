import { useState, useEffect } from 'react'
import { Table, Tag, Space, Button, Input, Select, Card, Modal, message, Popconfirm } from 'antd'
import { SearchOutlined, PlusOutlined, EditOutlined, CopyOutlined, DeleteOutlined, PlayCircleOutlined, PauseCircleOutlined, HistoryOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getRuleList, deleteRule, enableRule, disableRule, createRule } from '../services/ruleApi'
import { getStatistics } from '../services/logApi'
import useAppStore from '../store/useAppStore'
import { formatDate } from '../utils'
import RecommendationSection from '../components/recommendation/RecommendationSection'
import RuleHistoryTimeline from '../components/history/RuleHistoryTimeline'

const { Option } = Select

const statusMap = {
  1: { color: 'success', text: '启用' },
  0: { color: 'default', text: '停用' }
}

function RuleList() {
  const navigate = useNavigate()
  const { setLoading } = useAppStore()

  const [searchName, setSearchName] = useState('')
  const [searchStatus, setSearchStatus] = useState(null)
  const [data, setData] = useState([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const [stats, setStats] = useState({
    total: 0,
    enabled: 0,
    todayTriggerCount: 0,
    failedActionCount: 0
  })
  const [timelineVisible, setTimelineVisible] = useState(false)
  const [timelineRule, setTimelineRule] = useState(null)

  const fetchRuleList = async () => {
    setLoading(true)
    try {
      const params = {
        page: pagination.current,
        size: pagination.pageSize,
        name: searchName || undefined,
        status: searchStatus !== null && searchStatus !== undefined ? searchStatus : undefined
      }
      const res = await getRuleList(params)
      setData(res?.records || [])
      setPagination((prev) => ({
        ...prev,
        total: res?.total || 0,
        current: res?.current || prev.current,
        pageSize: res?.size || prev.pageSize
      }))
    } catch (error) {
      console.error('获取规则列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchStats = async () => {
    try {
      const res = await getStatistics()
      setStats({
        total: res?.totalRules || 0,
        enabled: res?.enabledRules || 0,
        todayTriggerCount: res?.todayTriggerCount || 0,
        failedActionCount: res?.failedActionCount || 0
      })
    } catch (error) {
      console.error('获取统计数据失败:', error)
    }
  }

  useEffect(() => {
    fetchStats()
    fetchRuleList()
  }, [pagination.current, pagination.pageSize])

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }))
    fetchRuleList()
  }

  const handleReset = () => {
    setSearchName('')
    setSearchStatus(null)
    setPagination((prev) => ({ ...prev, current: 1 }))
    setTimeout(fetchRuleList, 0)
  }

  const handleTableChange = (paginationState) => {
    setPagination({
      current: paginationState.current,
      pageSize: paginationState.pageSize,
      total: paginationState.total
    })
  }

  const handleRuleCreated = () => {
    fetchRuleList()
    fetchStats()
  }

  const handleEdit = (record) => {
    navigate(`/rule/${record.id}`)
  }

  const handleCopy = async (record) => {
    try {
      setLoading(true)
      const newRule = {
        ...record,
        name: `${record.name}_副本`,
        id: undefined
      }
      await createRule(newRule)
      message.success('复制成功')
      fetchRuleList()
    } catch (error) {
      console.error('复制规则失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleToggleStatus = async (record) => {
    try {
      setLoading(true)
      const currentStatus = typeof record.status === 'number' ? record.status : (record.status === 'enabled' || record.status === 'active' ? 1 : 0)
      if (currentStatus === 1) {
        await disableRule(record.id)
        message.success('已停用')
      } else {
        await enableRule(record.id)
        message.success('已启用')
      }
      fetchRuleList()
      fetchStats()
    } catch (error) {
      console.error('切换状态失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (id) => {
    try {
      setLoading(true)
      await deleteRule(id)
      message.success('删除成功')
      fetchRuleList()
      fetchStats()
    } catch (error) {
      console.error('删除规则失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleViewHistory = (record) => {
    setTimelineRule(record)
    setTimelineVisible(true)
  }

  const statCards = [
    { title: '总规则数', value: stats.total, color: '#1890ff' },
    { title: '启用数', value: stats.enabled, color: '#52c41a' },
    { title: '今日触发次数', value: stats.todayTriggerCount, color: '#faad14' },
    { title: '失败动作数', value: stats.failedActionCount, color: '#ff4d4f' }
  ]

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <a onClick={() => handleEdit(record)}>{text}</a>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 80,
      render: (val) => val ?? '-'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => {
        const numericStatus = typeof status === 'number' ? status : (status === 'enabled' || status === 'active' ? 1 : 0)
        const config = statusMap[numericStatus] || { color: 'default', text: String(status) }
        return <Tag color={config.color}>{config.text}</Tag>
      }
    },
    {
      title: '互斥组',
      dataIndex: 'mutexGroup',
      key: 'mutexGroup',
      width: 100,
      render: (val) => val || '-'
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (val) => val ? formatDate(val) : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 360,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => handleViewHistory(record)}>
            轨迹
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(record)}>
            复制
          </Button>
          {(() => {
            const currentStatus = typeof record.status === 'number' ? record.status : (record.status === 'enabled' || record.status === 'active' ? 1 : 0)
            return currentStatus === 1 ? (
              <Button type="link" size="small" icon={<PauseCircleOutlined />} onClick={() => handleToggleStatus(record)}>
                停用
              </Button>
            ) : (
              <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleToggleStatus(record)}>
                启用
              </Button>
            )
          })()}
          <Popconfirm
            title="确认删除该规则？"
            description="删除后无法恢复"
            okText="确认"
            cancelText="取消"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
        {statCards.map((card, idx) => (
          <Card key={idx} bordered={false} style={{ borderRadius: 8 }}>
            <div style={{ fontSize: 14, color: '#888', marginBottom: 8 }}>{card.title}</div>
            <div style={{ fontSize: 28, fontWeight: 'bold', color: card.color }}>{card.value}</div>
          </Card>
        ))}
      </div>

      <RecommendationSection onRuleCreated={handleRuleCreated} />

      <Card bordered={false} style={{ borderRadius: 8, marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
          <h3 style={{ margin: 0 }}>规则列表</h3>
          <Space wrap>
            <Input
              placeholder="规则名称"
              prefix={<SearchOutlined />}
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Select
              placeholder="状态"
              value={searchStatus}
              onChange={(val) => setSearchStatus(val)}
              style={{ width: 140 }}
              allowClear
            >
              <Option value={1}>启用</Option>
              <Option value={0}>停用</Option>
            </Select>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button onClick={handleReset}>重置</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rule/new')}>
            新建规则
          </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handleTableChange}
          scroll={{ x: 1000 }}
        />
      </Card>

      <RuleHistoryTimeline
        ruleId={timelineRule?.id}
        ruleName={timelineRule?.name}
        open={timelineVisible}
        onClose={() => {
          setTimelineVisible(false)
          setTimelineRule(null)
        }}
      />
    </div>
  )
}

export default RuleList
