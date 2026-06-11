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
    status: 'draft',
    priority: 5
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

  loadRule: (ruleData) => set({
    ruleInfo: {
      id: ruleData.id || null,
      name: ruleData.name || '',
      description: ruleData.description || '',
      status: ruleData.status || 'draft',
      priority: ruleData.priority || 5
    },
    nodes: ruleData.nodes || [],
    edges: ruleData.edges || [],
    past: [],
    future: [],
    selectedNode: null,
    selectedEdge: null,
    isDirty: false
  }),

  exportRuleJson: () => {
    const { ruleInfo, nodes, edges } = get()
    return JSON.stringify({
      id: ruleInfo.id || `rule_${Date.now()}`,
      name: ruleInfo.name,
      description: ruleInfo.description,
      status: ruleInfo.status,
      priority: ruleInfo.priority,
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
