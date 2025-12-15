package chat_gpt.no_ast;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import chat_gpt.ast_management.AbstractSyntaxNode;
import chat_gpt.ast_management.AbstractSyntaxTree;
import chat_gpt.ast_management.constants.AbstractType;
import other.MyOwnLogger;
import other.Pair;
import other.Utils;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.*;

public class Ast2StructuredInformation
{
	private final ArrayList<AbstractSyntaxTree> abstractSyntaxTrees;
	private StructuredInformation structuredInformation;

	public Ast2StructuredInformation(final ArrayList<AbstractSyntaxTree> abstractSyntaxTrees)
	{
		this.abstractSyntaxTrees = abstractSyntaxTrees;
	}

	public StructuredInformation structureInformation()
	{
		final Pair<DependencyGraph, HashMap<String, Node>> dependencyGraphAndNodes = this.buildDependencyGraph();
		System.out.println("Dependency graph built:\n\n" + dependencyGraphAndNodes.first().toString());

		final ArrayList<HashSet<Node>> explicitLoops = this.getExplicitLoops(dependencyGraphAndNodes.second());
		System.out.println(explicitLoops.size() + " loops extracted:");

		for (HashSet<Node> loop : explicitLoops)
		{
			System.out.println(" 	- " + loop);
		}

		System.out.println();

		final Pair<HashSet<AbstractSyntaxTree>, HashSet<AbstractSyntaxTree>> explicitChoicesAndParallels = this.getExplicitChoicesAndParallels();
		System.out.println("Choices extracted:\n");

		for (AbstractSyntaxTree choice : explicitChoicesAndParallels.first())
		{
			System.out.println(choice);
		}

		System.out.println("Parallels extracted:\n");

		for (AbstractSyntaxTree parallel : explicitChoicesAndParallels.second())
		{
			System.out.println(parallel);
		}

		this.structuredInformation = new StructuredInformation(dependencyGraphAndNodes.first());
		this.structuredInformation.addExplicitLoops(explicitLoops);
		this.structuredInformation.addExplicitChoices(explicitChoicesAndParallels.first());
		this.structuredInformation.addExplicitParallels(explicitChoicesAndParallels.second());

		MyOwnLogger.append("Original dependency graph:\n\n" + dependencyGraphAndNodes.first().toString());

		return this.structuredInformation;
	}

	public StructuredInformation getStructuredInformation()
	{
		if (this.structuredInformation == null)
		{
			throw new RuntimeException("No structured information computed yet!");
		}

		return this.structuredInformation;
	}

	//Private methods

	private Pair<HashSet<AbstractSyntaxTree>, HashSet<AbstractSyntaxTree>> getExplicitChoicesAndParallels()
	{
		final HashSet<AbstractSyntaxTree> explicitChoices = new HashSet<>();
		final HashSet<AbstractSyntaxTree> explicitParallels = new HashSet<>();

		for (AbstractSyntaxTree abstractSyntaxTree : this.abstractSyntaxTrees)
		{
			this.getExplicitChoicesAndParallels(abstractSyntaxTree.root(), explicitChoices, explicitParallels);
		}

		return new Pair<>(explicitChoices, explicitParallels);
	}

	private void getExplicitChoicesAndParallels(final AbstractSyntaxNode currentNode,
												final HashSet<AbstractSyntaxTree> explicitChoices,
												final HashSet<AbstractSyntaxTree> explicitParallels)
	{
		if (currentNode.type() == AbstractType.PAR
			|| currentNode.type() == AbstractType.XOR)
		{
			final ArrayList<HashSet<AbstractSyntaxNode>> orderedBelowTasks = currentNode.getOrderedBelowTasks();

			for (int i = 0; i < orderedBelowTasks.size(); i++)
			{
				final HashSet<AbstractSyntaxNode> pivotOrderedTasks = orderedBelowTasks.get(i);

				for (int j = i + 1; j < orderedBelowTasks.size(); j++)
				{
					final HashSet<AbstractSyntaxNode> otherOrderedTasks = orderedBelowTasks.get(j);

					for (AbstractSyntaxNode pivotTask : pivotOrderedTasks)
					{
						for (AbstractSyntaxNode otherTask : otherOrderedTasks)
						{
							final AbstractSyntaxTree tree = new AbstractSyntaxTree();
							final AbstractSyntaxNode root = new AbstractSyntaxNode(currentNode.type());
							root.addSuccessorAndForcePredecessor(pivotTask.copy());
							root.addSuccessorAndForcePredecessor(otherTask.copy());
							tree.setRoot(root);

							if (currentNode.type() == AbstractType.PAR)
							{
								explicitParallels.add(tree);
							}
							else
							{
								explicitChoices.add(tree);
							}
						}
					}
				}
			}
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			this.getExplicitChoicesAndParallels(successor, explicitChoices, explicitParallels);
		}
	}

