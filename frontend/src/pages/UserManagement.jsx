import { useState, useEffect } from 'react'
import { Table, Button, Input, Space, Modal, Form, Switch, message, Popconfirm, Card, Typography, Select, Avatar } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons'
import { getUserList, createUser, updateUser, deleteUser } from '../services/userApi'
import { getAllRoles } from '../services/roleApi'
import dayjs from 'dayjs'

const { Title } = Typography
const { Option } = Select

export default function UserManagement() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [modalVisible, setModalVisible] = useState(false)
  const [editing, setEditing] = useState(null)
  const [roles, setRoles] = useState([])
  const [form] = Form.useForm()

  const loadRoles = async () => {
    try {
      const res = await getAllRoles()
      setRoles(res || [])
    } catch (e) {
    }
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const res = await getUserList({ pageNum: page, pageSize, keyword })
      if (res && res.records) {
        setData(res.records)
        setTotal(res.total)
      } else {
        setData(res || [])
        setTotal((res || []).length)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    loadRoles()
  }, [page, pageSize, keyword])

  const handleCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      status: 1,
      roleIds: []
    })
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setEditing(record)
    const roleIds = (record.roles || []).map(r => r.id)
    form.setFieldsValue({
      username: record.username,
      nickname: record.nickname,
      email: record.email,
      phone: record.phone,
      status: record.status,
      roleIds
    })
    setModalVisible(true)
  }

  const handleDelete = async (id) => {
    await deleteUser(id)
    message.success('删除成功')
    loadData()
  }

  const handleSubmit = async () => {
    const values = await form.validateFields()
    const user = {
      id: editing?.id,
      username: values.username,
      password: values.password,
      nickname: values.nickname,
      email: values.email,
      phone: values.phone,
      status: values.status
    }
    if (editing) {
      await updateUser({ user, roleIds: values.roleIds })
      message.success('更新成功')
    } else {
      await createUser({ user, roleIds: values.roleIds })
      message.success('创建成功')
    }
    setModalVisible(false)
    loadData()
  }

  const handleToggleStatus = async (record, checked) => {
    await updateUser({
      user: { id: record.id, status: checked ? 1 : 0 }
    })
    message.success('状态更新成功')
    loadData()
  }

  const columns = [
    {
      title: '用户',
      dataIndex: 'username',
      width: 180,
      render: (_, r) => (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} src={r.avatar} />
          <div>
            <div style={{ fontWeight: 500 }}>{r.nickname}</div>
            <div style={{ color: '#8c8c8c', fontSize: 12 }}>@{r.username}</div>
          </div>
        </Space>
      )
    },
    {
      title: '角色',
      dataIndex: 'roles',
      width: 200,
      render: (roles) => (
        <Space wrap size={4}>
          {(roles || []).map(r => (
            <span key={r.id} className="role-tag" style={{
              padding: '2px 8px',
              background: r.roleType === 1 ? '#fff1f0' : '#e6f4ff',
              color: r.roleType === 1 ? '#cf1322' : '#1677ff',
              borderRadius: 4,
              fontSize: 12
            }}>
              {r.roleName}
            </span>
          ))}
        </Space>
      )
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 180
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 120
    },
    {
      title: '最后登录',
      width: 160,
      render: (_, r) => r.lastLoginTime ? dayjs(r.lastLoginTime).format('YYYY-MM-DD HH:mm') : '从未登录'
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (t) => dayjs(t).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (s, r) => (
        <Switch
          checked={s === 1}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          onChange={(v) => handleToggleStatus(r, v)}
        />
      )
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => handleDelete(r.id)} okText="确认" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <Card
      title={<Title level={4} style={{ margin: 0 }}>用户管理</Title>}
      extra={
        <Space>
          <Input.Search
            placeholder="搜索用户名/昵称"
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 240 }}
            onSearch={(v) => {
              setKeyword(v)
              setPage(1)
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新增用户
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p)
            setPageSize(s)
          }
        }}
      />
      <Modal
        title={editing ? '编辑用户' : '新增用户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={520}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input placeholder="登录用户名" disabled={!!editing} />
          </Form.Item>
          {!editing && (
            <Form.Item name="password" label="初始密码" rules={[{ required: true, min: 6, message: '至少6位' }]}>
              <Input.Password placeholder="默认123456" />
            </Form.Item>
          )}
          {editing && (
            <Form.Item name="password" label="新密码(留空不修改)">
              <Input.Password placeholder="留空则不修改密码" />
            </Form.Item>
          )}
          <Form.Item name="nickname" label="昵称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="手机号" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" placeholder="选择角色">
              {roles.map(r => (
                <Option key={r.id} value={r.id}>{r.roleName} ({r.roleCode})</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
