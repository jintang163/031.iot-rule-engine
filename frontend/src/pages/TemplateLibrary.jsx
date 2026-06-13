import { useState, useEffect } from 'react'
import { Card, Row, Col, Input, Select, Tabs, Button, Space, Modal, Form, message, Popconfirm, Empty, Spin, Tag } from 'antd'
import {
  SearchOutlined,
  AppstoreOutlined,
  PlusOutlined,
  FilterOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons'
import { getTemplateList, applyTemplate, deleteTemplate, reviewTemplate, createTemplate } from '../services/templateApi'
import { getRuleList } from '../services/ruleApi'
import TemplateCard from '../components/template/TemplateCard'
import SaveAsTemplateModal from '../components/template/SaveAsTemplateModal'
import useAppStore from '../store/useAppStore'
import { useNavigate } from 'react-router-dom'

const { Option } = Select
const { TabPane } = Tabs

const categoryOptions = [
  { value: 'energy_saving', label: '节能模式' },
  { value: 'away', label: '离家模式' },
  { value: 'security', label: '安防模式' },
  { value: 'custom', label: '自定义' }
]

function TemplateLibrary() {
  const navigate = useNavigate()
  const { setLoading } = useAppStore()

  const [data, setData] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 12, total: 0 })
  const [searchName, setSearchName] = useState('')
  const [searchCategory, setSearchCategory] = useState(null)
  const [activeTab, setActiveTab] = useState('all')
  const [loading, setLocalLoading] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showSaveFromRuleModal, setShowSaveFromRuleModal] = useState(false)
  const [createForm] = Form.useForm()

  const fetchTemplates = async () => {
    setLocalLoading(true)
    try {
      const params = {
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        name: searchName || undefined,
        category: searchCategory || undefined,
        scope: activeTab === 'all' || activeTab === 'pending' ? undefined : activeTab,
        status: 1,
        teamId: 'default'
      }
      if (activeTab === 'pending') {
        params.reviewStatus = 0
        params.status = undefined
      }
      if (activeTab === 'private') {
        params.authorId = 'admin'
        params.teamId = undefined
      }
      const res = await getTemplateList(params)
      setData(res?.records || [])
      setPagination((prev) => ({
        ...prev,
        total: res?.total || 0,
        current: res?.current || prev.current
      }))
    } catch (error) {
      console.error('获取模板列表失败:', error)
    } finally {
      setLocalLoading(false)
    }
  }

  useEffect(() => {
    fetchTemplates()
  }, [pagination.current, pagination.pageSize, activeTab])

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }))
    fetchTemplates()
  }

  const handleReset = () => {
    setSearchName('')
    setSearchCategory(null)
    setPagination((prev) => ({ ...prev, current: 1 }))
    setTimeout(fetchTemplates, 0)
  }

  const handleApply = async (template) => {
    try {
      setLoading(true)
      const res = await applyTemplate({ templateId: template.id })
      message.success(res?.message || `模板「${template.name}」应用成功`)
      if (res?.ruleId) {
        Modal.confirm({
          title: '规则已创建并部署',
          content: `规则「${res.ruleName}」已${res.status === 1 ? '自动启用并部署成功' : '创建（自动启用失败，请手动启用）'}，是否前往编辑器查看？`,
          okText: '前往编辑',
          cancelText: '留在此页',
          onOk: () => navigate(`/rule/${res.ruleId}`)
        })
      }
    } catch (error) {
      console.error('应用模板失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (template) => {
    Modal.confirm({
      title: '确认删除此模板？',
      icon: <ExclamationCircleOutlined />,
      content: `模板「${template.name}」删除后将无法恢复`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteTemplate(template.id)
          message.success('模板已删除')
          fetchTemplates()
        } catch (error) {
          console.error('删除模板失败:', error)
        }
      }
    })
  }

  const handleReview = async (templateId, reviewStatus) => {
    try {
      await reviewTemplate(templateId, reviewStatus, 'admin', reviewStatus === 1 ? '审核通过' : '审核拒绝')
      message.success(reviewStatus === 1 ? '已审核通过' : '已审核拒绝')
      fetchTemplates()
    } catch (error) {
      console.error('审核操作失败:', error)
    }
  }

  const handleCreateTemplate = async () => {
    try {
      const values = await createForm.validateFields()
      const templateData = {
        ...values,
        teamId: 'default',
        authorId: 'admin'
      }
      if (templateData.ruleJson && !templateData.ruleConfig) {
        try {
          const parsed = typeof templateData.ruleJson === 'string'
            ? JSON.parse(templateData.ruleJson)
            : templateData.ruleJson
          const nodes = parsed.nodes || []
          const conditions = []
          const actions = []
          let logic = 'AND'
          nodes.forEach(node => {
            const data = node.data || {}
            if (node.type === 'conditionNode') {
              conditions.push({
                deviceId: data.deviceId,
                field: data.field,
                operator: data.operator,
                value: data.value,
                label: data.label || `${data.field} ${data.operator} ${data.value}`
              })
            } else if (node.type === 'actionNode') {
              actions.push({
                deviceId: data.deviceId,
                action: data.action,
                params: data.params || {},
                label: data.label || data.action
              })
            } else if (node.type === 'logicNode') {
              logic = data.type || 'AND'
            }
          })
          templateData.ruleConfig = JSON.stringify({ conditions, actions, logic })
        } catch (e) {
          console.warn('自动生成 ruleConfig 失败:', e)
        }
      }
      await createTemplate(templateData)
      message.success('模板创建成功')
      setShowCreateModal(false)
      createForm.resetFields()
      fetchTemplates()
    } catch (error) {
      console.error('创建模板失败:', error)
    }
  }

  const handlePageChange = (page, pageSize) => {
    setPagination((prev) => ({ ...prev, current: page, pageSize }))
  }

  const tabItems = [
    { key: 'all', label: '全部模板' },
    { key: 'public', label: '公共模板' },
    { key: 'team', label: '团队模板' },
    { key: 'private', label: '我的模板' },
    { key: 'pending', label: '待审核' }
  ]

  return (
    <div>
      <Card bordered={false} style={{ borderRadius: 8, marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
          <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
            <AppstoreOutlined style={{ color: '#1677ff' }} />
            场景模板库
          </h3>
          <Space wrap>
            <Button
              icon={<PlusOutlined />}
              onClick={() => setShowSaveFromRuleModal(true)}
            >
              从规则保存
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setShowCreateModal(true)}
            >
              新建模板
            </Button>
          </Space>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
          <Input
            placeholder="搜索模板名称"
            prefix={<SearchOutlined />}
            value={searchName}
            onChange={(e) => setSearchName(e.target.value)}
            style={{ width: 220 }}
            allowClear
            onPressEnter={handleSearch}
          />
          <Select
            placeholder="模板分类"
            value={searchCategory}
            onChange={(val) => setSearchCategory(val)}
            style={{ width: 150 }}
            allowClear
          >
            {categoryOptions.map((opt) => (
              <Option key={opt.value} value={opt.value}>{opt.label}</Option>
            ))}
          </Select>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
          <Button onClick={handleReset}>重置</Button>
        </div>

        <Tabs
          activeKey={activeTab}
          onChange={(key) => {
            setActiveTab(key)
            setPagination((prev) => ({ ...prev, current: 1 }))
          }}
          items={tabItems}
        />
      </Card>

      <Spin spinning={loading}>
        {data.length === 0 && !loading ? (
          <Card bordered={false} style={{ borderRadius: 8 }}>
            <Empty
              description="暂无模板数据"
              style={{ padding: '40px 0' }}
            >
              <Button type="primary" onClick={() => setShowCreateModal(true)}>
                创建第一个模板
              </Button>
            </Empty>
          </Card>
        ) : (
          <Row gutter={[16, 16]}>
            {data.map((template) => (
              <Col key={template.id} xs={24} sm={12} md={8} lg={6}>
                <TemplateCard
                  template={template}
                  onApply={handleApply}
                  onDelete={handleDelete}
                  onReview={handleReview}
                />
              </Col>
            ))}
          </Row>
        )}

        {pagination.total > pagination.pageSize && (
          <div style={{ textAlign: 'center', marginTop: 24 }}>
            <Space>
              <Button
                disabled={pagination.current <= 1}
                onClick={() => handlePageChange(pagination.current - 1, pagination.pageSize)}
              >
                上一页
              </Button>
              <span style={{ color: '#888' }}>
                第 {pagination.current} / {Math.ceil(pagination.total / pagination.pageSize)} 页
              </span>
              <Button
                disabled={pagination.current >= Math.ceil(pagination.total / pagination.pageSize)}
                onClick={() => handlePageChange(pagination.current + 1, pagination.pageSize)}
              >
                下一页
              </Button>
            </Space>
          </div>
        )}
      </Spin>

      <Modal
        title="新建模板"
        open={showCreateModal}
        onOk={handleCreateTemplate}
        onCancel={() => {
          setShowCreateModal(false)
          createForm.resetFields()
        }}
        okText="创建"
        cancelText="取消"
        width={600}
      >
        <Form form={createForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="输入模板名称" maxLength={200} />
          </Form.Item>
          <Form.Item name="description" label="模板描述">
            <Input.TextArea placeholder="描述模板的用途和场景" maxLength={500} rows={3} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="category" label="模板分类" rules={[{ required: true, message: '请选择模板分类' }]}>
                <Select placeholder="选择分类">
                  {categoryOptions.map((opt) => (
                    <Option key={opt.value} value={opt.value}>{opt.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="scope" label="可见范围" initialValue="team">
                <Select placeholder="选择可见范围">
                  <Option value="public">公共（需审核）</Option>
                  <Option value="team">团队</Option>
                  <Option value="private">私有</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="icon" label="图标">
            <Input placeholder="输入图标（如 emoji）" maxLength={50} />
          </Form.Item>
          <Form.Item name="ruleJson" label="规则定义 (JSON)">
            <Input.TextArea
              placeholder='输入规则 JSON 定义，如: {"nodes":[], "edges":[]}'
              rows={6}
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item name="version" label="版本号" initialValue="1.0.0">
            <Input placeholder="如: 1.0.0" maxLength={30} />
          </Form.Item>
        </Form>
      </Modal>

      <SaveAsTemplateModal
        open={showSaveFromRuleModal}
        onCancel={() => setShowSaveFromRuleModal(false)}
        onSuccess={() => {
          setShowSaveFromRuleModal(false)
          fetchTemplates()
        }}
      />
    </div>
  )
}

export default TemplateLibrary