	private ArrayList<HashSet<Node>> getExplicitLoops(final HashMap<String, Node> correspondences)
	{
		final ArrayList<HashSet<Node>> explicitLoops = new ArrayList<>();

		for (AbstractSyntaxTree abstractSyntaxTree : this.abstractSyntaxTrees)
		{
			this.getExplicitLoops(abstractSyntaxTree.root(), explicitLoops, correspondences);
		}

		return explicitLoops;
	}

	private void getExplicitLoops(final AbstractSyntaxNode currentNode,
								  final ArrayList<HashSet<Node>> explicitLoops,
								  final HashMap<String, Node> correspondences)
	{
		if (currentNode.type() == AbstractType.LOOP)
		{
			final HashSet<Node> explicitLoop = new HashSet<>();
			explicitLoops.add(explicitLoop);

			for (AbstractSyntaxNode abstractSyntaxNode : currentNode.getAllTasksBelow())
			{
				final Node node = correspondences.computeIfAbsent(abstractSyntaxNode.id(), n -> new Node(BpmnProcessFactory.generateTask(abstractSyntaxNode.label())));
				explicitLoop.add(node);
			}
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			this.getExplicitLoops(successor, explicitLoops, correspondences);
		}
	}

	private Pair<DependencyGraph, HashMap<String, Node>> buildDependencyGraph()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final HashMap<String, Node> abstractSyntaxNode2Node = new HashMap<>();

		for (AbstractSyntaxTree abstractSyntaxTree : this.abstractSyntaxTrees)
		{
			this.connectNodesSequentially(abstractSyntaxTree.root(), abstractSyntaxNode2Node);
		}

		System.out.println("Nodes connected");

		this.computeStartAndEndNodes(dependencyGraph, abstractSyntaxNode2Node);
		System.out.println("Start and end nodes computed");
		dependencyGraph.reduce();
		System.out.println("Graph reduced");

