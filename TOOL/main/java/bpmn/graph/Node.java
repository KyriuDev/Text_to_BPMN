package bpmn.graph;

import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import chat_gpt.ast_management.ease.Path;
import other.Pair;
import other.Utils;
import bpmn.types.process.BpmnProcessObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Nodes are either traditional BPMN objects (tasks, gateways, ...)
 * or sequence flows.
 */
public class Node
{
    public static final boolean GRAPH_DUMP = false;
    private final BpmnProcessObject bpmnObject;
    private final Set<Node> childNodes;
    private final Set<Node> parentNodes;
    private final String id;
    private final HashSet<Node> parallelisableNodes;

    public Node(BpmnProcessObject bpmnObject)
    {
        this.bpmnObject = bpmnObject;
        this.childNodes = new HashSet<>();
        this.parentNodes = new HashSet<>();
        this.id = Utils.generateRandomIdentifier();
        this.parallelisableNodes = new HashSet<>();

        if (bpmnObject == null)
        {
            throw new AssertionError();
        }
    }

    //Public methods

    public BpmnProcessObject bpmnObject()
    {
        return this.bpmnObject;
    }

    public void addChild(Node node)
    {
        this.childNodes.add(node);
    }

    public void addChildAndForceParent(final Node node)
    {
        this.childNodes.add(node);
        node.addParent(this);
    }

    public void addParent(Node node)
    {
        this.parentNodes.add(node);
    }

    public void replaceParent(Node oldParent,
                              Node newParent)
    {
        this.parentNodes.remove(oldParent);
        this.parentNodes.add(newParent);
    }

    public void replaceChild(Node oldChild,
                             Node newChild)
    {
        this.childNodes.remove(oldChild);
        this.childNodes.add(newChild);
    }

    public void removeChildren(Node node)
    {
        this.childNodes.remove(node);
    }

    public void removeChildAndForceParent(Node node)
    {
        this.childNodes.remove(node);
        node.removeParent(this);
    }

    public void removeParent(Node node)
    {
        this.parentNodes.remove(node);
    }

    public Set<Node> childNodes()
    {
        return this.childNodes;
    }

    public Set<Node> parentNodes()
    {
        return this.parentNodes;
    }

    public boolean hasChildren()
    {
        return !this.childNodes.isEmpty();
    }

    public boolean hasParents()
    {
        return !this.parentNodes.isEmpty();
    }

    public boolean hasChild(final Node node)
    {
        return this.childNodes.contains(node);
    }

    public boolean hasParent(final Node node)
    {
        return this.parentNodes.contains(node);
    }

    public Node getChildFromID(final String id)
    {
        for (Node child : childNodes)
        {
            if (child.bpmnObject.id().equals(id))
            {
                return child;
            }
        }

        return null;
    }

    public int getMinDistanceToAncestor(final Node ancestor)
    {
        return this.getMinDistanceToAncestor(this, ancestor, 0, new HashSet<>());
    }

    public HashSet<Node> getReachableNodesUpTo(final Node bound)
    {
        final HashSet<Node> reachableNodes = new HashSet<>();
        this.getReachableNodesUpTo(this, bound, reachableNodes);
        return reachableNodes;
    }

    public boolean mayReachItselfBefore(final Node nodeToReachFirst)
    {
        for (Node child : this.childNodes)
        {
            if (!child.equals(this))
            {
                final boolean mayReachItselfBefore = this.mayReachItselfBefore(child, nodeToReachFirst, new HashSet<>());

                if (mayReachItselfBefore)
                {
                    return true;
                }
            }
        }

        return false;
    }

