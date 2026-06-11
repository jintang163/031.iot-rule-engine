import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ConfigProvider, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import 'dayjs/locale/zh-cn'
import router from './router/index.jsx'
import 'reactflow/dist/style.css'
import './styles/global.css'

const theme = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 6,
    colorInfo: '#1677ff',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif'
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      siderBg: '#001529'
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17'
    }
  }
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={theme}>
      <AntdApp>
        <RouterProvider router={router} />
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>
)
