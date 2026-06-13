import { useState, useEffect } from 'react'
import { Layout, Typography, Button, Space, Breadcrumb, message, Modal, Form, Input, Tag } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import { SaveOutlined, ExperimentOutlined, ArrowLeftOutlined, AppstoreOutlined } from '@ant-design/icons'
import Canvas from '../components/canvas/Canvas.jsx'
import Sidebar from '../components/sidebar/Sidebar.jsx'
import ConfigPanel from '../components/config/ConfigPanel.jsx'
import SandboxPanel from '../components/debug/SandboxPanel.jsx'
import { getRuleById, createRule, updateRule } from '../services/ruleApi'
import { saveRuleAsTemplate } from '../services/templateApi'
import useRuleStore from '../store/useRuleStore.js'
import useAppStore from '../store/useAppStore.js'

const { Sider, Content } = Layout
const { Title } = Typography

function RuleEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { setLoading } = useAppStore()
  const isEditMode = !!id && id !== 'new'
  const [ruleName, setRuleName] = useState('')
  const [loading, setLoadingState] = useState(false)
  const [sandboxVisible, setSandboxVisible] = useState(false)
  const [saveAsTemplateModal, setSaveAsTemplateModal] = useState(false)
  const [templateForm] = Form.useForm()

  const ruleId = isEditMode ? Number(id) : null

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
      let currentRuleId = id && id !== 'new' ? id : null
      if (!currentRuleId && ruleData.id) {
        currentRuleId = ruleData.id
      }
      if (!currentRuleId) {
        const res = await createRule(ruleData)
        currentRuleId = res?.id
        useRuleStore.getState().setRuleInfo({ id: currentRuleId })
      }
      await saveRuleAsTemplate(
        currentRuleId,
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
          <Button
            type={sandboxVisible ? 'primary' : 'default'}
            icon={<ExperimentOutlined />}
            onClick={() => setSandboxVisible(!sandboxVisible)}
          >
            {sandboxVisible ? '关闭沙箱' : '测试沙箱'}
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
          width={sandboxVisible ? 360 : 360}
          style={{
            background: '#fafbfc',
            borderLeft: '1px solid #eef0f3',
            overflow: 'auto'
          }}
        >
          <div style={{ padding: '12px 12px 24px 12px' }}>
            {sandboxVisible ? (
              <SandboxPanel ruleId={ruleId} />
            ) : (
              <ConfigPanel />
            )}
          </div>
        </Sider>
      </Layout>

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
