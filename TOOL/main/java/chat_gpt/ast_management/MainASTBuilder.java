package chat_gpt.ast_management;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.ease.Path;
import other.Pair;
import other.Triple;
import other.Utils;
import refactoring.legacy.dependencies.DependencyGraph;
import refactoring.legacy.exceptions.BadDependencyException;

import java.util.*;

public class MainASTBuilder
{
	private static final boolean DISABLE_NESTED_LOOPS = false;
	private static final String LOOPY_DUMMY_ENTRY = "LOOPY_DUMMY_ENTRY";
	private static final String LOOPY_DUMMY_EXIT = "LOOPY_DUMMY_EXIT";
	private final HashMap<Node, Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>>> loopInformation;
	private final HashMap<AbstractSyntaxNode, Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>>> buildingLoopInformation;
	private final HashMap<Node, AbstractSyntaxNode> upperBounds;
	private final HashMap<AbstractSyntaxNode, AbstractSyntaxNode> nestedLoops;
	private final DependencyGraph dependencyGraph;
	private final HashSet<Pair<Node, Node>> brokenConnections;
	private final ArrayList<Path<Node>> allPaths;
	private final HashSet<Node> loopEntryPoints;
	private final ArrayList<Pair<HashSet<String>, HashSet<String>>> classicalLoops;
	private final ArrayList<AbstractSyntaxTree> allConstraints;
	private AbstractSyntaxTree mainAST;
	private DependencyGraph noLoopGraph;

	public MainASTBuilder(final DependencyGraph dependencyGraph,
						  final ArrayList<Pair<HashSet<String>, HashSet<String>>> classicalLoops,
						  final ArrayList<AbstractSyntaxTree> allConstraints)
	{
		//System.out.println("Original depedency graph:\n\n" + dependencyGraph);
		this.dependencyGraph = dependencyGraph;
		this.loopInformation = new HashMap<>();
		this.buildingLoopInformation = new HashMap<>();
		this.upperBounds = new HashMap<>();
		this.brokenConnections = new HashSet<>();
		this.allPaths = new ArrayList<>();
		this.nestedLoops = new HashMap<>();
		this.loopEntryPoints = new HashSet<>();
		this.classicalLoops = classicalLoops;
		this.allConstraints = allConstraints;
	}

	public AbstractSyntaxTree buildMainAST() throws BadDependencyException
	{
		if ((this.dependencyGraph == null
			|| this.dependencyGraph.isEmpty())
			&& this.classicalLoops.isEmpty())
		{
			return null;
		}

		if (this.dependencyGraph == null
			|| this.dependencyGraph.isEmpty())
		{
			if (this.classicalLoops.size() > 1)
			{
				final AbstractSyntaxNode root = new AbstractSyntaxNode(AbstractType.PAR);
				this.mainAST = new AbstractSyntaxTree(root);

				//Manage classical loops
				for (Pair<HashSet<String>, HashSet<String>> classicalLoop : this.classicalLoops)
				{
					final HashSet<String> currentMandatoryNodes = classicalLoop.first();
					final HashSet<String> currentOptionalNodes = classicalLoop.second();
					currentOptionalNodes.removeAll(currentMandatoryNodes);

					final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
					final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
					final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);

					loopNode.addSuccessor(loopMandatoryNode);
					loopMandatoryNode.setPredecessor(loopNode);
					loopNode.addSuccessor(loopOptionalNode);
					loopOptionalNode.setPredecessor(loopNode);
					root.addSuccessor(loopNode);
					loopNode.setPredecessor(root);

					if (currentMandatoryNodes.size() > 1)
					{
						final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
						loopMandatoryNode.addSuccessor(parallelNode);
						parallelNode.setPredecessor(loopMandatoryNode);

						for (String mandatoryNodeName : currentMandatoryNodes)
						{
							final AbstractSyntaxNode mandatoryNode = new AbstractSyntaxNode(AbstractType.TASK, mandatoryNodeName);
							mandatoryNode.setLabel(mandatoryNodeName);
							parallelNode.addSuccessor(mandatoryNode);
							mandatoryNode.setPredecessor(parallelNode);
						}
					}
					else
					{
						final String mandatoryNodeName = currentMandatoryNodes.iterator().next();
						final AbstractSyntaxNode mandatoryNode = new AbstractSyntaxNode(AbstractType.TASK, mandatoryNodeName);
						mandatoryNode.setLabel(mandatoryNodeName);
						loopMandatoryNode.addSuccessor(mandatoryNode);
						mandatoryNode.setPredecessor(loopMandatoryNode);
					}
				}
			}
			else
			{
				final Pair<HashSet<String>, HashSet<String>> classicalLoop  = this.classicalLoops.iterator().next();
				final HashSet<String> currentMandatoryNodes = classicalLoop.first();
				final HashSet<String> currentOptionalNodes = classicalLoop.second();
				currentOptionalNodes.removeAll(currentMandatoryNodes);

				final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
				final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
				final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
				this.mainAST = new AbstractSyntaxTree(loopNode);

				loopNode.addSuccessor(loopMandatoryNode);
				loopMandatoryNode.setPredecessor(loopNode);
				loopNode.addSuccessor(loopOptionalNode);
				loopOptionalNode.setPredecessor(loopNode);

				if (currentMandatoryNodes.size() > 1)
				{
					final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
					loopMandatoryNode.addSuccessor(parallelNode);
					parallelNode.setPredecessor(loopMandatoryNode);

					for (String mandatoryNodeName : currentMandatoryNodes)
					{
						final AbstractSyntaxNode mandatoryNode = new AbstractSyntaxNode(AbstractType.TASK, mandatoryNodeName);
						mandatoryNode.setLabel(mandatoryNodeName);
						parallelNode.addSuccessor(mandatoryNode);
						mandatoryNode.setPredecessor(parallelNode);
					}
				}
				else
				{
					final String mandatoryNodeName = currentMandatoryNodes.iterator().next();
					final AbstractSyntaxNode mandatoryNode = new AbstractSyntaxNode(AbstractType.TASK, mandatoryNodeName);
					mandatoryNode.setLabel(mandatoryNodeName);
					loopMandatoryNode.addSuccessor(mandatoryNode);
					mandatoryNode.setPredecessor(loopMandatoryNode);
				}
			}
		}
		else
		{
			//We extract each loop of the dependency graph until no loop is found
			do
			{
				//Compute all the paths of the AST
				this.computeAllPaths();
			}
			while (this.extractLoops());

			//Manage classical loops
			this.manageClassicalLoops();
			//this.noLoopGraph = this.dependencyGraph.copy();

			//We plug back the loops
			for (Pair<Node, Node> brokenConnection : this.brokenConnections)
			{
				brokenConnection.first().addChild(brokenConnection.second());
				brokenConnection.second().addParent(brokenConnection.first());
			}

			this.computeAllPaths();

			//System.out.println("Dependency graph after loop management:\n\n" + this.dependencyGraph.stringify(0));

			//We generated the AST corresponding to the dependency graph
			this.mainAST = this.generateAST();

			//TODO VOIR SI GENERALISER L'IDEE DES LOOPY_DUMMY POUR DEMARRER/FINIR LES BOUCLES
			//We remove the LOOPY_DUMMY nodes necessary to generate the classical loops
			this.removeLoopyDummyNodes();
		}

