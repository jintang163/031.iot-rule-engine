import { useState, useEffect, useMemo } from 'react'
import { Table, Button, Input, Space, Modal, Form, InputNumber, Switch, message, Popconfirm, Card, Typography, Tree, Drawer, Checkbox, Select } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, SafetyOutlined, SettingOutlined } from '@ant-design/icons'
import { getRoleList, getPermissionTree, getRolePermissions, createRole, updateRole, assignPermissions, deleteRole } from '../services/roleApi'
import dayjs from 'dayjs'

const { Title } = Typography

export default function RoleManagement() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [modalVisible, setModalVisible] = useState(false)
  const [permDrawerVisible, setPermDrawerVisible] = useState(false)
  const [editing, setEditing] = useState(null)
  const [currentRole, setCurrentRole] = useState(null)
  const [permissionTree, setPermissionTree] = useState([])
  const [checkedPerms, setCheckedPerms] = useState([])
  const [indeterminate, setIndeterminate] = useState(false)
  const [checkAll, setCheckAll] = useState(false)
  const [form] = Form.useForm()

  const allPermIds = useMemo(() => {
    const ids = []
    const collect = (nodes) => {
      nodes.forEach(n => {
        ids.push(n.id)
        if (n.children && n.children.length) {
          collect(n.children)
        }
      })
    }
    collect(permissionTree)
    return ids
  }, [permissionTree])

  const loadPermTree = async () => {
    try {
      const res = await getPermissionTree()
      setPermissionTree(res || [])
    } catch (e) {
    }
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const res = await getRoleList({ pageNum: page, pageSize, keyword })
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
    loadPermTree()
  }, [page, pageSize, keyword])

  useEffect(() => {
    if (allPermIds.length > 0 && checkedPerms.length > 0) {
      const allChecked = allPermIds.every(id => checkedPerms.includes(id))
      setCheckAll(allChecked)
      setIndeterminate(checkedPerms.length > 0 && !allChecked)
    }
  }, [checkedPerms, allPermIds])

  const handleCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      roleType: 2,
      dataScope: 2,
      status: 1
    })
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setEditing(record)
    form.setFieldsValue(record)
    setModalVisible(true)
  }

  const handlePermAssign = async (record) => {
    setCurrentRole(record)
    try {
      const res = await getRolePermissions(record.id)
      setCheckedPerms(res || [])
    } catch (e) {
      setCheckedPerms([])
    }
    setPermDrawerVisible(true)
  }

  const handleDelete = async (id) => {
    await deleteRole(id)
    message.success('删除成功')
    loadData()
  }

  const handleSubmit = async () => {
    const values = await form.validateFields()
    if (editing) {
      await updateRole({ ...editing, ...values })
      message.success('更新成功')
    } else {
      await createRole(values)
      message.success('创建成功')
    }
    setModalVisible(false)
    loadData()
  }

  const handleSavePerms = async () => {
    if (!currentRole) return
    await assignPermissions(currentRole.id, checkedPerms)
    message.success('权限分配成功')
    setPermDrawerVisible(false)
  }

  const handleCheckAll = (e) => {
    setCheckedPerms(e.target.checked ? allPermIds : [])
    setIndeterminate(false)
    setCheckAll(e.target.checked)
  }

  const onCheck = (keys) => {
    setCheckedPerms(keys)
  }

  const columns = [
    {
      title: '角色编码',
      dataIndex: 'roleCode',
      width: 160,
      render: (t, r) => (
        <Space>
          <code style={{ color: '#1677ff' }}>{t}</code>
          {r.roleType === 1 && (
            <span style={{
              padding: '1px 6px',
              background: '#fff1f0',
              color: '#cf1322',
              fontSize: 11,
              borderRadius: 3
            }}>内置</span>
          )}
        </Space>
      )
    },
    {
      title: '角色名称',
      dataIndex: 'roleName',
      width: 140
    },
    {
      title: '数据权限',
      dataIndex: 'dataScope',
      width: 100,
      render: (s) => ({ 1: '全部数据', 2: '本租户', 3: '仅本人' }[s] || '-')
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 80
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (s) => s === 1 ? '启用' : '禁用'
    },
    {
      title: '备注',
      dataIndex: 'remark'
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (t) => dayjs(t).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<SafetyOutlined />} onClick={() => handlePermAssign(r)}>
            分配权限
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)} disabled={r.roleType === 1}>
            编辑
          </Button>
          <Popconfirm title="确认删除该角色？" onConfirm={() => handleDelete(r.id)} okText="确认" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled={r.roleType === 1}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <Card
      title={<Title level={4} style={{ margin: 0 }}>角色管理</Title>}
      extra={
        <Space>
          <Input.Search
            placeholder="搜索角色编码/名称"
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
            新增角色
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1100 }}
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
        title={editing ? '编辑角色' : '新增角色'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={520}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space style={{ width: '100%' }}>
            <Form.Item name="roleCode" label="角色编码" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="如: RULE_EDITOR" disabled={!!editing} />
            </Form.Item>
            <Form.Item name="roleName" label="角色名称" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="如: 规则编辑员" />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }}>
            <Form.Item name="dataScope" label="数据权限" style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 1, label: '全部数据' },
                  { value: 2, label: '本租户' },
                  { value: 3, label: '仅本人' }
                ]}
              />
            </Form.Item>
            <Form.Item name="sort" label="排序" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="status" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        title={<Space><SettingOutlined />分配权限 - {currentRole?.roleName}</Space>}
        open={permDrawerVisible}
        onClose={() => setPermDrawerVisible(false)}
        width={480}
        extra={
          <Space>
            <Button onClick={() => setPermDrawerVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleSavePerms}>保存</Button>
          </Space>
        }
      >
        <div style={{ marginBottom: 16, paddingBottom: 12, borderBottom: '1px solid #f0f0f0' }}>
          <Checkbox
            indeterminate={indeterminate}
            onChange={handleCheckAll}
            checked={checkAll}
          >
            全选
          </Checkbox>
          <span style={{ color: '#8c8c8c', marginLeft: 12, fontSize: 12 }}>
            已选 {checkedPerms.length} 项
          </span>
        </div>
        <Tree
          checkable
          checkStrictly={false}
          defaultExpandAll
          checkedKeys={checkedPerms}
          onCheck={onCheck}
          treeData={permissionTree}
          fieldNames={{ title: 'permName', key: 'id', children: 'children' }}
          titleRender={(node) => (
            <Space>
              {node.permType === 2 && <span style={{
                fontSize: 11,
                padding: '0 4px',
                background: '#f5f5f5',
                color: '#666',
                borderRadius: 2
              }}>按钮</span>}
              {node.permName}
              <span style={{ color: '#8c8c8c', fontSize: 12, marginLeft: 4 }}>
                ({node.permCode})
              </span>
            </Space>
          )}
        />
      </Drawer>
    </Card>
  )
}
