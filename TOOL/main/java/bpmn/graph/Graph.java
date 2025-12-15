package bpmn.graph;

import bpmn.types.process.*;
import other.Pair;
import other.Triple;
import other.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Graph
{
    protected final String id;
    protected final Node initialNode;
    protected final HashSet<Node> endNodes;
    protected boolean hasStrongFlows;

    public Graph(Node initialNode)
    {
        this.initialNode = initialNode;
        this.id = Utils.generateRandomIdentifier(30);
        this.hasStrongFlows = false;
        this.endNodes = new HashSet<>();
    }

    public Node getNodeFromObject(BpmnProcessObject object)
    {
        return this.getNodeFromObject(this.initialNode, object, new HashSet<>());
    }

    public boolean hasNode(Node n)
    {
        if (n == null)
        {
            return false;
        }

        return this.getNodeFromID(n.bpmnObject().id()) != null;
    }

    public boolean hasNodeNamed(final String name)
    {
        if (name == null)
        {
            return false;
        }

        return this.getNodeFromName(name) != null;
    }

    public Node getNodeFromName(final String name)
    {
        if (name == null)
        {
            return null;
        }

        return this.getNodeFromName(name, this.initialNode, new HashSet<>());
    }

    public boolean hasNode(BpmnProcessObject object)
    {
        if (object == null)
        {
            return false;
        }

        return this.getNodeFromID(object.id()) != null;
    }

    public void addEndNode(final Node endNode)
    {
        this.endNodes.add(endNode);
    }

    public void computeEndNodes()
    {
        this.computeEndNodes(this.initialNode, new HashSet<>());
    }

    public HashSet<Node> endNodes()
    {
        return this.endNodes;
    }

    public boolean hasNodeOfId(final String id)
    {
        if (id == null)
        {
            return false;
        }

        return this.getNodeFromID(id) != null;
    }

    public Node getNodeFromID(final String id)
    {
        return this.getNodeFromIDRec(id, new HashSet<>(), this.initialNode);
    }

    public Node initialNode()
    {
        return this.initialNode;
    }

    /**
     * CAUTION: In this implementation, we assume that there exists a SINGLE end event
     * inside the BPMN process, that is considered as the end node of our graph
     *
     * @return the end node of the process
     */
    public Node lastNode()
    {
        return this.findLastNodeRec(this.initialNode, new HashSet<>());
    }

    public Graph weakCopy()
    {
        final Node initialNode = new Node(this.initialNode.bpmnObject());
        final Graph copiedGraph = new Graph(initialNode);
        //Correspondences between the node of the old graph, and the node of the new graph
        final HashMap<Node, Node> correspondences = new HashMap<>();
        correspondences.put(this.initialNode, initialNode);

        this.copyRec(this.initialNode, initialNode, new HashSet<>(), correspondences, false);

        return copiedGraph;
    }

    public Graph weakCopyFrom(final Node n)
    {
        final Node initialNode = new Node(n.bpmnObject());
        final Graph copiedGraph = new Graph(initialNode);
        //Correspondences between the node of the old graph, and the node of the new graph
        final HashMap<Node, Node> correspondences = new HashMap<>();
        correspondences.put(n, initialNode);

        this.copyRec(n, initialNode, new HashSet<>(), correspondences, false);

        return copiedGraph;
    }

    public Graph deepCopy()
    {
        final Node initialNode = new Node(this.initialNode.bpmnObject().copy());
        final Graph copiedGraph = new Graph(initialNode);

        final HashMap<Node, Node> correspondences = new HashMap<>();
        correspondences.put(this.initialNode, initialNode);

        this.copyRec(this.initialNode, initialNode, new HashSet<>(), correspondences, true);

        return copiedGraph;
    }

    public Graph deepCopyFrom(final Node n)
    {
        final Node initialNode = new Node(n.bpmnObject().copy());
        final Graph copiedGraph = new Graph(initialNode);
        //Correspondences between the node of the old graph, and the node of the new graph
        final HashMap<Node, Node> correspondences = new HashMap<>();
        correspondences.put(n, initialNode);

        this.copyRec(n, initialNode, new HashSet<>(), correspondences, true);

        return copiedGraph;
    }

    public Graph cutAt(final Node n)
    {
        this.cutAtRec(this.initialNode, n, new HashSet<>());
        return this;
    }

    public HashSet<Node> toSet()
    {
        final HashSet<Node> nodes = new HashSet<>();
        this.toSet(this.initialNode, nodes);
        return nodes;
    }

    public void markAsContainingStrongFlows()
    {
        this.hasStrongFlows = true;
    }

    public boolean hasStrongFlows()
    {
        return this.hasStrongFlows;
    }

    public void clearParallelGatewaysTime()
    {
        this.clearParallelGatewayTime(this.initialNode, new HashSet<>());
    }

    public Graph cleanGateways()
    {
        boolean cleant = true;

        while (cleant)
        {
            cleant = false;

            final Triple<Node, Node, Boolean> cleanableGateway = this.getCleanableGateway(this.initialNode, new HashSet<>());

            if (cleanableGateway != null)
            {
                System.out.println("Cleanable gateway: " + cleanableGateway.toString());
                this.cleanGateway(cleanableGateway);
                cleant = true;
            }
        }

        return this;
    }

    public Graph cleanDummyTasks()
    {
        boolean cleant = true;

        while (cleant)
        {
            cleant = false;

            final Node cleanableTask = this.getCleanableTask(this.initialNode, new HashSet<>());

            if (cleanableTask != null)
            {
                final Node parentFlow = cleanableTask.parentNodes().iterator().next();
                final Node childFlow = cleanableTask.childNodes().iterator().next();
                final Node childFlowChild = childFlow.childNodes().iterator().next();
                parentFlow.removeChildAndForceParent(cleanableTask);
                childFlow.removeChildAndForceParent(childFlowChild);
                parentFlow.addChildAndForceParent(childFlowChild);
                cleant = true;
            }
        }

        return this;
    }

    public Graph synchronise()
    {
        boolean modified = true;

        while (modified)
        {
            modified = false;

            final Pair<Set<Node>, Set<Node>> synchronisableElements = this.getSynchronisableElements(this.initialNode, new HashSet<>());

            if (synchronisableElements != null)
            {
                this.synchronise(synchronisableElements);
                modified = true;
            }
        }

        return this;
    }

    public int nbNodes()
    {
        final HashSet<Node> nodes = new HashSet<>();
        this.getAllNodes(this.initialNode, nodes);
        return nodes.size();
    }

    public int nbTasks()
    {
        final HashSet<Node> tasks = new HashSet<>();
        this.getAllTasks(this.initialNode, tasks, new HashSet<>());
        return tasks.size();
    }

    public int nbParallelGateways()
    {
        final HashSet<Node> parallelGateways = new HashSet<>();
        this.getAllParallelGateways(this.initialNode, parallelGateways, new HashSet<>());
        return parallelGateways.size();
    }

    public int nbExclusiveGateways()
    {
        final HashSet<Node> exclusiveGateways = new HashSet<>();
        this.getAllExclusiveGateways(this.initialNode, exclusiveGateways, new HashSet<>());
        return exclusiveGateways.size();
    }

    public int nbParallelSplits()
    {
        final HashSet<Node> parallelSplits = new HashSet<>();
        this.getAllParallelSplits(this.initialNode, parallelSplits, new HashSet<>());
        return parallelSplits.size();
    }

    public int nbParallelMerges()
    {
        final HashSet<Node> parallelMerges = new HashSet<>();
        this.getAllParallelMerges(this.initialNode, parallelMerges, new HashSet<>());
        return parallelMerges.size();
    }

    public int nbExclusiveSplits()
    {
        final HashSet<Node> exclusiveSplits = new HashSet<>();
        this.getAllExclusiveSplits(this.initialNode, exclusiveSplits, new HashSet<>());
        return exclusiveSplits.size();
    }

    public int nbExclusiveMerges()
    {
        final HashSet<Node> exclusiveMerges = new HashSet<>();
        this.getAllExclusiveMerges(this.initialNode, exclusiveMerges, new HashSet<>());
        return exclusiveMerges.size();
    }

    //Private methods

    private void synchronise(final Pair<Set<Node>, Set<Node>> synchronisableElements)
    {
        final Node dummySynchro = new Node(BpmnProcessFactory.generateTask("DUMMY_SYNCHRO_" + Utils.generateRandomIdentifier()));

        for (Node parent : synchronisableElements.first())
        {
            for (Node child : synchronisableElements.second())
            {
                parent.removeChildren(child);
                child.removeParent(parent);
            }

            parent.addChildAndForceParent(dummySynchro);
        }

        for (Node child : synchronisableElements.second())
        {
            dummySynchro.addChildAndForceParent(child);
        }
    }

    /**
     * Two nodes are said to be synchronisable if they have exactly the same children,
     * and if their children have exactly the same parents
     *
     * @param currentNode
     * @param visitedNodes
     * @return
     */
    private Pair<Set<Node>, Set<Node>> getSynchronisableElements(final Node currentNode,
                                                                 final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.childNodes().size() > 1)
        {
            final Set<Node> parentsReference = currentNode.childNodes().iterator().next().parentNodes();

            if (parentsReference.size() > 1)
            {
                boolean synchronisable = true;

                for (Node child : currentNode.childNodes())
                {
                    if (!parentsReference.equals(child.parentNodes()))
                    {
                        //Parents were different
                        synchronisable = false;
                        break;
                    }

                    for (Node parent : child.parentNodes())
                    {
                        if (!parent.childNodes().equals(currentNode.childNodes()))
                        {
                            synchronisable = false;
                            break;
                        }
                    }

                    if (!synchronisable)
                    {
                        break;
                    }
                }

                if (synchronisable)
                {
                    return new Pair<>(currentNode.childNodes().iterator().next().parentNodes(), currentNode.childNodes());
                }
            }
        }

        for (Node child : currentNode.childNodes())
        {
            final Pair<Set<Node>, Set<Node>> synchronisableElements = this.getSynchronisableElements(child, visitedNodes);

            if (synchronisableElements != null)
            {
                return synchronisableElements;
            }
        }

        return null;
    }

    private void cleanGateway(final Triple<Node, Node, Boolean> gatewaysToClean)
    {
        final Node firstGateway = gatewaysToClean.first();
        final Node secondGateway = gatewaysToClean.second();
        final boolean areMergeGateways = gatewaysToClean.third();

        if (areMergeGateways)
        {
            //We should keep the second gateway and remove the first one
            final Set<Node> firstGatewayParents = firstGateway.parentNodes();
            secondGateway.removeParent(firstGateway);

            for (Node firstGatewayParent : firstGatewayParents)
            {
                firstGatewayParent.removeChildren(firstGateway);
                firstGatewayParent.addChildAndForceParent(secondGateway);
            }
        }
        else
        {
            //We should keep the first gateway and remove the second one
            final Set<Node> secondGatewayChildren = secondGateway.childNodes();
            firstGateway.removeChildren(secondGateway);

            for (Node secondGatewayChild : secondGatewayChildren)
            {
                secondGatewayChild.removeParent(secondGateway);
                firstGateway.addChildAndForceParent(secondGatewayChild);
            }
        }
    }

    private Triple<Node, Node, Boolean> getCleanableGateway(final Node currentNode,
                                                            final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.childNodes().isEmpty()) return null;

        if (currentNode.bpmnObject() instanceof Gateway)
        {
            final Node flowChild = currentNode.childNodes().iterator().next();

            for (Node child  : flowChild.childNodes())
            {
                if (child.bpmnObject().type() == currentNode.bpmnObject().type())
                {
                    final Gateway currentGateway = (Gateway) currentNode.bpmnObject();
                    final Gateway childGateway = (Gateway) child.bpmnObject();

                    if (currentGateway.isMergeGateway()
                        && childGateway.isMergeGateway())
                    {
                        return new Triple<>(currentNode, child, true);
                    }
                    else if (currentGateway.isSplitGateway()
                            && childGateway.isSplitGateway())
                    {
                        return new Triple<>(currentNode, child, false);
                    }
                }
            }
        }
        else
        {
            for (Node child : currentNode.childNodes())
            {
                final Triple<Node, Node, Boolean> cleanableGateway = this.getCleanableGateway(child, visitedNodes);

                if (cleanableGateway != null)
                {
                    return cleanableGateway;
                }
            }
        }

        return null;
    }

    private Node getCleanableTask(final Node currentNode,
                                  final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().id().contains("DUMMY")
            || currentNode.bpmnObject().id().contains("SYNCHRONIZATION"))
        {
            return currentNode;
        }

        for (Node child : currentNode.childNodes())
        {
            final Node cleanableTask = this.getCleanableTask(child, visitedNodes);

            if (cleanableTask != null)
            {
                return cleanableTask;
            }
        }

        return null;
    }

    private void getAllNodes(final Node currentNode,
                             final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            this.getAllNodes(child, visitedNodes);
        }
    }

    private void getAllTasks(final Node currentNode,
                             final HashSet<Node> tasks,
                             final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject() instanceof Task)
        {
            tasks.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllTasks(child, tasks, visitedNodes);
        }
    }

    private void getAllParallelGateways(final Node currentNode,
                                        final HashSet<Node> parallelGateways,
                                        final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
        {
            parallelGateways.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllParallelGateways(child, parallelGateways, visitedNodes);
        }
    }

    private void getAllParallelSplits(final Node currentNode,
                                      final HashSet<Node> parallelSplits,
                                      final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
            && ((Gateway) currentNode.bpmnObject()).isSplitGateway())
        {
            parallelSplits.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllParallelSplits(child, parallelSplits, visitedNodes);
        }
    }

    private void getAllParallelMerges(final Node currentNode,
                                      final HashSet<Node> parallelMerges,
                                      final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
                && ((Gateway) currentNode.bpmnObject()).isMergeGateway())
        {
            parallelMerges.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllParallelMerges(child, parallelMerges, visitedNodes);
        }
    }

    private void getAllExclusiveGateways(final Node currentNode,
                                         final HashSet<Node> exclusiveGateways,
                                         final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
        {
            exclusiveGateways.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllExclusiveGateways(child, exclusiveGateways, visitedNodes);
        }
    }

    private void getAllExclusiveSplits(final Node currentNode,
                                       final HashSet<Node> exclusiveSplits,
                                       final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
                && ((Gateway) currentNode.bpmnObject()).isSplitGateway())
        {
            exclusiveSplits.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllExclusiveSplits(child, exclusiveSplits, visitedNodes);
        }
    }

    private void getAllExclusiveMerges(final Node currentNode,
                                       final HashSet<Node> exclusiveMerges,
                                       final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
                && ((Gateway) currentNode.bpmnObject()).isMergeGateway())
        {
            exclusiveMerges.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.getAllExclusiveMerges(child, exclusiveMerges, visitedNodes);
        }
    }

    private void clearParallelGatewayTime(final Node currentNode,
                                          final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
            && ((Gateway) currentNode.bpmnObject()).isMergeGateway())
        {
            ((Gateway) currentNode.bpmnObject()).parallelPathsExecutionTimes().clear();
        }

        for (Node child : currentNode.childNodes())
        {
            this.clearParallelGatewayTime(child, visitedNodes);
        }
    }

    private void cutAtRec(final Node currentNode,
                          final Node nodeToReach,
                          final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.equals(nodeToReach))
        {
            //We remove the current node from the list of parents of the child node
            for (Node child : currentNode.childNodes())
            {
                child.parentNodes().remove(currentNode);
            }
            //We remove all the children of the current node
            currentNode.childNodes().clear();
            return;
        }

        for (Node child : currentNode.childNodes())
        {
            this.cutAtRec(child, nodeToReach, visitedNodes);
        }
    }

    private void toSet(final Node currentNode,
                       final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            this.toSet(child, visitedNodes);
        }
    }

    private void copyRec(final Node currentOldNode,
                         final Node currentNewNode,
                         final Set<Node> visitedNodes,
                         final HashMap<Node, Node> correspondences,
                         final boolean deepCopy)
    {
        if (visitedNodes.contains(currentOldNode))
        {
            return;
        }

        visitedNodes.add(currentOldNode);

        for (Node oldChild : currentOldNode.childNodes())
        {
            final Node newChild = correspondences.computeIfAbsent(oldChild, n -> deepCopy ? new Node(oldChild.bpmnObject().copy()) : new Node(oldChild.bpmnObject()));
            currentNewNode.addChild(newChild);
            newChild.addParent(currentNewNode);

            this.copyRec(oldChild, newChild, visitedNodes, correspondences, deepCopy);
        }
    }

    private Node getNodeFromName(final String name,
                                 final Node currentNode,
                                 final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().name().equals(name))
        {
            return currentNode;
        }

        for (Node child : currentNode.childNodes())
        {
            final Node nodeToFind = this.getNodeFromName(name, child, visitedNodes);

            if (nodeToFind != null)
            {
                return nodeToFind;
            }
        }

        return null;
    }

    private Node getNodeFromObject(Node node,
                                   BpmnProcessObject object,
                                   Set<Node> nodesAlreadyVisited)
    {
        if (node.bpmnObject().equals(object))
        {
            return node;
        }

        if (nodesAlreadyVisited.contains(node))
        {
            return null;
        }

        nodesAlreadyVisited.add(node);

        if (node.hasChildren())
        {
            for (Node childNode : node.childNodes())
            {
                Node foundNode = getNodeFromObject(childNode, object, nodesAlreadyVisited);

                if (foundNode != null)
                {
                    return foundNode;
                }
            }
        }

        return null;
    }

    private Node getNodeFromIDRec(final String id,
                                  final Set<Node> visitedNodes,
                                  final Node currentNode)
    {
        if (currentNode.bpmnObject().id().equals(id))
        {
            return currentNode;
        }

        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            Node existingNode = getNodeFromIDRec(id, visitedNodes, child);

            if (existingNode != null)
            {
                return existingNode;
            }
        }

        return null;
    }

    private Node findLastNodeRec(final Node currentNode,
                                 final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.childNodes().isEmpty())
        {
            //An event with no child is considered as the end event
            return currentNode;
        }

        for (Node child : currentNode.childNodes())
        {
            final Node endEvent = findLastNodeRec(child, visitedNodes);

            if (endEvent != null)
            {
                return endEvent;
            }
        }

        return null;
    }

    private void computeEndNodes(final Node currentNode,
                                 final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.END_EVENT)
        {
            this.endNodes.add(currentNode);
        }

        for (Node child : currentNode.childNodes())
        {
            this.computeEndNodes(child, visitedNodes);
        }
    }

    //Overrides

    @Override
    public String toString()
    {
        return this.initialNode.stringify(0, new HashSet<>());
        //return this.lastNode().stringifyRevert(0, new ArrayList<>());
    }

    public String stringify()
    {
        return this.lastNode().stringifyRevert(0, new ArrayList<>());
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Graph))
        {
            return false;
        }

        return this.id.equals(((Graph) o).id);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;

        for (int i = 0; i < this.id.length(); i++)
        {
            hash = hash * 31 + this.id.charAt(i);
        }

        return hash;
    }

    //OLD EQUALITY METHODS
    /*//TODO Check if the fact that we consider two graph equals by checking
    //TODO only their first node can be a problem or not.
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Graph))
        {
            return false;
        }

        return ((Graph) o).initialNode.bpmnObject().id().equals(this.initialNode.bpmnObject().id());
    }

    @Override
    public int hashCode()
    {
        int hash = 7;

        for (int i = 0; i < this.initialNode.bpmnObject().id().length(); i++)
        {
            hash = hash * 31 + this.initialNode.bpmnObject().id().charAt(i);
        }

        return hash;
    }*/
}
