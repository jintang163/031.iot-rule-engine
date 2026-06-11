import { Table, Tag, Space, Button, Input } from 'antd'
import { SearchOutlined } from '@ant-design/icons'

const mockDevices = [
  { id: 'D001', name: '温度传感器-01', type: 'sensor', status: 'online', lastSeen: '2024-01-15 14:30:00', location: 'A区-1号楼' },
  { id: 'D002', name: '湿度传感器-01', type: 'sensor', status: 'online', lastSeen: '2024-01-15 14:29:55', location: 'A区-1号楼' },
  { id: 'D003', name: '智能开关-01', type: 'actuator', status: 'offline', lastSeen: '2024-01-14 09:15:00', location: 'B区-2号楼' },
  { id: 'D004', name: '烟雾报警器-01', type: 'alarm', status: 'online', lastSeen: '2024-01-15 14:28:00', location: 'C区-3号楼' },
  { id: 'D005', name: '光照传感器-01', type: 'sensor', status: 'online', lastSeen: '2024-01-15 14:30:05', location: 'A区-1号楼' }
]

const statusMap = {
  online: { color: 'success', text: '在线' },
  offline: { color: 'error', text: '离线' }
}

const typeMap = {
  sensor: { color: 'blue', text: '传感器' },
  actuator: { color: 'orange', text: '执行器' },
  alarm: { color: 'red', text: '报警器' }
}

function DeviceList({ onEdit, onDelete, onView }) {
  const columns = [
    {
      title: '设备ID',
      dataIndex: 'id',
      key: 'id',
      width: 100
    },
    {
      title: '设备名称',
      dataIndex: 'name',
      key: 'name',
      filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
        <div style={{ padding: 8 }}>
          <Input
            placeholder="搜索设备名称"
            value={selectedKeys[0]}
            onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
            onPressEnter={confirm}
            style={{ width: 188, marginBottom: 8, display: 'block' }}
          />
          <Space>
            <Button type="primary" onClick={confirm} size="small" style={{ width: 90 }}>
              搜索
            </Button>
            <Button onClick={clearFilters} size="small" style={{ width: 90 }}>
              重置
            </Button>
          </Space>
        </div>
      ),
      filterIcon: (filtered) => (
        <SearchOutlined style={{ color: filtered ? '#1890ff' : undefined }} />
      ),
      onFilter: (value, record) =>
        record.name.toString().toLowerCase().includes(value.toLowerCase())
    },
    {
      title: '设备类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type) => <Tag color={typeMap[type]?.color}>{typeMap[type]?.text}</Tag>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status) => <Tag color={statusMap[status]?.color}>{statusMap[status]?.text}</Tag>
    },
    {
      title: '最后上线时间',
      dataIndex: 'lastSeen',
      key: 'lastSeen',
      width: 180
    },
    {
      title: '位置',
      dataIndex: 'location',
      key: 'location'
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="middle">
          <a onClick={() => onView?.(record)}>查看</a>
          <a onClick={() => onEdit?.(record)}>编辑</a>
          <a onClick={() => onDelete?.(record)} style={{ color: '#ff4d4f' }}>删除</a>
        </Space>
      )
    }
  ]

  return (
    <Table
      columns={columns}
      dataSource={mockDevices}
      rowKey="id"
      pagination={{ pageSize: 10 }}
    />
  )
}

export default DeviceList