		return new Pair<>(dependencyGraph, abstractSyntaxNode2Node);
	}

	private void connectNodesSequentially(final AbstractSyntaxNode currentAbstractNode,
										  final HashMap<String, Node> abstractSyntaxNode2Node)
	{
		if (currentAbstractNode.type() == AbstractType.SEQ)
		{
			final ArrayList<HashSet<AbstractSyntaxNode>> orderedTasks = currentAbstractNode.getOrderedBelowTasks();
			MyOwnLogger.append("Found " + orderedTasks.size() + " sets of ordered tasks.");

			for (int i = 0; i < orderedTasks.size() - 1; i++)
			{
				final HashSet<AbstractSyntaxNode> currentSet = orderedTasks.get(i);
				final HashSet<AbstractSyntaxNode> nextSet = orderedTasks.get(i + 1);

				for (AbstractSyntaxNode currentSetAbstractNode : currentSet)
				{
					final Node currentSetNode = abstractSyntaxNode2Node.computeIfAbsent(currentSetAbstractNode.id(), n -> new Node(BpmnProcessFactory.generateTask(currentSetAbstractNode.label())));

					for (AbstractSyntaxNode nextSetAbstractNode : nextSet)
					{
						final Node nextSetNode = abstractSyntaxNode2Node.computeIfAbsent(nextSetAbstractNode.id(), n -> new Node(BpmnProcessFactory.generateTask(nextSetAbstractNode.label())));
						currentSetNode.addChildAndForceParent(nextSetNode);
						MyOwnLogger.append("Node \"" + currentSetNode.bpmnObject().name() + "\" was connected to node \"" + nextSetNode.bpmnObject().name() + "\".");
					}
				}
			}
		}

		for (AbstractSyntaxNode successor : currentAbstractNode.successors())
		{
			this.connectNodesSequentially(successor, abstractSyntaxNode2Node);
		}
	}

	/**
	 * This method is used to compute the start/end nodes of the dependency graph just built.
	 * This is performed in the following way:
	 * 		1) Check whether some nodes have no parents
	 * 			--> Yes: these nodes are initial nodes
	 * 			--> No: the initial nodes have to be computed
	 * 	    2) Check whether some nodes have no children
	 * 	    	--> Yes: these nodes are end nodes
	 * 	    	--> No: the end nodes have to be computed
	 * 	    3) If there are no initial nodes nor end nodes:
	 * 	        => the initial node is the first node of the first expression (TODO: completely arbitrary)
	 * 	        => the end nodes are the furthest nodes reachable from the initial node
	 * 	    4) Else if there are no initial nodes:
	 * 	    	=> the initial nodes are the furthest nodes reachable from the end nodes
	 * 	    5) Else if there are no end nodes:
	 * 	    	=> the end nodes are the furthest nodes reachable from the initial nodes
	 * 	    6) Check whether each node can eventually reach a end node
	 * 	    	--> Yes: do nothing
	 * 	    	--> No: mark as end nodes the furthest nodes in the branches not able to reach end nodes
	 *
	 * @param dependencyGraph the dependency graph to which start/end nodes will be added
	 * @param abstractSyntaxNode2Node the correspondences between abstract syntax nodes and nodes
	 */
	private void computeStartAndEndNodes(final DependencyGraph dependencyGraph,
										 final HashMap<String, Node> abstractSyntaxNode2Node)
	{
		if (abstractSyntaxNode2Node.isEmpty()) return;

		final Collection<Node> nodes = abstractSyntaxNode2Node.values();
		final HashSet<Node> initialNodes = new HashSet<>();
		final HashSet<Node> endNodes = new HashSet<>();

		//Try to find initial/end nodes
		for (Node node : nodes)
		{
			if (node.parentNodes().isEmpty())
			{
				initialNodes.add(node);
				dependencyGraph.addInitialNode(node);
			}
			else if (node.childNodes().isEmpty())
			{
				endNodes.add(node);
				dependencyGraph.addEndNode(node);
			}
		}

		//Fill the empty sets if needed
		if (initialNodes.isEmpty()
			&& endNodes.isEmpty())
		{
			//Initial node is the first node found in the first AST (TODO arbitrary)
			final AbstractSyntaxTree firstTree = this.abstractSyntaxTrees.get(0);
			final AbstractSyntaxNode firstTask = firstTree.findFirstTask();
			final Node initialNode = abstractSyntaxNode2Node.get(firstTask.id());
			initialNodes.add(initialNode);
			dependencyGraph.addInitialNode(initialNode);

			//Compute end nodes as furthest nodes from the initial node
			final HashSet<Node> furthestNodes = this.computeFurthestNodesFrom(initialNodes, false, true);
			endNodes.addAll(furthestNodes);
			dependencyGraph.addEndNodes(furthestNodes);
		}
		else if (initialNodes.isEmpty())
		{
			//Compute initial node as furthest node from the end nodes
			final HashSet<Node> furthestNodes = this.computeFurthestNodesFrom(endNodes, true, false);
			initialNodes.addAll(furthestNodes);
			dependencyGraph.addInitialNodes(furthestNodes);
		}
		else if (endNodes.isEmpty())
		{
			//Compute end nodes as furthest nodes from the initial nodes
			final HashSet<Node> furthestNodes = this.computeFurthestNodesFrom(initialNodes, false, true);
			endNodes.addAll(furthestNodes);
			dependencyGraph.addEndNodes(furthestNodes);
		}

		//Add eventual missing end nodes
		final HashSet<Node> nodesThatCannotReachAnEndNode = this.computeNodesThatCannotReachAnEndNode(dependencyGraph);
		System.out.println("Found " + nodesThatCannotReachAnEndNode.size() + " nodes that cannot reach an end node.");

		while (!nodesThatCannotReachAnEndNode.isEmpty())
		{
			for (Node nodeThatCannotReachAnEndNode : nodesThatCannotReachAnEndNode)
			{
				final HashSet<Node> furthestNodes = this.computeFurthestNodesFrom(nodeThatCannotReachAnEndNode.childNodes(), false, true);
				dependencyGraph.addEndNodes(furthestNodes);
			}

			nodesThatCannotReachAnEndNode.clear();
			nodesThatCannotReachAnEndNode.addAll(this.computeNodesThatCannotReachAnEndNode(dependencyGraph));
		}
	}

	private HashSet<Node> computeNodesThatCannotReachAnEndNode(final DependencyGraph dependencyGraph)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();
		final HashSet<Node> nodesThatCannotReachAnEndNode = new HashSet<>();

		for (Node initialNode : dependencyGraph.initialNodes())
		{
			this.computeNodesThatCannotReachAnEndNode(initialNode, nodesThatCannotReachAnEndNode, dependencyGraph.endNodes(), visitedNodes);
		}

		return nodesThatCannotReachAnEndNode;
	}

	private void computeNodesThatCannotReachAnEndNode(final Node currentNode,
													  final HashSet<Node> nodesThatCannotReachAnEndNode,
													  final HashSet<Node> endNodes,
													  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)
			|| endNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		boolean currentNodeCanReachEndNode = false;

		for (Node endNode : endNodes)
		{
			if (currentNode.hasSuccessor(endNode))
			{
				currentNodeCanReachEndNode = true;
				break;
			}
		}

		if (!currentNodeCanReachEndNode)
		{
			nodesThatCannotReachAnEndNode.add(currentNode);
		}
		else
		{
			for (Node child : currentNode.childNodes())
			{
				this.computeNodesThatCannotReachAnEndNode(child, nodesThatCannotReachAnEndNode, endNodes, visitedNodes);
			}
		}
	}

	private HashSet<Node> computeFurthestNodesFrom(final Set<Node> nodes,
												   final boolean forceAppearance,
												   final boolean nextNodesAreChildren)
	{
		final HashMap<Node, HashMap<Node, Integer>> distancesPerInitialNode = new HashMap<>();

		for (Node node : nodes)
		{
			final HashMap<Node, Integer> distances = new HashMap<>();
			this.getNodesDistanceFromNode(node, 0, nextNodesAreChildren, distances, new HashSet<>());
			distancesPerInitialNode.put(node, distances);
		}

		final HashSet<Node> eligibleNodes = new HashSet<>();
		boolean firstIter = true;

		for (HashMap<Node, Integer> distances : distancesPerInitialNode.values())
		{
			if (forceAppearance)
			{
				if (firstIter)
				{
					eligibleNodes.addAll(distances.keySet());
					firstIter = false;
				}
				else
				{
					final Collection<Node> intersection = Utils.getIntersectionOf(eligibleNodes, distances.keySet());
					eligibleNodes.clear();
					eligibleNodes.addAll(intersection);
				}
			}
			else
			{
				eligibleNodes.addAll(distances.keySet());
			}
		}

		final HashSet<Node> furthestNodes = new HashSet<>();
		int maxDistance = -1;

		for (Node eligibleNode : eligibleNodes)
		{
			int nodeDistance = 0;

			for (HashMap<Node, Integer> distances : distancesPerInitialNode.values())
			{
				nodeDistance += distances.getOrDefault(eligibleNode, 0);
			}

			if (nodeDistance > maxDistance)
			{
				furthestNodes.clear();
				furthestNodes.add(eligibleNode);
				maxDistance = nodeDistance;
			}
			else if (nodeDistance == maxDistance)
			{
				furthestNodes.add(eligibleNode);
			}
		}

		return furthestNodes;
	}

	private void getNodesDistanceFromNode(final Node currentNode,
										  final int currentDistance,
										  final boolean nextNodesAreChildren,
										  final HashMap<Node, Integer> distances,
										  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		distances.put(currentNode, currentDistance);

		for (Node nextNode : (nextNodesAreChildren ? currentNode.childNodes() : currentNode.parentNodes()))
		{
			this.getNodesDistanceFromNode(nextNode, currentDistance + 1, nextNodesAreChildren, distances, visitedNodes);
		}
	}
}
