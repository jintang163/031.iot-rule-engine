import { Handle, Position } from 'reactflow'

const conditionTypeLabels = {
  temperature: { label: '温度', icon: '🌡️', color: '#fa8c16' },
  humidity: { label: '湿度', icon: '💧', color: '#1890ff' },
  presence: { label: '人体', icon: '👤', color: '#722ed1' },
  time: { label: '时间', icon: '⏰', color: '#13c2c2' }
}

const operatorLabels = {
  '>': '>',
  '<': '<',
  '==': '=',
  '>=': '≥',
  '<=': '≤',
  '!=': '≠',
  'between': '在范围内'
}

function ConditionNode({ data, selected }) {
  const isLogicGate = data.logicGate
  const config = conditionTypeLabels[data.conditionType]

  if (isLogicGate) {
    const isAnd = data.logicGate === 'AND'
    const gateColor = isAnd ? '#52c41a' : '#eb2f96'
    const gateBg = isAnd ? '#f6ffed' : '#fff0f6'

    return (
      <div
        style={{
          padding: 12,
          border: selected ? `2px solid ${gateColor}` : `1px solid ${gateColor}`,
          borderRadius: 50,
          background: gateBg,
          minWidth: 100,
          textAlign: 'center',
          boxShadow: selected ? `0 0 0 3px ${gateColor}33` : '0 2px 8px rgba(0,0,0,0.06)',
          transition: 'all 0.2s ease'
        }}
      >
        <Handle type="target" position={Position.Left} style={{ background: gateColor, border: '2px solid #fff' }} />
        <Handle type="target" position={Position.Top} style={{ background: gateColor, border: '2px solid #fff', left: '50%' }} />
        <div style={{ fontWeight: 700, fontSize: 16, color: gateColor, letterSpacing: 1 }}>
          {data.logicGate}
        </div>
        <div style={{ fontSize: 10, color: '#666', marginTop: 2 }}>
          {isAnd ? '所有条件满足' : '任一条件满足'}
        </div>
        <Handle type="source" position={Position.Right} style={{ background: gateColor, border: '2px solid #fff' }} />
        <Handle type="source" position={Position.Bottom} style={{ background: gateColor, border: '2px solid #fff', left: '50%' }} />
      </div>
    )
  }

  const themeColor = config?.color || '#1890ff'
  const themeBg = `${themeColor}10`

  const renderContent = () => {
    if (data.conditionType === 'time') {
      return (
        <>
          <div style={{ fontSize: 11, color: '#666', marginTop: 4 }}>
            {data.startTime} ~ {data.endTime}
          </div>
        </>
      )
    }
    if (data.conditionType === 'presence') {
      return (
        <div style={{ fontSize: 12, fontWeight: 600, marginTop: 4, color: themeColor }}>
          {data.threshold ? '有人' : '无人'}
        </div>
      )
    }
    return (
      <div style={{ fontSize: 12, fontWeight: 600, marginTop: 4, color: themeColor }}>
        {operatorLabels[data.operator] || data.operator} {data.threshold}{data.unit || ''}
      </div>
    )
  }

  return (
    <div
      style={{
        padding: 10,
        border: selected ? `2px solid ${themeColor}` : `1px solid ${themeColor}`,
        borderRadius: 8,
        background: themeBg,
        minWidth: 140,
        boxShadow: selected ? `0 0 0 3px ${themeColor}33` : '0 2px 8px rgba(0,0,0,0.06)',
        transition: 'all 0.2s ease'
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{ background: themeColor, width: 8, height: 8, border: '2px solid #fff', left: -5 }}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={{ fontSize: 16 }}>{config?.icon || '🔍'}</span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 12, color: '#333', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {data.label || config?.label || '条件'}
          </div>
        </div>
      </div>
      {renderContent()}
      <Handle
        type="source"
        position={Position.Right}
        style={{ background: themeColor, width: 8, height: 8, border: '2px solid #fff', right: -5 }}
      />
    </div>
  )
}

export default ConditionNode
