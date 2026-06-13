import { useState, useEffect } from 'react'
import { Modal, Form, Select, Input, message } from 'antd'
import { saveRuleAsTemplate } from '../../services/templateApi'
import { getRuleList } from '../../services/ruleApi'

const { Option } = Select

function SaveAsTemplateModal({ open, onCancel, onSuccess }) {
  const [form] = Form.useForm()
  const [rules, setRules] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      fetchRules()
    }
  }, [open])

  const fetchRules = async () => {
    setLoading(true)
    try {
      const res = await getRuleList({ page: 1, size: 200 })
      setRules(res?.records || [])
    } catch (error) {
      console.error('获取规则列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      await saveRuleAsTemplate(
        values.ruleId,
        values.templateName,
        values.templateDescription,
        values.authorName,
        'default',
        'admin'
      )
      message.success('规则已保存为模板')
      form.resetFields()
      onSuccess?.()
    } catch (error) {
      if (error.errorFields) return
      console.error('保存为模板失败:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="从规则保存为模板"
      open={open}
      onOk={handleSubmit}
      onCancel={() => {
        form.resetFields()
        onCancel?.()
      }}
      okText="保存为模板"
      cancelText="取消"
      confirmLoading={saving}
      width={520}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="ruleId"
          label="选择规则"
          rules={[{ required: true, message: '请选择要保存为模板的规则' }]}
        >
          <Select
            placeholder="选择一条已有规则"
            loading={loading}
            showSearch
            optionFilterProp="children"
            filterOption={(input, option) =>
              option.children?.toLowerCase().includes(input.toLowerCase())
            }
          >
            {rules.map((rule) => (
              <Option key={rule.id} value={rule.id}>
                #{rule.id} - {rule.name}
              </Option>
            ))}
          </Select>
        </Form.Item>
        <Form.Item
          name="templateName"
          label="模板名称"
          rules={[{ required: true, message: '请输入模板名称' }]}
        >
          <Input placeholder="为模板起一个名称" maxLength={200} />
        </Form.Item>
        <Form.Item name="templateDescription" label="模板描述">
          <Input.TextArea placeholder="描述模板的用途和适用场景" maxLength={500} rows={3} />
        </Form.Item>
        <Form.Item name="authorName" label="创建者" initialValue="管理员">
          <Input placeholder="创建者名称" maxLength={100} />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default SaveAsTemplateModal
