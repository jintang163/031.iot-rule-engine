import { create } from 'zustand'

const MAX_HISTORY = 50

const useRuleStore = create((set, get) => ({
  nodes: [],
  edges: [],
  selectedNode: null,
  selectedEdge: null,
  ruleInfo: {
    id: null,
    name: '',
    description: '',
    status: 0,
    priority: 5,
    mutexGroup: '',
    windowEnabled: 0,
    windowType: 'TUMBLING',
    windowDuration: 600,
    windowAggregation: 'DELTA',
    windowField: 'temperature',
    windowOperator: '>',
    windowThreshold: 5,
    cooldownSeconds: 0,
    chainTriggerEnabled: 0,
    chainNextRuleIds: '',
    chainDisableSelf: 0
  },

  past: [],
  future: [],

  isDirty: false,
  isSaving: false,

  _saveToHistory: () => {
    const { nodes, edges, past } = get()
    const snapshot = {
      nodes: JSON.parse(JSON.stringify(nodes)),
      edges: JSON.parse(JSON.stringify(edges))
    }
    const newPast = [...past, snapshot].slice(-MAX_HISTORY)
    set({ past: newPast, future: [], isDirty: true })
  },

  undo: () => {
    const { past, nodes, edges, future } = get()
    if (past.length === 0) return
    const previous = past[past.length - 1]
    const newPast = past.slice(0, past.length - 1)
    const currentSnapshot = {
      nodes: JSON.parse(JSON.stringify(nodes)),
      edges: JSON.parse(JSON.stringify(edges))
    }
    set({
      past: newPast,
      future: [currentSnapshot, ...future],
      nodes: previous.nodes,
      edges: previous.edges,
      isDirty: true
    })
  },

  redo: () => {
    const { future, nodes, edges, past } = get()
    if (future.length === 0) return
    const next = future[0]
    const newFuture = future.slice(1)
    const currentSnapshot = {
      nodes: JSON.parse(JSON.stringify(nodes)),
      edges: JSON.parse(JSON.stringify(edges))
    }
    set({
      past: [...past, currentSnapshot],
      future: newFuture,
      nodes: next.nodes,
      edges: next.edges,
      isDirty: true
    })
  },

  setRuleInfo: (info) => {
    set({ ruleInfo: { ...get().ruleInfo, ...info }, isDirty: true })
  },

  setNodes: (nodes) => {
    get()._saveToHistory()
    set({ nodes, isDirty: true })
  },

  setEdges: (edges) => {
    get()._saveToHistory()
    set({ edges, isDirty: true })
  },

  addNode: (node) => {
    get()._saveToHistory()
    set((state) => ({
      nodes: [...state.nodes, node],
      isDirty: true
    }))
  },

  updateNode: (id, data) => {
    get()._saveToHistory()
    set((state) => ({
      nodes: state.nodes.map((node) =>
        node.id === id ? { ...node, data: { ...node.data, ...data } } : node
      ),
      isDirty: true
    }))
  },

  deleteNode: (id) => {
    get()._saveToHistory()
    set((state) => ({
      nodes: state.nodes.filter((node) => node.id !== id),
      edges: state.edges.filter((edge) => edge.source !== id && edge.target !== id),
      selectedNode: state.selectedNode?.id === id ? null : state.selectedNode,
      isDirty: true
    }))
  },

  addEdge: (edge) => {
    get()._saveToHistory()
    set((state) => ({
      edges: [...state.edges, edge],
      isDirty: true
    }))
  },

  updateEdge: (id, data) => {
    get()._saveToHistory()
    set((state) => ({
      edges: state.edges.map((edge) =>
        edge.id === id ? { ...edge, ...data } : edge
      ),
      isDirty: true
    }))
  },

  deleteEdge: (id) => {
    get()._saveToHistory()
    set((state) => ({
      edges: state.edges.filter((edge) => edge.id !== id),
      selectedEdge: state.selectedEdge?.id === id ? null : state.selectedEdge,
      isDirty: true
    }))
  },

  setSelectedNode: (node) => set({ selectedNode: node, selectedEdge: null }),

  setSelectedEdge: (edge) => set({ selectedEdge: edge, selectedNode: null }),

  clearSelection: () => set({ selectedNode: null, selectedEdge: null }),

  clearAll: () => {
    get()._saveToHistory()
    set({
      nodes: [],
      edges: [],
      selectedNode: null,
      selectedEdge: null,
      isDirty: true
    })
  },

  loadRule: (ruleData) => {
    let parsedNodes = ruleData.nodes || []
    let parsedEdges = ruleData.edges || []

    if (ruleData.ruleJson && typeof ruleData.ruleJson === 'string') {
      try {
        const parsed = JSON.parse(ruleData.ruleJson)
        parsedNodes = parsed.nodes || []
        parsedEdges = parsed.edges || []
      } catch (e) {
        console.error('解析 ruleJson 失败:', e)
      }
    }

    set({
      ruleInfo: {
        id: ruleData.id || null,
        name: ruleData.name || '',
        description: ruleData.description || '',
        status: typeof ruleData.status === 'number' ? ruleData.status : (ruleData.status === 'active' || ruleData.status === 'enabled' ? 1 : 0),
        priority: ruleData.priority || 5,
        mutexGroup: ruleData.mutexGroup || '',
        windowEnabled: ruleData.windowEnabled ?? 0,
        windowType: ruleData.windowType || 'TUMBLING',
        windowDuration: ruleData.windowDuration ?? 600,
        windowAggregation: ruleData.windowAggregation || 'DELTA',
        windowField: ruleData.windowField || 'temperature',
        windowOperator: ruleData.windowOperator || '>',
        windowThreshold: ruleData.windowThreshold ?? 5,
        cooldownSeconds: ruleData.cooldownSeconds ?? 0,
        chainTriggerEnabled: ruleData.chainTriggerEnabled ?? 0,
        chainNextRuleIds: ruleData.chainNextRuleIds || '',
        chainDisableSelf: ruleData.chainDisableSelf ?? 0
      },
      nodes: parsedNodes,
      edges: parsedEdges,
      past: [],
      future: [],
      selectedNode: null,
      selectedEdge: null,
      isDirty: false
    })
  },

  exportRuleData: () => {
    const { ruleInfo, nodes, edges } = get()
    return {
      id: ruleInfo.id || null,
      name: ruleInfo.name,
      description: ruleInfo.description,
      status: ruleInfo.status,
      priority: ruleInfo.priority,
      mutexGroup: ruleInfo.mutexGroup || '',
      windowEnabled: ruleInfo.windowEnabled,
      windowType: ruleInfo.windowType,
      windowDuration: ruleInfo.windowDuration,
      windowAggregation: ruleInfo.windowAggregation,
      windowField: ruleInfo.windowField,
      windowOperator: ruleInfo.windowOperator,
      windowThreshold: ruleInfo.windowThreshold,
      cooldownSeconds: ruleInfo.cooldownSeconds,
      chainTriggerEnabled: ruleInfo.chainTriggerEnabled,
      chainNextRuleIds: ruleInfo.chainNextRuleIds,
      chainDisableSelf: ruleInfo.chainDisableSelf,
      ruleJson: JSON.stringify({ nodes, edges, version: '1.0' })
    }
  },

  exportRuleJson: () => {
    const { ruleInfo, nodes, edges } = get()
    return JSON.stringify({
      id: ruleInfo.id || `rule_${Date.now()}`,
      name: ruleInfo.name,
      description: ruleInfo.description,
      status: ruleInfo.status,
      priority: ruleInfo.priority,
      mutexGroup: ruleInfo.mutexGroup || '',
      nodes,
      edges,
      version: '1.0',
      createdAt: new Date().toISOString()
    }, null, 2)
  },

  resetDirty: () => set({ isDirty: false }),

  setSaving: (saving) => set({ isSaving: saving })
}))

export default useRuleStore
