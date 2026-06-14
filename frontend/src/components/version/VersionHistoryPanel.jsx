import { useState, useEffect } from 'react'
import { List, Tag, Button, Space, Empty, message, Modal, Input, Tooltip, Popconfirm } from 'antd'
import { HistoryOutlined, RollbackOutlined, DiffOutlined, EditOutlined, ClockCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getVersionList, rollbackVersion, updateVersionComment } from '../../services/versionApi'
import VersionDiffViewer from './VersionDiffViewer.jsx'

const { TextArea } = Input

function VersionHistoryPanel({ ruleId, onRollback, onClose }) {
  const [versions, setVersions] = useState([])
  const [loading, setLoading] = useState(false)
  const [diffVisible, setDiffVisible] = useState(false)
  const [selectedVersion, setSelectedVersion] = useState(null)
  const [compareMode, setCompareMode] = useState('current')
  const [editingId, setEditingId] = useState(null)
  const [editingComment, setEditingComment] = useState('')

  useEffect(() => {
    if (ruleId) {
      fetchVersions()
    }
  }, [ruleId])

  const fetchVersions = async () => {
    if (!ruleId) return
    setLoading(true)
    try {
      const res = await getVersionList(ruleId, { pageNum: 1, pageSize: 50 })
      setVersions(res?.records || [])
    } catch (error) {
      console.error('获取版本列表失败:', error)
      message.error('获取版本列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleRollback = async (version) => {
    try {
      const res = await rollbackVersion({
        ruleId,
        version: version.version,
        comment: `回滚至 v${version.version}`
      })
      message.success(`已回滚至版本 v${version.version}`)
      if (onRollback) {
        onRollback(res)
      }
      fetchVersions()
    } catch (error) {
      console.error('回滚失败:', error)
      message.error('回滚失败: ' + (error?.message || '未知错误'))
    }
  }

  const handleCompareWithCurrent = (version) => {
    setSelectedVersion(version)
    setCompareMode('current')
    setDiffVisible(true)
  }

  const handleEditComment = (version) => {
    setEditingId(version.id)
    setEditingComment(version.comment || '')
  }

  const handleSaveComment = async (versionId) => {
    try {
      await updateVersionComment(versionId, editingComment)
      message.success('注释已更新')
      setEditingId(null)
      fetchVersions()
    } catch (error) {
      console.error('更新注释失败:', error)
      message.error('更新注释失败')
    }
  }

  const getChangeTypeTag = (changeSummary) => {
    if (!changeSummary) return null
    if (changeSummary.includes('创建')) {
      return <Tag color="green">创建</Tag>
    }
    if (changeSummary.includes('回滚')) {
      return <Tag color="orange">回滚</Tag>
    }
    return <Tag color="blue">更新</Tag>
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div
        style={{
          padding: '16px 20px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <HistoryOutlined style={{ color: '#1677ff', fontSize: 16 }} />
          <span style={{ fontWeight: 600, fontSize: 15 }}>版本历史</span>
          <Tag color="default">{versions.length} 个版本</Tag>
        </div>
        {onClose && (
          <Button type="text" size="small" onClick={onClose}>
            关闭
          </Button>
        )}
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: '12px 16px' }}>
        {versions.length === 0 && !loading ? (
          <Empty
            description="暂无历史版本"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ marginTop: 60 }}
          />
        ) : (
          <List
            loading={loading}
            dataSource={versions}
            renderItem={(item, index) => (
              <List.Item
                key={item.id}
                style={{
                  padding: '12px 0',
                  borderBottom: index < versions.length - 1 ? '1px solid #f5f5f5' : 'none'
                }}
              >
                <List.Item.Meta
                  avatar={
                    <div
                      style={{
                        width: 36,
                        height: 36,
                        borderRadius: '50%',
                        background: index === 0 ? '#1677ff' : '#e6f4ff',
                        color: index === 0 ? '#fff' : '#1677ff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 12,
                        fontWeight: 600
                      }}
                    >
                      v{item.version}
                    </div>
                  }
                  title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ fontWeight: 500 }}>版本 {item.version}</span>
                      {index === 0 && <Tag color="blue">当前</Tag>}
                      {getChangeTypeTag(item.changeSummary)}
                    </div>
                  }
                  description={
                    <div>
                      <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 4 }}>
                        <ClockCircleOutlined style={{ marginRight: 4 }} />
                        {dayjs(item.createTime).format('YYYY-MM-DD HH:mm:ss')}
                      </div>
                      {editingId === item.id ? (
                        <div style={{ marginTop: 8 }}>
                          <TextArea
                            value={editingComment}
                            onChange={(e) => setEditingComment(e.target.value)}
                            placeholder="输入版本注释..."
                            rows={2}
                            autoSize
                          />
                          <div style={{ marginTop: 4, textAlign: 'right' }}>
                            <Space size="small">
                              <Button size="small" onClick={() => setEditingId(null)}>
                                取消
                              </Button>
                              <Button
                                size="small"
                                type="primary"
                                onClick={() => handleSaveComment(item.id)}
                              >
                                保存
                              </Button>
                            </Space>
                          </div>
                        </div>
                      ) : (
                        <div
                          style={{
                            fontSize: 12,
                            color: item.comment ? '#595959' : '#bfbfbf',
                            fontStyle: item.comment ? 'normal' : 'italic'
                          }}
                          onClick={() => handleEditComment(item)}
                        >
                          {item.comment || '点击添加注释...'}
                        </div>
                      )}
                    </div>
                  }
                />
                <Space size="small">
                  <Tooltip title="与当前版本对比">
                    <Button
                      type="text"
                      size="small"
                      icon={<DiffOutlined />}
                      onClick={() => handleCompareWithCurrent(item)}
                    >
                      对比
                    </Button>
                  </Tooltip>
                  {index !== 0 && (
                    <Popconfirm
                      title="确认回滚"
                      description={`确定要回滚到版本 v${item.version} 吗？`}
                      onConfirm={() => handleRollback(item)}
                      okText="确认回滚"
                      cancelText="取消"
                    >
                      <Tooltip title="回滚到此版本">
                        <Button type="text" size="small" icon={<RollbackOutlined />} danger>
                          回滚
                        </Button>
                      </Tooltip>
                    </Popconfirm>
                  )}
                </Space>
              </List.Item>
            )}
          />
        )}
      </div>

      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <DiffOutlined style={{ color: '#1677ff' }} />
            <span>版本差异对比</span>
          </div>
        }
        open={diffVisible}
        onCancel={() => setDiffVisible(false)}
        footer={null}
        width={800}
        destroyOnClose
      >
        {selectedVersion && (
          <VersionDiffViewer
            ruleId={ruleId}
            fromVersion={selectedVersion.version}
            compareMode={compareMode}
          />
        )}
      </Modal>
    </div>
  )
}

export default VersionHistoryPanel
