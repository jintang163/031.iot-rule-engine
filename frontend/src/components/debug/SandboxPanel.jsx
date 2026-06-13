import { useState, useCallback, useRef } from 'react'
import {
  Card, Button, Space, Tag, Input, InputNumber, Switch, Select,
  Steps, Timeline, Empty, Divider, Tooltip, Badge, Collapse, Alert
} from 'antd'
import {
  PlayCircleOutlined, StepForwardOutlined, PauseCircleOutlined,
  StopOutlined, ForwardOutlined, PlusOutlined, DeleteOutlined,
  ExperimentOutlined, BugOutlined, CheckCircleFilled, CloseCircleFilled,
  InfoCircleFilled, ThunderboltOutlined, ReloadOutlined
} from '@ant-design/icons'
import { sandboxTest, startDebugSession, getDebugStatus, debugStepNext, debugResume, debugStop } from '../../services/debugApi'
import useRuleStore from '../../store/useRuleStore'

const { Option } = Select

const sensorPresets = [
  { key: 'temperature', label: '温度', type: 'number', unit: '°C', defaultVal: 25 },
  { key: 'humidity', label: '湿度', type: 'number', unit: '%', defaultVal: 60 },
  { key: 'presence', label: '人体', type: 'boolean', defaultVal: false },
  { key: 'time', label: '时间', type: 'string', defaultVal: '12:00' }
]

const typeColorMap = {
  CONDITION: '#fa8c16',
  ACTION: '#1890ff',
  THRESHOLD: '#eb2f96',
  TIME_RANGE: '#13c2c2',
  OPERATOR: '#52c41a',
  TRIGGER: '#722ed1'
}

const typeLabelMap = {
  CONDITION: '条件',
  ACTION: '动作',
  THRESHOLD: '阈值',
  TIME_RANGE: '时间范围',
  OPERATOR: '逻辑',
  TRIGGER: '触发'
}

function SensorInputPanel({ sensorData, onChange }) {
  const addSensor = (preset) => {
    if (sensorData.find(s => s.key === preset.key)) return
    const newEntry = { key: preset.key, label: preset.label, type: preset.type, value: preset.defaultVal }
    onChange([...sensorData, newEntry])
  }

  const addCustom = () => {
    const idx = sensorData.length + 1
    onChange([...sensorData, { key: `custom_${idx}`, label: `自定义${idx}`, type: 'number', value: 0 }])
  }

  const removeSensor = (idx) => {
    onChange(sensorData.filter((_, i) => i !== idx))
  }

  const updateSensor = (idx, field, val) => {
    const updated = [...sensorData]
    updated[idx] = { ...updated[idx], [field]: val }
    onChange(updated)
  }

  const existingKeys = sensorData.map(s => s.key)
  const availablePresets = sensorPresets.filter(p => !existingKeys.includes(p.key))

  return (
    <div>
      <div style={{ marginBottom: 12, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        {availablePresets.map(preset => (
          <Button
            key={preset.key}
            size="small"
            icon={<PlusOutlined />}
            onClick={() => addSensor(preset)}
          >
            {preset.label}
          </Button>
        ))}
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addCustom}>
          自定义
        </Button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {sensorData.map((sensor, idx) => (
          <div
            key={idx}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 8px',
              background: '#fafafa',
              borderRadius: 6,
              border: '1px solid #f0f0f0'
            }}
          >
            <Input
              size="small"
              value={sensor.label}
              onChange={e => updateSensor(idx, 'label', e.target.value)}
              style={{ width: 80 }}
              placeholder="名称"
            />
            <Input
              size="small"
              value={sensor.key}
              onChange={e => updateSensor(idx, 'key', e.target.value)}
              style={{ width: 100 }}
              placeholder="字段key"
            />
            <Select
              size="small"
              value={sensor.type}
              onChange={val => {
                updateSensor(idx, 'type', val)
                if (val === 'boolean') updateSensor(idx, 'value', false)
                else if (val === 'number') updateSensor(idx, 'value', 0)
                else updateSensor(idx, 'value', '')
              }}
              style={{ width: 80 }}
            >
              <Option value="number">数值</Option>
              <Option value="boolean">布尔</Option>
              <Option value="string">文本</Option>
            </Select>
            {sensor.type === 'boolean' ? (
              <Switch
                size="small"
                checked={!!sensor.value}
                onChange={val => updateSensor(idx, 'value', val)}
              />
            ) : sensor.type === 'number' ? (
              <InputNumber
                size="small"
                value={sensor.value}
                onChange={val => updateSensor(idx, 'value', val)}
                style={{ flex: 1 }}
              />
            ) : (
              <Input
                size="small"
                value={sensor.value}
                onChange={e => updateSensor(idx, 'value', e.target.value)}
                style={{ flex: 1 }}
              />
            )}
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => removeSensor(idx)}
            />
          </div>
        ))}
      </div>

      {sensorData.length === 0 && (
        <div style={{ color: '#bbb', textAlign: 'center', padding: '12px 0', fontSize: 12 }}>
          点击上方按钮添加传感器模拟值
        </div>
      )}
    </div>
  )
}

