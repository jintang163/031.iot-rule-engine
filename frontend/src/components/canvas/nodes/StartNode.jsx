import { Handle, Position } from 'reactflow'

function StartNode({ data, selected }) {
  return (
    <div
      style={{
        padding: '12px 20px',
        border: selected ? '3px solid #52c41a' : '2px solid #52c41a',
        borderRadius: 50,
        background: 'linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%)',
        minWidth: 80,
        textAlign: 'center',
        boxShadow: selected ? '0 0 0 3px #52c41a33, 0 4px 12px rgba(82,196,26,0.2)' : '0 2px 8px rgba(82,196,26,0.15)',
        transition: 'all 0.2s ease'
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
        <span style={{ fontSize: 16 }}>▶️</span>
        <span style={{ fontWeight: 700, fontSize: 13, color: '#389e0d', letterSpacing: 0.5 }}>
          {data.label || '开始'}
        </span>
      </div>
      <Handle
        type="source"
        position={Position.Right}
        style={{
          background: '#52c41a',
          width: 10,
          height: 10,
          border: '2px solid #fff',
          right: -6
        }}
      />
    </div>
  )
}

export default StartNode