    public String stringify(int depth,
                            HashSet<Node> nodesAlreadyPrinted)
    {
        StringBuilder tabBuilder = new StringBuilder();
        tabBuilder.append("    ".repeat(Math.max(0, depth)));

        final StringBuilder builder = new StringBuilder();

        if (nodesAlreadyPrinted.contains(this))
        {
            builder
                .append(tabBuilder)
                .append("- Node \"")
                .append(this.bpmnObject.id())
                .append("\" (")
                .append(this.bpmnObject.name())
                .append(")")
                .append("\" (end of loop)\n")
            ;
        }
        else
        {
            nodesAlreadyPrinted.add(this);

            builder
                .append(tabBuilder)
                .append("- Node ")
                .append("\"")
                .append(this.bpmnObject.id())
                .append("\" ")
                .append(this.bpmnObject instanceof Gateway ? this.bpmnObject.type() == BpmnProcessType.EXCLUSIVE_GATEWAY ? "EXCLUSIVE" : "PARALLEL" : "")
                .append(this.bpmnObject instanceof Gateway ? ((Gateway) this.bpmnObject).isSplitGateway() ? " SPLIT" : " MERGE" : "")
                .append(" (")
                .append(this.bpmnObject.name())
                .append(") executes with probability ")
                .append(this.bpmnObject.probability())
                .append(" and has ")
                .append(this.childNodes.isEmpty() ? "no" : this.childNodes.size())
                .append(" child:\n");

            final ArrayList<HashSet<Node>> visitedNodes = new ArrayList<>();
            visitedNodes.add(nodesAlreadyPrinted);

            for (int i = 1; i < this.childNodes.size(); i++)
            {
                visitedNodes.add(new HashSet<>(nodesAlreadyPrinted));
            }

            int i = 0;

            for (Node child : this.childNodes)
            {
                builder.append(child.stringify(depth + 1, visitedNodes.get(i++)));
            }
        }

        if (GRAPH_DUMP)
        {
            try
            {
                Files.write(Paths.get("/home/quentin/Bureau/test.txt"), builder.toString().getBytes());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        return builder.toString();
    }

    public String stringifyRevert(int depth,
                                  ArrayList<Node> nodesAlreadyPrinted)
    {
        StringBuilder tabBuilder = new StringBuilder();
        tabBuilder.append("    ".repeat(Math.max(0, depth)));

        final StringBuilder builder = new StringBuilder();

        if (nodesAlreadyPrinted.contains(this))
        {
            builder
                    .append(tabBuilder)
                    .append("- Node \"")
                    .append(this.bpmnObject.id())
                    .append("\" (")
                    .append(this.bpmnObject.name())
                    .append(")")
                    .append("\" (end of loop)\n")
            ;
        }
        else
        {
            nodesAlreadyPrinted.add(this);

            builder
                    .append(tabBuilder)
                    .append("- Node ")
                    .append("\"")
                    .append(this.bpmnObject.id())
                    .append("\" (")
                    .append(this.bpmnObject.name())
                    .append(") executes with probability ")
                    .append(this.bpmnObject.probability())
                    .append(" and has ")
                    .append(this.parentNodes.size() == 0 ? "no" : this.parentNodes.size())
                    .append(" parents:\n");

            final ArrayList<ArrayList<Node>> visitedNodes = new ArrayList<>();
            visitedNodes.add(nodesAlreadyPrinted);

            for (int i = 1; i < this.parentNodes.size(); i++)
            {
                visitedNodes.add(new ArrayList<>(nodesAlreadyPrinted));
            }

            int i = 0;

            for (Node parent : this.parentNodes)
            {
                builder.append(parent.stringifyRevert(depth + 1, visitedNodes.get(i++)));
            }
        }

        if (GRAPH_DUMP)
        {
            try {
                Files.write(Paths.get("/home/quentin/Bureau/test.txt"), builder.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return builder.toString();
    }

    public HashSet<Node> getParentsReaching(final HashSet<Node> nodes)
    {
        final HashSet<Node> reachingParents = new HashSet<>();

        for (Node parent : this.parentNodes)
        {
            for (Node node : nodes)
            {
                if (parent.equals(node)
                    || parent.hasAncestor(node))
                {
                    reachingParents.add(parent);
                }
            }
        }

        return reachingParents;
    }

    public boolean isInLoop()
    {
        return this.isAncestorOf(this);
    }

    public boolean hasSuccessor(Node n)
    {
        return this.isAncestorOf(n);
    }

    public boolean hasAtLeastOneSuccessorAmong(final Collection<Node> nodes)
    {
        for (Node node : nodes)
        {
            if (this.hasSuccessor(node))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isAtLeastSuccessorOfOneNodeAmong(final Collection<Node> nodes)
    {
        for (Node node : nodes)
        {
            if (this.isSuccessorOf(node))
            {
                return true;
            }
        }

        return false;
    }

    public HashSet<Node> getAllSuccessorsUpTo(final Node boundNode)
    {
        final HashSet<Node> successors = new HashSet<>();

        for (Node child : this.childNodes)
        {
            this.getAllSuccessorsUpTo(child, boundNode, successors);
        }

        return successors;
    }

    public boolean canReachStartBefore(final Node nodeToReachFirst)
    {
        return this.canReachStartBefore(this, nodeToReachFirst, new HashSet<>());
    }

    public boolean hasAncestor(Node n)
    {
        return this.isSuccessorOf(n);
    }

    public boolean hasSuccessor(Node n,
                                final HashSet<Pair<Node,Node>> loopyCorrespondences)
    {
        return this.isAncestorOf(n, loopyCorrespondences);
    }

    public boolean hasAncestor(Node n,
                               final HashSet<Pair<Node, Node>> loopyCorrespondences)
    {
        return this.isSuccessorOf(n, loopyCorrespondences);
    }

    public boolean isAncestorOf(Node n)
    {
        return isAncestorOf(n, new HashSet<>(), new HashSet<>());
    }

    public boolean isSuccessorOf(Node n)
    {
        return isSuccessorOf(n, new HashSet<>(), new HashSet<>());
    }

    public boolean isAncestorOf(final Node n,
                                final HashSet<Pair<Node, Node>> loopLinks)
    {
        return isAncestorOf(n, loopLinks, new HashSet<>());
    }

    public boolean isSuccessorOf(final Node n,
                                 final HashSet<Pair<Node, Node>> loopLinks)
    {
        return isSuccessorOf(n, loopLinks, new HashSet<>());
    }

    public Node weakCopy()
    {
        return new Node(this.bpmnObject);
    }

    public Node deepCopy()
    {
        return new Node(this.bpmnObject.copy());
    }

    public void removeParents()
    {
        this.parentNodes.clear();
    }

    public void removeChildren()
    {
        this.childNodes.clear();
    }

    public boolean canEscapeLoop()
    {
        if (!this.isInLoop())
        {
            return true;
        }

        for (Node parent : this.parentNodes)
        {
            if (!parent.isInLoop())
            {
                return true;
            }
        }

        return false;
    }

    public void addParallellisableNode(final Node node)
    {
        this.parallelisableNodes.add(node);
    }

    public HashSet<Node> parallelisableNodes()
    {
        return this.parallelisableNodes;
    }

    public boolean isSplit()
    {
        return this.childNodes.size() > 1;
    }

    public boolean isMerge()
    {
        return this.parentNodes.size() > 1;
    }

    //Private methods

    private int getMinDistanceToAncestor(final Node currentNode,
                                         final Node ancestorToReach,
                                         final int currentDistance,
                                         final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return Integer.MAX_VALUE;
        }

        visitedNodes.add(currentNode);

        if (currentNode.equals(ancestorToReach))
        {
            return currentDistance;
        }

        int minDistance = -1;

        for (Node parent : currentNode.parentNodes())
        {
            if (minDistance == -1)
            {
                minDistance = this.getMinDistanceToAncestor(parent, ancestorToReach, currentDistance + 1, visitedNodes);
            }
            else
            {
                final int parentDistance = this.getMinDistanceToAncestor(parent, ancestorToReach, currentDistance + 1, visitedNodes);

                if (parentDistance < minDistance)
                {
                    minDistance = parentDistance;
                }
            }
        }

        return minDistance;
    }

    private boolean canReachStartBefore(final Node currentNode,
                                        final Node nodeToReachFirst,
                                        final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return false;
        }

        visitedNodes.add(currentNode);

        if (currentNode.equals(nodeToReachFirst))
        {
            return false;
        }

        if (!currentNode.hasParents())
        {
            return true;
        }

        for (Node parent : currentNode.parentNodes())
        {
            final boolean canReachStartBeforeNode = this.canReachStartBefore(parent, nodeToReachFirst, visitedNodes);

            if (canReachStartBeforeNode)
            {
                return true;
            }
        }

        return false;
    }

    private void getAllSuccessorsUpTo(final Node currentNode,
                                      final Node boundNode,
                                      final HashSet<Node> successors)
    {
        if (currentNode.equals(boundNode)
            || successors.contains(currentNode))
        {
            return;
        }

        successors.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            this.getAllSuccessorsUpTo(child, boundNode, successors);
        }
    }

    private void getReachableNodesUpTo(final Node currentNode,
                                       final Node bound,
                                       final HashSet<Node> reachableNodes)
    {
        if (reachableNodes.contains(currentNode)
            || currentNode.equals(bound))
        {
            return;
        }

        reachableNodes.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            this.getReachableNodesUpTo(child, bound, reachableNodes);
        }
    }

    private boolean mayReachItselfBefore(final Node currentNode,
                                         final Node nodeToReachFirst,
                                         final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return false;
        }

        visitedNodes.add(currentNode);

        if (currentNode.equals(this))
        {
            return true;
        }
        else if (currentNode.equals(nodeToReachFirst))
        {
            return false;
        }

        if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
            && ((Gateway) currentNode.bpmnObject()).isSplitGateway())
        {
            final Node correspondingMerge = new Node(BpmnProcessFactory.generateParallelGateway(currentNode.bpmnObject().id().replace("Gateway_", "") + "_merge"));
            //System.out.println("Corresponding merge: " + correspondingMerge);

            if (currentNode.hasSuccessor(correspondingMerge))
            {
                final HashSet<Node> betweenNodes = currentNode.getReachableNodesUpTo(correspondingMerge);

                if (betweenNodes.contains(nodeToReachFirst))
                {
                    return false;
                }
            }
        }

        for (Node child : currentNode.childNodes())
        {
            final boolean canReachItselfBefore = this.mayReachItselfBefore(child, nodeToReachFirst, visitedNodes);

            if (canReachItselfBefore)
            {
                return true;
            }
        }

        return false;
    }

    private void getAllPathsLeadingTo(final Node nodeToReach,
                                      final Node currentNode,
                                      final HashSet<Node> visitedNodes,
                                      final HashSet<Path<Node>> allPaths,
                                      final Path<Node> currentPath)
    {
        currentPath.add(currentNode);

        if (visitedNodes.contains(currentNode))
        {
            return;
        }

        visitedNodes.add(currentNode);

        if (currentNode.equals(nodeToReach)) return;

        final ArrayList<Path<Node>> nextPaths = new ArrayList<>();
        nextPaths.add(currentPath);

        for (int i = 1; i < currentNode.childNodes().size(); i++)
        {
            final Path<Node> nextPath = currentPath.copy();
            allPaths.add(nextPath);
        }

        int i = 0;

        for (Node child : currentNode.childNodes())
        {
            this.getAllPathsLeadingTo(nodeToReach, child, new HashSet<>(visitedNodes), allPaths, nextPaths.get(i++));
        }
    }

    /**
     * Used to know whether the current node (this)
     * is an ancestor of the Node n in parameter.
     *
     * @param n the node to be found
     * @param visitedChild the list of already visited child (recursion breaker)
     * @return true if the Node on which this function has been called is an ancestor of Node n, false otherwise
     */
    private boolean isAncestorOf(Node n,
                                 final HashSet<Pair<Node, Node>> loopLinks,
                                 Set<Node> visitedChild)
    {
        if (this.childNodes.contains(n))
        {
            return true;
        }

        if (visitedChild.contains(this))
        {
            return false;
        }

        visitedChild.add(this);

        for (Node child : this.childNodes)
        {
            final boolean childIsParent = child.isAncestorOf(n, loopLinks, visitedChild);

            if (childIsParent)
            {
                return true;
            }
        }

        for (Pair<Node, Node> loopLink : loopLinks)
        {
            if (loopLink.first().equals(this))
            {
                if (loopLink.second().equals(n))
                {
                    return true;
                }

                final boolean childIsParent = loopLink.second().isAncestorOf(n, loopLinks, visitedChild);

                if (childIsParent)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Used to know whether the current node (this)
     * is a successor of the Node n in parameter.
     *
     * @param n the node to be found
     * @param visitedParents the list of already visited child (recursion breaker)
     * @return true if the Node on which this function has been called is a successor of Node n, false otherwise
     */
    private boolean isSuccessorOf(Node n,
                                  final HashSet<Pair<Node, Node>> loopLinks,
                                  Set<Node> visitedParents)
    {
        if (this.parentNodes.contains(n))
        {
            return true;
        }

        if (visitedParents.contains(this))
        {
            return false;
        }

        visitedParents.add(this);

        for (Node parent : this.parentNodes)
        {
            final boolean parentIsChild = parent.isSuccessorOf(n, loopLinks, visitedParents);

            if (parentIsChild)
            {
                return true;
            }
        }

        for (Pair<Node, Node> loopLink : loopLinks)
        {
            if (loopLink.second().equals(this))
            {
                if (loopLink.first().equals(n))
                {
                    return true;
                }

                final boolean parentIsChild = loopLink.first().isSuccessorOf(n, loopLinks, visitedParents);

                if (parentIsChild)
                {
                    return true;
                }
            }
        }

        return false;
    }

    //Overrides

    @Override
    public int hashCode()
    {
        return this.bpmnObject.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Node))
        {
            return false;
        }

        return this.bpmnObject.equals(((Node) o).bpmnObject);
    }

    @Override
    public String toString()
    {
        return (this.bpmnObject.name() == null || this.bpmnObject.name().isEmpty()) ? this.bpmnObject.id() : this.bpmnObject.name();
    }
}