function DebugStepLog({ steps }) {
  if (!steps || steps.length === 0) {
    return <Empty description="暂无执行步骤" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  return (
    <Timeline
      items={steps.map((step, idx) => {
        const isAction = step.nodeType === 'ACTION'
        const isSuccess = step.conditionResult
        const color = isAction ? '#1890ff' : (isSuccess ? '#52c41a' : '#ff4d4f')
        const dotIcon = isAction
          ? <ThunderboltOutlined style={{ color: '#1890ff', fontSize: 14 }} />
          : (isSuccess
            ? <CheckCircleFilled style={{ color: '#52c41a', fontSize: 14 }} />
            : <CloseCircleFilled style={{ color: '#ff4d4f', fontSize: 14 }} />)

        return {
          color,
          dot: dotIcon,
          children: (
            <div style={{ paddingBottom: 4 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
                <Tag color={typeColorMap[step.nodeType] || '#default'} style={{ margin: 0, fontSize: 10 }}>
                  {typeLabelMap[step.nodeType] || step.nodeType}
                </Tag>
                <span style={{ fontWeight: 600, fontSize: 12 }}>{step.nodeName || step.nodeId}</span>
                <span style={{ fontSize: 11, color: '#999' }}>#{step.stepIndex}</span>
              </div>
              {step.condition && (
                <div style={{ fontSize: 11, color: '#666', marginTop: 2, fontFamily: 'monospace' }}>
                  {step.condition}
                </div>
              )}
              {step.actualValue !== null && step.actualValue !== undefined && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 1 }}>
                  实际值: {String(step.actualValue)}
                </div>
              )}
              <div style={{ fontSize: 11, color: isSuccess ? '#52c41a' : '#ff4d4f', marginTop: 2 }}>
                {step.message || (isSuccess ? '通过' : '未通过')}
              </div>
            </div>
          )
        }
      })}
    />
  )
}

