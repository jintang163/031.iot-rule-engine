import { createBrowserRouter, Navigate } from 'react-router-dom'
import App from '../App.jsx'
import RuleList from '../pages/RuleList.jsx'
import RuleEditor from '../pages/RuleEditor.jsx'
import DeviceManager from '../pages/DeviceManager.jsx'
import ActionLog from '../pages/ActionLog.jsx'
import AlertCenter from '../pages/AlertCenter.jsx'
import TemplateLibrary from '../pages/TemplateLibrary.jsx'
import RuleStatsPage from '../pages/RuleStatsPage.jsx'
import LoginPage from '../pages/LoginPage.jsx'
import UserManagement from '../pages/UserManagement.jsx'
import RoleManagement from '../pages/RoleManagement.jsx'
import TenantManagement from '../pages/TenantManagement.jsx'
import { RequireAuth, AuthProvider } from '../contexts/AuthProvider.jsx'

const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    )
  },
  {
    path: '/',
    element: (
      <AuthProvider>
        <RequireAuth>
          <App />
        </RequireAuth>
      </AuthProvider>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/rules" replace />
      },
      {
        path: 'rules',
        element: <RuleList />
      },
      {
        path: 'rule/new',
        element: <RuleEditor />
      },
      {
        path: 'rule/:id',
        element: <RuleEditor />
      },
      {
        path: 'devices',
        element: <DeviceManager />
      },
      {
        path: 'logs',
        element: <ActionLog />
      },
      {
        path: 'alerts',
        element: <AlertCenter />
      },
      {
        path: 'templates',
        element: <TemplateLibrary />
      },
      {
        path: 'stats',
        element: <RuleStatsPage />
      },
      {
        path: 'system/users',
        element: <UserManagement />
      },
      {
        path: 'system/roles',
        element: <RoleManagement />
      },
      {
        path: 'system/tenants',
        element: <TenantManagement />
      },
      {
        path: '*',
        element: <Navigate to="/rules" replace />
      }
    ]
  }
])

export default router
