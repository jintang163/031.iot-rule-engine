import { useEffect } from 'react'
import { Card, Form, Input, Select, InputNumber, Switch, Divider, Typography, Space, Empty, Tag, Slider, Radio, TimePicker, Collapse, Alert } from 'antd'
import {
  SettingOutlined,
  EnvironmentOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  ClockCircleOutlined,
  BorderOutlined,
  SplitCellsOutlined,
  CloudServerOutlined,
  BulbFilled,
  BulbOutlined,
  NotificationOutlined,
  PlayCircleOutlined,
  StopOutlined,
  RiseOutlined,
  PauseCircleOutlined,
  LinkOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import useRuleStore from '../../store/useRuleStore.js'

const { Text } = Typography
const { TextArea } = Input
const { Option } = Select

const conditionTypeMeta = {
  temperature: { label: '温度传感器', icon: EnvironmentOutlined, color: '#fa8c16', unit: '°C', defaultThreshold: 30 },
  humidity: { label: '湿度传感器', icon: ThunderboltOutlined, color: '#1890ff', unit: '%', defaultThreshold: 80 },
  presence: { label: '人体存在传感器', icon: TeamOutlined, color: '#722ed1', unit: '', defaultThreshold: true },
  time: { label: '时间条件', icon: ClockCircleOutlined, color: '#13c2c2', unit: '', defaultThreshold: null }
}

const logicGateMeta = {
  AND: { label: '逻辑AND门', icon: BorderOutlined, color: '#52c41a', desc: '所有输入条件同时满足时触发' },
  OR: { label: '逻辑OR门', icon: SplitCellsOutlined, color: '#eb2f96', desc: '任一输入条件满足时触发' }
}

const actionTypeMeta = {
  turn_on_aircon: { label: '开空调', icon: CloudServerOutlined, color: '#1890ff' },
  turn_off_aircon: { label: '关空调', icon: CloudServerOutlined, color: '#8c8c8c' },
  turn_on_light: { label: '开灯', icon: BulbFilled, color: '#faad14' },
  turn_off_light: { label: '关灯', icon: BulbOutlined, color: '#8c8c8c' },
  send_alert: { label: '推送告警', icon: NotificationOutlined, color: '#ff4d4f' }
}

const deviceOptions = [
  { value: 'aircon_living', label: '客厅空调', type: 'aircon' },
  { value: 'aircon_bedroom', label: '卧室空调', type: 'aircon' },
  { value: 'light_living', label: '客厅灯', type: 'light' },
  { value: 'light_bedroom', label: '卧室灯', type: 'light' },
  { value: 'light_kitchen', label: '厨房灯', type: 'light' },
  { value: 'light_bathroom', label: '卫生间灯', type: 'light' },
  { value: 'gateway_01', label: '主网关', type: 'gateway' },
  { value: 'hub_all', label: '所有设备', type: 'all' }
]

const operatorOptions = [
  { value: '>', label: '大于 (>)' },
  { value: '<', label: '小于 (<)' },
  { value: '==', label: '等于 (==)' },
  { value: '>=', label: '大于等于 (≥)' },
  { value: '<=', label: '小于等于 (≤)' },
  { value: '!=', label: '不等于 (!=)' }
]

const alertLevelOptions = [
  { value: 'info', label: '信息', color: '#1890ff' },
  { value: 'warning', label: '警告', color: '#faad14' },
  { value: 'error', label: '严重', color: '#ff4d4f' }
]

const windowTypeOptions = [
  { value: 'TUMBLING', label: '滚动窗口 (Tumbling)', desc: '固定时间间隔，不重叠' },
  { value: 'SLIDING', label: '滑动窗口 (Sliding)', desc: '按步长滑动，可重叠' },
  { value: 'SESSION', label: '会话窗口 (Session)', desc: '基于数据活跃度' }
]

const aggregationOptions = [
  { value: 'SUM', label: '求和 (SUM)' },
  { value: 'AVG', label: '平均值 (AVG)' },
  { value: 'MAX', label: '最大值 (MAX)' },
  { value: 'MIN', label: '最小值 (MIN)' },
  { value: 'COUNT', label: '计数 (COUNT)' },
  { value: 'DELTA', label: '差值 (DELTA)' }
]

const windowFieldOptions = [
  { value: 'temperature', label: '温度 (temperature)' },
  { value: 'humidity', label: '湿度 (humidity)' }
]

const windowOperatorOptions = [
  { value: '>', label: '大于 (>)' },
  { value: '<', label: '小于 (<)' },
  { value: '>=', label: '大于等于 (≥)' },
  { value: '<=', label: '小于等于 (≤)' },
  { value: '==', label: '等于 (==)' }
]

const { Panel } = Collapse

function SectionHeader({ icon: Icon, title, color, subtitle }) {
  return (
    <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 8,
          background: `${color}15`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color
        }}
      >
        <Icon style={{ fontSize: 18 }} />
      </div>
      <div>
        <div style={{ fontSize: 15, fontWeight: 700, color: '#1f1f1f' }}>{title}</div>
        {subtitle && <Text type="secondary" style={{ fontSize: 12 }}>{subtitle}</Text>}
      </div>
    </div>
  )
}

