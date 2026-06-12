import { useState, useEffect } from 'react'
import { Card, Empty, Button, Spin, message, Space, Divider } from 'antd'
import {
  RobotOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from '@ant-design/icons'
import RecommendationCard from './RecommendationCard'
import { getRecommendations, applyRecommendation } from '../../services/recommendationApi'
import { createRule } from '../../services/ruleApi'
import useAppStore from '../../store/useAppStore'

function RecommendationSection({ onRuleCreated }) {
  const { setLoading } = useAppStore()
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoadingState] = useState(false)
  const [dismissedIds, setDismissedIds] = useState(() => {
    try {
      const saved = localStorage.getItem('dismissedRecommendations')
      return saved ? JSON.parse(saved) : []
    } catch {
      return []
    }
  })

  const fetchRecommendations = async () => {
    setLoadingState(true)
    try {
      const data = await getRecommendations()
      const filtered = (data || []).filter(
        (rec) => !dismissedIds.includes(rec.recommendationId)
      )
      setRecommendations(filtered)
    } catch (error) {
      console.error('获取推荐失败:', error)
      message.error('获取AI推荐失败')
    } finally {
      setLoadingState(false)
    }
  }

  useEffect(() => {
    fetchRecommendations()
  }, [])

  const handleApply = async (recommendation) => {
    setLoading(true)
    try {
      const result = await applyRecommendation(recommendation)
      message.success(`推荐规则 "${recommendation.title}" 已创建成功！`)
      handleDismiss(recommendation)
      if (onRuleCreated) {
        onRuleCreated(result)
      }
      return result
    } catch (error) {
      console.error('应用推荐失败:', error)
      message.error('应用推荐失败: ' + (error.message || '未知错误'))
      throw error
    } finally {
      setLoading(false)
    }
  }

  const handleDismiss = (recommendation) => {
    const newDismissed = [...dismissedIds, recommendation.recommendationId]
    setDismissedIds(newDismissed)
    try {
      localStorage.setItem('dismissedRecommendations', JSON.stringify(newDismissed))
    } catch {
    }
    setRecommendations((prev) =>
      prev.filter((r) => r.recommendationId !== recommendation.recommendationId)
    )
    message.info('已忽略此推荐')
  }

  const handleRefresh = () => {
    fetchRecommendations()
  }

  const handleClearDismissed = () => {
    setDismissedIds([])
    try {
      localStorage.removeItem('dismissedRecommendations')
    } catch {
    }
    message.success('已重置所有忽略的推荐')
    fetchRecommendations()
  }

  const visibleRecommendations = recommendations.slice(0, 6)

  return (
    <Card
      bordered={false}
      style={{
        borderRadius: 12,
        marginBottom: 16,
        background: 'linear-gradient(135deg, #f0f5ff 0%, #ffffff 100%)'
      }}
      bodyStyle={{ padding: 0 }}
    >
      <div
        style={{
          padding: '20px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid #e6f4ff',
          background: 'linear-gradient(90deg, #1677ff 0%, #4096ff 100%)',
          borderRadius: '12px 12px 0 0'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: '#fff' }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'rgba(255,255,255,0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20
            }}
          >
            <RobotOutlined />
          </div>
          <div>
            <h3 style={{ margin: 0, color: '#fff', fontSize: 18, fontWeight: 600 }}>
              AI 智能推荐
            </h3>
            <p style={{ margin: '2px 0 0 0', color: 'rgba(255,255,255,0.8)', fontSize: 12 }}>
              基于您的操作习惯和协同过滤算法，为您推荐合适的自动化规则
            </p>
          </div>
        </div>
        <Space>
          <Button
            type="default"
            size="small"
            icon={<ReloadOutlined />}
            onClick={handleClearDismissed}
            style={{ borderRadius: 6 }}
          >
            重置忽略
          </Button>
          <Button
            type="default"
            size="small"
            icon={<ReloadOutlined spin={loading} />}
            onClick={handleRefresh}
            loading={loading}
            style={{ borderRadius: 6 }}
          >
            刷新推荐
          </Button>
        </Space>
      </div>

      <div style={{ padding: 24 }}>
        <Spin spinning={loading}>
          {visibleRecommendations.length > 0 ? (
            <>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
                  gap: 16
                }}
              >
                {visibleRecommendations.map((rec) => (
                  <RecommendationCard
                    key={rec.recommendationId}
                    recommendation={rec}
                    onApply={handleApply}
                    onDismiss={handleDismiss}
                  />
                ))}
              </div>
              {recommendations.length > 6 && (
                <div style={{ textAlign: 'center', marginTop: 16 }}>
                  <p style={{ color: '#888', fontSize: 12, margin: 0 }}>
                    还有 {recommendations.length - 6} 条推荐，应用或忽略当前推荐后可查看更多
                  </p>
                </div>
              )}
            </>
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <div style={{ padding: '20px 0' }}>
                  <p style={{ marginBottom: 8, color: '#595959' }}>
                    {loading ? '正在分析您的操作习惯...' : '暂无新的推荐'}
                  </p>
                  <p style={{ margin: 0, color: '#8c8c8c', fontSize: 12 }}>
                    {loading
                      ? 'AI 正在努力为您发现可以自动化的场景'
                      : '系统已分析您的历史操作，当发现可优化的场景时会自动推荐'}
                  </p>
                </div>
              }
            >
              <Button type="primary" icon={<ThunderboltOutlined />} onClick={handleRefresh}>
                重新分析
              </Button>
            </Empty>
          )}
        </Spin>
      </div>
    </Card>
  )
}

export default RecommendationSection
