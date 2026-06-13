import { useState, useEffect } from 'react'
import { Layout, Typography, Button, Space, Breadcrumb, message, Modal, Form, Input, Tag } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import { SaveOutlined, PlayCircleOutlined, ArrowLeftOutlined, AppstoreOutlined } from '@ant-design/icons'
import Canvas from '../components/canvas/Canvas.jsx'
import Sidebar from '../components/sidebar/Sidebar.jsx'
import ConfigPanel from '../components/config/ConfigPanel.jsx'
import { getRuleById, createRule, updateRule, testRule } from '../services/ruleApi'
import { saveRuleAsTemplate } from '../services/templateApi'
import useRuleStore from '../store/useRuleStore.js'
import useAppStore from '../store/useAppStore.js'

const { Sider, Content } = Layout
const { Title } = Typography
const { TextArea } = Input

function RuleEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { setLoading } = useAppStore()
  const isEditMode = !!id && id !== 'new'
  const [ruleName, setRuleName] = useState('')
  const [loading, setLoadingState] = useState(false)
  const [testModalOpen, setTestModalOpen] = useState(false)
  const [resultModalOpen, setResultModalOpen] = useState(false)
  const [testResult, setTestResult] = useState(null)
  const [testForm] = Form.useForm()
  const [saveAsTemplateModal, setSaveAsTemplateModal] = useState(false)
  const [templateForm] = Form.useForm()

  useEffect(() => {
    if (isEditMode) {
      fetchRuleDetail()
    } else {
      setRuleName('新建规则')
    }
  }, [id])

  const fetchRuleDetail = async () => {
    setLoadingState(true)
    try {
      const res = await getRuleById(id)
      setRuleName(res?.ruleInfo?.name || res?.name || `规则 ${id}`)
      const ruleData = {
        ...(res.ruleInfo || res),
        nodes: res.nodes || [],
        edges: res.edges || []
      }
      useRuleStore.getState().loadRule(ruleData)
    } catch (error) {
      console.error('获取规则详情失败:', error)
      setRuleName(`规则 ${id}`)
    } finally {
      setLoadingState(false)
    }
  }

  const handleSave = async () => {
    const ruleData = useRuleStore.getState().exportRuleData()
    if (!ruleData.name || !ruleData.name.trim()) {
      message.warning('请先填写规则名称')
      return
    }
    try {
      setLoading(true)
      let res
      if (isEditMode) {
        res = await updateRule(ruleData)
      } else {
        res = await createRule(ruleData)
      }
      message.success('规则保存成功')
      if (res?.id) {
        useRuleStore.getState().setRuleInfo({ id: res.id })
        navigate('/rules')
      }
    } catch (error) {
      console.error('保存规则失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSaveAsTemplate = () => {
    const ruleData = useRuleStore.getState().exportRuleData()
    if (!ruleData.name || !ruleData.name.trim()) {
      message.warning('请先填写规则名称再保存为模板')
      return
    }
    templateForm.setFieldsValue({
      templateName: ruleData.name,
      templateDescription: ruleData.description,
      authorName: '管理员'
    })
    setSaveAsTemplateModal(true)
  }

  const handleSaveAsTemplateConfirm = async () => {
    try {
      const values = await templateForm.validateFields()
      setLoading(true)
      const ruleData = useRuleStore.getState().exportRuleData()
      let ruleId = id && id !== 'new' ? id : null
      if (!ruleId && ruleData.id) {
        ruleId = ruleData.id
      }
      if (!ruleId) {
        const res = await createRule(ruleData)
        ruleId = res?.id
        useRuleStore.getState().setRuleInfo({ id: ruleId })
      }
      await saveRuleAsTemplate(
        ruleId,
        values.templateName,
        values.templateDescription,
        values.authorName,
        'default',
        'admin'
      )
      message.success('规则已保存为模板')
      setSaveAsTemplateModal(false)
      templateForm.resetFields()
    } catch (error) {
      if (error?.errorFields) return
      console.error('保存为模板失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleRun = () => {
    testForm.setFieldsValue({
      deviceData: '{"temperature":32,"presence":false}'
    })
    setTestModalOpen(true)
  }

  const handleTestConfirm = async () => {
    try {
      const values = await testForm.validateFields()
      let parsedData
      try {
        parsedData = JSON.parse(values.deviceData)
      } catch (e) {
        message.error('设备数据必须是有效的JSON格式')
        return
      }
      setLoading(true)
      const ruleId = isEditMode ? id : null
      const res = await testRule({
        ruleId,
        deviceData: values.deviceData
      })
      setTestResult(res)
      setTestModalOpen(false)
      setResultModalOpen(true)
    } catch (error) {
      if (error?.errorFields) return
      console.error('测试规则失败:', error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <div
        style={{
          marginBottom: 16,
          padding: '16px 20px',
          background: '#fff',
          borderRadius: 10,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          boxShadow: '0 1px 4px rgba(0,0,0,0.04)'
        }}
      >
        <Space>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/rules')}
          >
            返回
          </Button>
          <div>
            <Breadcrumb
              style={{ marginBottom: 4 }}
              items={[
                { title: <a onClick={() => navigate('/rules')}>规则列表</a> },
                { title: isEditMode ? '编辑规则' : '新建规则' }
              ]}
            />
            <Title level={4} style={{ margin: 0 }}>
              {ruleName}
              {isEditMode && (
                <span
                  style={{
                    fontSize: 12,
                    color: '#8c8c8c',
                    fontWeight: 400,
                    marginLeft: 8
                  }}
                >
                  ID: {id}
                </span>
              )}
            </Title>
          </div>
        </Space>
        <Space>
          <Button icon={<PlayCircleOutlined />} onClick={handleRun}>
            运行测试
          </Button>
          <Button
            icon={<AppstoreOutlined />}
            onClick={handleSaveAsTemplate}
          >
            存为模板
          </Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
            保存规则
          </Button>
        </Space>
      </div>

      <Layout
        style={{
          flex: 1,
          minHeight: 0,
          background: '#fff',
          borderRadius: 10,
          overflow: 'hidden',
          boxShadow: '0 1px 4px rgba(0,0,0,0.04)'
        }}
      >
        <Sider
          width={260}
          style={{
            background: '#fff',
            borderRight: '1px solid #eef0f3',
            overflow: 'hidden'
          }}
        >
          <Sidebar />
        </Sider>
        <Content style={{ background: '#fafbfc', minWidth: 0, position: 'relative' }}>
          <Canvas />
        </Content>
        <Sider
          width={360}
          style={{
            background: '#fafbfc',
            borderLeft: '1px solid #eef0f3',
            overflow: 'auto'
          }}
        >
          <div style={{ padding: '12px 12px 24px 12px' }}>
            <ConfigPanel />
            <DebugPanel />
          </div>
        </Sider>
      </Layout>

      <Modal
        title="运行规则测试"
        open={testModalOpen}
        onOk={handleTestConfirm}
        onCancel={() => setTestModalOpen(false)}
        okText="执行测试"
        cancelText="取消"
        width={560}
      >
        <Form form={testForm} layout="vertical">
          <Form.Item
            label="模拟设备数据 (JSON)"
            name="deviceData"
            rules={[
              { required: true, message: '请输入设备数据JSON' }
            ]}
            extra="例如：{&quot;temperature&quot;:32,&quot;presence&quot;:false}"
          >
            <TextArea
              rows={6}
              placeholder='{"temperature":32,"presence":false}'
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="测试结果"
        open={resultModalOpen}
        onCancel={() => setResultModalOpen(false)}
        footer={[
          <Button key="close" type="primary" onClick={() => setResultModalOpen(false)}>
            关闭
          </Button>
        ]}
        width={600}
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: '#1f1f1f' }}>
            触发的规则
          </div>
          {testResult?.triggeredRules?.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {testResult.triggeredRules.map((rule, idx) => (
                <div
                  key={idx}
                  style={{
                    padding: '8px 12px',
                    background: '#f6ffed',
                    border: '1px solid #b7eb8f',
                    borderRadius: 6,
                    color: '#389e0d'
                  }}
                >
                  <strong>{rule.name || `规则 #${rule.id || idx}`}</strong>
                  {rule.description && <div style={{ fontSize: 12, marginTop: 4, opacity: 0.8 }}>{rule.description}</div>}
                </div>
              ))}
            </div>
          ) : (
            <div style={{ color: '#8c8c8c', padding: '8px 0' }}>无规则被触发</div>
          )}
        </div>

        <div>
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: '#1f1f1f' }}>
            执行的动作
          </div>
          {testResult?.executedActions?.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {testResult.executedActions.map((action, idx) => (
                <div
                  key={idx}
                  style={{
                    padding: '8px 12px',
                    background: '#e6f7ff',
                    border: '1px solid #91d5ff',
                    borderRadius: 6,
                    color: '#0958d9'
                  }}
                >
                  <strong>{action.actionType || action.name || `动作 ${idx + 1}`}</strong>
                  {action.deviceId && <div style={{ fontSize: 12, marginTop: 4 }}>设备: {action.deviceId}</div>}
                  {action.message && <div style={{ fontSize: 12, marginTop: 4 }}>消息: {action.message}</div>}
                </div>
              ))}
            </div>
          ) : (
            <div style={{ color: '#8c8c8c', padding: '8px 0' }}>无动作执行</div>
          )}
        </div>
      </Modal>

      <Modal
        title="保存为模板"
        open={saveAsTemplateModal}
        onOk={handleSaveAsTemplateConfirm}
        onCancel={() => {
          setSaveAsTemplateModal(false)
          templateForm.resetFields()
        }}
        okText="保存为模板"
        cancelText="取消"
        width={520}
      >
        <Form form={templateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="templateName"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input placeholder="为模板起一个名称" maxLength={200} />
          </Form.Item>
          <Form.Item name="templateDescription" label="模板描述">
            <Input.TextArea placeholder="描述模板的用途和适用场景" maxLength={500} rows={3} />
          </Form.Item>
          <Form.Item name="authorName" label="创建者" initialValue="管理员">
            <Input placeholder="创建者名称" maxLength={100} />
          </Form.Item>
          {!isEditMode && (
            <div style={{ padding: '8px 12px', background: '#fff7e6', borderRadius: 6, fontSize: 12, color: '#d46b08' }}>
              <Tag color="orange" style={{ marginRight: 8 }}>提示</Tag>
              当前规则尚未保存，保存为模板时将自动保存规则
            </div>
          )}
        </Form>
      </Modal>
    </div>
  )
}

export default RuleEditor