		return this.mainAST;
	}

	public AbstractSyntaxTree mainAST()
	{
		return this.mainAST;
	}

	//Private methods

	private AbstractSyntaxTree generateAST() throws BadDependencyException
	{
		final AbstractSyntaxTree abstractSyntaxTree = new AbstractSyntaxTree();
		final HashMap<Node, AbstractSyntaxNode> correspondences = new HashMap<>();

		for (Path<Node> path : this.allPaths)
		{
			//System.out.println("Current path is " + path);
			final ArrayList<Path<Node>> splitPaths = this.splitPath(path, correspondences);
			//System.out.println("It has been split into " + splitPaths);

			for (int i = 0; i < splitPaths.size(); i++)
			{
				final Path<Node> splitPath = splitPaths.get(i);
				//System.out.println("The current split path is: " + splitPath);

				if (!splitPath.isInGraph())
				{
					//We consider only the parts of the path that are not already in the AST
					final Path<Node> previousPathInGraph = i > 0 ? splitPaths.get(i - 1) : null;
					final Path<Node> nextPathInGraph = i < splitPaths.size() - 1 ? splitPaths.get(i + 1) : null;
					final Node previousNodeInGraph = previousPathInGraph == null ? null : previousPathInGraph.getLast();
					final Node nextNodeInGraph = nextPathInGraph == null ? null : nextPathInGraph.getFirst();

					if (previousNodeInGraph == null
						&& nextNodeInGraph == null)
					{
						if (abstractSyntaxTree.isEmpty())
						{
							//System.out.println("AST is empty");
							//The AST is empty
							final Node firstNode = splitPath.removeFirst();
							final AbstractSyntaxNode firstTask = new AbstractSyntaxNode(AbstractType.TASK, firstNode.bpmnObject().name());
							firstTask.setLabel(firstNode.bpmnObject().name());
							correspondences.put(firstNode, firstTask);
							final AbstractSyntaxNode root;
							final AbstractSyntaxNode currentNodeSequence = new AbstractSyntaxNode(AbstractType.SEQ);
							currentNodeSequence.addSuccessor(firstTask);
							firstTask.setPredecessor(currentNodeSequence);

							if (this.loopInformation.containsKey(firstNode))
							{
								//The first node is a loop
								if (splitPath.isEmpty())
								{
									throw new IllegalStateException("The path should contain the node twice in case of self loop!");
								}

								//We create a loop
								final AbstractSyntaxNode rootSequence = new AbstractSyntaxNode(AbstractType.SEQ);
								final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
								final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
								final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);

								rootSequence.addSuccessor(loopNode);
								loopNode.setPredecessor(rootSequence);
								loopNode.addSuccessor(loopMandatoryNode);
								loopMandatoryNode.setPredecessor(loopNode);
								loopNode.addSuccessor(loopOptionalNode);
								loopOptionalNode.setPredecessor(loopNode);
								loopMandatoryNode.addSuccessor(currentNodeSequence);
								currentNodeSequence.setPredecessor(loopMandatoryNode);
								root = rootSequence;

								final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = this.loopInformation.get(firstNode);
								this.buildingLoopInformation.put(loopNode, loopInformation);

								for (Node mandatoryNode : loopInformation.second())
								{
									this.upperBounds.put(mandatoryNode, loopMandatoryNode);
									//System.out.println("\"" + mandatoryNode.bpmnObject().name() + "\" has \"+\" upper bound");
								}

								for (Node exitNode : loopInformation.first())
								{
									this.upperBounds.put(exitNode, loopMandatoryNode);
									//System.out.println("\"" + exitNode.bpmnObject().name() + "\" has \"+\" upper bound");
								}

								for (Node optionalNode : loopInformation.third())
								{
									this.upperBounds.put(optionalNode, loopOptionalNode);
									//System.out.println("\"" + optionalNode.bpmnObject().name() + "\" has \"?\" upper bound");
								}
							}
							else
							{
								root = currentNodeSequence;
							}

							abstractSyntaxTree.setRoot(root);
							splitPath.addElementAtPosition(0, firstNode);

							this.integratePath(abstractSyntaxTree, splitPath, 1, correspondences, null, null);
						}
						else
						{
							this.integratePath(abstractSyntaxTree, splitPath, 0, correspondences, null, null);
						}
					}
					else
					{
						this.integratePath(abstractSyntaxTree, splitPath, 0, correspondences, previousNodeInGraph, nextNodeInGraph);
					}
				}
			}

			ASTSequenceReducerV2.releaseGlobalConstraintsV1(abstractSyntaxTree, this.allConstraints);
		}

		return abstractSyntaxTree;
	}

	private void integratePath(final AbstractSyntaxTree tree,
							   final Path<Node> currentPath,
							   final int positionInPath,
							   final HashMap<Node, AbstractSyntaxNode> correspondences,
							   final Node previousNodeInGraph,
							   final Node nextNodeInGraph) throws BadDependencyException
	{
		if (positionInPath >= currentPath.size())
		{
			//We are done with the current path
			return;
		}

		final Node node = currentPath.get(positionInPath);

		if (correspondences.containsKey(node))
		{
			//End of loop
			return;
		}

		final AbstractSyntaxNode currentNode = new AbstractSyntaxNode(AbstractType.TASK, node.bpmnObject().name());
		currentNode.setLabel(node.bpmnObject().name());
		correspondences.put(node, currentNode);

		final AbstractSyntaxNode abstractSyntaxNode;

		if (this.loopInformation.containsKey(node))
		{
			final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = this.loopInformation.get(node);

			//The corresponding loop must be generated
			abstractSyntaxNode = new AbstractSyntaxNode(AbstractType.LOOP);
			final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
			abstractSyntaxNode.addSuccessor(loopMandatoryNode);
			loopMandatoryNode.setPredecessor(abstractSyntaxNode);
			final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
			abstractSyntaxNode.addSuccessor(loopOptionalNode);
			loopOptionalNode.setPredecessor(abstractSyntaxNode);
			final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);
			loopMandatoryNode.addSuccessor(sequenceNode);
			sequenceNode.setPredecessor(loopMandatoryNode);
			sequenceNode.addSuccessor(currentNode);
			currentNode.setPredecessor(sequenceNode);
			this.buildingLoopInformation.put(abstractSyntaxNode, loopInformation);

			if (this.upperBounds.containsKey(node))
			{
				/*
					The current node already has an upper bound.
					Thus, it means that the loop that we are going
					to generate is a nested loop thus we can map it
					to its upper loop in order to now the "real" upper
					bound of the current node
				 */
				this.nestedLoops.put(abstractSyntaxNode, this.upperBounds.get(node));
			}

			if (loopInformation.third().contains(node))
			{
				//The loop has no mandatory part => pas sûr que ce soit possible
				throw new IllegalStateException();
			}

			for (Node mandatoryNode : loopInformation.second())
			{
				this.upperBounds.put(mandatoryNode, loopMandatoryNode);
				//System.out.println("\"" + mandatoryNode.bpmnObject().name() + "\" has \"+\" upper bound");
			}

			for (Node exitNode : loopInformation.first())
			{
				this.upperBounds.put(exitNode, loopMandatoryNode);
				//System.out.println("\"" + exitNode.bpmnObject().name() + "\" has \"+\" upper bound");
			}

			for (Node optionalNode : loopInformation.third())
			{
				this.upperBounds.put(optionalNode, loopOptionalNode);
				//System.out.println("\"" + optionalNode.bpmnObject().name() + "\" has \"?\" upper bound");
			}
		}
		else
		{
			abstractSyntaxNode = new AbstractSyntaxNode(AbstractType.SEQ);
			abstractSyntaxNode.addSuccessor(currentNode);
			currentNode.setPredecessor(abstractSyntaxNode);
		}

		this.add(node, abstractSyntaxNode, tree);
		ASTReductor.reduce(tree);
		this.integratePath(tree, currentPath, positionInPath + 1, correspondences, previousNodeInGraph, nextNodeInGraph);
	}

	private void add(final Node node,
					 final AbstractSyntaxNode abstractSyntaxNode,
					 final AbstractSyntaxTree tree) throws BadDependencyException
	{
		/*
			We verify whether our current node has an upper bound that
			it can not exceed (i.e., a loop that it should belong to).
		 */
		final AbstractSyntaxNode upperBound = abstractSyntaxNode.type() == AbstractType.LOOP ?
				this.nestedLoops.get(abstractSyntaxNode) :
				this.upperBounds.get(node);

		/*
			First, we compute the left bounding nodes, i.e.,
			the nodes after which our current node should be put,
			and the right bounding nodes (similar).
		 */
		final HashSet<AbstractSyntaxNode> leftBoundingNodes = new HashSet<>();
		final HashSet<AbstractSyntaxNode> rightBoundingNodes = new HashSet<>();
		this.retrieveBoundingNodes(leftBoundingNodes, rightBoundingNodes, node, tree, upperBound);

		System.out.println("Current node to add to the tree: " + node);
		System.out.println("Upper bound: " + upperBound);
		System.out.println("Left bounding nodes: " + leftBoundingNodes);
		System.out.println("Right bounding node: " + rightBoundingNodes);
		System.out.println("Tree before adding \"" + node + "\":\n\n" + tree.toString());

		if (leftBoundingNodes.isEmpty()
			&& rightBoundingNodes.isEmpty())
		{
			//The current node can be put in parallel of the whole tree
			final AbstractSyntaxNode parallelNode = AbstractSyntaxNodeFactory.newParallel();
			parallelNode.addSuccessor(abstractSyntaxNode);
			abstractSyntaxNode.setPredecessor(parallelNode);
			parallelNode.addSuccessor(tree.root());
			tree.root().setPredecessor(parallelNode);
			tree.setRoot(parallelNode);
			return;
		}

		//Then, we compute the theoretical sequence node to which our current node should be added
		final AbstractSyntaxNode theoreticalSequenceNode = this.getTheoreticalSequenceNode(
				leftBoundingNodes,
				rightBoundingNodes,
				upperBound,
				node
		);

		if (theoreticalSequenceNode == null)
		{
			//TODO Pas sûr...
			throw new IllegalStateException();
		}

		System.out.println("Theoretical sequence node to branch our node: \"" + theoreticalSequenceNode.id() + "\".");

		/*
			Then we verify whether we are in a loop or not.
			If yes, we verify whether our current node belongs
			to this loop or not.
			If not we go upward in the tree until finding an
			eligible sequence node
		 */
		AbstractSyntaxNode loopNode = theoreticalSequenceNode.findClosestAncestorWithOperator(AbstractType.LOOP);
		AbstractSyntaxNode newClosestSequence = theoreticalSequenceNode;

		while (loopNode != null)
		{
			final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = this.buildingLoopInformation.get(loopNode);

			if (!loopInformation.second().contains(node)
				&& !loopInformation.third().contains(node))
			{
				//We are in a loop to which the current node does not belong
				newClosestSequence = loopNode.findClosestAncestorWithOperator(AbstractType.SEQ);

				if (newClosestSequence == null)
				{
					throw new IllegalStateException();
				}
				else
				{
					loopNode = newClosestSequence.findClosestAncestorWithOperator(AbstractType.LOOP);
				}
			}
			else
			{
				//The current node belongs to the loop
				break;
			}
		}

		//System.out.println("New closest sequence is \"" + newClosestSequence.id() + "\".");

		if (!leftBoundingNodes.isEmpty()
			&& !rightBoundingNodes.isEmpty())
		{
			final AbstractSyntaxNode boundsCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(
					ASTUtils.getLeastCommonAncestor(leftBoundingNodes),
					ASTUtils.getLeastCommonAncestor(rightBoundingNodes)
			);

			if (boundsCommonAncestor.hasAncestor(newClosestSequence))
			{
				throw new BadDependencyException(
						"Node \"" + node.bpmnObject().name() + "\" can not be put between \"" + leftBoundingNodes
						+ "\" and \"" + rightBoundingNodes + "\"."
				);
			}
		}

		if (upperBound != null)
		{
			//System.out.println("Current node upper bound: " + upperBound.id());

			if (newClosestSequence.hasAncestor(upperBound))
			{
				//As we are in a loop, some bounding nodes may be useless
				this.removeEventualUnnecessaryBoundingNodes(node, upperBound, leftBoundingNodes, rightBoundingNodes);

				final Pair<ArrayList<AbstractSyntaxNode>, Integer> info = newClosestSequence.getAllNodesBetween(leftBoundingNodes, rightBoundingNodes);

				if (info.first().isEmpty())
				{
					newClosestSequence.addSuccessor(info.second(), abstractSyntaxNode);
					abstractSyntaxNode.setPredecessor(newClosestSequence);
				}
				else
				{
					final AbstractSyntaxNode comaNode = new AbstractSyntaxNode(AbstractType.PAR);
					final AbstractSyntaxNode currentNodeSequence = new AbstractSyntaxNode(AbstractType.SEQ);
					final AbstractSyntaxNode oldNodesSequence = new AbstractSyntaxNode(AbstractType.SEQ);
					comaNode.addSuccessor(currentNodeSequence);
					currentNodeSequence.setPredecessor(comaNode);
					comaNode.addSuccessor(oldNodesSequence);
					oldNodesSequence.setPredecessor(comaNode);
					currentNodeSequence.addSuccessor(abstractSyntaxNode);
					abstractSyntaxNode.setPredecessor(currentNodeSequence);

					final AbstractSyntaxNode firstNodeToParallelise = info.first().remove(0);
					oldNodesSequence.addSuccessor(firstNodeToParallelise);
					firstNodeToParallelise.setPredecessor(oldNodesSequence);
					newClosestSequence.removeSuccessors(info.first());
					newClosestSequence.replaceSuccessor(firstNodeToParallelise, comaNode);
					comaNode.setPredecessor(newClosestSequence);
					oldNodesSequence.addSuccessors(info.first());

					for (final AbstractSyntaxNode nodeToParallelise : info.first())
					{
						nodeToParallelise.setPredecessor(oldNodesSequence);
					}
				}
			}
			else
			{
				//System.out.println("Closest sequence \"" + newClosestSequence.id() + "\" is not below upper bound.");
				/*
					Bad case: the closest sequence is not below the upper bound.
					We thus have to verify whether the dependency can be added with regard
					to the other elements of the path.
				 */
				final AbstractSyntaxNode loopMainNode = upperBound.predecessor();
				if (loopMainNode.type() != AbstractType.LOOP) throw new IllegalStateException();

				//Verify that our node can be on the right of the left bounding node
				if (!leftBoundingNodes.isEmpty())
				{
					if (upperBound.type() == AbstractType.LOOP_MANDATORY)
					{
						final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(leftBoundingNodes, loopMainNode);

						if (leastCommonAncestor == null
							|| leastCommonAncestor.type() != AbstractType.SEQ)
						{
							//System.out.println(leastCommonAncestor == null ? "Least common ancestor is null" : "Least" +
									//" common ancestor is of type \"" + leastCommonAncestor.type() + "\".");
							throw new BadDependencyException(
								"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
								+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
							);
						}

						//System.out.println("Least common ancestor of \"" + leftBoundingNodes + "\" and" +
								//" \"" + loopMainNode.id() + "\" is \"" + leastCommonAncestor.id() + "\".");

						if (leastCommonAncestor.getIndexOfSuccessorLeadingTo(ASTUtils.getLeastCommonAncestor(leftBoundingNodes))
							> leastCommonAncestor.getIndexOfSuccessorLeadingTo(loopMainNode))
						{
							throw new BadDependencyException(
								"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
								+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
							);
						}
					}
					else
					{
						AbstractSyntaxNode mandatoryLoopNode = null;

						for (AbstractSyntaxNode child : loopMainNode.successors())
						{
							if (child.type() == AbstractType.LOOP_MANDATORY)
							{
								mandatoryLoopNode = child;
								break;
							}
						}

						if (mandatoryLoopNode == null) throw new IllegalStateException();

						if (!mandatoryLoopNode.hasDescendants(leftBoundingNodes))
						{
							final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(leftBoundingNodes, loopMainNode);

							if (leastCommonAncestor == null
								|| leastCommonAncestor.type() != AbstractType.SEQ)
							{
								throw new BadDependencyException(
										"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
												+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
								);
							}

							if (leastCommonAncestor.getIndexOfSuccessorLeadingTo(ASTUtils.getLeastCommonAncestor(leftBoundingNodes))
									> leastCommonAncestor.getIndexOfSuccessorLeadingTo(loopMainNode))
							{
								throw new BadDependencyException(
										"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
												+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
								);
							}
						}
					}
				}

				//Verify that our node can be on the left of the right bounding node
				if (!rightBoundingNodes.isEmpty())
				{
					if (upperBound.type() == AbstractType.LOOP_OPTIONAL)
					{
						final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(rightBoundingNodes, loopMainNode);

						if (leastCommonAncestor == null
								|| leastCommonAncestor.type() != AbstractType.SEQ)
						{
							throw new BadDependencyException(
									"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
											+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
							);
						}

						if (leastCommonAncestor.getIndexOfSuccessorLeadingTo(ASTUtils.getLeastCommonAncestor(rightBoundingNodes))
								< leastCommonAncestor.getIndexOfSuccessorLeadingTo(loopMainNode))
						{
							throw new BadDependencyException(
									"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
											+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
							);
						}
					}
					else
					{
						AbstractSyntaxNode optionalLoopNode = null;

						for (AbstractSyntaxNode child : loopMainNode.successors())
						{
							if (child.type() == AbstractType.LOOP_OPTIONAL)
							{
								optionalLoopNode = child;
								break;
							}
						}

						if (optionalLoopNode == null) throw new IllegalStateException();

						if (!optionalLoopNode.hasDescendants(rightBoundingNodes))
						{
							final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(rightBoundingNodes, loopMainNode);

							if (leastCommonAncestor == null
								|| leastCommonAncestor.type() != AbstractType.SEQ)
							{
								throw new BadDependencyException(
										"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
												+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
								);
							}

							if (leastCommonAncestor.getIndexOfSuccessorLeadingTo(ASTUtils.getLeastCommonAncestor(rightBoundingNodes))
									< leastCommonAncestor.getIndexOfSuccessorLeadingTo(loopMainNode))
							{
								throw new BadDependencyException(
										"Node \"" + abstractSyntaxNode.id() + "\" can not be put inside loop \""
												+ loopMainNode.id() + "\" and after node \"" + leftBoundingNodes + "\"."
								);
							}
						}
					}
				}

				this.removeEventualUnnecessaryBoundingNodes(node, upperBound, leftBoundingNodes, rightBoundingNodes);
				//System.out.println("Remaining left bounding nodes: " + leftBoundingNodes);
				//System.out.println("Remaining right bounding nodes: " + rightBoundingNodes);

				final Pair<ArrayList<AbstractSyntaxNode>, Integer> info = upperBound.getAllNodesBetween(leftBoundingNodes, rightBoundingNodes);

				if (info.first().isEmpty())
				{
					upperBound.addSuccessor(info.second(), abstractSyntaxNode);
					abstractSyntaxNode.setPredecessor(upperBound);
				}
				else
				{
					if (info.first().size() > 1) throw new IllegalStateException();

					final AbstractSyntaxNode comaNode = new AbstractSyntaxNode(AbstractType.PAR);
					comaNode.addSuccessor(abstractSyntaxNode);
					abstractSyntaxNode.setPredecessor(comaNode);
					comaNode.addSuccessor(info.first().get(0));
					info.first().get(0).setPredecessor(comaNode);
					upperBound.replaceSuccessor(info.first().get(0), comaNode);
					comaNode.setPredecessor(upperBound);
				}
			}
		}
		else
		{
			/*
				There is no upper bound! :-)
			 */
			//System.out.println("Adding node \"" + node + "\" to node \"" + newClosestSequence.id() + "\".");

			final Pair<ArrayList<AbstractSyntaxNode>, Integer> info = newClosestSequence.getAllNodesBetween(leftBoundingNodes, rightBoundingNodes);

			//System.out.println("Nodes to parallelise: " + info.first());

			if (info.first().isEmpty())
			{
				newClosestSequence.addSuccessor(info.second(), abstractSyntaxNode);
				abstractSyntaxNode.setPredecessor(newClosestSequence);
			}
			else
			{
				final AbstractSyntaxNode comaNode = new AbstractSyntaxNode(AbstractType.PAR);
				final AbstractSyntaxNode currentNodeSequence = new AbstractSyntaxNode(AbstractType.SEQ);
				final AbstractSyntaxNode oldNodesSequence = new AbstractSyntaxNode(AbstractType.SEQ);
				comaNode.addSuccessor(currentNodeSequence);
				currentNodeSequence.setPredecessor(comaNode);
				comaNode.addSuccessor(oldNodesSequence);
				oldNodesSequence.setPredecessor(comaNode);
				currentNodeSequence.addSuccessor(abstractSyntaxNode);
				abstractSyntaxNode.setPredecessor(currentNodeSequence);

				final AbstractSyntaxNode firstNodeToParallelise = info.first().remove(0);
				oldNodesSequence.addSuccessor(firstNodeToParallelise);
				firstNodeToParallelise.setPredecessor(oldNodesSequence);
				newClosestSequence.removeSuccessors(info.first());
				newClosestSequence.replaceSuccessor(firstNodeToParallelise, comaNode);
				comaNode.setPredecessor(newClosestSequence);
				oldNodesSequence.addSuccessors(info.first());

				for (final AbstractSyntaxNode nodeToParallelise : info.first())
				{
					nodeToParallelise.setPredecessor(oldNodesSequence);
				}
			}
		}

		if (upperBound != null
			&& !abstractSyntaxNode.hasAncestor(upperBound))
		{
			throw new IllegalStateException("Node \"" + abstractSyntaxNode.id() + "\" is not below its upper bound" +
					" (node \"" + upperBound.id() + "\") in tree\n\n" + tree);
		}

		System.out.println("Tree after adding \"" + node + "\":\n\n" + tree.toString());
	}

	private void retrieveBoundingNodes(final HashSet<AbstractSyntaxNode> leftBoundingNodes,
									   final HashSet<AbstractSyntaxNode> rightBoundingNodes,
									   final Node node,
									   final AbstractSyntaxTree tree,
									   final AbstractSyntaxNode upperBound)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();
		this.getLeftBoundingNodes(node, leftBoundingNodes, tree, visitedNodes);
		visitedNodes.clear();
		final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInfo = upperBound == null ? null : this.buildingLoopInformation.get(upperBound.predecessor());
		this.getRightBoundingNodes(node, rightBoundingNodes, tree, visitedNodes, loopInfo == null ? null : loopInfo.second());
		rightBoundingNodes.removeAll(leftBoundingNodes); //Loops
		rightBoundingNodes.removeIf(rightBoundingNode -> rightBoundingNode.id().contains("DUMMY"));
	}

	private void getLeftBoundingNodes(final Node currentNode,
									  final HashSet<AbstractSyntaxNode> leftBoundingNodes,
									  final AbstractSyntaxTree tree,
									  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		final AbstractSyntaxNode abstractSyntaxNode = tree.findNodeOfId(currentNode.bpmnObject().id());

		if (abstractSyntaxNode != null)
		{
			leftBoundingNodes.add(abstractSyntaxNode);
		}
		else
		{
			for (Node parent : currentNode.parentNodes())
			{
				this.getLeftBoundingNodes(parent, leftBoundingNodes, tree, visitedNodes);
			}
		}
	}

	private void getRightBoundingNodes(final Node currentNode,
									   final HashSet<AbstractSyntaxNode> rightBoundingNodes,
									   final AbstractSyntaxTree tree,
									   final HashSet<Node> visitedNodes,
									   final HashSet<Node> mandatoryNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (mandatoryNodes != null)
		{
			if (mandatoryNodes.contains(currentNode)
				&& this.loopEntryPoints.contains(currentNode))
			{
				//We are on the starting loop of a loop (or sub-loop) to which the current node belongs
				return;
			}
		}

		final AbstractSyntaxNode abstractSyntaxNode = tree.findNodeOfId(currentNode.bpmnObject().id());

		if (abstractSyntaxNode != null)
		{
			rightBoundingNodes.add(abstractSyntaxNode);
		}
		else
		{
			for (Node child : currentNode.childNodes())
			{
				this.getRightBoundingNodes(child, rightBoundingNodes, tree, visitedNodes, mandatoryNodes);
			}
		}
	}

	private void removeEventualUnnecessaryBoundingNodes(final Node node,
														final AbstractSyntaxNode upperBound,
														final HashSet<AbstractSyntaxNode> leftBoundingNodes,
														final HashSet<AbstractSyntaxNode> rightBoundingNodes)
	{
		final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = this.buildingLoopInformation.get(upperBound.predecessor());

		if (loopInformation.second().contains(node))
		{
			//We can remove the bounding nodes that do not belong to the mandatory part of the loop
			for (Iterator<AbstractSyntaxNode> iterator = leftBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode leftBoundingNode = iterator.next();
				boolean found = false;

				for (Node mandatoryNode : loopInformation.second())
				{
					if (leftBoundingNode.id().equals(mandatoryNode.bpmnObject().id()))
					{
						found = true;
						break;
					}
				}

				if (!found)
				{
					iterator.remove();
				}
			}

			for (Iterator<AbstractSyntaxNode> iterator = rightBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode rightBoundingNode = iterator.next();
				boolean found = false;

				for (Node mandatoryNode : loopInformation.second())
				{
					if (rightBoundingNode.id().equals(mandatoryNode.bpmnObject().id()))
					{
						found = true;
						break;
					}
				}

				if (!found)
				{
					iterator.remove();
				}
			}
		}
		else if (loopInformation.third().contains(node))
		{
			//We can remove the bounding nodes that do not belong to the optional part of the loop
			for (Iterator<AbstractSyntaxNode> iterator = leftBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode leftBoundingNode = iterator.next();
				boolean found = false;

				for (Node optionalNode : loopInformation.third())
				{
					if (leftBoundingNode.id().equals(optionalNode.bpmnObject().id()))
					{
						found = true;
						break;
					}
				}

				if (!found)
				{
					iterator.remove();
				}
			}

			for (Iterator<AbstractSyntaxNode> iterator = rightBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode rightBoundingNode = iterator.next();
				boolean found = false;

				for (Node optionalNode : loopInformation.third())
				{
					if (rightBoundingNode.id().equals(optionalNode.bpmnObject().id()))
					{
						found = true;
						break;
					}
				}

				if (!found)
				{
					iterator.remove();
				}
			}
		}
		else
		{
			throw new IllegalStateException();
		}
	}

	/**
	 * This function is used to check whether the computed rightBound
	 * must be taken into account or can be ignored.
	 * It can be ignored if it is the entry node of a loop for which
	 * the currentNode belongs to the optional part.
	 *
	 * @param rightBound	the bound to verify the necessity of
	 * @param currentNode	the current node right-bounded by rightBound
	 * @return				true if the rightBound can be ignored, false if it necessary
	 */
	private boolean checkIfRightBoundCanBeIgnored(final AbstractSyntaxNode rightBound,
												  final Node currentNode)
	{
		if (rightBound == null) return true;

		boolean rightBoundIsALoopEntryPoint = false;

		for (Node loopEntryPoint : this.loopEntryPoints)
		{
			if (rightBound.id().equals(loopEntryPoint.bpmnObject().id()))
			{
				rightBoundIsALoopEntryPoint = true;
				break;
			}
		}

		//System.out.println("Right bound \"" + rightBound.id() + "\" is the starting point of a loop: " + rightBoundIsALoopEntryPoint);

		if (!rightBoundIsALoopEntryPoint) return false;

		AbstractSyntaxNode currentLoopAncestor = rightBound.findClosestAncestorWithOperator(AbstractType.LOOP);
		boolean currentNodeBelongToOptionalPart = false;

		while (currentLoopAncestor != null)
		{
			final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> currentLoopInformation = this.buildingLoopInformation.get(currentLoopAncestor);

			if (currentLoopInformation == null) throw new IllegalStateException();

			if (currentLoopInformation.third().contains(currentNode)
				|| currentLoopInformation.second().contains(currentNode))
			{
				currentNodeBelongToOptionalPart = true;
				break;
			}

			currentLoopAncestor = currentLoopAncestor.findClosestAncestorWithOperator(AbstractType.LOOP);
		}

		//System.out.println("Current node \"" + currentNode.bpmnObject().id() + "\" belongs to the loop optional part: " + currentNodeBelongToOptionalPart);

		return currentNodeBelongToOptionalPart;
	}

	private AbstractSyntaxNode getTheoreticalSequenceNode(final HashSet<AbstractSyntaxNode> leftBoundingNodes,
														  final HashSet<AbstractSyntaxNode> rightBoundingNodes,
														  final AbstractSyntaxNode upperBound,
														  final Node node)
	{
		if (leftBoundingNodes.isEmpty())
		{
			final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestor(rightBoundingNodes);
			return leastCommonAncestor.getClosestNodeOfTypeBelow(AbstractType.SEQ, upperBound); //TODO VOIR SI MIEUX FURTHEST OU CLOSEST
		}
		else if (rightBoundingNodes.isEmpty())
		{
			final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestor(leftBoundingNodes);
			return leastCommonAncestor.getClosestNodeOfTypeBelow(AbstractType.SEQ, upperBound); //TODO VOIR SI MIEUX FURTHEST OU CLOSEST
		}
		else
		{
			final AbstractSyntaxNode leftLeastCommonAncestor = ASTUtils.getLeastCommonAncestor(leftBoundingNodes);
			final AbstractSyntaxNode rightLeastCommonAncestor = ASTUtils.getLeastCommonAncestor(rightBoundingNodes);
			final AbstractSyntaxNode leftBoundingNodeSeqAncestor = leftLeastCommonAncestor.type() == AbstractType.SEQ ? leftLeastCommonAncestor : leftLeastCommonAncestor.findClosestAncestorWithOperator(AbstractType.SEQ);
			final AbstractSyntaxNode rightBoundingNodeSeqAncestor = rightLeastCommonAncestor.type() == AbstractType.SEQ ? rightLeastCommonAncestor : rightLeastCommonAncestor.findClosestAncestorWithOperator(AbstractType.SEQ);
			//System.out.println("Left bounding node seq ancestor: " + leftBoundingNodeSeqAncestor);
			//System.out.println("Right bounding node seq ancestor: " + rightBoundingNodeSeqAncestor);

			if (leftBoundingNodeSeqAncestor == null
				|| rightBoundingNodeSeqAncestor == null)
			{
				throw new IllegalStateException();
			}

			//Both are non-null
			final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestorFromId(leftLeastCommonAncestor, rightLeastCommonAncestor);
			//System.out.println("Least common ancestor: " + leastCommonAncestor);

			if (leastCommonAncestor == null)
			{
				throw new IllegalStateException("The LCA should not be null!");
				/*throw new IllegalStateException(
						"Expected non-null LCA of type \"<\", got " + ((leastCommonAncestor == null) ? "NULL" : "LCA" +
								" of type \"" + leastCommonAncestor.type().symbol() + "\".")
				);*/
			}

			if (leastCommonAncestor.type() != AbstractType.SEQ)
			{
				if (leastCommonAncestor.type() == AbstractType.LOOP)
				{
					final Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = this.buildingLoopInformation.get(leastCommonAncestor);

					if (loopInformation == null) throw new IllegalStateException();

					if (loopInformation.second().contains(node))
					{
						/*
							The current node is mandatory, thus it is necessarily before the right bounding
							node that is in the optional part of the loop
						 */
						return leftBoundingNodeSeqAncestor;
					}
					else if (loopInformation.third().contains(node))
					{
						/*
							The current node is optional, thus it is necessarily after the left bounding
							node that is in the mandatory part of the loop
						 */
						return rightBoundingNodeSeqAncestor;
					}
					else
					{
						throw new IllegalStateException();
					}
				}
				else
				{
					throw new IllegalStateException("The type of the LCA should not be \"" + leastCommonAncestor.type() + "\"!");
				}
			}

			if (leftBoundingNodeSeqAncestor.equals(rightBoundingNodeSeqAncestor)) return leastCommonAncestor;

			if (leftBoundingNodeSeqAncestor.hasAncestor(rightBoundingNodeSeqAncestor))
			{
				/*
					leftBoundingNodeSeqAncestor is below rightBoundingNodeSeqAncestor.
					We must check whether leftBoundingNodeSeqAncestor or any other
					intermediate sequence is eligible for adding our node.
				 */
				final int indexOfSuccessorLeadingToLeftBoundingNode = rightBoundingNodeSeqAncestor.getIndexOfSuccessorLeadingTo(leftLeastCommonAncestor);
				final int indexOfSuccessorLeadingToRightBoundingNode = rightBoundingNodeSeqAncestor.getIndexOfSuccessorLeadingTo(rightLeastCommonAncestor);

				if (indexOfSuccessorLeadingToLeftBoundingNode < indexOfSuccessorLeadingToRightBoundingNode)
				{
					return leftBoundingNodeSeqAncestor;
				}
				else
				{
					return leastCommonAncestor;
				}
			}
			else if (rightBoundingNodeSeqAncestor.hasAncestor(leftBoundingNodeSeqAncestor))
			{
				/*
					rightBoundingNodeSeqAncestor is below leftBoundingNodeSeqAncestor.
					We must check whether rightBoundingNodeSeqAncestor or any other
					intermediate sequence is eligible for adding our node.
				 */
				final int indexOfSuccessorLeadingToLeftBoundingNode = leftBoundingNodeSeqAncestor.getIndexOfSuccessorLeadingTo(leftLeastCommonAncestor);
				final int indexOfSuccessorLeadingToRightBoundingNode = leftBoundingNodeSeqAncestor.getIndexOfSuccessorLeadingTo(rightLeastCommonAncestor);

				if (indexOfSuccessorLeadingToLeftBoundingNode < indexOfSuccessorLeadingToRightBoundingNode)
				{
					return rightBoundingNodeSeqAncestor;
				}
				else
				{
					return leastCommonAncestor;
				}
			}
			else
			{
				return leastCommonAncestor;
			}
		}
	}

	private ArrayList<Path<Node>> splitPath(final Path<Node> path,
											final HashMap<Node, AbstractSyntaxNode> correspondences)
	{
		final ArrayList<Path<Node>> splitPaths = new ArrayList<>();
		boolean found = false;
		Path<Node> currentPath = new Path<>();
		splitPaths.add(currentPath);

		for (Node node : path.elements())
		{
			if (correspondences.containsKey(node))
			{
				//The current node belongs to the AST.
				if (!found)
				{
					//The previous node does not belong to the AST, so we must create a new path.
					if (!currentPath.isEmpty())
					{
						currentPath = new Path<>();
						splitPaths.add(currentPath);
					}

					currentPath.markAsInGraph();
				}

				currentPath.add(node);
				found = true;
			}
			else
			{
				//The current node does not belong to the AST.
				if (!currentPath.isEmpty()
					&& found)
				{
					//The previous node belongs to the AST, so we must create a new path.
					currentPath = new Path<>();
					splitPaths.add(currentPath);
				}

				currentPath.add(node);
				found = false;
			}
		}

		return splitPaths;
	}

	private boolean extractLoops()
	{
		Node mostExternalLoopNode = null;
		int mostExternalLoopNodeIndex = -1;
		//System.out.println("Paths found in the dependency graph: " + this.allPaths);

		for (Path<Node> path : this.allPaths)
		{
			for (int i = 0; i < path.size(); i++)
			{
				if (mostExternalLoopNodeIndex == 0) break; //There is no "more external" loop node

				final Node node = path.get(i);

				if (this.loopInformation.containsKey(node))
				{
					//TODO Normalement ne sert a rien
					continue;
				}

				if (node.equals(path.getLast())
					&& i != path.size() - 1)
				{
					if (mostExternalLoopNode == null)
					{
						mostExternalLoopNode = node;
						mostExternalLoopNodeIndex = i;
					}
					else
					{
						if (i < mostExternalLoopNodeIndex)
						{
							mostExternalLoopNode = node;
							mostExternalLoopNodeIndex = i;
						}
					}
				}
			}
		}

		if (mostExternalLoopNode == null)
		{
			//We managed all the loops
			return false;
		}

		if (DISABLE_NESTED_LOOPS)
		{
			//Temporarily disable nested loops: too hard to handle properly
			Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInformation = null;

			for (Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> currentLoop : this.loopInformation.values())
			{
				if (currentLoop.second().contains(mostExternalLoopNode)
					|| currentLoop.third().contains(mostExternalLoopNode))
				{
					loopInformation = currentLoop;
					break;
				}
			}

			if (loopInformation != null)
			{
				//The current most external loop node already belongs to a loop
				/*
					We remove temporarily the loopy nodes in order not to get
					undesired behaviors/results in the future computations.
				 */

				final Set<Node> originalParents = new HashSet<>(mostExternalLoopNode.parentNodes());

				for (Node entryNodeParent : originalParents)
				{
					if (entryNodeParent.isInLoop())
					{
						entryNodeParent.removeChildren(mostExternalLoopNode);
						mostExternalLoopNode.removeParent(entryNodeParent);
						this.brokenConnections.add(new Pair<>(entryNodeParent, mostExternalLoopNode));
					}
				}

				return true;
			}
		}

		this.loopEntryPoints.add(mostExternalLoopNode);

		//System.out.println("Current most external loop node: " + mostExternalLoopNode.bpmnObject().name() + "\n");

		final HashSet<Node> loopNodes = this.getLoopNodes(this.dependencyGraph, mostExternalLoopNode);
		loopNodes.add(mostExternalLoopNode);
		//System.out.println("Loop nodes: " + loopNodes);

		//Retrieve all corresponding paths
		final ArrayList<Path<Node>> loopPaths = new ArrayList<>();

		for (Path<Node> path : this.allPaths)
		{
			final int indexOfLoopStartNode = path.indexOf(mostExternalLoopNode);
			boolean pathIsEligible = true;

			if (indexOfLoopStartNode == -1)
			{
				continue;
				//throw new IllegalStateException("Path " + path + " does not contain loop node " + mostExternalLoopNode.bpmnObject().id());
			}

			for (int i = indexOfLoopStartNode; i < path.size(); i++)
			{
				final Node node = path.get(i);

				if (!loopNodes.contains(node))
				{
					pathIsEligible = false;
					break;
				}
			}

			if (pathIsEligible)
			{
				loopPaths.add(path);
			}
		}

		//System.out.println("Loop paths: " + loopPaths);

		if (loopPaths.isEmpty()) throw new IllegalStateException();

		final ArrayList<Node> loopNodesHavingEscapingChildren = this.getLoopNodesHavingEscapingChildren(loopPaths, mostExternalLoopNode);
		//System.out.println("Loop nodes having escaping children: " + loopNodesHavingEscapingChildren.toString());

		final HashSet<Node> exitNodes = new HashSet<>();
		final HashSet<Node> mandatoryNodes = new HashSet<>();
		mandatoryNodes.add(mostExternalLoopNode);
		final HashSet<Node> optionalNodes = new HashSet<>();

		if (loopPaths.size() == 1)
		{
			final Path<Node> currentPath = loopPaths.get(0);
			final Node exitNode;

			if (loopNodesHavingEscapingChildren.isEmpty())
			{
				//Pick the latest node of the path as exit node
				if (currentPath.size() == 1)
				{
					exitNode = currentPath.getFirst();
				}
				else
				{
					exitNode = currentPath.get(currentPath.size() - 2);
				}
			}
			else
			{
				exitNode = loopNodesHavingEscapingChildren.remove(0);

				if (!loopNodesHavingEscapingChildren.isEmpty())
				{
					/*
						The loop has several escaping nodes => Replug the remaining ones to have only one exit node
					 */
					for (Node loopNodeHavingEscapingChildren : loopNodesHavingEscapingChildren)
					{
						final HashSet<Node> originalChildren = new HashSet<>(loopNodeHavingEscapingChildren.childNodes());

						for (Node child : originalChildren)
						{
							if (!child.hasSuccessor(loopNodeHavingEscapingChildren))
							{
								/*
									Each escaping child is unplugged and plugged back
									to the selected exit node
								 */
								loopNodeHavingEscapingChildren.removeChildren(child);
								exitNode.addChild(child);
								child.replaceParent(loopNodeHavingEscapingChildren, exitNode);
							}
						}
					}
				}
			}

			exitNodes.add(exitNode);

			//Store information about mandatory/optional nodes of the loop
			final int indexOfExitNode = currentPath.indexOf(exitNode);
			final int loopEntryNodeIndex = currentPath.indexOf(mostExternalLoopNode);

			for (int i = loopEntryNodeIndex + 1; i < currentPath.size(); i++)
			{
				final Node currentNode = currentPath.get(i);

				if (i <= indexOfExitNode)
				{
					mandatoryNodes.add(currentNode);
				}
				else
				{
					optionalNodes.add(currentNode);
				}
			}
		}
		else
		{
			//System.out.println("Avant separate paths");
			final ArrayList<ArrayList<Path<Node>>> groupedPaths = this.separatePaths(loopPaths, mostExternalLoopNode);
			//System.out.println("Grouped paths: " + groupedPaths);

			if (groupedPaths.isEmpty()) throw new IllegalStateException();

			if (groupedPaths.size() == 1)
			{
				/*
					All the paths share nodes together (most common case).
					They should have at least one common ancestor, otherwise
					the loop cannot be generated.
				 */
				final ArrayList<Path<Node>> singleGroup = groupedPaths.iterator().next();
				final ArrayList<Node> intersection = this.intersect(singleGroup, mostExternalLoopNode);
				//System.out.println("Intersection of paths: " + intersection);

				if (intersection.isEmpty()) throw new IllegalStateException();

				final Node exitNode;

				if (intersection.size() == 1)
				{
					/*
						There is only one intersection node => it is the exit node of the loop
					 */
					exitNode = intersection.iterator().next();
				}
				else
				{
					/*
						There are several intersection nodes => pick the latest one
					 */
					exitNode = this.getLatestIntersectionNode(intersection, loopPaths);
				}

				exitNodes.add(exitNode);

				for (Node loopNodeHavingEscapingChildren : loopNodesHavingEscapingChildren)
				{
					if (!exitNode.equals(loopNodeHavingEscapingChildren))
					{
						final HashSet<Node> originalChildren = new HashSet<>(loopNodeHavingEscapingChildren.childNodes());

						for (Node child : originalChildren)
						{
							if (!child.hasSuccessor(loopNodeHavingEscapingChildren))
							{
								/*
									Each escaping child is unplugged and plugged back
									to the selected exit node
								 */
								loopNodeHavingEscapingChildren.removeChildren(child);
								exitNode.addChild(child);
								child.replaceParent(loopNodeHavingEscapingChildren, exitNode);
							}
						}
					}
				}

				//Store information about mandatory/optional nodes of the loop
				for (Path<Node> currentPath : singleGroup)
				{
					final int indexOfExitNode = currentPath.indexOf(exitNode);
					final int indexOfEntryNode = currentPath.indexOf(mostExternalLoopNode);

					if (indexOfExitNode == -1
						|| indexOfEntryNode == -1) throw new IllegalStateException();

					for (int i = indexOfEntryNode; i < currentPath.size(); i++)
					{
						final Node currentNode = currentPath.get(i);

						if (i <= indexOfExitNode)
						{
							mandatoryNodes.add(currentNode);
						}
						else
						{
							optionalNodes.add(currentNode);
						}
					}
				}
			}
			else
			{
				/*
					Some paths share no node and must thus be separated.
					In this case, the only valid option is to consider that
					the loop has no task in its returning path and thus that
					all the tasks must belong to the main part of the loop.
					By doing so, the disjoint paths can be synchronized after
					their end of execution.
				 */

				final HashSet<Node> lastNodesInLoopPaths = new HashSet<>();

				for (ArrayList<Path<Node>> group : groupedPaths)
				{
					for (Path<Node> path : group)
					{
						lastNodesInLoopPaths.add(path.getLast());
						//In this case, all the nodes are mandatory
						mandatoryNodes.addAll(path.elements());
					}
				}

				exitNodes.addAll(lastNodesInLoopPaths);

				for (Node nodeWithEscapingChild : loopNodesHavingEscapingChildren)
				{
					final HashSet<Node> originalChildren = new HashSet<>(nodeWithEscapingChild.childNodes());

					for (Node child : originalChildren)
					{
						if (!child.hasSuccessor(mostExternalLoopNode))
						{
							/*
								This node will be connected to the end nodes of the loop,
								that are the lastNodesInLoopPaths.
							 */
							nodeWithEscapingChild.removeChildren(child);
							child.removeParent(nodeWithEscapingChild);

							for (Node lastNode : lastNodesInLoopPaths)
							{
								lastNode.addChild(child);
								child.addParent(lastNode);
							}
						}
					}
				}
			}
		}

		/*
			Replug all the nodes that lead to nodes of the loop which are not
			the entry node.
		 */
		for (Node pathNode : loopNodes)
		{
			/*
				We keep the nodes leading to the entry point of the loop
			 */
			if (!pathNode.equals(mostExternalLoopNode))
			{
				final HashSet<Node> originalParents = new HashSet<>(pathNode.parentNodes());

				for (Node parent : originalParents)
				{
					if (!loopNodes.contains(parent))
					{
						/*
							Each parent of a loop node that does not belong
							to the loop is unplugged and plugged back to the
							entry point of the loop
						 */
						pathNode.removeParent(parent);
						mostExternalLoopNode.addParent(parent);
						parent.replaceChild(pathNode, mostExternalLoopNode);
					}
				}
			}
		}

		/*
			We remove temporarily the loopy nodes in order not to get
			undesired behaviors/results in the future computations.
		 */

		final Set<Node> originalParents = new HashSet<>(mostExternalLoopNode.parentNodes());
		//System.out.println("Original parents: " + originalParents);

		for (Node entryNodeParent : originalParents)
		{
			if (entryNodeParent.isInLoop())
			{
				entryNodeParent.removeChildren(mostExternalLoopNode);
				mostExternalLoopNode.removeParent(entryNodeParent);
				this.brokenConnections.add(new Pair<>(entryNodeParent, mostExternalLoopNode));
			}
		}

		//The entry node of the loop that we just managed should no longer belong to a loop
		if (mostExternalLoopNode.isInLoop()) throw new IllegalStateException();

		//The entry point of the loop may have been added to the optional nodes => remove it
		optionalNodes.remove(mostExternalLoopNode);
		optionalNodes.removeAll(mandatoryNodes);

		//The exit nodes are mandatory nodes
		mandatoryNodes.addAll(exitNodes);

		//Save the correspondence between the entry node of the loop and its exit nodes
		this.loopInformation.put(mostExternalLoopNode, new Triple<>(exitNodes, mandatoryNodes, optionalNodes));

		//System.out.println("-----------------------------------------------------------------------");
		//System.out.println("Loop starting with " + mostExternalLoopNode.bpmnObject().name() + " has the following nodes:\n\n-" +
				//" Exit nodes: " + exitNodes.toString() + "\n- Mandatory nodes: " + mandatoryNodes.toString() +
				//"\n- Optional nodes: " + optionalNodes.toString() + "\n");
		//System.out.println("-----------------------------------------------------------------------");

		return true;
	}

	private Node getLatestIntersectionNode(final ArrayList<Node> intersection,
										   final ArrayList<Path<Node>> paths)
	{
		final HashMap<Node, ArrayList<Integer>> appearanceMeasurement = new HashMap<>();
		Node latestNode = null;
		double biggestMean = -1;

		for (Node intersectionNode : intersection)
		{
			final ArrayList<Integer> currentList = appearanceMeasurement.computeIfAbsent(intersectionNode, a -> new ArrayList<>());

			for (Path<Node> path : paths)
			{
				currentList.add(path.indexOf(intersectionNode));
			}
		}

		for (Node node : intersection)
		{
			final double mean = this.mean(appearanceMeasurement.get(node));

			if (mean > biggestMean)
			{
				latestNode = node;
				biggestMean = mean;
			}
		}

		if (latestNode == null) throw new IllegalStateException();

		return latestNode;
	}

	private Double mean(final Collection<Integer> collection)
	{
		int sum = 0;

		for (Integer integer : collection)
		{
			sum += integer;
		}

		return (double) sum / (double) collection.size();
	}

	private HashSet<Node> getLoopNodes(final DependencyGraph dependencyGraph,
									   final Node loopEntryNode)
	{
		final HashSet<Node> nodes = dependencyGraph.toSet();
		final HashSet<Node> loopNodes = new HashSet<>();

		for (Node node : nodes)
		{
			if (node.hasSuccessor(loopEntryNode)
				&& node.hasAncestor(loopEntryNode))
			{
				loopNodes.add(node);
			}
		}

		return loopNodes;
	}

	private ArrayList<Node> intersect(final ArrayList<Path<Node>> paths,
									  final Node loopMandatoryNode)
	{
		ArrayList<Node> intersection = new ArrayList<>(paths.get(0).elements());

		for (int i = 1; i < paths.size(); i++)
		{
			final Path<Node> path = paths.get(i);
			intersection = path.intersectAfter(intersection, loopMandatoryNode);

			if (intersection.isEmpty())
			{
				throw new IllegalStateException();
			}
		}

		return intersection;
	}

	private ArrayList<ArrayList<Path<Node>>> separatePaths(final ArrayList<Path<Node>> paths,
														   final Node loopEntryNode)
	{
		final ArrayList<ArrayList<Path<Node>>> separatedPaths = new ArrayList<>();
		final ArrayList<Path<Node>> firstGroup = new ArrayList<>();
		firstGroup.add(paths.get(0));
		separatedPaths.add(firstGroup);

		for (int i = 1; i < paths.size(); i++)
		{
			//System.out.println("la");
			final Path<Node> currentPath = paths.get(i);
			boolean pathAdded = false;

			for (ArrayList<Path<Node>> group : separatedPaths)
			{
				for (Path<Node> path : group)
				{
					//System.out.println("ici");
					if (currentPath.hasNonEmptyIntersectionWith(path, loopEntryNode))
					{
						group.add(currentPath);
						pathAdded = true;
						break;
					}
				}

				if (pathAdded) break;
			}

			if (!pathAdded)
			{
				final ArrayList<Path<Node>> newGroup = new ArrayList<>();
				newGroup.add(currentPath);
				separatedPaths.add(newGroup);
			}

			this.mergeGroupsIfNeeded(separatedPaths, loopEntryNode);
		}

		return separatedPaths;
	}

	private void mergeGroupsIfNeeded(final ArrayList<ArrayList<Path<Node>>> separatedPaths,
									 final Node loopNode)
	{
		boolean setsWereMerged = true;

		while (setsWereMerged)
		{
			//System.out.println("Separated paths : " + separatedPaths);
			setsWereMerged = false;
			int positionOfGroupToRemove = -1;

			for (int i = 0; i < separatedPaths.size(); i++)
			{
				final ArrayList<Path<Node>> group1 = separatedPaths.get(i);

				for (int j = 0; j < separatedPaths.size(); j++)
				{
					if (i != j)
					{
						final ArrayList<Path<Node>> group2 = separatedPaths.get(i);

						if (this.groupsHaveNonEmptyIntersection(group1, group2, loopNode))
						{
							setsWereMerged = true;
							positionOfGroupToRemove = j;
							group1.addAll(group2);
							break;
						}
					}
				}

				if (positionOfGroupToRemove != -1)
				{
					break;
				}
			}

			if (positionOfGroupToRemove != -1)
			{
				separatedPaths.remove(positionOfGroupToRemove);
			}
		}
	}

	private boolean groupsHaveNonEmptyIntersection(final ArrayList<Path<Node>> group1,
												   final ArrayList<Path<Node>> group2,
												   final Node loopNode)
	{
		if (group1.equals(group2)) return false;

		for (Path<Node> path1 : group1)
		{
			for (Path<Node> path2 : group2)
			{
				if (path1.hasNonEmptyIntersectionWith(path2, loopNode))
				{
					return true;
				}
			}
		}

		return false;
	}

	private ArrayList<Node> getLoopNodesHavingEscapingChildren(final Collection<Path<Node>> paths,
															   final Node loopStartNode)
	{
		final ArrayList<Node> nodesHavingEscapingChildren = new ArrayList<>();

		for (Path<Node> path : paths)
		{
			final int loopStartNodeIndex = path.indexOf(loopStartNode);

			for (int i = loopStartNodeIndex + 1; i < path.size(); i++)
			{
				final Node node = path.get(i);

				for (Node child : node.childNodes())
				{
					if (!child.hasSuccessor(node))
					{
						if (!nodesHavingEscapingChildren.contains(node))
						{
							nodesHavingEscapingChildren.add(node);
						}
					}
				}
			}
		}

		return nodesHavingEscapingChildren;
	}

	private void computeAllPaths()
	{
		this.allPaths.clear();

		for (final Node initialNode : this.dependencyGraph.initialNodes())
		{
			final Path<Node> currentPath = new Path<>();
			this.allPaths.add(currentPath);

			this.computeAllPathsRec(initialNode, this.allPaths, currentPath, new HashSet<>());
		}
	}

	private void computeAllPathsRec(final Node currentNode,
									final ArrayList<Path<Node>> paths,
									final Path<Node> currentPath,
									final HashSet<Node> currentVisitedNodes)
	{
		currentPath.add(currentNode);

		if (currentVisitedNodes.contains(currentNode))
		{
			return;
		}

		currentVisitedNodes.add(currentNode);

		final Path<Node> immutableCurrentPath = currentPath.copy();
		final HashSet<Node> immutableVisitedNodes = new HashSet<>(currentVisitedNodes);
		int i = 0;

		for (Node child : currentNode.childNodes())
		{
			if (i++ == 0)
			{
				this.computeAllPathsRec(child, paths, currentPath, currentVisitedNodes);
			}
			else
			{
				final Path<Node> copiedPath = immutableCurrentPath.copy();
				paths.add(copiedPath);
				this.computeAllPathsRec(child, paths, copiedPath, new HashSet<>(immutableVisitedNodes));
			}
		}
	}

	private void manageClassicalLoops() throws BadDependencyException
	{
		int i = -1;

		for (Pair<HashSet<String>, HashSet<String>> classicalLoop : this.classicalLoops)
		{
			//System.out.println("Dependency graph before classical loop management:\n\n" + this.dependencyGraph);

			//System.out.println("Managing loop containing " + classicalLoop.first());
			i++;
			final HashSet<String> loopNodesIds = classicalLoop.first();
			final HashSet<Node> loopNodes = this.dependencyGraph.getNodesFromID(loopNodesIds);
			final HashMap<Node, HashSet<Node>> reachableLoopNodes = this.computeReachableLoopNodes(loopNodes);

			boolean loopIsAlreadyManaged = true;

			for (Node node : reachableLoopNodes.keySet())
			{
				final HashSet<Node> reachableNodes = reachableLoopNodes.get(node);

				if (reachableNodes.size() != loopNodes.size())
				{
					loopIsAlreadyManaged = false;
					break;
				}
			}

			if (loopIsAlreadyManaged) continue;

			for (Triple<HashSet<Node>, HashSet<Node>, HashSet<Node>> loopInfo : this.loopInformation.values())
			{
				if (Utils.intersectionIsNotEmpty(loopNodes, loopInfo.first())
					|| Utils.intersectionIsNotEmpty(loopNodes, loopInfo.second())
					|| Utils.intersectionIsNotEmpty(loopNodes, loopInfo.third()))
				{
					final HashSet<Node> implicitNodes = new HashSet<>();
					implicitNodes.addAll(loopInfo.first());
					implicitNodes.addAll(loopInfo.second());
					implicitNodes.addAll(loopInfo.third());
					loopIsAlreadyManaged = true;
					break;
					//throw new BadDependencyException("Can not merge explicit loop containing " + loopNodes + " and" +
					//		" implicit loop containing " + implicitNodes);
				}
			}

			if (loopIsAlreadyManaged) continue;

			//System.out.println("Current loop is already managed: " + loopIsAlreadyManaged);

			//Compute the entry point of the loop
			final HashSet<Node> nodesReachingWholeLoop = this.getLoopNodesReachingWholeLoop(reachableLoopNodes, loopNodes.size());
			final Node dummyLoopEntryPoint = new Node(BpmnProcessFactory.generateTask(LOOPY_DUMMY_ENTRY + "_" + i));

			//System.out.println("Nodes reaching whole loop: " + nodesReachingWholeLoop);

			if (nodesReachingWholeLoop.isEmpty())
			{
				/*
					We need to find the smallest set of loop nodes reaching all the loop nodes
				 */
				final Collection<Node> smallestSetOfNodesReachingWholeLoop = this.computeSmallestSetOfNodesReachingWholeLoop(reachableLoopNodes, loopNodes.size());

				for (Node nodeReachingWholeLoop : smallestSetOfNodesReachingWholeLoop)
				{
					final HashSet<Node> realLoopEntryPointParents = new HashSet<>(nodeReachingWholeLoop.parentNodes());
					nodeReachingWholeLoop.removeParents();

					for (Node parent : realLoopEntryPointParents)
					{
						parent.replaceChild(nodeReachingWholeLoop, dummyLoopEntryPoint);
						dummyLoopEntryPoint.addParent(parent);
					}

					dummyLoopEntryPoint.addChild(nodeReachingWholeLoop);
					nodeReachingWholeLoop.addParent(dummyLoopEntryPoint);

					if (this.dependencyGraph.initialNodes().contains(nodeReachingWholeLoop))
					{
						this.dependencyGraph.removeInitialNode(nodeReachingWholeLoop);
						this.dependencyGraph.addInitialNode(dummyLoopEntryPoint);
					}
				}
			}
			else
			{
				//We already have (at least) one entry point
				final Node realLoopEntryPoint = nodesReachingWholeLoop.iterator().next();
				final HashSet<Node> realLoopEntryPointParents = new HashSet<>(realLoopEntryPoint.parentNodes());
				realLoopEntryPoint.removeParents();

				for (Node parent : realLoopEntryPointParents)
				{
					parent.replaceChild(realLoopEntryPoint, dummyLoopEntryPoint);
					dummyLoopEntryPoint.addParent(parent);
				}

				dummyLoopEntryPoint.addChild(realLoopEntryPoint);
				realLoopEntryPoint.addParent(dummyLoopEntryPoint);

				if (this.dependencyGraph.initialNodes().contains(realLoopEntryPoint))
				{
					this.dependencyGraph.removeInitialNode(realLoopEntryPoint);
					this.dependencyGraph.addInitialNode(dummyLoopEntryPoint);
				}
			}

			//Remove spurious entry points
			for (Node loopNode : loopNodes)
			{
				final HashSet<Node> originalParents = new HashSet<>(loopNode.parentNodes());

				for (Node originalParent : originalParents)
				{
					if (!loopNodes.contains(originalParent)
						&& !originalParent.bpmnObject().id().startsWith(LOOPY_DUMMY_ENTRY))
					{
						boolean parentShouldBeRemoved = true;

						for (Node loopNodeToCheck : loopNodes)
						{
							if (originalParent.hasAncestor(loopNodeToCheck))
							{
								parentShouldBeRemoved = false;
								break;
							}
						}

						if (parentShouldBeRemoved)
						{
							//System.out.println("Parent node \"" + originalParent.bpmnObject().id() + "\" of " +
									//"node \"" + loopNode.bpmnObject().id() + "\" should be removed.");
							loopNode.removeParent(originalParent);
							originalParent.replaceChild(loopNode, dummyLoopEntryPoint);
							dummyLoopEntryPoint.addParent(originalParent);
						}
					}
				}
			}

			//Compute the exit node of the loop
			final HashSet<Node> nodesReachedByWholeLoop = this.getLoopNodesReachedByWholeLoop(reachableLoopNodes);
			//System.out.println("Nodes reached by whole loop: " + nodesReachedByWholeLoop);
			final Node dummyLoopExitPoint = new Node(BpmnProcessFactory.generateTask(LOOPY_DUMMY_EXIT + "_" + i));

			if (nodesReachedByWholeLoop.isEmpty())
			{
				/*
					We need to find the smallest set of loop nodes reaching all the loop nodes
				 */
				final Collection<Node> smallestSetOfNodesReachedByWholeLoop = this.computeSmallestSetOfNodesReachedByWholeLoop(reachableLoopNodes);

				for (Node nodeReachedByWholeLoop : smallestSetOfNodesReachedByWholeLoop)
				{
					final HashSet<Node> realLoopExitPointChildren = new HashSet<>(nodeReachedByWholeLoop.childNodes());
					nodeReachedByWholeLoop.removeChildren();

					for (Node child : realLoopExitPointChildren)
					{
						child.replaceParent(nodeReachedByWholeLoop, dummyLoopExitPoint);
						dummyLoopExitPoint.addChild(child);
					}

					dummyLoopExitPoint.addParent(nodeReachedByWholeLoop);
					nodeReachedByWholeLoop.addChild(dummyLoopExitPoint);
				}
			}
			else
			{
				//We already have (at least) one exit point
				final Node realLoopExitPoint = nodesReachedByWholeLoop.iterator().next();
				final HashSet<Node> realLoopExitPointChildren = new HashSet<>(realLoopExitPoint.childNodes());
				realLoopExitPoint.removeChildren();

				for (Node child : realLoopExitPointChildren)
				{
					child.replaceParent(realLoopExitPoint, dummyLoopExitPoint);
					dummyLoopExitPoint.addChild(child);
				}

				dummyLoopExitPoint.addParent(realLoopExitPoint);
				realLoopExitPoint.addChild(dummyLoopExitPoint);
			}

			//Remove spurious exit points
			for (Node loopNode : loopNodes)
			{
				final HashSet<Node> originalChildren = new HashSet<>(loopNode.childNodes());

				for (Node originalChild : originalChildren)
				{
					if (!loopNodes.contains(originalChild)
						&& !originalChild.bpmnObject().id().startsWith(LOOPY_DUMMY_EXIT))
					{
						boolean childShouldBeRemoved = true;

						for (Node loopNodeToCheck : loopNodes)
						{
							if (originalChild.hasSuccessor(loopNodeToCheck))
							{
								childShouldBeRemoved = false;
								break;
							}
						}

						if (childShouldBeRemoved)
						{
							//System.out.println("Child node \"" + originalChild.bpmnObject().id() + "\" of " +
									//"node \"" + loopNode.bpmnObject().id() + "\" should be removed.");
							loopNode.removeChildren(originalChild);
							originalChild.replaceParent(loopNode, dummyLoopExitPoint);
							dummyLoopExitPoint.addChild(originalChild);
						}
					}
				}
			}

			//Add new loop connections to broken connections
			this.brokenConnections.add(new Pair<>(dummyLoopExitPoint, dummyLoopEntryPoint));
			final HashSet<Node> exitNodes = new HashSet<>();
			exitNodes.add(dummyLoopExitPoint);
			loopNodes.add(dummyLoopEntryPoint);
			loopNodes.add(dummyLoopExitPoint);
			this.loopInformation.put(dummyLoopEntryPoint, new Triple<>(exitNodes, loopNodes, new HashSet<>()));
			this.loopEntryPoints.add(dummyLoopEntryPoint);
		}
	}

	private Collection<Node> computeSmallestSetOfNodesReachingWholeLoop(final HashMap<Node, HashSet<Node>> reachableLoopNodes,
																		final int nbLoopNodes)
	{
		final Set<Node> loopNodes = reachableLoopNodes.keySet();

		for (int i = 2; i <= loopNodes.size(); i++)
		{
			final Collection<Collection<Node>> subSets = Utils.getCombinationsOf(loopNodes, i);

			for (Collection<Node> subSet : subSets)
			{
				final HashSet<Node> subSetReachableNodes = new HashSet<>();

				for (Node node : subSet)
				{
					subSetReachableNodes.addAll(reachableLoopNodes.get(node));
				}

				if (subSetReachableNodes.size() == nbLoopNodes)
				{
					return subSet;
				}
			}
		}

		throw new IllegalStateException("No eligible set of nodes found!");
	}

	private Collection<Node> computeSmallestSetOfNodesReachedByWholeLoop(final HashMap<Node, HashSet<Node>> reachableLoopNodes)
	{
		final Set<Node> loopNodes = reachableLoopNodes.keySet();

		for (int i = 2; i <= loopNodes.size(); i++)
		{
			final Collection<Collection<Node>> subSets = Utils.getCombinationsOf(loopNodes, i);

			for (Collection<Node> subSet : subSets)
			{
				boolean eligibleSubset = true;

				for (Node loopNode : reachableLoopNodes.keySet())
				{
					final HashSet<Node> currentReachableLoopNodes = reachableLoopNodes.get(loopNode);
					boolean atLeastOneNodeIsReachable = false;

					for (Node subsetNode : subSet)
					{
						if (currentReachableLoopNodes.contains(subsetNode))
						{
							atLeastOneNodeIsReachable = true;
							break;
						}
					}

					if (!atLeastOneNodeIsReachable)
					{
						eligibleSubset = false;
						break;
					}
				}

				if (eligibleSubset)
				{
					return subSet;
				}
			}
		}

		throw new IllegalStateException("No eligible set of nodes found!");
	}

	private HashSet<Node> getLoopNodesReachingWholeLoop(final HashMap<Node, HashSet<Node>> reachableLoopNodes,
														final int nbLoopNodes)
	{
		final HashSet<Node> eligibleNodes = new HashSet<>();

		for (Node node : reachableLoopNodes.keySet())
		{
			final HashSet<Node> reachableNodes = reachableLoopNodes.get(node);

			if (reachableNodes.size() == nbLoopNodes)
			{
				eligibleNodes.add(node);
			}
		}

		return eligibleNodes;
	}

	private HashSet<Node> getLoopNodesReachedByWholeLoop(final HashMap<Node, HashSet<Node>> reachableLoopNodes)
	{
		final HashSet<Node> eligibleNodes = new HashSet<>(reachableLoopNodes.values().iterator().next());

		for (HashSet<Node> reachableNodes : reachableLoopNodes.values())
		{
			final Collection<Node> currentIntersection = Utils.getIntersectionOf(eligibleNodes, reachableNodes);
			eligibleNodes.clear();
			eligibleNodes.addAll(currentIntersection);

			if (eligibleNodes.isEmpty()) break;
		}

		return eligibleNodes;
	}

	private HashMap<Node, HashSet<Node>> computeReachableLoopNodes(final HashSet<Node> loopNodes)
	{
		final HashMap<Node, HashSet<Node>> reachableLoopNodes = new HashMap<>();

		for (Node loopNode : loopNodes)
		{
			final HashSet<Node> currentReachableLoopNodes = reachableLoopNodes.computeIfAbsent(loopNode, h -> new HashSet<>());
			currentReachableLoopNodes.add(loopNode);

			for (Node loopNodeToCheck : loopNodes)
			{
				if (loopNode.hasSuccessor(loopNodeToCheck, this.brokenConnections))
				{
					currentReachableLoopNodes.add(loopNodeToCheck);
				}
			}

			//System.out.println("Node \"" + loopNode.bpmnObject().id() + "\" can reach " + currentReachableLoopNodes);
		}

		return reachableLoopNodes;
	}

	private void removeLoopyDummyNodes()
	{
		final HashSet<AbstractSyntaxNode> loopyDummyNodes = new HashSet<>();
		this.getLoopyDummyNodes(this.mainAST.root(), loopyDummyNodes);

		for (AbstractSyntaxNode loopyDummyNode : loopyDummyNodes)
		{
			loopyDummyNode.predecessor().removeSuccessor(loopyDummyNode);
			loopyDummyNode.resetPredecessor();
		}
	}

	private void getLoopyDummyNodes(final AbstractSyntaxNode currentNode,
									final HashSet<AbstractSyntaxNode> loopyDummyNodes)
	{
		if (currentNode.type() == AbstractType.TASK
			&& currentNode.id().startsWith("LOOPY_DUMMY"))
		{
			loopyDummyNodes.add(currentNode);
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			this.getLoopyDummyNodes(successor, loopyDummyNodes);
		}
	}
}