function RuleInfoPanel() {
  const [form] = Form.useForm()
  const ruleInfo = useRuleStore((state) => state.ruleInfo)
  const setRuleInfo = useRuleStore((state) => state.setRuleInfo)

  useEffect(() => {
    form.setFieldsValue({
      ...ruleInfo,
      priority: ruleInfo.priority ?? 5,
      status: typeof ruleInfo.status === 'number' ? ruleInfo.status : (ruleInfo.status === 'active' || ruleInfo.status === 'enabled' ? 1 : 0),
      mutexGroup: ruleInfo.mutexGroup || '',
      windowEnabled: ruleInfo.windowEnabled ?? 0,
      windowType: ruleInfo.windowType || 'TUMBLING',
      windowDuration: ruleInfo.windowDuration ?? 600,
      windowAggregation: ruleInfo.windowAggregation || 'DELTA',
      windowField: ruleInfo.windowField || 'temperature',
      windowOperator: ruleInfo.windowOperator || '>',
      windowThreshold: ruleInfo.windowThreshold ?? 5,
      cooldownSeconds: ruleInfo.cooldownSeconds ?? 0,
      chainTriggerEnabled: ruleInfo.chainTriggerEnabled ?? 0,
      chainNextRuleIds: ruleInfo.chainNextRuleIds || '',
      chainDisableSelf: ruleInfo.chainDisableSelf ?? 0
    })
  }, [ruleInfo.id])

  const handleValuesChange = (_, allValues) => {
    setRuleInfo(allValues)
  }

  const statusTagColor = ruleInfo.status === 1 ? 'green' : 'default'
  const statusTagText = ruleInfo.status === 1 ? '启用' : '停用(草稿)'

  return (
    <div style={{ padding: 12, height: '100%', overflowY: 'auto' }}>
      <Card
        size="small"
        bordered={false}
        style={{ boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={SettingOutlined}
          title="规则信息"
          color="#1677ff"
          subtitle="配置规则的基本属性"
        />

        <Form
          form={form}
          layout="vertical"
          initialValues={{ ...ruleInfo, priority: 5, status: 0, mutexGroup: '' }}
          onValuesChange={handleValuesChange}
        >
          <Form.Item
            label="规则名称"
            name="name"
            rules={[{ required: true, message: '请输入规则名称' }]}
            style={{ marginBottom: 16 }}
          >
            <Input placeholder="例如：温度过高自动开空调" size="middle" />
          </Form.Item>

          <Form.Item
            label="规则描述"
            name="description"
            style={{ marginBottom: 16 }}
          >
            <TextArea
              rows={3}
              placeholder="描述此规则的用途和触发条件..."
              showCount
              maxLength={200}
            />
          </Form.Item>

          <Form.Item
            label={
              <Space>
                <span>规则状态</span>
                <Tag color={statusTagColor}>
                  {statusTagText}
                </Tag>
              </Space>
            }
            name="status"
            style={{ marginBottom: 16 }}
          >
            <Radio.Group>
              <Radio.Button value={0}>
                <Space size={4}>
                  <BorderOutlined />停用(草稿)
                </Space>
              </Radio.Button>
              <Radio.Button value={1}>
                <Space size={4}>
                  <PlayCircleOutlined />启用
                </Space>
              </Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            label="互斥规则组"
            name="mutexGroup"
            style={{ marginBottom: 16 }}
            extra="同一互斥组中的规则在触发时只会执行优先级最高的一条"
          >
            <Input placeholder="例如：aircon_group，留空表示不参与互斥" size="middle" allowClear />
          </Form.Item>

          <Divider style={{ margin: '16px 0' }} />

          <Form.Item
            label={
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>执行优先级</span>
                <Tag color="blue">{ruleInfo.priority ?? 5}</Tag>
              </Space>
            }
            name="priority"
            style={{ marginBottom: 0 }}
          >
            <Slider
              min={1}
              max={10}
              marks={{
                1: '低',
                5: '中',
                10: '高'
              }}
              tooltip={{ formatter: (v) => `优先级 ${v}` }}
            />
          </Form.Item>
        </Form>
      </Card>

      <Card
        size="small"
        bordered={false}
        style={{ marginTop: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={RiseOutlined}
          title="时间窗口"
          color="#fa8c16"
          subtitle="支持时间窗口类复杂事件处理"
        />

        <Form
          form={form}
          layout="vertical"
          onValuesChange={handleValuesChange}
        >
          <Form.Item
            label={
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>启用时间窗口</span>
                <Tag color={ruleInfo.windowEnabled === 1 ? 'green' : 'default'}>
                  {ruleInfo.windowEnabled === 1 ? '已启用' : '未启用'}
                </Tag>
              </Space>
            }
            name="windowEnabled"
            valuePropName="checked"
            style={{ marginBottom: 16 }}
          >
            <Switch checkedChildren="ON" unCheckedChildren="OFF" />
          </Form.Item>

          {ruleInfo.windowEnabled === 1 && (
            <>
              <Alert
                message="示例：10分钟内温度上升超过5度且无人则告警"
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
              />

              <Form.Item
                label="窗口类型"
                name="windowType"
                style={{ marginBottom: 16 }}
              >
                <Select options={windowTypeOptions.map(o => ({ value: o.value, label: o.label }))} />
              </Form.Item>

              <Form.Item
                label={
                  <Space>
                    <span>窗口时长</span>
                    <Text type="secondary">秒</Text>
                  </Space>
                }
                name="windowDuration"
                style={{ marginBottom: 16 }}
                extra="例如：600秒 = 10分钟"
              >
                <InputNumber
                  style={{ width: '100%' }}
                  min={1}
                  max={86400}
                  step={60}
                />
              </Form.Item>

              <Form.Item
                label="聚合函数"
                name="windowAggregation"
                style={{ marginBottom: 16 }}
              >
                <Select options={aggregationOptions.map(o => ({ value: o.value, label: o.label }))} />
              </Form.Item>

              <Form.Item
                label="监控字段"
                name="windowField"
                style={{ marginBottom: 16 }}
              >
                <Select options={windowFieldOptions.map(o => ({ value: o.value, label: o.label }))} />
              </Form.Item>

              <Form.Item
                label="比较运算符"
                name="windowOperator"
                style={{ marginBottom: 16 }}
              >
                <Select options={windowOperatorOptions.map(o => ({ value: o.value, label: o.label }))} />
              </Form.Item>

              <Form.Item
                label="阈值"
                name="windowThreshold"
                style={{ marginBottom: 0 }}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  step={0.5}
                  min={-1000}
                  max={1000}
                />
              </Form.Item>
            </>
          )}
        </Form>
      </Card>

      <Card
        size="small"
        bordered={false}
        style={{ marginTop: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={PauseCircleOutlined}
          title="重复触发间隔"
          color="#13c2c2"
          subtitle="防止动作频繁执行"
        />

        <Form
          form={form}
          layout="vertical"
          onValuesChange={handleValuesChange}
        >
          <Form.Item
            label={
              <Space>
                <span>冷却时间</span>
                <Text type="secondary">秒</Text>
                {ruleInfo.cooldownSeconds > 0 && (
                  <Tag color="cyan">{ruleInfo.cooldownSeconds}秒内只触发一次</Tag>
                )}
              </Space>
            }
            name="cooldownSeconds"
            style={{ marginBottom: 0 }}
            extra="例如：60秒 = 空调1分钟内只响应一次。0表示不限制。"
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              max={86400}
              step={10}
              placeholder="0表示无冷却"
            />
          </Form.Item>
        </Form>
      </Card>

      <Card
        size="small"
        bordered={false}
        style={{ marginTop: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={LinkOutlined}
          title="规则链"
          color="#722ed1"
          subtitle="规则触发后启用/禁用其他规则"
        />

        <Form
          form={form}
          layout="vertical"
          onValuesChange={handleValuesChange}
        >
          <Form.Item
            label={
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>启用规则链</span>
                <Tag color={ruleInfo.chainTriggerEnabled === 1 ? 'purple' : 'default'}>
                  {ruleInfo.chainTriggerEnabled === 1 ? '已启用' : '未启用'}
                </Tag>
              </Space>
            }
            name="chainTriggerEnabled"
            valuePropName="checked"
            style={{ marginBottom: 16 }}
          >
            <Switch checkedChildren="ON" unCheckedChildren="OFF" />
          </Form.Item>

          {ruleInfo.chainTriggerEnabled === 1 && (
            <>
              <Form.Item
                label="下一规则ID列表"
                name="chainNextRuleIds"
                style={{ marginBottom: 16 }}
                extra="多个规则用逗号分隔，例如：2,5,8"
              >
                <Input placeholder="例如：2,5,8" />
              </Form.Item>

              <Form.Item
                label="触发后禁用自身"
                name="chainDisableSelf"
                valuePropName="checked"
                style={{ marginBottom: 0 }}
                extra="当前规则触发后自动禁用自身，由下一规则接管"
              >
                <Switch checkedChildren="ON" unCheckedChildren="OFF" />
              </Form.Item>
            </>
          )}
        </Form>
      </Card>
    </div>
  )
}

function StartEndPanel() {
  const [form] = Form.useForm()
  const selectedNode = useRuleStore((state) => state.selectedNode)
  const updateNode = useRuleStore((state) => state.updateNode)

  const isStart = selectedNode?.type === 'start'
  const themeColor = isStart ? '#52c41a' : '#ff4d4f'

  useEffect(() => {
    if (selectedNode) {
      form.setFieldsValue(selectedNode.data)
    }
  }, [selectedNode?.id])

  const handleValuesChange = (_, allValues) => {
    if (selectedNode) {
      updateNode(selectedNode.id, allValues)
    }
  }

  return (
    <div style={{ padding: 12, height: '100%', overflowY: 'auto' }}>
      <Card
        size="small"
        bordered={false}
        style={{ boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={isStart ? PlayCircleOutlined : StopOutlined}
          title={isStart ? '开始节点配置' : '结束节点配置'}
          color={themeColor}
          subtitle={isStart ? '规则流程的起点' : '规则流程的终点'}
        />

        <Form
          form={form}
          layout="vertical"
          initialValues={selectedNode?.data}
          onValuesChange={handleValuesChange}
        >
          <Form.Item label="节点标签" name="label" style={{ marginBottom: 0 }}>
            <Input placeholder={isStart ? '开始' : '结束'} />
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

function ConditionPanel() {
  const [form] = Form.useForm()
  const selectedNode = useRuleStore((state) => state.selectedNode)
  const updateNode = useRuleStore((state) => state.updateNode)

  const data = selectedNode?.data || {}
  const isLogicGate = !!data.logicGate
  const meta = isLogicGate
    ? logicGateMeta[data.logicGate]
    : conditionTypeMeta[data.conditionType]

  useEffect(() => {
    if (selectedNode) {
      const initData = { ...selectedNode.data }
      if (data.conditionType === 'time') {
        initData.startTime = initData.startTime ? dayjs(initData.startTime, 'HH:mm') : dayjs('08:00', 'HH:mm')
        initData.endTime = initData.endTime ? dayjs(initData.endTime, 'HH:mm') : dayjs('18:00', 'HH:mm')
      }
      form.setFieldsValue(initData)
    }
  }, [selectedNode?.id])

  const handleValuesChange = (changedValues, allValues) => {
    if (!selectedNode) return

    const saveValues = { ...allValues }

    if (saveValues.startTime && dayjs.isDayjs(saveValues.startTime)) {
      saveValues.startTime = saveValues.startTime.format('HH:mm')
    }
    if (saveValues.endTime && dayjs.isDayjs(saveValues.endTime)) {
      saveValues.endTime = saveValues.endTime.format('HH:mm')
    }

    updateNode(selectedNode.id, saveValues)
  }

  const renderLogicGateForm = () => (
    <Form form={form} layout="vertical" initialValues={data} onValuesChange={handleValuesChange}>
      <Form.Item label="节点标签" name="label" style={{ marginBottom: 16 }}>
        <Input placeholder="AND / OR" />
      </Form.Item>

      <Form.Item label="逻辑门类型" name="logicGate" style={{ marginBottom: 0 }}>
        <Radio.Group size="large" style={{ width: '100%' }}>
          <Radio.Button value="AND" style={{ width: '50%', textAlign: 'center' }}>
            <Space direction="vertical" size={2} style={{ padding: '4px 0' }}>
              <strong style={{ fontSize: 14, color: '#52c41a' }}>AND</strong>
              <span style={{ fontSize: 10, color: '#888' }}>全部满足</span>
            </Space>
          </Radio.Button>
          <Radio.Button value="OR" style={{ width: '50%', textAlign: 'center' }}>
            <Space direction="vertical" size={2} style={{ padding: '4px 0' }}>
              <strong style={{ fontSize: 14, color: '#eb2f96' }}>OR</strong>
              <span style={{ fontSize: 10, color: '#888' }}>任一满足</span>
            </Space>
          </Radio.Button>
        </Radio.Group>
      </Form.Item>
    </Form>
  )

  const renderSensorForm = () => {
    const unit = meta?.unit || ''

    if (data.conditionType === 'time') {
      return (
        <Form form={form} layout="vertical" initialValues={data} onValuesChange={handleValuesChange}>
          <Form.Item label="节点标签" name="label" style={{ marginBottom: 16 }}>
            <Input />
          </Form.Item>

          <Form.Item label="时间范围" required style={{ marginBottom: 16 }}>
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item name="startTime" noStyle>
                <TimePicker format="HH:mm" style={{ width: '50%' }} placeholder="开始时间" />
              </Form.Item>
              <Form.Item name="endTime" noStyle>
                <TimePicker format="HH:mm" style={{ width: '50%' }} placeholder="结束时间" />
              </Form.Item>
            </Space.Compact>
          </Form.Item>

          <Form.Item label="判断逻辑" name="operator" style={{ marginBottom: 0 }}>
            <Select>
              <Option value="between">在范围内</Option>
              <Option value="not_between">不在范围内</Option>
            </Select>
          </Form.Item>
        </Form>
      )
    }

    if (data.conditionType === 'presence') {
      return (
        <Form form={form} layout="vertical" initialValues={data} onValuesChange={handleValuesChange}>
          <Form.Item label="节点标签" name="label" style={{ marginBottom: 16 }}>
            <Input />
          </Form.Item>

          <Form.Item label="判断条件" name="threshold" style={{ marginBottom: 0 }}>
            <Radio.Group>
              <Radio.Button value={true}>检测到有人</Radio.Button>
              <Radio.Button value={false}>检测到无人</Radio.Button>
            </Radio.Group>
          </Form.Item>
        </Form>
      )
    }

    return (
      <Form form={form} layout="vertical" initialValues={data} onValuesChange={handleValuesChange}>
        <Form.Item label="节点标签" name="label" style={{ marginBottom: 16 }}>
          <Input />
        </Form.Item>

        <Form.Item label="比较运算符" name="operator" style={{ marginBottom: 16 }}>
          <Select options={operatorOptions} />
        </Form.Item>

        <Form.Item
          label={
            <Space>
              <span>阈值</span>
              <Text type="secondary">{unit}</Text>
            </Space>
          }
          name="threshold"
          style={{ marginBottom: 16 }}
        >
          <InputNumber
            style={{ width: '100%' }}
            step={data.conditionType === 'temperature' ? 0.5 : 1}
            min={-100}
            max={999}
          />
        </Form.Item>

        <Form.Item label="单位" name="unit" style={{ marginBottom: 0 }}>
          <Select>
            <Option value={unit}>{unit}</Option>
            <Option value="">无</Option>
          </Select>
        </Form.Item>
      </Form>
    )
  }

  return (
    <div style={{ padding: 12, height: '100%', overflowY: 'auto' }}>
      <Card
        size="small"
        bordered={false}
        style={{ boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={meta?.icon || SettingOutlined}
          title={meta?.label || '条件节点'}
          color={meta?.color || '#1890ff'}
          subtitle={isLogicGate ? meta?.desc : '条件满足时继续执行后续流程'}
        />

        {isLogicGate ? renderLogicGateForm() : renderSensorForm()}
      </Card>
    </div>
  )
}

function ActionPanel() {
  const [form] = Form.useForm()
  const selectedNode = useRuleStore((state) => state.selectedNode)
  const updateNode = useRuleStore((state) => state.updateNode)

  const data = selectedNode?.data || {}
  const meta = actionTypeMeta[data.actionType] || { label: '动作', icon: SettingOutlined, color: '#faad14' }

  const filteredDevices = deviceOptions.filter((d) => {
    if (data.actionType?.includes('aircon')) return d.type === 'aircon' || d.type === 'all'
    if (data.actionType?.includes('light')) return d.type === 'light' || d.type === 'all'
    return true
  })

  useEffect(() => {
    if (selectedNode) {
      form.setFieldsValue(selectedNode.data)
    }
  }, [selectedNode?.id])

  const handleValuesChange = (changedValues, allValues) => {
    if (selectedNode) {
      updateNode(selectedNode.id, allValues)
    }
  }

  const hasTemperature = data.actionType?.includes('aircon') && data.actionType?.includes('on')
  const hasAlert = data.actionType === 'send_alert'

  return (
    <div style={{ padding: 12, height: '100%', overflowY: 'auto' }}>
      <Card
        size="small"
        bordered={false}
        style={{ boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 16 }}
      >
        <SectionHeader
          icon={meta.icon}
          title={meta.label}
          color={meta.color}
          subtitle="条件满足时执行的动作"
        />

        <Form
          form={form}
          layout="vertical"
          initialValues={data}
          onValuesChange={handleValuesChange}
        >
          <Form.Item label="节点标签" name="label" style={{ marginBottom: 16 }}>
            <Input />
          </Form.Item>

          <Form.Item label="目标设备" name="deviceId" style={{ marginBottom: 16 }}>
            <Select
              placeholder="请选择目标设备"
              options={filteredDevices.map((d) => ({ value: d.value, label: d.label }))}
              showSearch
              allowClear
            />
          </Form.Item>

          {hasTemperature && (
            <Form.Item
              label={
                <Space>
                  <span>空调温度</span>
                  <Tag color="blue">{data.temperature ?? 25}°C</Tag>
                </Space>
              }
              name="temperature"
              style={{ marginBottom: 16 }}
            >
              <Slider
                min={16}
                max={30}
                step={1}
                marks={{
                  16: '16°C',
                  22: '22°C',
                  25: '25°C',
                  30: '30°C'
                }}
                tooltip={{ formatter: (v) => `${v}°C` }}
              />
            </Form.Item>
          )}

          {hasAlert && (
            <>
              <Form.Item
                label="告警消息"
                name="message"
                rules={[{ required: true, message: '请输入告警消息内容' }]}
                style={{ marginBottom: 16 }}
              >
                <TextArea
                  rows={3}
                  placeholder="例如：客厅温度过高，请检查设备"
                  showCount
                  maxLength={200}
                />
              </Form.Item>

              <Form.Item label="告警级别" name="level" style={{ marginBottom: 0 }}>
                <Radio.Group>
                  {alertLevelOptions.map((opt) => (
                    <Radio.Button key={opt.value} value={opt.value}>
                      <Space size={4}>
                        <span
                          style={{
                            width: 8,
                            height: 8,
                            borderRadius: '50%',
                            background: opt.color,
                            display: 'inline-block'
                          }}
                        />
                        {opt.label}
                      </Space>
                    </Radio.Button>
                  ))}
                </Radio.Group>
              </Form.Item>
            </>
          )}
        </Form>
      </Card>
    </div>
  )
}

function ConfigPanel() {
  const selectedNode = useRuleStore((state) => state.selectedNode)

  if (!selectedNode) {
    return <RuleInfoPanel />
  }

  switch (selectedNode.type) {
    case 'condition':
      return <ConditionPanel />
    case 'action':
      return <ActionPanel />
    case 'start':
    case 'end':
      return <StartEndPanel />
    default:
      return <RuleInfoPanel />
  }
}

export default ConfigPanel
