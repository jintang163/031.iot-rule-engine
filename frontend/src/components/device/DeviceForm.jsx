import { Modal, Form, Input, Select } from 'antd'

const { Option } = Select

const deviceTypeOptions = [
  { value: 'aircon', label: '空调' },
  { value: 'light', label: '灯光' },
  { value: 'sensor_temp', label: '温度传感器' },
  { value: 'sensor_humidity', label: '湿度传感器' },
  { value: 'sensor_presence', label: '人体传感器' }
]

const protocolOptions = [
  { value: 'MQTT', label: 'MQTT' },
  { value: 'HTTP', label: 'HTTP' }
]

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
      width={500}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={editData || { type: 'sensor_temp', protocol: 'MQTT' }}
      >
        <Form.Item
          name="deviceId"
          label="设备ID"
          rules={[{ required: true, message: '请输入设备ID' }]}
        >
          <Input placeholder="请输入设备唯一标识" disabled={!!editData} />
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
            {deviceTypeOptions.map((opt) => (
              <Option key={opt.value} value={opt.value}>
                {opt.label}
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          name="room"
          label="所属房间"
        >
          <Input placeholder="请输入所属房间，如：客厅、主卧" />
        </Form.Item>

        <Form.Item
          name="protocol"
          label="通信协议"
          rules={[{ required: true, message: '请选择通信协议' }]}
        >
          <Select placeholder="请选择通信协议">
            {protocolOptions.map((opt) => (
              <Option key={opt.value} value={opt.value}>
                {opt.label}
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          name="location"
          label="安装位置"
        >
          <Input placeholder="请输入安装位置，如：客厅-吊顶" />
        </Form.Item>

        <Form.Item
          name="actions"
          label="支持动作"
        >
          <Input.TextArea
            rows={3}
            placeholder='支持的动作列表，JSON格式，如：["turn_on", "turn_off"]'
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default DeviceForm
