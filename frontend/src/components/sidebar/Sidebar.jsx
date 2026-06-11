import { useState } from 'react'
import { Card, Collapse, Typography } from 'antd'
import {
  ThunderboltOutlined,
  EnvironmentOutlined,
  TeamOutlined,
  ClockCircleOutlined,
  BorderOutlined,
  SplitCellsOutlined,
  CloudServerOutlined,
  BulbOutlined,
  BulbFilled,
  NotificationOutlined
} from '@ant-design/icons'

const { Panel } = Collapse
const { Text } = Typography

const conditionComponents = [
  {
    key: 'temperature',
    type: 'condition',
    conditionType: 'temperature',
    label: '温度传感器',
    icon: EnvironmentOutlined,
    description: '监测环境温度',
    color: '#fa8c16',
    defaultData: {
      label: '温度传感器',
      conditionType: 'temperature',
      operator: '>',
      threshold: 30,
      unit: '°C'
    }
  },
  {
    key: 'humidity',
    type: 'condition',
    conditionType: 'humidity',
    label: '湿度传感器',
    icon: ThunderboltOutlined,
    description: '监测环境湿度',
    color: '#1890ff',
    defaultData: {
      label: '湿度传感器',
      conditionType: 'humidity',
      operator: '<',
      threshold: 80,
      unit: '%'
    }
  },
  {
    key: 'presence',
    type: 'condition',
    conditionType: 'presence',
    label: '人体存在传感器',
    icon: TeamOutlined,
    description: '检测是否有人',
    color: '#722ed1',
    defaultData: {
      label: '人体存在传感器',
      conditionType: 'presence',
      operator: '==',
      threshold: true,
      unit: ''
    }
  },
  {
    key: 'time',
    type: 'condition',
    conditionType: 'time',
    label: '时间条件',
    icon: ClockCircleOutlined,
    description: '时间范围判断',
    color: '#13c2c2',
    defaultData: {
      label: '时间条件',
      conditionType: 'time',
      startTime: '08:00',
      endTime: '18:00',
      operator: 'between'
    }
  },
  {
    key: 'and_gate',
    type: 'condition',
    logicGate: 'AND',
    label: '逻辑AND门',
    icon: BorderOutlined,
    description: '所有条件满足',
    color: '#52c41a',
    defaultData: {
      label: 'AND 门',
      logicGate: 'AND'
    }
  },
  {
    key: 'or_gate',
    type: 'condition',
    logicGate: 'OR',
    label: '逻辑OR门',
    icon: SplitCellsOutlined,
    description: '任一条件满足',
    color: '#eb2f96',
    defaultData: {
      label: 'OR 门',
      logicGate: 'OR'
    }
  }
]

const actionComponents = [
  {
    key: 'turn_on_aircon',
    type: 'action',
    actionType: 'turn_on_aircon',
    label: '开空调',
    icon: CloudServerOutlined,
    description: '启动空调设备',
    color: '#1890ff',
    defaultData: {
      label: '开空调',
      actionType: 'turn_on_aircon',
      temperature: 25,
      deviceId: ''
    }
  },
  {
    key: 'turn_off_aircon',
    type: 'action',
    actionType: 'turn_off_aircon',
    label: '关空调',
    icon: CloudServerOutlined,
    description: '关闭空调设备',
    color: '#8c8c8c',
    defaultData: {
      label: '关空调',
      actionType: 'turn_off_aircon',
      deviceId: ''
    }
  },
  {
    key: 'turn_on_light',
    type: 'action',
    actionType: 'turn_on_light',
    label: '开灯',
    icon: BulbFilled,
    description: '打开照明设备',
    color: '#faad14',
    defaultData: {
      label: '开灯',
      actionType: 'turn_on_light',
      deviceId: ''
    }
  },
  {
    key: 'turn_off_light',
    type: 'action',
    actionType: 'turn_off_light',
    label: '关灯',
    icon: BulbOutlined,
    description: '关闭照明设备',
    color: '#8c8c8c',
    defaultData: {
      label: '关灯',
      actionType: 'turn_off_light',
      deviceId: ''
    }
  },
  {
    key: 'send_alert',
    type: 'action',
    actionType: 'send_alert',
    label: '推送告警',
    icon: NotificationOutlined,
    description: '发送告警消息',
    color: '#ff4d4f',
    defaultData: {
      label: '推送告警',
      actionType: 'send_alert',
      message: '设备异常告警',
      level: 'warning'
    }
  }
]

const startEndComponents = [
  {
    key: 'start',
    type: 'start',
    label: '开始节点',
    icon: BorderOutlined,
    description: '规则流程起点',
    color: '#52c41a',
    defaultData: {
      label: '开始'
    }
  },
  {
    key: 'end',
    type: 'end',
    label: '结束节点',
    icon: BorderOutlined,
    description: '规则流程终点',
    color: '#ff4d4f',
    defaultData: {
      label: '结束'
    }
  }
]

function DraggableCard({ item }) {
  const IconComponent = item.icon

  const onDragStart = (event) => {
    event.dataTransfer.setData('application/reactflow', JSON.stringify({
      type: item.type,
      data: item.defaultData,
      key: item.key
    }))
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <Card
      size="small"
      draggable
      onDragStart={onDragStart}
      hoverable
      style={{
        cursor: 'grab',
        marginBottom: 8,
        borderLeft: `3px solid ${item.color}`,
        borderRadius: 6
      }}
      bodyStyle={{ padding: 10 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div
          style={{
            width: 28,
            height: 28,
            borderRadius: 6,
            background: `${item.color}15`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: item.color,
            fontSize: 14
          }}
        >
          <IconComponent />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 13, lineHeight: 1.4 }}>{item.label}</div>
          <Text type="secondary" style={{ fontSize: 11 }}>{item.description}</Text>
        </div>
      </div>
    </Card>
  )
}

function Sidebar() {
  const [activeKeys, setActiveKeys] = useState(['startend', 'conditions', 'actions'])

  return (
    <div style={{ padding: 12, height: '100%', overflowY: 'auto' }}>
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>组件库</div>
        <Text type="secondary" style={{ fontSize: 12 }}>拖拽组件到画布构建规则</Text>
      </div>

      <Collapse
        activeKey={activeKeys}
        onChange={(keys) => setActiveKeys(keys)}
        ghost
        expandIconPosition="end"
        style={{ background: 'transparent' }}
      >
        <Panel
          key="startend"
          header={
            <span style={{ fontWeight: 600 }}>
              <span style={{ color: '#52c41a', marginRight: 6 }}>●</span>
              流程控制
            </span>
          }
        >
          {startEndComponents.map((item) => (
            <DraggableCard key={item.key} item={item} />
          ))}
        </Panel>

        <Panel
          key="conditions"
          header={
            <span style={{ fontWeight: 600 }}>
              <span style={{ color: '#1890ff', marginRight: 6 }}>●</span>
              条件组件
            </span>
          }
        >
          {conditionComponents.map((item) => (
            <DraggableCard key={item.key} item={item} />
          ))}
        </Panel>

        <Panel
          key="actions"
          header={
            <span style={{ fontWeight: 600 }}>
              <span style={{ color: '#faad14', marginRight: 6 }}>●</span>
              动作组件
            </span>
          }
        >
          {actionComponents.map((item) => (
            <DraggableCard key={item.key} item={item} />
          ))}
        </Panel>
      </Collapse>
    </div>
  )
}

export default Sidebar
