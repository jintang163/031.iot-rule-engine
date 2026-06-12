import { useState } from 'react'
import { Card, Tag, Button, Progress, Space, Tooltip, Modal, List } from 'antd'
import {
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  RobotOutlined,
  BulbOutlined,
  EyeOutlined
} from '@ant-design/icons'

const categoryColors = {
  energy_saving: { color: '#52c41a', bg: '#f6ffed', label: '节能' },
  comfort: { color: '#fa8c16', bg: '#fff7e6', label: '舒适' },
  convenience: { color: '#1890ff', bg: '#e6f7ff', label: '便捷' },
  combo: { color: '#722ed1', bg: '#f9f0ff', label: '联动' },
  general: { color: '#8c8c8c', bg: '#fafafa', label: '通用' }
}

function RecommendationCard({ recommendation, onApply, onDismiss }) {
  const [applying, setApplying] = useState(false)
  const [showDetail, setShowDetail] = useState(false)

  const category = categoryColors[recommendation.category] || categoryColors.general

  const confidencePercent = Math.round((recommendation.confidence || 0) * 100)
  const confidenceColor = confidencePercent >= 80 ? '#52c41a' : confidencePercent >= 60 ? '#faad14' : '#ff4d4f'

  const handleApply = async () => {
    Modal.confirm({
      title: '确认应用此推荐？',
      content: (
        <div>
          <p>将创建规则：<strong>{recommendation.title}</strong></p>
          <p style={{ color: '#888', fontSize: 12 }}>规则创建后默认处于禁用状态，您可以检查后手动启用</p>
        </div>
      ),
      okText: '确认应用',
      cancelText: '取消',
      onOk: async () => {
        setApplying(true)
        try {
          await onApply(recommendation)
        } finally {
          setApplying(false)
        }
      }
    })
  }

  return (
    <>
      <Card
        className="recommendation-card"
        hoverable
        style={{
          height: '100%',
          border: '1px solid #eef0f3',
          borderRadius: 12,
          background: 'linear-gradient(135deg, #ffffff 0%, #fafbfc 100%)'
        }}
        bodyStyle={{ padding: 16 }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 12 }}>
          <div
            className="recommendation-icon"
            style={{
              width: 48,
              height: 48,
              borderRadius: 12,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 24,
              background: category.bg,
              flexShrink: 0
            }}
          >
            {recommendation.icon || '💡'}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <Tag color={category.color} style={{ margin: 0 }}>
                {category.label}
              </Tag>
              <Tooltip title={`推荐优先级: ${recommendation.suggestionPriority}/100`}>
                <span style={{ fontSize: 12, color: '#888' }}>
                  <RobotOutlined style={{ marginRight: 4 }} />
                  AI推荐
                </span>
              </Tooltip>
            </div>
            <h4 style={{ margin: '4px 0', fontSize: 15, fontWeight: 600, color: '#262626' }}>
              {recommendation.title}
            </h4>
            <p style={{ margin: 0, fontSize: 13, color: '#595959', lineHeight: 1.5 }}>
              {recommendation.description}
            </p>
          </div>
        </div>

        <div style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
            <span style={{ fontSize: 12, color: '#888' }}>置信度</span>
            <span style={{ fontSize: 12, fontWeight: 600, color: confidenceColor }}>
              {confidencePercent}%
            </span>
          </div>
          <Progress
            percent={confidencePercent}
            showInfo={false}
            size="small"
            strokeColor={confidenceColor}
            trailColor="#f0f0f0"
          />
        </div>

        <div
          style={{
            background: '#f5f7fa',
            borderRadius: 8,
            padding: 10,
            marginBottom: 12,
            fontSize: 12,
            color: '#666',
            lineHeight: 1.5
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 6 }}>
            <BulbOutlined style={{ color: '#faad14', marginTop: 2, flexShrink: 0 }} />
            <span>{recommendation.reason}</span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space size={8}>
            <Tooltip title={`相关设备: ${recommendation.relatedDeviceIds?.join(', ') || '无'}`}>
              <Tag color="blue" style={{ margin: 0, cursor: 'pointer' }}>
                {recommendation.relatedDeviceIds?.length || 0} 个设备
              </Tag>
            </Tooltip>
            {recommendation.manualActionCount && (
              <Tag color="default" style={{ margin: 0 }}>
                手动操作 {recommendation.manualActionCount} 次
              </Tag>
            )}
          </Space>
          <Space size={4}>
            <Tooltip title="查看详情">
              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => setShowDetail(true)}
              />
            </Tooltip>
            <Tooltip title="忽略此推荐">
              <Button
                type="text"
                size="small"
                danger
                icon={<CloseCircleOutlined />}
                onClick={() => onDismiss?.(recommendation)}
              />
            </Tooltip>
            <Button
              type="primary"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={applying}
              onClick={handleApply}
            >
              一键应用
            </Button>
          </Space>
        </div>
      </Card>

      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 24 }}>{recommendation.icon}</span>
            <span>{recommendation.title}</span>
          </div>
        }
        open={showDetail}
        onCancel={() => setShowDetail(false)}
        footer={[
          <Button key="close" onClick={() => setShowDetail(false)}>
            关闭
          </Button>,
          <Button
            key="apply"
            type="primary"
            icon={<ThunderboltOutlined />}
            loading={applying}
            onClick={() => {
              setShowDetail(false)
              handleApply()
            }}
          >
            一键应用
          </Button>
        ]}
        width={600}
      >
        <div style={{ marginBottom: 16 }}>
          <Tag color={category.color} style={{ marginBottom: 8 }}>
            {category.label}
          </Tag>
          <p style={{ margin: '8px 0', color: '#595959', fontSize: 14, lineHeight: 1.6 }}>
            {recommendation.description}
          </p>
        </div>

        <div
          style={{
            background: '#f0f5ff',
            border: '1px solid #d6e4ff',
            borderRadius: 8,
            padding: 12,
            marginBottom: 16
          }}
        >
          <div style={{ fontWeight: 600, color: '#1677ff', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
            <RobotOutlined /> AI 分析结果
          </div>
          <p style={{ margin: 0, color: '#595959', fontSize: 13, lineHeight: 1.6 }}>
            {recommendation.reason}
          </p>
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 12, color: '#262626' }}>
            规则条件
          </div>
          {recommendation.conditions && recommendation.conditions.length > 0 ? (
            <List
              size="small"
              dataSource={recommendation.conditions}
              renderItem={(cond, idx) => (
                <List.Item style={{ paddingLeft: 0, paddingRight: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%' }}>
                    {idx > 0 && <span style={{ color: '#52c41a', fontWeight: 600 }}>AND</span>}
                    <Tag color="blue">{cond.deviceName}</Tag>
                    <span style={{ flex: 1 }}>
                      {cond.fieldLabel} {cond.operator} {cond.value}
                    </span>
                    <Tag color="processing">{cond.label}</Tag>
                  </div>
                </List.Item>
              )}
            />
          ) : (
            <p style={{ color: '#888', fontSize: 13, margin: 0 }}>无特定条件，可自定义</p>
          )}
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 12, color: '#262626' }}>
            执行动作
          </div>
          <List
            size="small"
            dataSource={recommendation.actions}
            renderItem={(action, idx) => (
              <List.Item style={{ paddingLeft: 0, paddingRight: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%' }}>
                  <Tag color="orange">{action.deviceName}</Tag>
                  <span style={{ flex: 1 }}>
                    {action.actionLabel}
                    {action.params && Object.keys(action.params).length > 0 && (
                      <span style={{ color: '#888', marginLeft: 8 }}>
                        ({Object.entries(action.params).map(([k, v]) => `${k}: ${v}`).join(', ')})
                      </span>
                    )}
                  </span>
                  <Tag color="warning">{action.label}</Tag>
                </div>
              </List.Item>
            )}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>置信度</div>
            <Progress percent={confidencePercent} strokeColor={confidenceColor} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>推荐优先级</div>
            <div style={{ fontSize: 20, fontWeight: 600, color: '#1677ff' }}>
              {recommendation.suggestionPriority || 0}
              <span style={{ fontSize: 12, color: '#888', fontWeight: 'normal' }}>/100</span>
            </div>
          </div>
        </div>
      </Modal>
    </>
  )
}

export default RecommendationCard
