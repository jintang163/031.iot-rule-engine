import { Handle, Position } from 'reactflow'

function EndNode({ data, selected }) {
  return (
    <div
      style={{
        padding: '12px 20px',
        border: selected ? '3px solid #ff4d4f' : '2px solid #ff4d4f',
        borderRadius: 50,
        background: 'linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%)',
        minWidth: 80,
        textAlign: 'center',
        boxShadow: selected ? '0 0 0 3px #ff4d4f33, 0 4px 12px rgba(255,77,79,0.2)' : '0 2px 8px rgba(255,77,79,0.15)',
        transition: 'all 0.2s ease'
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{
          background: '#ff4d4f',
          width: 10,
          height: 10,
          border: '2px solid #fff',
          left: -6
        }}
      />
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
        <span style={{ fontSize: 16 }}>⏹️</span>
        <span style={{ fontWeight: 700, fontSize: 13, color: '#cf1322', letterSpacing: 0.5 }}>
          {data.label || '结束'}
        </span>
      </div>
    </div>
  )
}

export default EndNode
