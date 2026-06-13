import { createBrowserRouter, Navigate } from 'react-router-dom'
import App from '../App.jsx'
import RuleList from '../pages/RuleList.jsx'
import RuleEditor from '../pages/RuleEditor.jsx'
import DeviceManager from '../pages/DeviceManager.jsx'
import ActionLog from '../pages/ActionLog.jsx'
import TemplateLibrary from '../pages/TemplateLibrary.jsx'
import RuleStatsPage from '../pages/RuleStatsPage.jsx'

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
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
        path: 'templates',
        element: <TemplateLibrary />
      },
      {
        path: 'stats',
        element: <RuleStatsPage />
      },
      {
        path: '*',
        element: <Navigate to="/rules" replace />
      }
    ]
  }
])

export default router