function SandboxPanel({ ruleId }) {
  const [mode, setMode] = useState('sandbox')
  const [sensorData, setSensorData] = useState([
    { key: 'temperature', label: '温度', type: 'number', value: 35 },
    { key: 'presence', label: '人体', type: 'boolean', value: false }
  ])
  const [sandboxResult, setSandboxResult] = useState(null)
  const [loading, setLoading] = useState(false)

  const [debugSessionId, setDebugSessionId] = useState(null)
  const [debugStatus, setDebugStatus] = useState(null)
  const [singleStepMode, setSingleStepMode] = useState(true)
  const pollRef = useRef(null)

  const { nodes, edges } = useRuleStore(state => ({ nodes: state.nodes, edges: state.edges }))

  const getRuleJson = useCallback(() => {
    return JSON.stringify({ nodes, edges, version: '1.0' })
  }, [nodes, edges])

  const buildSensorMap = useCallback(() => {
    const map = {}
    sensorData.forEach(s => {
      map[s.key] = s.value
    })
    return map
  }, [sensorData])

  const handleSandboxTest = async () => {
    setLoading(true)
    setSandboxResult(null)
    try {
      const res = await sandboxTest({
        ruleId,
        sensorData: buildSensorMap(),
        ruleJson: getRuleJson()
      })
      setSandboxResult(res)
    } catch (err) {
      console.error('沙箱测试失败:', err)
      setSandboxResult({ error: true, message: err.message || '测试失败' })
    } finally {
      setLoading(false)
    }
  }

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const startPolling = (sessionId) => {
    stopPolling()
    pollRef.current = setInterval(async () => {
      try {
        const status = await getDebugStatus(sessionId)
        setDebugStatus(status)
        if (['COMPLETED', 'ERROR', 'STOPPED'].includes(status.state)) {
          stopPolling()
        }
      } catch {
        stopPolling()
      }
    }, 1000)
  }

  const handleStartDebug = async () => {
    setLoading(true)
    setDebugStatus(null)
    try {
      const res = await startDebugSession({
        ruleId,
        singleStepMode,
        sensorData: buildSensorMap()
      })
      setDebugSessionId(res.sessionId)
      setDebugStatus(res)
      if (res.state === 'PAUSED' || res.state === 'RUNNING') {
        startPolling(res.sessionId)
      }
    } catch (err) {
      console.error('启动调试失败:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleStep = async () => {
    if (!debugSessionId) return
    setLoading(true)
    try {
      const res = await debugStepNext(debugSessionId)
      setDebugStatus(res)
    } catch (err) {
      console.error('单步执行失败:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleResume = async () => {
    if (!debugSessionId) return
    setLoading(true)
    try {
      const res = await debugResume(debugSessionId)
      setDebugStatus(res)
    } catch (err) {
      console.error('继续执行失败:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleStopDebug = async () => {
    if (!debugSessionId) return
    stopPolling()
    setLoading(true)
    try {
      const res = await debugStop(debugSessionId)
      setDebugStatus(res)
    } catch (err) {
      console.error('停止调试失败:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleReset = () => {
    stopPolling()
    setSandboxResult(null)
    setDebugStatus(null)
    setDebugSessionId(null)
  }

  const isDebugActive = debugStatus && ['RUNNING', 'PAUSED', 'STEPPING', 'WAITING'].includes(debugStatus.state)

  const stateTagMap = {
    WAITING: { color: 'default', text: '等待中' },
    RUNNING: { color: 'processing', text: '运行中' },
    PAUSED: { color: 'warning', text: '已暂停' },
    STEPPING: { color: 'processing', text: '单步中' },
    COMPLETED: { color: 'success', text: '已完成' },
    ERROR: { color: 'error', text: '出错' },
    STOPPED: { color: 'default', text: '已停止' }
  }

  return (
    <div>
      <Card
        size="small"
        bordered={false}
        style={{ boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 12 }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <ExperimentOutlined style={{ color: '#722ed1', fontSize: 16 }} />
          <span style={{ fontWeight: 700, fontSize: 14, color: '#1f1f1f' }}>规则测试沙箱</span>
        </div>

        <div style={{ marginBottom: 12, display: 'flex', gap: 8 }}>
          <Button
            size="small"
            type={mode === 'sandbox' ? 'primary' : 'default'}
            icon={<ThunderboltOutlined />}
            onClick={() => { setMode('sandbox'); handleReset() }}
          >
            快速测试
          </Button>
          <Button
            size="small"
            type={mode === 'debug' ? 'primary' : 'default'}
            icon={<BugOutlined />}
            onClick={() => { setMode('debug'); handleReset() }}
          >
            断点调试
          </Button>
        </div>

        {mode === 'debug' && (
          <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: '#666' }}>单步模式</span>
            <Switch size="small" checked={singleStepMode} onChange={setSingleStepMode} />
            <Tooltip title="开启后每执行一个节点都会暂停，可逐步查看评估结果">
              <InfoCircleFilled style={{ color: '#bbb', fontSize: 12 }} />
            </Tooltip>
          </div>
        )}
      </Card>

      <Card
        size="small"
        bordered={false}
        style={{ marginTop: 8, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
        bodyStyle={{ padding: 12 }}
        title={<span style={{ fontSize: 12, fontWeight: 600 }}>模拟数据输入</span>}
      >
        <SensorInputPanel sensorData={sensorData} onChange={setSensorData} />

        <Divider style={{ margin: '12px 0 8px 0' }} />

        <div style={{ display: 'flex', gap: 8 }}>
          {mode === 'sandbox' ? (
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={loading}
              onClick={handleSandboxTest}
              block
            >
              执行沙箱测试
            </Button>
          ) : (
            <>
              {!isDebugActive ? (
                <Button
                  type="primary"
                  icon={<BugOutlined />}
                  loading={loading}
                  onClick={handleStartDebug}
                  block
                >
                  启动调试
                </Button>
              ) : (
                <div style={{ display: 'flex', gap: 6, width: '100%' }}>
                  <Button
                    size="small"
                    icon={<StepForwardOutlined />}
                    loading={loading}
                    onClick={handleStep}
                    disabled={debugStatus?.state !== 'PAUSED'}
                    style={{ flex: 1 }}
                  >
                    单步
                  </Button>
                  <Button
                    size="small"
                    icon={<ForwardOutlined />}
                    loading={loading}
                    onClick={handleResume}
                    disabled={debugStatus?.state !== 'PAUSED'}
                    style={{ flex: 1 }}
                  >
                    继续
                  </Button>
                  <Button
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    onClick={handleStopDebug}
                    style={{ flex: 1 }}
                  >
                    停止
                  </Button>
                </div>
              )}
            </>
          )}
          <Button icon={<ReloadOutlined />} onClick={handleReset} />
        </div>
      </Card>

      {mode === 'sandbox' && sandboxResult && (
        <Card
          size="small"
          bordered={false}
          style={{ marginTop: 8, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
          bodyStyle={{ padding: 12 }}
          title={<span style={{ fontSize: 12, fontWeight: 600 }}>测试结果</span>}
        >
          {sandboxResult.error ? (
            <Alert type="error" message={sandboxResult.message} />
          ) : (
            <>
              <Alert
                type={sandboxResult.matchedRuleCount > 0 ? 'success' : 'warning'}
                message={sandboxResult.summary || '测试完成'}
                style={{ marginBottom: 12 }}
              />

              {sandboxResult.conditionEvaluations?.length > 0 && (
                <div style={{ marginBottom: 12 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6, color: '#1f1f1f' }}>
                    规则命中情况
                  </div>
                  {sandboxResult.conditionEvaluations.map((evalItem, idx) => (
                    <div
                      key={idx}
                      style={{
                        padding: '6px 8px',
                        background: '#f6ffed',
                        border: '1px solid #b7eb8f',
                        borderRadius: 6,
                        marginBottom: 4,
                        fontSize: 12
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <CheckCircleFilled style={{ color: '#52c41a' }} />
                        <strong>{evalItem.ruleName || `规则 #${evalItem.ruleId}`}</strong>
                      </div>
                      {evalItem.matchedExpression && (
                        <div style={{ fontFamily: 'monospace', fontSize: 11, color: '#666', marginTop: 2, paddingLeft: 20 }}>
                          {evalItem.matchedExpression}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {sandboxResult.simulatedActions?.length > 0 && (
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6, color: '#1f1f1f' }}>
                    预期动作输出
                  </div>
                  {sandboxResult.simulatedActions.map((action, idx) => (
                    <div
                      key={idx}
                      style={{
                        padding: '6px 8px',
                        background: '#e6f7ff',
                        border: '1px solid #91d5ff',
                        borderRadius: 6,
                        marginBottom: 4,
                        fontSize: 12
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <ThunderboltOutlined style={{ color: '#1890ff' }} />
                        <strong>{action.actionType}</strong>
                        <Tag color="blue" style={{ fontSize: 10, margin: 0 }}>模拟</Tag>
                      </div>
                      {action.targetDeviceId && (
                        <div style={{ fontSize: 11, color: '#666', marginTop: 2, paddingLeft: 20 }}>
                          目标设备: {action.targetDeviceId}
                        </div>
                      )}
                      {action.params && Object.keys(action.params).length > 0 && (
                        <div style={{ fontSize: 11, color: '#666', marginTop: 1, paddingLeft: 20, fontFamily: 'monospace' }}>
                          {JSON.stringify(action.params)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {sandboxResult.matchedRuleCount === 0 && (
                <div style={{ color: '#8c8c8c', fontSize: 12, textAlign: 'center', padding: '8px 0' }}>
                  无规则被触发，当前传感器数据不满足任何规则条件
                </div>
              )}
            </>
          )}
        </Card>
      )}

      {mode === 'debug' && debugStatus && (
        <Card
          size="small"
          bordered={false}
          style={{ marginTop: 8, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}
          bodyStyle={{ padding: 12 }}
          title={<span style={{ fontSize: 12, fontWeight: 600 }}>调试状态</span>}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Tag color={stateTagMap[debugStatus.state]?.color || 'default'}>
              {stateTagMap[debugStatus.state]?.text || debugStatus.state}
            </Tag>
            <span style={{ fontSize: 11, color: '#999' }}>
              步骤 {debugStatus.currentStepIndex + 1} / {debugStatus.totalSteps}
            </span>
          </div>

          {debugStatus.message && (
            <div style={{ fontSize: 11, color: '#666', marginBottom: 8 }}>
              {debugStatus.message}
            </div>
          )}

          <Divider style={{ margin: '8px 0' }} />

          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6, color: '#1f1f1f' }}>
            执行日志
          </div>
          <div style={{ maxHeight: 300, overflowY: 'auto' }}>
            <DebugStepLog steps={debugStatus.executionSteps} />
          </div>
        </Card>
      )}
    </div>
  )
}

export default SandboxPanel
