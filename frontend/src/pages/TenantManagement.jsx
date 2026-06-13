import { useState, useEffect } from 'react'
import { Table, Button, Input, Space, Modal, Form, InputNumber, DatePicker, message, Popconfirm, Switch, Card, Typography } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import { getTenantList, createTenant, updateTenant, deleteTenant } from '../services/tenantApi'
import dayjs from 'dayjs'

const { Title } = Typography

export default function TenantManagement() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [modalVisible, setModalVisible] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form] = Form.useForm()

  const loadData = async () => {
    setLoading(true)
    try {
      const res = await getTenantList({ pageNum: page, pageSize, keyword })
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
  }, [page, pageSize, keyword])

  const handleCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      status: 1,
      maxUsers: 50,
      maxDevices: 500,
      maxRules: 100
    })
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setEditing(record)
    form.setFieldsValue({
      ...record,
      expireTime: record.expireTime ? dayjs(record.expireTime) : null
    })
    setModalVisible(true)
  }

  const handleDelete = async (id) => {
    await deleteTenant(id)
    message.success('删除成功')
    loadData()
  }

  const handleSubmit = async () => {
    const values = await form.validateFields()
    const submitData = {
      ...editing,
      ...values,
      expireTime: values.expireTime ? values.expireTime.format('YYYY-MM-DD HH:mm:ss') : null
    }
    if (editing) {
      await updateTenant(submitData)
      message.success('更新成功')
    } else {
      await createTenant(submitData)
      message.success('创建成功')
    }
    setModalVisible(false)
    loadData()
  }

  const handleToggleStatus = async (record, checked) => {
    await updateTenant({ ...record, status: checked ? 1 : 0 })
    message.success('状态更新成功')
    loadData()
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60
    },
    {
      title: '租户编码',
      dataIndex: 'tenantCode',
      width: 140,
      render: (t) => <code style={{ color: '#1677ff' }}>{t}</code>
    },
    {
      title: '租户名称',
      dataIndex: 'tenantName',
      width: 160
    },
    {
      title: '联系人',
      dataIndex: 'contactPerson',
      width: 100
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      width: 120
    },
    {
      title: '联系邮箱',
      dataIndex: 'contactEmail',
      width: 160
    },
    {
      title: '配额(用户/设备/规则)',
      width: 180,
      render: (_, r) => `${r.maxUsers || 0} / ${r.maxDevices || 0} / ${r.maxRules || 0}`
    },
    {
      title: '过期时间',
      dataIndex: 'expireTime',
      width: 160,
      render: (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '永久'
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
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (t) => dayjs(t).format('YYYY-MM-DD HH:mm')
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
          <Popconfirm title="确认删除该租户？" onConfirm={() => handleDelete(r.id)} okText="确认" cancelText="取消">
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
      title={<Title level={4} style={{ margin: 0 }}>租户管理</Title>}
      extra={
        <Space>
          <Input.Search
            placeholder="搜索租户编码/名称"
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
            onSearch={(v) => {
              setKeyword(v)
              setPage(1)
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新增租户
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1400 }}
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
        title={editing ? '编辑租户' : '新增租户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="tenantCode" label="租户编码" rules={[{ required: true }]}>
            <Input placeholder="唯一编码，如: DEMO" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="tenantName" label="租户名称" rules={[{ required: true }]}>
            <Input placeholder="企业/组织名称" />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="contactPerson" label="联系人" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="contactPhone" label="联系电话" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="contactEmail" label="联系邮箱">
            <Input />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="maxUsers" label="最大用户数" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="maxDevices" label="最大设备数" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="maxRules" label="最大规则数" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="expireTime" label="过期时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
