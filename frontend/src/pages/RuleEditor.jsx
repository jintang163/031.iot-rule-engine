import { useState, useEffect } from 'react'
import { Layout, Typography, Button, Space, Breadcrumb, message } from 'antd'
import { useParams, useNavigate } from 'react-router-dom'
import { SaveOutlined, PlayCircleOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import Canvas from '../components/canvas/Canvas.jsx'
import Sidebar from '../components/sidebar/Sidebar.jsx'
import ConfigPanel from '../components/config/ConfigPanel.jsx'
import { getRuleById } from '../services/ruleApi'

const { Sider, Content } = Layout
const { Title } = Typography

function RuleEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEditMode = !!id && id !== 'new'
  const [ruleName, setRuleName] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (isEditMode) {
      fetchRuleDetail()
    } else {
      setRuleName('新建规则')
    }
  }, [id])

  const fetchRuleDetail = async () => {
    setLoading(true)
    try {
      const res = await getRuleById(id)
      setRuleName(res?.name || `规则 ${id}`)
    } catch (error) {
      console.error('获取规则详情失败:', error)
      setRuleName(`规则 ${id}`)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = () => {
    message.success('规则保存成功')
  }

  const handleRun = () => {
    message.info('开始执行规则...')
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
          width={340}
          style={{
            background: '#fafbfc',
            borderLeft: '1px solid #eef0f3',
            overflow: 'hidden'
          }}
        >
          <ConfigPanel />
        </Sider>
      </Layout>
    </div>
  )
}

export default RuleEditor
