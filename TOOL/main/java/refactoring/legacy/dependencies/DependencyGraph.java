package refactoring.legacy.dependencies;

import bpmn.types.process.BpmnProcessFactory;
import chat_gpt.ast_management.AbstractSyntaxNode;
import chat_gpt.ast_management.AbstractSyntaxNodeFactory;
import chat_gpt.ast_management.AbstractSyntaxTree;
import other.MyOwnLogger;
import other.Pair;
import other.Utils;
import bpmn.graph.Node;

import java.util.*;

public class DependencyGraph
{
	private static final String DUMMY_STATIC_NODE_NAME = "SYNCHRONIZATION_";
	private final HashSet<Node> initialNodes;
	private final HashSet<Node> endNodes;
	private final String id;
	private String hash;

	public DependencyGraph()
	{
		this.initialNodes = new HashSet<>();
		this.endNodes = new HashSet<>();
		this.id = Utils.generateRandomIdentifier(30);
		this.hash = "-1";
	}

	public DependencyGraph(final String id)
	{
		this.initialNodes = new HashSet<>();
		this.endNodes = new HashSet<>();
		this.id = id;
		this.hash = "-1";
	}

	//Overrides

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof DependencyGraph))
		{
			return false;
		}

		return ((DependencyGraph) o).id.equals(this.id);
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

	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder();

		for (Node initialNode : this.initialNodes())
		{
			builder.append(initialNode.stringify(0, new HashSet<>()))
					.append("\n\n");
		}

		return builder.toString();
	}

	//Public methods

	public void cleanInitialNodes()
	{
		/*
            Due to the fact that dependency graphs now handle loops, we
            need a more refined way of detecting the initial nodes.
         */
        final HashSet<Node> loopyInitialNodes = new HashSet<>();
        final HashSet<Node> nonLoopyInitialNodes = new HashSet<>();

        for (Iterator<Node> iterator = this.initialNodes().iterator(); iterator.hasNext(); )
        {
            final Node currentInitialNode = iterator.next();

            if (currentInitialNode.isInLoop())
            {
                loopyInitialNodes.add(currentInitialNode);
            }
            else
            {
                if (currentInitialNode.parentNodes().isEmpty())
                {
                   nonLoopyInitialNodes.add(currentInitialNode);
                }
                else
                {
                    iterator.remove();
                }
            }
        }

        for (Node nonLoopyInitialNode : nonLoopyInitialNodes)
        {
			loopyInitialNodes.removeIf(nonLoopyInitialNode::hasSuccessor);
            this.initialNodes().removeIf(nonLoopyInitialNode::hasSuccessor);
        }

        final HashSet<Node> originalLoopyNodes = new HashSet<>(loopyInitialNodes);

        for (Node loopyInitialNode1 : originalLoopyNodes)
        {
            //Necessary check to avoid removing entirely a loop
            if (loopyInitialNodes.contains(loopyInitialNode1))
            {
                for (Iterator<Node> iterator = loopyInitialNodes.iterator(); iterator.hasNext(); )
                {
                    final Node loopyInitialNode2 = iterator.next();

                    if (!loopyInitialNode1.equals(loopyInitialNode2))
                    {
                        if (loopyInitialNode1.hasSuccessor(loopyInitialNode2))
                        {
                            iterator.remove();
                            this.initialNodes().remove(loopyInitialNode2);
                        }
                    }
                }
            }
        }
	}

	public void reduceAcyclicGraph()
	{
		if (this.hasLoops())
		{
			throw new IllegalStateException();
		}

		this.reduceAcyclicGraph(new HashMap<>());
	}

	/**
	 * This method performs a pseudo transitive reduction.
	 * More precisely, it performs a transitive reduction in the case
	 * of acyclic graph, and a pseudo-transitive reduction in the case
	 * of cyclic graph.
	 * In case of cyclic graph, this means that it may remain unnecessary
	 * edges in the graph after the reduction, i.e., edges that are not
	 * necessary to preserve the reachability of the original graph.
	 * These edges are voluntarily kept as they may represent sub-cycles,
	 * i.e., nested loops of the process.
	 * Finally, this method synchronizes the graph flows as explained in
	 * the signature of method synchronizeFlows()
	 * This methods returns the list of new ASTs corresponding to the added
	 * synchronization nodes.
	 */
	public HashSet<AbstractSyntaxTree> reduce()
	{

		if (this.hasLoops())
		{
			this.reduceCyclicGraph();
		}
		else
		{
			this.reduceAcyclicGraph(new HashMap<>());
		}



		final HashSet<AbstractSyntaxTree> newConstraints = this.synchronizeFlows();
		System.out.println("Final reduced dependency graph:\n\n" + this);
		MyOwnLogger.append("Final reduced dependency graph:\n\n" + this);

		return newConstraints;
	}

	public HashMap<Node, Node> removeCycles()
	{
		final HashSet<Dependency> brokenCycles = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.extractCycles(null, initialNode, new HashSet<>(), brokenCycles);
		}

		final HashMap<Node, Node> dummyReplacement = new HashMap<>();
		final HashMap<Node, Node> dummyReplacementReverse = new HashMap<>();
		int i = 0;

		for (Dependency dependency : brokenCycles)
		{
			int finalI = i;
			final Node dummySecondNode = dummyReplacement.computeIfAbsent(dependency.secondNode(), n -> new Node(BpmnProcessFactory.generateTask("DUMMY_" + finalI)));
			dummyReplacementReverse.putIfAbsent(dummySecondNode, dependency.secondNode());

			dependency.firstNode().addChild(dummySecondNode);
			dummySecondNode.addParent(dependency.firstNode());
			dependency.firstNode().removeChildren(dependency.secondNode());
			dependency.secondNode().removeParent(dependency.firstNode());
			i++;
		}

		final Set<Node> graphNodes = this.toSet();

		for (Node node : graphNodes)
		{
			if (dummyReplacement.containsKey(node))
			{
				final Node currentDummy = dummyReplacement.get(node);

				final HashSet<Node> parents = new HashSet<>(node.parentNodes());
				final HashSet<Node> children = new HashSet<>(node.childNodes());

				for (Node parent : parents)
				{
					parent.replaceChild(node, currentDummy);
					node.removeParent(parent);
					currentDummy.addParent(parent);
				}

				for (Node child : children)
				{
					child.replaceParent(node, currentDummy);
					node.removeChildren(child);
					currentDummy.addChild(child);
				}
			}
		}

		return dummyReplacementReverse;
	}

	public boolean hasLoops()
	{
		for (Node initialNode : this.initialNodes)
		{
			if (this.hasLoop(initialNode))
			{
				return true;
			}
		}

		return false;
	}

	public boolean hashComputed()
	{
		return !this.hash.equals("-1");
	}

	public String setHash(final String hash)
	{
		return this.hash = hash;
	}

	public String hash()
	{
		return this.hash;
	}

	public int size()
	{
		return this.toSet().size();
	}

	public String stringify(final int depth)
	{
		final StringBuilder builder = new StringBuilder();

		for (Node initialNode : this.initialNodes())
		{
			builder.append(initialNode.stringify(depth, new HashSet<>()))
					.append("\n\n");
		}

		return builder.toString();
	}

	public void addInitialNode(final Node node)
	{
		this.initialNodes.add(node);
	}

	public void addInitialNodes(final Collection<Node> node)
	{
		this.initialNodes.addAll(node);
	}

	public void removeInitialNode(final Node node)
	{
		this.initialNodes.remove(node);
	}

	public void removeInitialNodes(final Collection<Node> nodes)
	{
		this.initialNodes.removeAll(nodes);
	}

	public Node getNodeFromID(final String id)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node node = this.getNodeFromID(id, initialNode, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}

	public HashSet<Node> getNodesFromID(final Collection<String> ids)
	{
		final HashSet<Node> nodes = this.toSet();
		final HashSet<Node> nodesOfId = new HashSet<>();

		for (Node node : nodes)
		{
			if (ids.contains(node.bpmnObject().id()))
			{
				nodesOfId.add(node);
			}
		}

		return nodesOfId;
	}

	public Node getNodeFromName(final String name)
	{
		if (name == null) return null;

		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node node = this.getNodeFromName(name, initialNode, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}

	public boolean hasNode(final Node node)
	{
		return this.hasNode(node.bpmnObject().id());
	}

	public boolean hasNode(final String id)
	{
		return this.getNodeFromID(id) != null;
	}

	public boolean isEmpty()
	{
		return this.initialNodes.isEmpty();
	}

	public HashSet<Node> initialNodes()
	{
		return this.initialNodes;
	}

	public void addEndNode(final Node node)
	{
		this.endNodes.add(node);
	}

	public void addEndNodes(final Collection<Node> endNodes)
	{
		this.endNodes.addAll(endNodes);
	}

	public void removeEndNode(final Node node)
	{
		this.endNodes.remove(node);
	}

	public HashSet<Node> endNodes()
	{
		return this.endNodes;
	}

	public HashSet<Node> finalNodes()
	{
		final HashSet<Node> finalNodes = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node node : this.initialNodes)
		{
			this.getFinalNodes(node, visitedNodes, finalNodes);
		}

		return finalNodes;
	}

	public HashSet<Dependency> toDependencySet()
	{
		final HashSet<Dependency> dependencies = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.transformIntoDependencySet(initialNode, dependencies, visitedNodes);
		}

		return dependencies;
	}

	public HashMap<String, HashSet<String>> toExpandedDependencySet()
	{
		final HashMap<String, HashSet<String>> expandedDependencies = new HashMap<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.transformIntoExtendedDependencySet(initialNode, expandedDependencies, visitedNodes);
		}

		return expandedDependencies;
	}

	public HashSet<Node> toSet()
	{
		final HashSet<Node> nodes = new HashSet<>();

		for (Node initialNode : initialNodes)
		{
			this.transformIntoSet(initialNode, nodes);
		}

		return nodes;
	}

	public HashSet<String> nodesIds()
	{
		final HashSet<String> nodesIds = new HashSet<>();

		for (Node node : this.toSet())
		{
			nodesIds.add(node.bpmnObject().id());
		}

		return nodesIds;
	}

	public DependencyGraph weakCopy()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph(this.id);
		final HashSet<Node> visitedNodes = new HashSet<>();
		final HashMap<Node, Node> correspondences = new HashMap<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node newInitialNode = initialNode.weakCopy();
			dependencyGraph.addInitialNode(newInitialNode);
			this.copy(initialNode, newInitialNode, visitedNodes, correspondences, false);
		}

		for (Node endNode : this.endNodes)
		{
			dependencyGraph.addEndNode(correspondences.get(endNode));
		}

		return dependencyGraph;
	}

	public DependencyGraph deepCopy()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph(this.id);
		final HashSet<Node> visitedNodes = new HashSet<>();
		final HashMap<Node, Node> correspondences = new HashMap<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node newInitialNode = initialNode.deepCopy();
			dependencyGraph.addInitialNode(newInitialNode);
			this.copy(initialNode, newInitialNode, visitedNodes, correspondences, true);
		}

		for (Node endNode : this.endNodes)
		{
			dependencyGraph.addEndNode(correspondences.get(endNode));
		}

		return dependencyGraph;
	}

	public DependencyGraph cutAfter(final Node node)
	{
		final Node correspondingNode = this.getNodeFromID(node.bpmnObject().id());

		for (Node child : correspondingNode.childNodes())
		{
			child.removeParent(correspondingNode);
		}

		correspondingNode.removeChildren();

		return this;
	}

	public DependencyGraph cutBefore(final Node node)
	{
		final Node correspondingNode = this.getNodeFromID(node.bpmnObject().id());

		for (Node parent : correspondingNode.parentNodes())
		{
			parent.removeChildren(correspondingNode);
		}

		correspondingNode.removeParents();

		return this;
	}

	public DependencyGraph cutBetween(final Node startNode,
									  final Node endNode)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.cutBetween(startNode, endNode, initialNode, false, visitedNodes);
		}

		return this;
	}

	public void findPathsBetween(final Node currentNode,
								 final Node endNode,
								 final ArrayList<Node> currentPath,
								 final ArrayList<ArrayList<Node>> allPaths)
	{

		if (currentNode.equals(endNode))
		{
			return;
		}

		currentPath.add(currentNode);

		ArrayList<ArrayList<Node>> tempPaths = new ArrayList<>();
		tempPaths.add(currentPath);

		for (int i = 1; i < currentNode.childNodes().size(); i++)
		{
			final ArrayList<Node> tempPath = new ArrayList<>(currentPath);
			allPaths.add(tempPath);
			tempPaths.add(tempPath);
		}

		int i = 0;

		for (Node child : currentNode.childNodes())
		{
			this.findPathsBetween(child, endNode, tempPaths.get(i++), allPaths);
		}
	}

	//Private methods

	private void getFinalNodes(final Node currentNode,
							   final HashSet<Node> visitedNodes,
							   final HashSet<Node> finalNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.childNodes().isEmpty())
		{
			finalNodes.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.getFinalNodes(child, visitedNodes, finalNodes);
		}
	}

	private void reduceCyclicGraph()
	{
		System.out.println("Graph before reduction:\n\n" + this);
		MyOwnLogger.append("Graph before reduction:\n\n" + this);
		final Pair<HashMap<Node, HashSet<Node>>, HashMap<Node, Node>> cyclesConnections = this.makeGraphAcyclic();
		System.out.println("Graph before reduction without cycles:\n\n" + this);
		MyOwnLogger.append("Graph before reduction without cycles:\n\n" + this);
		this.reduceAcyclicGraph(new HashMap<>());
		System.out.println("Graph after reduction without cycles:\n\n" + this);
		MyOwnLogger.append("Graph after reduction without cycles:\n\n" + this);

		for (Node key : cyclesConnections.first().keySet())
		{
			final HashSet<Node> currentRemovedCycles = cyclesConnections.first().get(key);

			for (Node cycleNode : currentRemovedCycles)
			{
				final Node dummyReplacement = cyclesConnections.second().get(cycleNode);
				key.addChild(dummyReplacement);
				dummyReplacement.addParent(key);
			}
		}

		this.reduceAcyclicGraph(cyclesConnections.second());

		this.replugCycles(cyclesConnections.second());
		System.out.println("Graph after reduction with cycles:\n\n" + this);
		MyOwnLogger.append("Graph after reduction with cycles:\n\n" + this);
	}

	private void replugCycles(final HashMap<Node, Node> cyclesConnections)
	{
		for (Map.Entry<Node, Node> entry : cyclesConnections.entrySet())
		{
			final Node realNode = entry.getKey();
			final Node dummyNode = entry.getValue();

			final HashSet<Node> dummyNodeParents = new HashSet<>(dummyNode.parentNodes());

			for (Node dummyNodeParent : dummyNodeParents)
			{
				dummyNodeParent.replaceChild(dummyNode, realNode);
				realNode.addParent(dummyNodeParent);
				dummyNode.removeParent(dummyNodeParent);
			}
		}
	}

	private Pair<HashMap<Node, HashSet<Node>>, HashMap<Node, Node>> makeGraphAcyclic()
	{
		final HashMap<Node, Node> dummyReplacements = new HashMap<>();
		final HashMap<Node, HashSet<Node>> removedCycles = new HashMap<>();

		boolean cycleDetected = true;

		while (cycleDetected)
		{
			final HashMap<Node, Node> localConnection = new HashMap<>();
			cycleDetected = false;

			for (Node initialNode : this.initialNodes)
			{
				this.getCycle(null, initialNode, dummyReplacements, localConnection, dummyReplacements.size(), new HashSet<>());
			}

			if (!localConnection.isEmpty())
			{
				cycleDetected = true;
				final Node key = localConnection.keySet().iterator().next();
				final Node value = localConnection.get(key);
				final HashSet<Node> currentRemovedCycles = removedCycles.computeIfAbsent(key, h -> new HashSet<>());
				currentRemovedCycles.add(value);
				key.removeChildren(value);
				value.removeParent(key);
			}
		}

		return new Pair<>(removedCycles, dummyReplacements);
	}

	private void getCycle(final Node previousNode,
						  final Node currentNode,
						  final HashMap<Node, Node> dummyReplacements,
						  final HashMap<Node, Node> removedCycles,
						  final int identifier,
						  final HashSet<Node> visitedNodes)
	{
		if (!removedCycles.isEmpty())
		{
			//We already found a cycle in this iteration => STOP
			return;
		}

		if (visitedNodes.contains(currentNode))
		{
			//Cycle detected
			dummyReplacements.computeIfAbsent(currentNode, n -> new Node(BpmnProcessFactory.generateTask("LOOPY_DUMMY_" + identifier)));
			removedCycles.put(previousNode, currentNode);
			return;
		}

		visitedNodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.getCycle(currentNode, child, dummyReplacements, removedCycles, identifier, new HashSet<>(visitedNodes));
		}
	}

	private void reduceAcyclicGraph(final HashMap<Node,Node> dummyNodesCorrespondence)
	{
		final HashMap<Node, HashSet<Node>> toRemove = new HashMap<>();

		for (Node initialNode : this.initialNodes)
		{
			this.computeUndesiredAcyclicLinks(initialNode, toRemove, dummyNodesCorrespondence);
		}

		System.out.println("Found the following undesired links:");
		MyOwnLogger.append("Found the following undesired links:");

		for (Node key : toRemove.keySet())
		{
			MyOwnLogger.append("- Node " + key.bpmnObject().id() + " is non-necessarily connected to [");
			System.out.print("- Node " + key.bpmnObject().id() + " is non-necessarily connected to [");

			for (Node value : toRemove.get(key))
			{
				MyOwnLogger.appendNextTo(value.bpmnObject().id() + ", ");
				System.out.print(value.bpmnObject().id() + ", ");
				key.removeChildren(value);
				value.removeParent(key);
			}

			MyOwnLogger.appendNextTo("]");
			System.out.println("]");
		}
	}

	private void computeUndesiredAcyclicLinks(final Node currentNode,
											  final HashMap<Node, HashSet<Node>> toRemove,
											  final HashMap<Node, Node> dummyNodesCorrespondence)
	{
		for (Node child1 : currentNode.childNodes())
		{
			for (Node child2 : currentNode.childNodes())
			{
				if (!child1.equals(child2))
				{
					final HashSet<Node> nodesToRemove = toRemove.computeIfAbsent(currentNode, h -> new HashSet<>());

					if (child1.hasSuccessor(child2))
					{
						nodesToRemove.add(child2);
					}
					else
					{
						final Node dummyCorrespondence = dummyNodesCorrespondence.get(child2);

						if (dummyCorrespondence != null
							&& child1.hasSuccessor(dummyCorrespondence))
						{
							nodesToRemove.add(dummyCorrespondence);
						}
					}
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.computeUndesiredAcyclicLinks(child, toRemove, dummyNodesCorrespondence);
		}
	}

	private void extractCycles(final Node predecessor,
							   final Node currentNode,
							   final HashSet<Node> visitedNodes,
							   final HashSet<Dependency> brokenCycles)
	{
		if (visitedNodes.contains(currentNode))
		{
			//We found a loop
			if (predecessor == null) throw new IllegalStateException();

			//Break it
			final Dependency dependency = new Dependency(predecessor, currentNode);
			brokenCycles.add(dependency);
			System.out.println("Broken cycle: " + dependency.toString());
			//predecessor.removeChildren(currentNode);
			//currentNode.removeParent(predecessor);
			return;
		}

		visitedNodes.add(currentNode);

		final ArrayList<HashSet<Node>> nextVisitedNodes = new ArrayList<>();
		nextVisitedNodes.add(visitedNodes);

		for (int i = 1; i < currentNode.childNodes().size(); i++)
		{
			nextVisitedNodes.add(new HashSet<>(visitedNodes));
		}

		int i = 0;

		for (Node child : currentNode.childNodes())
		{
			this.extractCycles(currentNode, child, nextVisitedNodes.get(i++), brokenCycles);
		}
	}

	private boolean hasLoop(final Node currentNode)
	{
		if (currentNode.isAncestorOf(currentNode))
		{
			return true;
		}

		for (final Node child : currentNode.childNodes())
		{
			if (this.hasLoop(child))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * This method is used to synchronize the flows in the case where several
	 * nodes have the exact same children, and these children have the exact same parents.
	 * For instance, the graph
	 *
	 * /---\		 /---\
	 * | A |  ---->  | B |
	 * \---/  \	 /	 \---/
	 *		   /\
	 * /---\ /	 \	 /---\
	 * | C |  ---->  | D |
	 * \---/		 \---/
	 *
	 * will be synchronized, in the sense that after the execution of this method,
	 * the outgoing flows of A and C will now lead to a new node called DUMMY_SYNC_1,
	 * and the incoming flows of B and D will start from DUMMY_SYNC_1.
	 * This mainly eases the transformation of dependency graph to AST.
	 */
	public HashSet<AbstractSyntaxTree> synchronizeFlows()
	{
		final Pair<HashSet<Node>, HashSet<Node>> toSynchronize = new Pair<>(new HashSet<>(), new HashSet<>());
		final HashSet<AbstractSyntaxTree> newConstraints = new HashSet<>();
		int index = 0;

		do
		{
			toSynchronize.first().clear();
			toSynchronize.second().clear();
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node initialNode : this.initialNodes)
			{
				this.synchronizeFlowsRec(initialNode, toSynchronize, visitedNodes);
			}

			final Node dummySyncNode = new Node(BpmnProcessFactory.generateTask(DUMMY_STATIC_NODE_NAME + index++));

			for (Node parent : toSynchronize.first())
			{
				for (Node child : toSynchronize.second())
				{
					final AbstractSyntaxTree newConstraint1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newSequence());
					final AbstractSyntaxTree newConstraint2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newSequence());
					final AbstractSyntaxNode leftNode = AbstractSyntaxNodeFactory.newTask(parent.bpmnObject().name());
					final AbstractSyntaxNode intermediateNode = AbstractSyntaxNodeFactory.newTask(dummySyncNode.bpmnObject().name());
					final AbstractSyntaxNode rightNode = AbstractSyntaxNodeFactory.newTask(child.bpmnObject().name());
					newConstraint1.root().addSuccessor(leftNode);
					newConstraint1.root().addSuccessor(intermediateNode);
					newConstraint2.root().addSuccessor(intermediateNode);
					newConstraint2.root().addSuccessor(rightNode);

					newConstraints.add(newConstraint1);
					newConstraints.add(newConstraint2);
					System.out.println("New constraint:\n\n" + newConstraint1);

					parent.replaceChild(child, dummySyncNode);
					child.replaceParent(parent, dummySyncNode);
					dummySyncNode.addChild(child);
					dummySyncNode.addParent(parent);
				}
			}
		}
		while (!toSynchronize.first().isEmpty());

		return newConstraints;
	}

	public HashSet<Node> synchronizeFlowsV2()
	{
		final Pair<HashSet<Node>, HashSet<Node>> toSynchronize = new Pair<>(new HashSet<>(), new HashSet<>());
		final HashSet<Node> synchroNodes = new HashSet<>();
		int index = 0;

		do
		{
			toSynchronize.first().clear();
			toSynchronize.second().clear();
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node initialNode : this.initialNodes)
			{
				this.synchronizeFlowsRec(initialNode, toSynchronize, visitedNodes);
			}

			final Node dummySyncNode = new Node(BpmnProcessFactory.generateTask(DUMMY_STATIC_NODE_NAME + index++));
			synchroNodes.add(dummySyncNode);

			for (Node parent : toSynchronize.first())
			{
				for (Node child : toSynchronize.second())
				{
					final AbstractSyntaxTree newConstraint1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newSequence());
					final AbstractSyntaxTree newConstraint2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newSequence());
					final AbstractSyntaxNode leftNode = AbstractSyntaxNodeFactory.newTask(parent.bpmnObject().name());
					final AbstractSyntaxNode intermediateNode = AbstractSyntaxNodeFactory.newTask(dummySyncNode.bpmnObject().name());
					final AbstractSyntaxNode rightNode = AbstractSyntaxNodeFactory.newTask(child.bpmnObject().name());
					newConstraint1.root().addSuccessor(leftNode);
					newConstraint1.root().addSuccessor(intermediateNode);
					newConstraint2.root().addSuccessor(intermediateNode);
					newConstraint2.root().addSuccessor(rightNode);

					parent.replaceChild(child, dummySyncNode);
					child.replaceParent(parent, dummySyncNode);
					dummySyncNode.addChild(child);
					dummySyncNode.addParent(parent);
				}
			}
		}
		while (!toSynchronize.first().isEmpty());

		return synchroNodes;
	}

	private void synchronizeFlowsRec(final Node currentNode,
									 final Pair<HashSet<Node>, HashSet<Node>> toSynchronize,
									 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		final HashSet<HashSet<Node>> childrenWithSameParents = new HashSet<>();

		for (Node child : currentNode.childNodes())
		{
			if (childrenWithSameParents.isEmpty())
			{
				final HashSet<Node> firstHashSet = new HashSet<>();
				firstHashSet.add(child);
				childrenWithSameParents.add(firstHashSet);
			}
			else
			{
				boolean added = false;

				for (HashSet<Node> currentSet : childrenWithSameParents)
				{
					final Node firstNode = currentSet.iterator().next();

					if (firstNode.parentNodes().equals(child.parentNodes()))
					{
						currentSet.add(child);
						added = true;
						break;
					}
				}

				if (!added)
				{
					final HashSet<Node> newHashSet = new HashSet<>();
					newHashSet.add(child);
					childrenWithSameParents.add(newHashSet);
				}
			}
		}

		System.out.println("Children with same parents: " + childrenWithSameParents);

		Set<Node> maxHashSet = new HashSet<>();

		for (HashSet<Node> currentChildrenWithSameParents : childrenWithSameParents)
		{
			if (currentChildrenWithSameParents.size() > 1)
			{
				final Node currentChild = currentChildrenWithSameParents.iterator().next();

				if (currentChild.parentNodes().size() > 1)
				{
					if (currentChild.parentNodes().size() > maxHashSet.size())
					{
						maxHashSet = currentChild.parentNodes();
					}
				}
			}
		}

		if (!maxHashSet.isEmpty())
		{
			//We found a perfect synchronizable set
			final HashSet<Node> childToSynchronize = new HashSet<>(maxHashSet.iterator().next().childNodes());

			for (Node parent : maxHashSet)
			{
				final Collection<Node> nextChildToSynchronize = Utils.getIntersectionOf(childToSynchronize, parent.childNodes());
				childToSynchronize.clear();
				childToSynchronize.addAll(nextChildToSynchronize);
			}

			if (maxHashSet.size() > 1
				&& childToSynchronize.size() > 1)
			{
				toSynchronize.first().addAll(maxHashSet);
				toSynchronize.second().addAll(childToSynchronize);
			}
		}
		else
		{
			final HashSet<HashSet<Node>> childrenWithSimilarParents = new HashSet<>();

			for (Node child : currentNode.childNodes())
			{
				if (childrenWithSimilarParents.isEmpty())
				{
					final HashSet<Node> firstHashSet = new HashSet<>();
					firstHashSet.add(child);
					childrenWithSimilarParents.add(firstHashSet);
				}
				else
				{
					boolean added = false;

					for (HashSet<Node> currentSet : childrenWithSimilarParents)
					{
						final Node firstNode = currentSet.iterator().next();

						if (firstNode.parentNodes().containsAll(child.parentNodes())
							|| child.parentNodes().containsAll(firstNode.parentNodes()))
						{
							currentSet.add(child);
							added = true;
							break;
						}
					}

					if (!added)
					{
						final HashSet<Node> newHashSet = new HashSet<>();
						newHashSet.add(child);
						childrenWithSimilarParents.add(newHashSet);
					}
				}
			}

			Set<Node> maxHashSet2 = new HashSet<>();

			for (HashSet<Node> currentChildrenWithSimilarParents : childrenWithSimilarParents)
			{
				if (currentChildrenWithSimilarParents.size() > 1)
				{
					final HashSet<Node> currentSameParents = new HashSet<>(currentChildrenWithSimilarParents.iterator().next().parentNodes());

					for (Node currentChild : currentChildrenWithSimilarParents)
					{
						final Collection<Node> newSameParents = Utils.getIntersectionOf(currentSameParents, currentChild.parentNodes());
						currentSameParents.clear();
						currentSameParents.addAll(newSameParents);
					}

					if (currentSameParents.size() > maxHashSet2.size())
					{
						maxHashSet2 = currentSameParents;
					}
				}
			}

			if (!maxHashSet2.isEmpty())
			{
				//We found a partially synchronizable set
				final HashSet<Node> childToSynchronize = new HashSet<>(maxHashSet2.iterator().next().childNodes());

				for (Node parent : maxHashSet2)
				{
					final Collection<Node> nextChildToSynchronize = Utils.getIntersectionOf(childToSynchronize, parent.childNodes());
					childToSynchronize.clear();
					childToSynchronize.addAll(nextChildToSynchronize);
				}

				if (maxHashSet2.size() > 1
					&& childToSynchronize.size() > 1)
				{
					toSynchronize.first().addAll(maxHashSet2);
					toSynchronize.second().addAll(childToSynchronize);
				}
			}
		}

		if (toSynchronize.first().isEmpty())
		{
			for (Node child : currentNode.childNodes())
			{
				this.synchronizeFlowsRec(child, toSynchronize, visitedNodes);
			}
		}
	}

	private void computeHash(final ArrayList<StringBuilder> builders,
							 final StringBuilder currentBuilder,
							 final ArrayList<Node> orderedNodes)
	{
		for (int i = 0; i < orderedNodes.size(); i++)
		{
			final Node currentNode = orderedNodes.get(0);
			final StringBuilder nextBuilder;

			if (i == 0)
			{
				nextBuilder = currentBuilder;
			}
			else
			{
				nextBuilder = new StringBuilder(currentBuilder);
				builders.add(nextBuilder);
			}

			nextBuilder.append(currentNode.bpmnObject().name());

			if (currentNode.hasChildren())
			{
				final ArrayList<Node> orderedChild = new ArrayList<>(currentNode.childNodes());

				if (orderedChild.size() > 1)
				{
					orderedChild.sort(Comparator.comparing(o -> o.bpmnObject().name()));
				}

				this.computeHash(builders, currentBuilder, orderedChild);
			}
		}
	}

	private void cutBetween(final Node startNode,
							final Node endNode,
							final Node currentNode,
							final boolean nodeCut,
							final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);
		boolean newNodeCut = false;

		if (startNode.equals(currentNode))
		{
			for (Node parent : currentNode.parentNodes())
			{
				parent.removeChildren(currentNode);
			}

			currentNode.removeParents();
			newNodeCut = true;

			if (nodeCut)
			{
				return;
			}
		}
		if (endNode.equals(currentNode))
		{
			for (Node child : currentNode.childNodes())
			{
				child.removeParent(endNode);
			}

			currentNode.removeChildren();

			if (nodeCut
					|| newNodeCut)
			{
				return;
			}

			newNodeCut = true;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutBetween(startNode, endNode, child, newNodeCut, visitedNodes);
		}
	}

	private void cutBefore(final Node nodeToReach,
						   final Node currentNode,
						   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(nodeToReach))
		{
			for (Node parent : currentNode.parentNodes())
			{
				parent.removeChildren(currentNode);
			}

			currentNode.removeParents();
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutBefore(nodeToReach, child, visitedNodes);
		}
	}

	private void cutAfter(final Node nodeToReach,
						  final Node currentNode,
						  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(nodeToReach))
		{
			for (Node child : currentNode.childNodes())
			{
				child.removeParent(currentNode);
			}

			currentNode.removeChildren();
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutAfter(nodeToReach, child, visitedNodes);
		}
	}

	private void copy(final Node oldNode,
					  final Node newNode,
					  final HashSet<Node> visitedNodes,
					  final HashMap<Node, Node> correspondences,
					  final boolean deepCopy)
	{
		if (visitedNodes.contains(oldNode))
		{
			return;
		}

		visitedNodes.add(oldNode);

		for (Node oldChild : oldNode.childNodes())
		{
			final Node newChild = correspondences.computeIfAbsent(oldChild, o -> deepCopy ? oldChild.deepCopy() : oldChild.weakCopy());
			newNode.addChild(newChild);
			newChild.addParent(newNode);

			this.copy(oldChild, newChild, visitedNodes, correspondences, deepCopy);
		}
	}

	private void transformIntoSet(final Node currentNode,
								  final HashSet<Node> nodes)
	{
		if (nodes.contains(currentNode))
		{
			return;
		}

		nodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.transformIntoSet(child, nodes);
		}
	}

	private void transformIntoDependencySet(final Node currentNode,
											final HashSet<Dependency> dependencies,
											final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			final Dependency dependency = new Dependency(currentNode, child);
			dependencies.add(dependency);

			this.transformIntoDependencySet(child, dependencies, visitedNodes);
		}
	}

	private void transformIntoExtendedDependencySet(final Node currentNode,
													final HashMap<String, HashSet<String>> dependencies,
													final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (dependencies.containsKey(currentNode.bpmnObject().name())) return;

		final HashSet<String> currentHashset = dependencies.computeIfAbsent(currentNode.bpmnObject().name(), h -> new HashSet<>());

		for (Node child : currentNode.childNodes())
		{
			currentHashset.add(child.bpmnObject().name());
			this.transformIntoExtendedDependencySet(child, dependencies, visitedNodes);
		}

		for (Node child : currentNode.childNodes())
		{
			currentHashset.addAll(dependencies.get(child.bpmnObject().name()));
		}
	}

	private Node getNodeFromID(final String id,
							   final Node currentNode,
							   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().id().equals(id))
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node node = this.getNodeFromID(id, child, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
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

		if (name.equals(currentNode.bpmnObject().name()))
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node node = this.getNodeFromName(name, child, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}
}
