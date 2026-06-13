import { useState } from 'react'
import { Card, Tag, Button, Space, Tooltip, Modal, List, Badge } from 'antd'
import {
  ThunderboltOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  UserOutlined,
  TeamOutlined,
  GlobalOutlined,
  LockOutlined,
  FireOutlined
} from '@ant-design/icons'

const categoryConfig = {
  energy_saving: { color: '#52c41a', bg: '#f6ffed', label: '节能' },
  away: { color: '#fa8c16', bg: '#fff7e6', label: '离家' },
  security: { color: '#eb2f96', bg: '#fff0f6', label: '安防' },
  custom: { color: '#722ed1', bg: '#f9f0ff', label: '自定义' }
}

const scopeConfig = {
  public: { icon: <GlobalOutlined />, label: '公共', color: '#1677ff' },
  team: { icon: <TeamOutlined />, label: '团队', color: '#52c41a' },
  private: { icon: <LockOutlined />, label: '私有', color: '#8c8c8c' }
}

const reviewStatusConfig = {
  0: { icon: <ClockCircleOutlined />, color: '#faad14', label: '待审核' },
  1: { icon: <CheckCircleOutlined />, color: '#52c41a', label: '已通过' },
  2: { icon: <CloseCircleOutlined />, color: '#ff4d4f', label: '已拒绝' }
}

