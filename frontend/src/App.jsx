import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  Layout,
  Menu,
  Button,
  Dropdown,
  Avatar,
  Space,
  Typography,
  Tooltip,
  message,
  Divider
} from 'antd'
import {
  DashboardOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  FileTextOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ReloadOutlined,
  QuestionCircleOutlined,
  LogoutOutlined,
  UserOutlined,
  WifiOutlined,
  BulbOutlined
} from '@ant-design/icons'

const { Header, Sider, Content, Footer } = Layout
const { Title } = Typography

function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const [mqttConnected, setMqttConnected] = useState(true)

  const menuItems = [
    {
      key: '/rules',
      icon: <DashboardOutlined />,
      label: '规则列表'
    },
    {
      key: '/rule/new',
      icon: <ThunderboltOutlined />,
      label: '规则编辑器'
    },
    {
      key: '/devices',
      icon: <ApiOutlined />,
      label: '设备管理'
    },
    {
      key: '/logs',
      icon: <FileTextOutlined />,
      label: '执行日志'
    }
  ]

  const getSelectedKey = () => {
    const path = location.pathname
    if (path.startsWith('/rule/')) return '/rule/new'
    if (path === '/rules') return '/rules'
    if (path === '/devices') return '/devices'
    if (path === '/logs') return '/logs'
    return '/rules'
  }

  const handleMenuClick = ({ key }) => {
    navigate(key)
  }

  const handleRefreshMqtt = () => {
    message.loading({ content: '正在重连 MQTT...', key: 'mqtt-reconnect' })
    setMqttConnected(false)
    setTimeout(() => {
      setMqttConnected(true)
      message.success({ content: 'MQTT 连接已恢复', key: 'mqtt-reconnect' })
    }, 1500)
  }

  const userMenuItems = [
    {
      key: 'mqtt',
      icon: <ReloadOutlined />,
      label: '刷新 MQTT 连接',
      onClick: handleRefreshMqtt
    },
    {
      key: 'help',
      icon: <QuestionCircleOutlined />,
      label: '帮助文档'
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => message.info('退出登录功能')
    }
  ]

  return (
    <Layout className="app-layout">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        className="app-sider"
      >
        <div className="app-logo">
          <div className="app-logo-icon">
            <BulbOutlined />
          </div>
          {!collapsed && <span className="app-logo-text">IoT 规则引擎</span>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[getSelectedKey()]}
          items={menuItems}
          onClick={handleMenuClick}
          className="app-menu"
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <div className="app-header-left">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="app-trigger"
            />
            <Divider type="vertical" style={{ height: 24 }} />
            <Space size={12}>
              <Title level={5} style={{ margin: 0 }}>
                物联网规则引擎平台
              </Title>
              <Tooltip title={mqttConnected ? 'MQTT 已连接' : 'MQTT 连接断开'}>
                <span
                  className={`mqtt-status ${mqttConnected ? 'connected' : 'disconnected'}`}
                >
                  <WifiOutlined />
                </span>
              </Tooltip>
            </Space>
          </div>
          <div className="app-header-right">
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
              <Space className="app-user-info">
                <Avatar size="small" icon={<UserOutlined />} />
                <span className="app-username">管理员</span>
              </Space>
            </Dropdown>
          </div>
        </Header>
        <Content className="app-content">
          <div className="app-content-inner">
            <Outlet />
          </div>
        </Content>
        <Footer className="app-footer">
          <span>物联网规则引擎平台 © {new Date().getFullYear()} IoT Rule Engine Team</span>
        </Footer>
      </Layout>
    </Layout>
  )
}

export default App
