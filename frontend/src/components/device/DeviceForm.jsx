import { Modal, Form, Input, Select, InputNumber } from 'antd'

const { Option } = Select

function DeviceForm({ open, onCancel, onSubmit, editData }) {
  const [form] = Form.useForm()

  const handleOk = () => {
    form.validateFields().then((values) => {
      onSubmit?.(values)
      form.resetFields()
    })
  }

  const handleCancel = () => {
    form.resetFields()
    onCancel?.()
  }

  return (
    <Modal
      title={editData ? '编辑设备' : '新增设备'}
      open={open}
      onOk={handleOk}
      onCancel={handleCancel}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={editData || { type: 'sensor', status: 'offline' }}
      >
        <Form.Item
          name="id"
          label="设备ID"
          rules={[{ required: true, message: '请输入设备ID' }]}
        >
          <Input placeholder="请输入设备ID" disabled={!!editData} />
        </Form.Item>
        <Form.Item
          name="name"
          label="设备名称"
          rules={[{ required: true, message: '请输入设备名称' }]}
        >
          <Input placeholder="请输入设备名称" />
        </Form.Item>
        <Form.Item
          name="type"
          label="设备类型"
          rules={[{ required: true, message: '请选择设备类型' }]}
        >
          <Select placeholder="请选择设备类型">
            <Option value="sensor">传感器</Option>
            <Option value="actuator">执行器</Option>
            <Option value="alarm">报警器</Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="location"
          label="安装位置"
          rules={[{ required: true, message: '请输入安装位置' }]}
        >
          <Input placeholder="请输入安装位置" />
        </Form.Item>
        <Form.Item name="description" label="设备描述">
          <Input.TextArea rows={3} placeholder="请输入设备描述" />
        </Form.Item>
        <Form.Item
          name="mqttTopic"
          label="MQTT主题"
          rules={[{ required: true, message: '请输入MQTT主题' }]}
        >
          <Input placeholder="devices/{id}/data" />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default DeviceForm