function TemplateCard({ template, onApply, onDelete, onReview }) {
  const [applying, setApplying] = useState(false)
  const [showDetail, setShowDetail] = useState(false)

  const category = categoryConfig[template.category] || categoryConfig.custom
  const scope = scopeConfig[template.scope] || scopeConfig.team
  const reviewStatus = reviewStatusConfig[template.reviewStatus] || reviewStatusConfig[0]

  const ruleConfig = template.ruleConfig ? (typeof template.ruleConfig === 'string' ? JSON.parse(template.ruleConfig) : template.ruleConfig) : null

  const handleApply = async () => {
    Modal.confirm({
      title: '确认应用此模板？',
      content: (
        <div>
          <p>将基于模板 <strong>{template.name}</strong> 创建新规则</p>
          <p style={{ color: '#888', fontSize: 12 }}>规则创建后默认处于停用状态，您可以在规则编辑器中检查并手动启用</p>
        </div>
      ),
      okText: '确认应用',
      cancelText: '取消',
      onOk: async () => {
        setApplying(true)
        try {
          await onApply(template)
        } finally {
          setApplying(false)
        }
      }
    })
  }

  return (
    <>
      <Card
        className="template-card"
        hoverable
        style={{
          height: '100%',
          border: '1px solid #eef0f3',
          borderRadius: 12,
          background: 'linear-gradient(135deg, #ffffff 0%, #fafbfc 100%)'
        }}
        bodyStyle={{ padding: 20 }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 14 }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: 14,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 28,
              background: category.bg,
              flexShrink: 0,
              transition: 'transform 0.3s ease'
            }}
          >
            {template.icon || '📋'}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4, flexWrap: 'wrap' }}>
              <Tag color={category.color} style={{ margin: 0 }}>
                {category.label}
              </Tag>
              <Tooltip title={`${scope.label}模板`}>
                <Tag
                  icon={scope.icon}
                  color={scope.color}
                  style={{ margin: 0 }}
                >
                  {scope.label}
                </Tag>
              </Tooltip>
              {template.sourceType === 'system' && (
                <Tag color="blue" style={{ margin: 0 }}>内置</Tag>
              )}
            </div>
            <h4 style={{ margin: '6px 0 2px', fontSize: 16, fontWeight: 600, color: '#262626', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {template.name}
            </h4>
            <p style={{ margin: 0, fontSize: 13, color: '#595959', lineHeight: 1.5, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
              {template.description}
            </p>
          </div>
        </div>

        {ruleConfig && (
          <div
            style={{
              background: '#f5f7fa',
              borderRadius: 8,
              padding: 10,
              marginBottom: 14,
              fontSize: 12,
              color: '#666',
              lineHeight: 1.6
            }}
          >
            {ruleConfig.conditions && ruleConfig.conditions.length > 0 && (
              <div style={{ marginBottom: 4 }}>
                <span style={{ fontWeight: 600, color: '#1677ff' }}>条件: </span>
                {ruleConfig.conditions.map((c, i) => (
                  <span key={i}>
                    {i > 0 && <span style={{ color: '#52c41a', fontWeight: 600 }}> AND </span>}
                    {c.label || `${c.field} ${c.operator} ${c.value}`}
                  </span>
                ))}
              </div>
            )}
            {ruleConfig.actions && ruleConfig.actions.length > 0 && (
              <div>
                <span style={{ fontWeight: 600, color: '#fa8c16' }}>动作: </span>
                {ruleConfig.actions.map((a, i) => (
                  <span key={i}>
                    {i > 0 && '、'}
                    {a.label || `${a.action}`}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space size={8}>
            <Tooltip title={`应用次数: ${template.applyCount || 0}`}>
              <Tag
                icon={<FireOutlined />}
                color="orange"
                style={{ margin: 0, cursor: 'pointer' }}
              >
                {template.applyCount || 0}
              </Tag>
            </Tooltip>
            {template.version && (
              <Tag style={{ margin: 0, fontSize: 11 }}>v{template.version}</Tag>
            )}
            {template.scope === 'public' && template.reviewStatus !== 1 && (
              <Tooltip title={reviewStatus.label}>
                <Badge status={template.reviewStatus === 0 ? 'warning' : 'error'} />
              </Tooltip>
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
            {template.sourceType !== 'system' && onDelete && (
              <Tooltip title="删除模板">
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => onDelete(template)}
                />
              </Tooltip>
            )}
            <Button
              type="primary"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={applying}
              onClick={handleApply}
              disabled={template.reviewStatus !== 1 || template.status === 0}
            >
              一键应用
            </Button>
          </Space>
        </div>
      </Card>

      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 28 }}>{template.icon || '📋'}</span>
            <div>
              <div>{template.name}</div>
              <div style={{ fontSize: 12, color: '#888', fontWeight: 'normal' }}>
                v{template.version || '1.0.0'}
              </div>
            </div>
          </div>
        }
        open={showDetail}
        onCancel={() => setShowDetail(false)}
        footer={[
          <Button key="close" onClick={() => setShowDetail(false)}>
            关闭
          </Button>,
          onReview && template.reviewStatus === 0 && (
            <Button
              key="approve"
              type="default"
              icon={<CheckCircleOutlined />}
              style={{ color: '#52c41a', borderColor: '#52c41a' }}
              onClick={() => {
                setShowDetail(false)
                onReview(template.id, 1)
              }}
            >
              审核通过
            </Button>
          ),
          onReview && template.reviewStatus === 0 && (
            <Button
              key="reject"
              danger
              icon={<CloseCircleOutlined />}
              onClick={() => {
                setShowDetail(false)
                onReview(template.id, 2)
              }}
            >
              审核拒绝
            </Button>
          ),
          <Button
            key="apply"
            type="primary"
            icon={<ThunderboltOutlined />}
            loading={applying}
            onClick={() => {
              setShowDetail(false)
              handleApply()
            }}
            disabled={template.reviewStatus !== 1 || template.status === 0}
          >
            一键应用
          </Button>
        ].filter(Boolean)}
        width={680}
      >
        <div style={{ marginBottom: 16 }}>
          <Space size={8} style={{ marginBottom: 8 }}>
            <Tag color={category.color}>{category.label}</Tag>
            <Tag icon={scope.icon} color={scope.color}>{scope.label}</Tag>
            {template.sourceType === 'system' && <Tag color="blue">系统内置</Tag>}
            {template.sourceType === 'user' && <Tag color="purple">用户创建</Tag>}
            <Tag icon={reviewStatus.icon} color={reviewStatus.color}>{reviewStatus.label}</Tag>
          </Space>
          <p style={{ margin: '8px 0', color: '#595959', fontSize: 14, lineHeight: 1.6 }}>
            {template.description}
          </p>
        </div>

        {ruleConfig && (
          <>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontWeight: 600, marginBottom: 10, color: '#262626', fontSize: 14 }}>
                触发条件
              </div>
              {ruleConfig.conditions && ruleConfig.conditions.length > 0 ? (
                <List
                  size="small"
                  dataSource={ruleConfig.conditions}
                  renderItem={(cond, idx) => (
                    <List.Item style={{ paddingLeft: 0, paddingRight: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%' }}>
                        {idx > 0 && <Tag color="green" style={{ margin: 0 }}>AND</Tag>}
                        <Tag color="blue">{cond.deviceId}</Tag>
                        <span style={{ flex: 1 }}>
                          {cond.label || `${cond.field} ${cond.operator} ${cond.value}`}
                        </span>
                      </div>
                    </List.Item>
                  )}
                />
              ) : (
                <p style={{ color: '#888', fontSize: 13, margin: 0 }}>无特定条件</p>
              )}
            </div>

            <div style={{ marginBottom: 16 }}>
              <div style={{ fontWeight: 600, marginBottom: 10, color: '#262626', fontSize: 14 }}>
                执行动作
              </div>
              <List
                size="small"
                dataSource={ruleConfig.actions || []}
                renderItem={(action, idx) => (
                  <List.Item style={{ paddingLeft: 0, paddingRight: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%' }}>
                      <Tag color="orange">{action.deviceId}</Tag>
                      <span style={{ flex: 1 }}>
                        {action.label || action.action}
                        {action.params && Object.keys(action.params).length > 0 && (
                          <span style={{ color: '#888', marginLeft: 8, fontSize: 12 }}>
                            ({Object.entries(action.params).map(([k, v]) => `${k}: ${v}`).join(', ')})
                          </span>
                        )}
                      </span>
                    </div>
                  </List.Item>
                )}
              />
            </div>
          </>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: 20, color: '#888', fontSize: 13, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
          {template.authorName && (
            <span><UserOutlined style={{ marginRight: 4 }} />{template.authorName}</span>
          )}
          <span><FireOutlined style={{ marginRight: 4, color: '#fa8c16' }} />应用 {template.applyCount || 0} 次</span>
          {template.createTime && (
            <span>创建于 {template.createTime}</span>
          )}
        </div>
      </Modal>
    </>
  )
}

export default TemplateCard
