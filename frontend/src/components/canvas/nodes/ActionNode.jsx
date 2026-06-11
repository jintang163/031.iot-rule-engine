import { Handle, Position } from 'reactflow'

const actionTypeConfig = {
  turn_on_aircon: { label: '开空调', icon: '❄️', color: '#1890ff' },
  turn_off_aircon: { label: '关空调', icon: '🔌', color: '#8c8c8c' },
  turn_on_light: { label: '开灯', icon: '💡', color: '#faad14' },
  turn_off_light: { label: '关灯', icon: '💤', color: '#8c8c8c' },
  send_alert: { label: '推送告警', icon: '🔔', color: '#ff4d4f' }
}

function ActionNode({ data, selected }) {
  const config = actionTypeConfig[data.actionType] || { label: data.label || '动作', icon: '🎯', color: '#faad14' }
  const themeColor = config.color
  const themeBg = `${themeColor}12`

  const renderParams = () => {
    const params = []
    if (data.temperature !== undefined) {
      params.push(`🌡️ ${data.temperature}°C`)
    }
    if (data.message) {
      params.push(
        <span key="msg" style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 180 }}>
          📝 {data.message}
        </span>
      )
    }
    if (data.level) {
      const levelColors = { info: '#1890ff', warning: '#faad14', error: '#ff4d4f' }
      params.push(
        <span key="lvl" style={{ color: levelColors[data.level] || '#666' }}>
          ⚠️ {data.level}
        </span>
      )
    }
    if (params.length === 0) {
      params.push(<span key="target" style={{ color: '#999' }}>{data.deviceId || '未指定设备'}</span>)
    }
    return params
  }

  return (
    <div
      style={{
        padding: 10,
        border: selected ? `2px solid ${themeColor}` : `1px solid ${themeColor}`,
        borderRadius: 6,
        background: themeBg,
        minWidth: 160,
        boxShadow: selected ? `0 0 0 3px ${themeColor}33` : '0 2px 8px rgba(0,0,0,0.06)',
        transition: 'all 0.2s ease'
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{ background: themeColor, width: 8, height: 8, border: '2px solid #fff', left: -5 }}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 6,
            background: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 16,
            boxShadow: '0 1px 4px rgba(0,0,0,0.08)'
          }}
        >
          {config.icon}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: '#333' }}>
            {data.label || config.label}
          </div>
          <div style={{ fontSize: 11, color: themeColor, fontWeight: 500 }}>
            {data.actionType}
          </div>
        </div>
      </div>
      <div
        style={{
          marginTop: 8,
          paddingTop: 8,
          borderTop: `1px dashed ${themeColor}40`,
          fontSize: 11,
          color: '#555',
          lineHeight: 1.6
        }}
      >
        {renderParams()}
      </div>
      <Handle
        type="source"
        position={Position.Right}
        style={{ background: themeColor, width: 8, height: 8, border: '2px solid #fff', right: -5 }}
      />
    </div>
  )
}

export default ActionNode
