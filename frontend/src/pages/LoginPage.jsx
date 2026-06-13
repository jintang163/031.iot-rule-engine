import { useState } from 'react'
import { Form, Input, Button, Card, Typography, message } from 'antd'
import { UserOutlined, LockOutlined, TeamOutlined, BulbOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthProvider'

const { Title } = Typography

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const onFinish = async (values) => {
    setLoading(true)
    try {
      await login(values)
      const from = location.state?.from?.pathname || '/rules'
      navigate(from, { replace: true })
    } catch (e) {
    } finally {
      setLoading(false)
    }
  }

  const containerStyle = {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    overflow: 'hidden'
  }

  const bgStyle = {
    position: 'absolute',
    inset: 0,
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    zIndex: 0
  }

  const cardStyle = {
    width: 420,
    zIndex: 1,
    boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
    borderRadius: 12,
    position: 'relative'
  }

  const headerStyle = {
    textAlign: 'center',
    marginBottom: 32
  }

  const logoStyle = {
    fontSize: 48,
    color: '#1677ff',
    marginBottom: 16
  }

  const tipsStyle = {
    marginTop: 16,
    paddingTop: 16,
    borderTop: '1px solid #f0f0f0',
    fontSize: 12,
    color: '#8c8c8c',
    lineHeight: 1.8
  }

  return (
    <div style={containerStyle}>
      <div style={bgStyle} />
      <Card style={cardStyle} bordered={false}>
        <div style={headerStyle}>
          <div style={logoStyle}>
            <BulbOutlined />
          </div>
          <Title level={3} style={{ margin: '0 0 8px 0' }}>IoT 规则引擎</Title>
          <div style={{ color: '#8c8c8c', fontSize: 14 }}>企业版多租户平台</div>
        </div>
        <Form
          form={form}
          name="login"
          onFinish={onFinish}
          size="large"
          autoComplete="off"
        >
          <Form.Item
            name="tenantCode"
            rules={[{ required: true, message: 'Please input tenant code' }]}
          >
            <Input prefix={<TeamOutlined />} placeholder="Tenant Code (e.g. PLATFORM)" allowClear />
          </Form.Item>
          <Form.Item
            name="username"
            rules={[{ required: true, message: 'Please input username' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="Username (e.g. super_admin)" allowClear />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Please input password' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Password (e.g. Super@2024)" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Sign In
            </Button>
          </Form.Item>
        </Form>
        <div style={tipsStyle}>
          <div>Platform Admin: PLATFORM / super_admin / Super@2024</div>
          <div>Tenant Admin: [tenantCode] / admin@[tenantCode] / Admin@123</div>
        </div>
      </Card>
    </div>
  )
}
