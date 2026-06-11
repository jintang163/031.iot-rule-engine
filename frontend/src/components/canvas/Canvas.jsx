import { useCallback, useRef, useEffect, useState } from 'react'
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  ReactFlowProvider,
  addEdge,
  useReactFlow,
  applyNodeChanges,
  applyEdgeChanges
} from 'reactflow'
import 'reactflow/dist/style.css'
import { Button, Space, Tooltip, Modal, message, Upload } from 'antd'
import {
  SaveOutlined,
  UndoOutlined,
  RedoOutlined,
  ClearOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  FullscreenOutlined,
  DownloadOutlined,
  UploadOutlined,
  CopyOutlined,
  ScissorOutlined,
  DeleteOutlined
} from '@ant-design/icons'
import useRuleStore from '../../store/useRuleStore.js'
import nodeTypes from './NodeTypes.jsx'

const rfStyle = {
  backgroundColor: '#FAFBFC'
}

const minimapStyle = {
  height: 120
}

function CanvasInner() {
  const reactFlowWrapper = useRef(null)
  const fileInputRef = useRef(null)
  const { project, zoomIn, zoomOut, fitView, getViewport, setViewport } = useReactFlow()
  const [clipboard, setClipboard] = useState(null)
  const [multiSelected, setMultiSelected] = useState(new Set())

  const nodes = useRuleStore((state) => state.nodes)
  const edges = useRuleStore((state) => state.edges)
  const selectedNode = useRuleStore((state) => state.selectedNode)
  const past = useRuleStore((state) => state.past)
  const future = useRuleStore((state) => state.future)

  const addNode = useRuleStore((state) => state.addNode)
  const addEdge = useRuleStore((state) => state.addEdge)
  const updateNode = useRuleStore((state) => state.updateNode)
  const deleteNode = useRuleStore((state) => state.deleteNode)
  const deleteEdge = useRuleStore((state) => state.deleteEdge)
  const setSelectedNode = useRuleStore((state) => state.setSelectedNode)
  const setSelectedEdge = useRuleStore((state) => state.setSelectedEdge)
  const clearSelection = useRuleStore((state) => state.clearSelection)
  const setNodes = useRuleStore((state) => state.setNodes)
  const setEdges = useRuleStore((state) => state.setEdges)
  const undo = useRuleStore((state) => state.undo)
  const redo = useRuleStore((state) => state.redo)
  const clearAll = useRuleStore((state) => state.clearAll)
  const loadRule = useRuleStore((state) => state.loadRule)
  const exportRuleJson = useRuleStore((state) => state.exportRuleJson)

  const onDragOver = useCallback((event) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event) => {
      event.preventDefault()

      const rawData = event.dataTransfer.getData('application/reactflow')
      if (!rawData) return

      let dragData
      try {
        dragData = JSON.parse(rawData)
      } catch (e) {
        return
      }

      const position = project({
        x: event.clientX - (reactFlowWrapper.current?.getBoundingClientRect().left || 0),
        y: event.clientY - (reactFlowWrapper.current?.getBoundingClientRect().top || 0)
      })

      const newNode = {
        id: `node_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`,
        type: dragData.type,
        position,
        data: { ...dragData.data }
      }

      addNode(newNode)
    },
    [project, addNode]
  )

  const onConnect = useCallback(
    (params) => {
      const newEdge = {
        ...params,
        id: `edge_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`,
        animated: true,
        style: { strokeWidth: 2 }
      }
      addEdge(newEdge)
    },
    [addEdge]
  )

  const onNodesChange = useCallback(
    (changes) => {
      const hasSelectionChange = changes.some((c) => c.type === 'select')
      const otherChanges = changes.filter((c) => c.type !== 'select')

      if (otherChanges.length > 0) {
        setNodes(applyNodeChanges(otherChanges, nodes))
      }

      if (hasSelectionChange) {
        const selectedIds = new Set()
        changes.forEach((c) => {
          if (c.type === 'select' && c.selected) {
            selectedIds.add(c.id)
          } else if (c.type === 'select' && !c.selected) {
            selectedIds.delete(c.id)
          }
        })
        if (selectedIds.size === 0) {
          clearSelection()
        } else if (selectedIds.size === 1) {
          const id = Array.from(selectedIds)[0]
          const node = nodes.find((n) => n.id === id)
          if (node) setSelectedNode(node)
        }
        setMultiSelected(selectedIds)
      }
    },
    [nodes, setNodes, setSelectedNode, clearSelection]
  )

  const onEdgesChange = useCallback(
    (changes) => {
      const hasSelectionChange = changes.some((c) => c.type === 'select')
      const otherChanges = changes.filter((c) => c.type !== 'select')

      if (otherChanges.length > 0) {
        setEdges(applyEdgeChanges(otherChanges, edges))
      }

      if (hasSelectionChange) {
        changes.forEach((c) => {
          if (c.type === 'select' && c.selected) {
            const edge = edges.find((e) => e.id === c.id)
            if (edge) setSelectedEdge(edge)
          }
        })
      }
    },
    [edges, setEdges, setSelectedEdge]
  )

  const handleDelete = useCallback(() => {
    if (multiSelected.size > 0) {
      multiSelected.forEach((id) => {
        deleteNode(id)
      })
      setMultiSelected(new Set())
      clearSelection()
      message.success(`已删除 ${multiSelected.size} 个节点`)
    } else if (selectedNode) {
      deleteNode(selectedNode.id)
      message.success('节点已删除')
    } else {
      const selectedEdge = useRuleStore.getState().selectedEdge
      if (selectedEdge) {
        deleteEdge(selectedEdge.id)
        message.success('连线已删除')
      }
    }
  }, [multiSelected, selectedNode, deleteNode, deleteEdge, clearSelection])

  const handleCopy = useCallback(() => {
    if (multiSelected.size > 0) {
      const copiedNodes = nodes
        .filter((n) => multiSelected.has(n.id))
        .map((n) => JSON.parse(JSON.stringify(n)))
      setClipboard({ nodes: copiedNodes, offset: 50 })
      message.success(`已复制 ${copiedNodes.length} 个节点`)
    } else if (selectedNode) {
      setClipboard({ nodes: [JSON.parse(JSON.stringify(selectedNode))], offset: 50 })
      message.success('已复制节点')
    }
  }, [multiSelected, nodes, selectedNode])

  const handlePaste = useCallback(() => {
    if (!clipboard || !clipboard.nodes || clipboard.nodes.length === 0) return

    const idMap = {}
    const newNodes = clipboard.nodes.map((node) => {
      const newId = `node_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`
      idMap[node.id] = newId
      return {
        ...node,
        id: newId,
        position: {
          x: node.position.x + clipboard.offset,
          y: node.position.y + clipboard.offset
        },
        selected: false
      }
    })

    newNodes.forEach((node) => addNode(node))
    setClipboard({ ...clipboard, offset: clipboard.offset + 30 })

    if (newNodes.length === 1) {
      setSelectedNode(newNodes[0])
    }

    message.success(`已粘贴 ${newNodes.length} 个节点`)
  }, [clipboard, addNode, setSelectedNode])

  useEffect(() => {
    const handleKeyDown = (e) => {
      const target = e.target
      const tagName = target.tagName.toLowerCase()
      const isInput = tagName === 'input' || tagName === 'textarea' || tagName === 'select' || target.isContentEditable

      if (isInput) return

      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault()
        undo()
      } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
        e.preventDefault()
        redo()
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
        e.preventDefault()
        handleCopy()
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
        e.preventDefault()
        handlePaste()
      } else if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault()
        handleDelete()
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'a') {
        e.preventDefault()
        const allIds = new Set(nodes.map((n) => n.id))
        setMultiSelected(allIds)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [undo, redo, handleCopy, handlePaste, handleDelete, nodes])

  const handleClear = () => {
    if (nodes.length === 0) {
      message.info('画布已经是空的')
      return
    }
    Modal.confirm({
      title: '确认清空画布',
      content: '此操作将删除所有节点和连线，且可通过撤销恢复。是否继续？',
      okText: '确认清空',
      cancelText: '取消',
      okType: 'danger',
      onOk: () => {
        clearAll()
        setMultiSelected(new Set())
        message.success('画布已清空')
      }
    })
  }

  const handleExport = () => {
    const json = exportRuleJson()
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ruleInfo = useRuleStore.getState().ruleInfo
    a.download = `${ruleInfo.name || 'rule'}_${Date.now()}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success('规则已导出')
  }

  const handleImportClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileImport = (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      try {
        const data = JSON.parse(event.target.result)
        loadRule(data)
        setMultiSelected(new Set())
        message.success('规则导入成功')
        setTimeout(() => fitView({ padding: 0.2 }), 100)
      } catch (err) {
        message.error('导入失败：JSON格式不正确')
      }
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  const handleZoomIn = () => zoomIn(0.2)
  const handleZoomOut = () => zoomOut(0.2)
  const handleFitView = () => fitView({ padding: 0.2 })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div
        style={{
          padding: '10px 16px',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          zIndex: 10
        }}
      >
        <Space size={4}>
          <Tooltip title="保存 (Ctrl+S)">
            <Button
              type="primary"
              icon={<SaveOutlined />}
              size="small"
              onClick={() => message.success('保存功能待接入后端')}
            >
              保存
            </Button>
          </Tooltip>
          <Tooltip title="撤销 (Ctrl+Z)">
            <Button
              icon={<UndoOutlined />}
              size="small"
              onClick={undo}
              disabled={past.length === 0}
            />
          </Tooltip>
          <Tooltip title="重做 (Ctrl+Y)">
            <Button
              icon={<RedoOutlined />}
              size="small"
              onClick={redo}
              disabled={future.length === 0}
            />
          </Tooltip>
          <div style={{ width: 1, height: 20, background: '#e8e8e8', margin: '0 4px' }} />
          <Tooltip title="复制 (Ctrl+C)">
            <Button
              icon={<CopyOutlined />}
              size="small"
              onClick={handleCopy}
              disabled={!selectedNode && multiSelected.size === 0}
            />
          </Tooltip>
          <Tooltip title="粘贴 (Ctrl+V)">
            <Button
              icon={<ScissorOutlined />}
              size="small"
              onClick={handlePaste}
              disabled={!clipboard}
            />
          </Tooltip>
          <Tooltip title="删除 (Delete)">
            <Button
              icon={<DeleteOutlined />}
              size="small"
              danger
              onClick={handleDelete}
              disabled={!selectedNode && multiSelected.size === 0 && !useRuleStore.getState().selectedEdge}
            />
          </Tooltip>
          <div style={{ width: 1, height: 20, background: '#e8e8e8', margin: '0 4px' }} />
          <Tooltip title="清空画布">
            <Button
              icon={<ClearOutlined />}
              size="small"
              onClick={handleClear}
              danger
            />
          </Tooltip>
        </Space>

        <Space size={4}>
          <Tooltip title="缩小">
            <Button icon={<ZoomOutOutlined />} size="small" onClick={handleZoomOut} />
          </Tooltip>
          <Tooltip title="放大">
            <Button icon={<ZoomInOutlined />} size="small" onClick={handleZoomIn} />
          </Tooltip>
          <Tooltip title="适应视图">
            <Button icon={<FullscreenOutlined />} size="small" onClick={handleFitView} />
          </Tooltip>
          <div style={{ width: 1, height: 20, background: '#e8e8e8', margin: '0 4px' }} />
          <Tooltip title="导入JSON">
            <Button icon={<UploadOutlined />} size="small" onClick={handleImportClick} />
          </Tooltip>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json"
            style={{ display: 'none' }}
            onChange={handleFileImport}
          />
          <Tooltip title="导出JSON">
            <Button icon={<DownloadOutlined />} size="small" onClick={handleExport} />
          </Tooltip>
        </Space>
      </div>

      <div
        ref={reactFlowWrapper}
        style={{ flex: 1, minHeight: 0, position: 'relative' }}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onPaneClick={clearSelection}
          nodeTypes={nodeTypes}
          style={rfStyle}
          fitView
          snapToGrid
          snapGrid={[15, 15]}
          defaultEdgeOptions={{
            animated: true,
            style: { strokeWidth: 2, stroke: '#91caff' }
          }}
          selectionOnDrag
          multiSelectionKeyCode={['Control', 'Meta']}
          deleteKeyCode={null}
          panOnDrag
          selectionModel="partial"
        >
          <Background gap={16} color="#e0e0e0" variant="dots" />
          <Controls showInteractive={false} position="bottom-left" />
          <MiniMap
            pannable
            zoomable
            style={minimapStyle}
            position="bottom-right"
            nodeColor={(node) => {
              switch (node.type) {
                case 'start': return '#52c41a'
                case 'end': return '#ff4d4f'
                case 'condition': return '#1890ff'
                case 'action': return '#faad14'
                default: return '#ccc'
              }
            }}
          />
        </ReactFlow>

        {(multiSelected.size > 1) && (
          <div
            style={{
              position: 'absolute',
              top: 60,
              left: 16,
              padding: '6px 12px',
              background: '#1677ff',
              color: '#fff',
              borderRadius: 4,
              fontSize: 12,
              fontWeight: 500,
              zIndex: 100,
              boxShadow: '0 2px 8px rgba(22,119,255,0.3)'
            }}
          >
            已选中 {multiSelected.size} 个节点
          </div>
        )}
      </div>
    </div>
  )
}

function Canvas() {
  return (
    <ReactFlowProvider>
      <CanvasInner />
    </ReactFlowProvider>
  )
}

export default Canvas
