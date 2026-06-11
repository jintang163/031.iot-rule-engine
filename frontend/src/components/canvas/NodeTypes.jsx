import ConditionNode from './nodes/ConditionNode.jsx'
import ActionNode from './nodes/ActionNode.jsx'
import StartNode from './nodes/StartNode.jsx'
import EndNode from './nodes/EndNode.jsx'

const nodeTypes = {
  condition: ConditionNode,
  action: ActionNode,
  start: StartNode,
  end: EndNode
}

export default nodeTypes
