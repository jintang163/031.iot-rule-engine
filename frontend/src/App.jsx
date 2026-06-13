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
  Divider,
  Tag
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
  BulbOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  SettingOutlined,
  TeamOutlined,
  SafetyOutlined,
  ApartmentOutlined
} from '@ant-design/icons'
import { useAuth } from './contexts/AuthProvider'

const { Header, Sider, Content, Footer } = Layout
const { Title } = Typography

function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout, hasRole, hasPerm } = useAuth()
  const [collapsed, setCollapsed] = useState(false)
  const [mqttConnected, setMqttConnected] = useState(true)

  const menuItems = []

  if (hasPerm('rule:view')) {
    menuItems.push({
      key: '/rules',
      icon: <DashboardOutlined />,
      label: '规则列表'
    })
  }

  if (hasPerm('rule:edit')) {
    menuItems.push({
      key: '/rule/new',
      icon: <ThunderboltOutlined />,
      label: '规则编辑器'
    })
  }

  if (hasPerm('rule:view')) {
    menuItems.push({
      key: '/templates',
      icon: <AppstoreOutlined />,
      label: '场景模板库'
    })
  }

  if (hasPerm('device:view')) {
    menuItems.push({
      key: '/devices',
      icon: <ApiOutlined />,
      label: '设备管理'
    })
  }

  if (hasPerm('rule:view')) {
    menuItems.push({
      key: '/logs',
      icon: <FileTextOutlined />,
      label: '执行日志'
    })
  }

  if (hasPerm('stats:view')) {
    menuItems.push({
      key: '/stats',
      icon: <BarChartOutlined />,
      label: '统计与成本'
    })
  }

  if (hasRole(['SUPER_ADMIN', 'TENANT_ADMIN'])) {
    const systemChildren = []
    if (hasRole(['TENANT_ADMIN', 'SUPER_ADMIN'])) {
      systemChildren.push({
        key: '/system/users',
        icon: <TeamOutlined />,
        label: '用户管理'
      })
      systemChildren.push({
        key: '/system/roles',
        icon: <SafetyOutlined />,
        label: '角色管理'
      })
    }
    if (hasRole(['SUPER_ADMIN'])) {
      systemChildren.push({
        key: '/system/tenants',
        icon: <ApartmentOutlined />,
        label: '租户管理'
      })
    }
    if (systemChildren.length > 0) {
      menuItems.push({
        key: '/system',
        icon: <SettingOutlined />,
        label: '系统管理',
        children: systemChildren
      })
    }
  }

  const getSelectedKey = () => {
    const path = location.pathname
    if (path.startsWith('/rule/')) return '/rule/new'
    if (path.startsWith('/system/')) return '/system'
    if (path === '/rules') return '/rules'
    if (path === '/templates') return '/templates'
    if (path === '/devices') return '/devices'
    if (path === '/logs') return '/logs'
    if (path === '/stats') return '/stats'
    return '/rules'
  }

  const handleMenuClick = ({ key }) => {
    if (key === '/system') return
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

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const userMenuItems = [
    {
      key: 'info',
      icon: <UserOutlined />,
      label: (
        <Space direction="vertical" size={2} style={{ minWidth: 200 }}>
          <div style={{ fontWeight: 500 }}>{user?.nickname || user?.username}</div>
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>
            @{user?.username}
          </div>
          {user?.tenantName && (
            <Tag color="blue" style={{ margin: 0 }}>
              {user.tenantName}
            </Tag>
          )}
        </Space>
      )
    },
    { type: 'divider' },
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
      onClick: handleLogout
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
          openKeys={getSelectedKey().startsWith('/system') ? ['/system'] : undefined}
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
              {user?.tenantName && (
                <Tag color="geekblue" style={{ marginLeft: 8 }}>
                  {user.tenantName}
                </Tag>
              )}
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
                <Avatar size="small" icon={<UserOutlined />} src={user?.avatar} />
                <span className="app-username">{user?.nickname || user?.username}</span>
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
