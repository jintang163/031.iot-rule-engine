import { useState, useEffect } from 'react'
import { Tabs, Empty, Spin, Tag, List, Card, Row, Col, Typography, Divider } from 'antd'
import { PlusOutlined, MinusOutlined, EditOutlined } from '@ant-design/icons'
import { compareWithCurrent, compareVersions } from '../../services/versionApi'

const { Title, Text } = Typography

function VersionDiffViewer({ ruleId, fromVersion, toVersion, compareMode = 'current' }) {
  const [diffData, setDiffData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('all')

  useEffect(() => {
    if (ruleId && fromVersion) {
      fetchDiff()
    }
  }, [ruleId, fromVersion, toVersion, compareMode])

  const fetchDiff = async () => {
    if (!ruleId || !fromVersion) return
    setLoading(true)
    try {
      let res
      if (compareMode === 'current') {
        res = await compareWithCurrent(ruleId, fromVersion)
      } else {
        res = await compareVersions(ruleId, fromVersion, toVersion)
      }
      setDiffData(res)
    } catch (error) {
      console.error('获取版本差异失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const getChangeTypeIcon = (changeType) => {
    switch (changeType) {
      case 'ADD':
        return <PlusOutlined style={{ color: '#52c41a' }} />
      case 'REMOVE':
        return <MinusOutlined style={{ color: '#ff4d4f' }} />
      case 'MODIFY':
        return <EditOutlined style={{ color: '#faad14' }} />
      default:
        return null
    }
  }

  const getChangeTypeText = (changeType) => {
    switch (changeType) {
      case 'ADD':
        return '新增'
      case 'REMOVE':
        return '删除'
      case 'MODIFY':
        return '修改'
      default:
        return ''
    }
  }

  const getChangeTypeColor = (changeType) => {
    switch (changeType) {
      case 'ADD':
        return 'green'
      case 'REMOVE':
        return 'red'
      case 'MODIFY':
        return 'gold'
      default:
        return 'default'
    }
  }

  const getNodeTypeLabel = (type) => {
    const typeMap = {
      startNode: '开始节点',
      endNode: '结束节点',
      conditionNode: '条件节点',
      actionNode: '动作节点',
      logicNode: '逻辑节点'
    }
    return typeMap[type] || type
  }

  const renderBasicDiffs = () => {
    const basicDiffs = (diffData?.diffs || []).filter((d) => d.field !== 'ruleJson')

    if (basicDiffs.length === 0) {
      return (
        <Empty
          description="基本属性无变更"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          style={{ padding: '40px 0' }}
        />
      )
    }

    return (
      <List
        dataSource={basicDiffs}
        renderItem={(item) => (
          <List.Item
            style={{
              padding: '12px 16px',
              marginBottom: 8,
              background: '#fafafa',
              borderRadius: 8
            }}
          >
            <List.Item.Meta
              avatar={
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    background:
                      item.changeType === 'ADD'
                        ? '#f6ffed'
                        : item.changeType === 'REMOVE'
                        ? '#fff2f0'
                        : '#fffbe6',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}
                >
                  {getChangeTypeIcon(item.changeType)}
                </div>
              }
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontWeight: 500 }}>{item.fieldName}</span>
                  <Tag color={getChangeTypeColor(item.changeType)} size="small">
                    {getChangeTypeText(item.changeType)}
                  </Tag>
                </div>
              }
              description={
                <div>
                  {item.changeType === 'MODIFY' && (
                    <div style={{ fontSize: 12 }}>
                      <div style={{ color: '#ff4d4f', textDecoration: 'line-through' }}>
                        旧: {item.oldValue || '(空)'}
                      </div>
                      <div style={{ color: '#52c41a', marginTop: 4 }}>
                        新: {item.newValue || '(空)'}
                      </div>
                    </div>
                  )}
                  {item.changeType === 'ADD' && (
                    <div style={{ color: '#52c41a', fontSize: 12 }}>
                      新增: {item.newValue || '(空)'}
                    </div>
                  )}
                  {item.changeType === 'REMOVE' && (
                    <div style={{ color: '#ff4d4f', fontSize: 12 }}>
                      删除: {item.oldValue || '(空)'}
                    </div>
                  )}
                </div>
              }
            />
          </List.Item>
        )}
      />
    )
  }

  const renderCanvasDiffs = () => {
    const canvasDiff = (diffData?.diffs || []).find((d) => d.field === 'ruleJson')

    if (!canvasDiff || !canvasDiff.nodeChanges || canvasDiff.nodeChanges.length === 0) {
      return (
        <Empty
          description="画布节点无变更"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          style={{ padding: '40px 0' }}
        />
      )
    }

    const addNodes = canvasDiff.nodeChanges.filter((n) => n.changeType === 'ADD')
    const removeNodes = canvasDiff.nodeChanges.filter((n) => n.changeType === 'REMOVE')
    const modifyNodes = canvasDiff.nodeChanges.filter((n) => n.changeType === 'MODIFY')

    return (
      <div>
        <Row gutter={[16, 16]}>
          {addNodes.length > 0 && (
            <Col span={24}>
              <Card
                size="small"
                title={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <PlusOutlined style={{ color: '#52c41a' }} />
                    <span>新增节点</span>
                    <Tag color="green">{addNodes.length} 个</Tag>
                  </div>
                }
                style={{ background: '#f6ffed', borderColor: '#b7eb8f' }}
              >
                {addNodes.map((node, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: '8px 12px',
                      background: '#fff',
                      borderRadius: 6,
                      marginBottom: 8,
                      borderLeft: '3px solid #52c41a'
                    }}
                  >
                    <div style={{ fontWeight: 500 }}>{node.label}</div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                      类型: {getNodeTypeLabel(node.type)}
                    </div>
                  </div>
                ))}
              </Card>
            </Col>
          )}

          {removeNodes.length > 0 && (
            <Col span={24}>
              <Card
                size="small"
                title={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <MinusOutlined style={{ color: '#ff4d4f' }} />
                    <span>删除节点</span>
                    <Tag color="red">{removeNodes.length} 个</Tag>
                  </div>
                }
                style={{ background: '#fff2f0', borderColor: '#ffccc7' }}
              >
                {removeNodes.map((node, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: '8px 12px',
                      background: '#fff',
                      borderRadius: 6,
                      marginBottom: 8,
                      borderLeft: '3px solid #ff4d4f',
                      opacity: 0.7
                    }}
                  >
                    <div style={{ fontWeight: 500, textDecoration: 'line-through' }}>
                      {node.label}
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                      类型: {getNodeTypeLabel(node.type)}
                    </div>
                  </div>
                ))}
              </Card>
            </Col>
          )}

          {modifyNodes.length > 0 && (
            <Col span={24}>
              <Card
                size="small"
                title={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <EditOutlined style={{ color: '#faad14' }} />
                    <span>修改节点</span>
                    <Tag color="gold">{modifyNodes.length} 个</Tag>
                  </div>
                }
                style={{ background: '#fffbe6', borderColor: '#ffe58f' }}
              >
                {modifyNodes.map((node, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: '8px 12px',
                      background: '#fff',
                      borderRadius: 6,
                      marginBottom: 8,
                      borderLeft: '3px solid #faad14'
                    }}
                  >
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                      类型: {getNodeTypeLabel(node.type)}
                    </div>
                    <div style={{ color: '#ff4d4f', textDecoration: 'line-through', fontSize: 12 }}>
                      {node.oldLabel}
                    </div>
                    <div style={{ color: '#52c41a', fontSize: 12, marginTop: 2 }}>
                      {node.newLabel}
                    </div>
                  </div>
                ))}
              </Card>
            </Col>
          )}
        </Row>
      </div>
    )
  }

  const tabItems = [
    {
      key: 'all',
      label: `全部变更 (${diffData?.totalChanges || 0})`
    },
    {
      key: 'basic',
      label: '基本属性'
    },
    {
      key: 'canvas',
      label: '画布节点'
    }
  ]

  return (
    <div>
      <div
        style={{
          padding: '12px 16px',
          background: '#f5f5f5',
          borderRadius: 8,
          marginBottom: 16
        }}
      >
        <Row align="middle" gutter={16}>
          <Col>
            <Text type="secondary">对比版本:</Text>
          </Col>
          <Col>
            <Tag color="blue">v{fromVersion}</Tag>
            <span style={{ margin: '0 8px', color: '#bfbfbf' }}>→</span>
            <Tag color="green">{compareMode === 'current' ? '当前版本' : `v${toVersion}`}</Tag>
          </Col>
          <Col flex="auto" style={{ textAlign: 'right' }}>
            <Text type="secondary">共 {diffData?.totalChanges || 0} 处变更</Text>
          </Col>
        </Row>
      </div>

      <Spin spinning={loading}>
        {diffData ? (
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={tabItems}
            size="small"
          />
        ) : (
          <div style={{ padding: '40px 0', textAlign: 'center' }}>
            <Spin />
          </div>
        )}

        {diffData && activeTab === 'all' && (
          <div>
            {diffData.totalChanges === 0 ? (
              <Empty
                description="两个版本完全相同，无任何变更"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '40px 0' }}
              />
            ) : (
              <div>
                <Divider orientation="left" plain>
                  基本属性变更
                </Divider>
                {renderBasicDiffs()}

                <Divider orientation="left" plain>
                  画布节点变更
                </Divider>
                {renderCanvasDiffs()}
              </div>
            )}
          </div>
        )}

        {diffData && activeTab === 'basic' && renderBasicDiffs()}

        {diffData && activeTab === 'canvas' && renderCanvasDiffs()}
      </Spin>
    </div>
  )
}

export default VersionDiffViewer
