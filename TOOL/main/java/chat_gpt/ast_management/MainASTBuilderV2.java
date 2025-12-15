package chat_gpt.ast_management;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.ease.Path;
import other.MyOwnLogger;
import other.Pair;
import other.Utils;
import refactoring.legacy.dependencies.DependencyGraph;
import refactoring.legacy.exceptions.BadDependencyException;

import java.util.*;

public class MainASTBuilderV2
{
	private static final boolean DISABLE_NESTED_LOOPS = false;
	private static final String LOOPY_DUMMY_ENTRY = "LOOPY_DUMMY_ENTRY";
	private static final String LOOPY_DUMMY_EXIT = "LOOPY_DUMMY_EXIT";
	private final HashMap<Node, LoopInformation> loopInformation;
	private final HashMap<AbstractSyntaxNode, LoopInformation> buildingLoopInformation;
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

	public MainASTBuilderV2(final DependencyGraph dependencyGraph,
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
			MyOwnLogger.append("Graph used in MainASTBuilderV2:\n\n" + this.dependencyGraph);
			//We extract each loop of the dependency graph until no loop is found
			/*do
			{
				//Compute all the paths of the AST
				this.computeAllPaths();
			}
			while (this.extractLoops());*/
			this.extractLoopsV2();

			//Manage classical loops
			this.manageClassicalLoops();
			//this.noLoopGraph = this.dependencyGraph.copy();

			//We plug back the loops
			/*for (Pair<Node, Node> brokenConnection : this.brokenConnections)
			{
				brokenConnection.first().addChild(brokenConnection.second());
				brokenConnection.second().addParent(brokenConnection.first());
			}*/

			this.computeAllPaths();

			System.out.println("Dependency graph after loop management:\n\n" + this.dependencyGraph.stringify(0));

			//We generated the AST corresponding to the dependency graph
			this.mainAST = new AbstractSyntaxTree();
			this.generateAST(this.mainAST, this.allPaths);

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

	private void generateAST(final AbstractSyntaxTree abstractSyntaxTree,
							 final Collection<Path<Node>> allPaths) throws BadDependencyException
	{
		final HashMap<Node, AbstractSyntaxNode> correspondences = new HashMap<>();

		for (Path<Node> path : allPaths)
		{
			//System.out.println("Current path is " + path);
			final ArrayList<Path<Node>> splitPaths = this.splitPath(path, correspondences);
			//System.out.println("It has been split into " + splitPaths);

			for (final Path<Node> splitPath : splitPaths)
			{
				//System.out.println("The current split path is: " + splitPath);

				if (!splitPath.isInGraph())
				{
					for (Node node : splitPath.elements())
					{
						this.add(node, abstractSyntaxTree, allPaths);
						ASTReductor.reduce(abstractSyntaxTree);
					}
				}
			}

			ASTSequenceReducerV2.releaseGlobalConstraintsV1(abstractSyntaxTree, this.allConstraints);
		}
	}

	private void add(final Node node,
					 final AbstractSyntaxTree tree,
					 final Collection<Path<Node>> paths) throws BadDependencyException
	{
		if (tree != null
			&& !tree.isEmpty()
			&& tree.hasNodeOfLabel(node.bpmnObject().name()))
		{
			return;
		}


		final HashSet<AbstractSyntaxNode> leftBoundingNodes = (tree == null || tree.isEmpty()) ? new HashSet<>() : this.computeLeftBoundingNodesV2(paths, node, tree);
		final HashSet<AbstractSyntaxNode> rightBoundingNodes = (tree == null || tree.isEmpty()) ? new HashSet<>() : this.computeRightBoundingNodesV2(paths, node, tree);
		final AbstractSyntaxNode nodeToInsert;

		System.out.println("Node to insert: " + node);
		System.out.println("Left bounding nodes: " + leftBoundingNodes);
		System.out.println("Right bounding nodes: " + rightBoundingNodes);
		System.out.println("Tree before insertion of " + node + ":\n\n" + (tree.isEmpty() ? null : tree));

		if (this.loopEntryPoints.contains(node))
		{
			//We create the loop elements
			final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
			nodeToInsert = loopNode;
			final AbstractSyntaxNode mandatoryLoopNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
			final AbstractSyntaxNode optionalLoopNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
			loopNode.addSuccessor(mandatoryLoopNode);
			mandatoryLoopNode.setPredecessor(loopNode);
			loopNode.addSuccessor(optionalLoopNode);
			optionalLoopNode.setPredecessor(loopNode);

			final LoopInformation loopInformation = this.loopInformation.get(node);
			final HashSet<Path<Node>> mandatoryPaths = this.getMandatoryPaths(loopInformation, paths);
			final HashSet<Path<Node>> optionalPaths = this.getOptionalPaths(loopInformation, paths);
			System.out.println("Mandatory paths: " + mandatoryPaths);
			System.out.println("Optional paths: " + optionalPaths);

			//We build the tree corresponding to the mandatory part and the tree corresponding to the optional part
			//We add the loop entry node to avoid infinite recursion
			final AbstractSyntaxNode loopEntryNode = AbstractSyntaxNodeFactory.newTask(node.bpmnObject().name());
			final AbstractSyntaxTree loopMandatoryTree = new AbstractSyntaxTree(loopEntryNode);
			this.generateAST(loopMandatoryTree, mandatoryPaths);

			if (!loopMandatoryTree.isEmpty())
			{
				mandatoryLoopNode.addSuccessor(loopMandatoryTree.root());
				loopMandatoryTree.root().setPredecessor(mandatoryLoopNode);
			}

			final AbstractSyntaxTree loopOptionalTree = new AbstractSyntaxTree();
			this.generateAST(loopOptionalTree, optionalPaths);

			if (!loopOptionalTree.isEmpty())
			{
				optionalLoopNode.addSuccessor(loopOptionalTree.root());
				loopOptionalTree.root().setPredecessor(optionalLoopNode);
			}
		}
		else
		{
			nodeToInsert = AbstractSyntaxNodeFactory.newTask(node.bpmnObject().name());
		}

		if (leftBoundingNodes.isEmpty()
			&& rightBoundingNodes.isEmpty())
		{
			this.insertIfNoBoundingNodes(tree, nodeToInsert);
		}
		else if (leftBoundingNodes.isEmpty())
		{
			//The current node has right bounding nodes in the tree
			this.insertIfRightBoundingNodes(tree, nodeToInsert, rightBoundingNodes);
		}
		else if (rightBoundingNodes.isEmpty())
		{
			//The current node has left bounding nodes in the tree
			this.insertIfLeftBoundingNodes(tree, nodeToInsert, leftBoundingNodes);
		}
		else
		{
			//The current node has both left and right bounding nodes in the tree
			this.insertIfBothLeftAndRightBoundingNodes(tree, nodeToInsert, leftBoundingNodes, rightBoundingNodes);
		}

		System.out.println("Tree after insertion of " + node + ":\n\n" + tree);
	}

	private void insertIfNoBoundingNodes(final AbstractSyntaxTree tree,
										 final AbstractSyntaxNode nodeToInsert)
	{
		if (tree.isEmpty())
			{
				tree.setRoot(nodeToInsert);
			}
			else
			{
				if (tree.root().type() == AbstractType.PAR)
				{
					tree.root().addSuccessor(nodeToInsert);
					nodeToInsert.setPredecessor(tree.root());
				}
				else
				{
					final AbstractSyntaxNode parNode = AbstractSyntaxNodeFactory.newParallel();
					parNode.addSuccessor(tree.root());
					tree.root().setPredecessor(parNode);
					parNode.addSuccessor(nodeToInsert);
					nodeToInsert.setPredecessor(parNode);
					tree.setRoot(parNode);
				}
			}
	}

	private void insertIfLeftBoundingNodes(final AbstractSyntaxTree tree,
										   final AbstractSyntaxNode nodeToInsert,
										   final HashSet<AbstractSyntaxNode> leftBoundingNodes)
	{
		final AbstractSyntaxNode sequence = AbstractSyntaxNodeFactory.newSequence();
			final AbstractSyntaxNode leftBoundingAncestor;

			if (leftBoundingNodes.size() == 1)
			{
				leftBoundingAncestor = leftBoundingNodes.iterator().next();
			}
			else
			{
				leftBoundingAncestor = ASTUtils.getLeastCommonAncestor(leftBoundingNodes);
			}

			final AbstractSyntaxNode leftBoundingAncestorParent = leftBoundingAncestor.predecessor();

			if (leftBoundingAncestorParent == null)
			{
				sequence.addSuccessor(tree.root());
				tree.root().setPredecessor(sequence);
				sequence.addSuccessor(nodeToInsert);
				nodeToInsert.setPredecessor(sequence);
				tree.setRoot(sequence);
			}
			else
			{
				if (leftBoundingAncestorParent.type() == AbstractType.SEQ)
				{
					//We can minimize the impact by putting the right nodes in parallel
					final ArrayList<AbstractSyntaxNode> rightNodes = leftBoundingAncestorParent.getAllNodesAfter(leftBoundingAncestor);

					if (!rightNodes.isEmpty())
					{
						if (rightNodes.size() == 1)
						{
							final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
							leftBoundingAncestorParent.removeSuccessors(rightNodes);
							leftBoundingAncestorParent.addSuccessor(subParallel);
							subParallel.setPredecessor(leftBoundingAncestorParent);
							subParallel.addSuccessor(rightNodes.iterator().next());
							rightNodes.iterator().next().setPredecessor(subParallel);
							subParallel.addSuccessor(nodeToInsert);
							nodeToInsert.setPredecessor(subParallel);
						}
						else
						{
							final AbstractSyntaxNode subSequence = AbstractSyntaxNodeFactory.newSequence();
							final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
							leftBoundingAncestorParent.removeSuccessors(rightNodes);
							leftBoundingAncestorParent.addSuccessor(subParallel);
							subParallel.setPredecessor(leftBoundingAncestorParent);
							subParallel.addSuccessor(subSequence);
							subSequence.setPredecessor(subParallel);
							subParallel.addSuccessor(nodeToInsert);
							nodeToInsert.setPredecessor(subParallel);

							for (AbstractSyntaxNode rightNode : rightNodes)
							{
								subSequence.addSuccessor(rightNode);
								rightNode.setPredecessor(subSequence);
							}
						}
					}
					else
					{
						leftBoundingAncestorParent.addSuccessor(nodeToInsert);
						nodeToInsert.setPredecessor(leftBoundingAncestorParent);
					}
				}
				else
				{
					leftBoundingAncestorParent.replaceSuccessor(leftBoundingAncestor, sequence);
					sequence.setPredecessor(leftBoundingAncestorParent);
					sequence.addSuccessor(leftBoundingAncestor);
					leftBoundingAncestor.setPredecessor(sequence);
					sequence.addSuccessor(nodeToInsert);
					nodeToInsert.setPredecessor(sequence);
				}
			}
	}

	private void insertIfRightBoundingNodes(final AbstractSyntaxTree tree,
											final AbstractSyntaxNode nodeToInsert,
											final HashSet<AbstractSyntaxNode> rightBoundingNodes)
	{
		final AbstractSyntaxNode sequence = AbstractSyntaxNodeFactory.newSequence();
		final AbstractSyntaxNode rightBoundingAncestor;

		if (rightBoundingNodes.size() == 1)
		{
			rightBoundingAncestor = rightBoundingNodes.iterator().next();
		}
		else
		{
			rightBoundingAncestor = ASTUtils.getLeastCommonAncestor(rightBoundingNodes);
		}

		final AbstractSyntaxNode rightBoundingAncestorParent = rightBoundingAncestor.predecessor();

		if (rightBoundingAncestorParent == null)
		{
			sequence.addSuccessor(nodeToInsert);
			nodeToInsert.setPredecessor(sequence);
			sequence.addSuccessor(tree.root());
			tree.root().setPredecessor(sequence);
			tree.setRoot(sequence);
		}
		else
		{
			if (rightBoundingAncestorParent.type() == AbstractType.SEQ)
			{
				//We can minimize the impact by putting the left nodes in parallel
				final ArrayList<AbstractSyntaxNode> leftNodes = rightBoundingAncestorParent.getAllNodesBefore(rightBoundingAncestor);

				if (!leftNodes.isEmpty())
				{
					if (leftNodes.size() == 1)
					{
						final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
						rightBoundingAncestorParent.removeSuccessors(leftNodes);
						rightBoundingAncestorParent.addSuccessor(0, subParallel);
						subParallel.setPredecessor(rightBoundingAncestorParent);
						subParallel.addSuccessor(leftNodes.iterator().next());
						leftNodes.iterator().next().setPredecessor(subParallel);
						subParallel.addSuccessor(nodeToInsert);
						nodeToInsert.setPredecessor(subParallel);
					}
					else
					{
						final AbstractSyntaxNode subSequence = AbstractSyntaxNodeFactory.newSequence();
						final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
						rightBoundingAncestorParent.removeSuccessors(leftNodes);
						rightBoundingAncestorParent.addSuccessor(0, subParallel);
						subParallel.setPredecessor(rightBoundingAncestorParent);
						subParallel.addSuccessor(subSequence);
						subSequence.setPredecessor(subParallel);
						subParallel.addSuccessor(nodeToInsert);
						nodeToInsert.setPredecessor(subParallel);

						for (AbstractSyntaxNode leftNode : leftNodes)
						{
							subSequence.addSuccessor(leftNode);
							leftNode.setPredecessor(subSequence);
						}
					}
				}
				else
				{
					rightBoundingAncestorParent.addSuccessor(0, nodeToInsert);
					nodeToInsert.setPredecessor(rightBoundingAncestorParent);
				}
			}
			else
			{
				rightBoundingAncestorParent.replaceSuccessor(rightBoundingAncestor, sequence);
				sequence.setPredecessor(rightBoundingAncestorParent);
				sequence.addSuccessor(nodeToInsert);
				nodeToInsert.setPredecessor(sequence);
				sequence.addSuccessor(rightBoundingAncestor);
				rightBoundingAncestor.setPredecessor(sequence);
			}
		}
	}

	private void insertIfBothLeftAndRightBoundingNodes(final AbstractSyntaxTree tree,
													   final AbstractSyntaxNode nodeToInsert,
													   final HashSet<AbstractSyntaxNode> leftBoundingNodes,
													   final HashSet<AbstractSyntaxNode> rightBoundingNodes)
	{
		final AbstractSyntaxNode rightBoundingAncestor;

		if (rightBoundingNodes.size() == 1)
		{
			rightBoundingAncestor = rightBoundingNodes.iterator().next();
		}
		else
		{
			rightBoundingAncestor = ASTUtils.getLeastCommonAncestor(rightBoundingNodes);
		}

		final AbstractSyntaxNode leftBoundingAncestor;

		if (leftBoundingNodes.size() == 1)
		{
			leftBoundingAncestor = leftBoundingNodes.iterator().next();
		}
		else
		{
			leftBoundingAncestor = ASTUtils.getLeastCommonAncestor(leftBoundingNodes);
		}

		final AbstractSyntaxNode boundingAncestor = ASTUtils.getLeastCommonAncestor(leftBoundingAncestor, rightBoundingAncestor);

		if (!boundingAncestor.successors().contains(leftBoundingAncestor)
			&& !boundingAncestor.successors().contains(rightBoundingAncestor))
		{
			throw new IllegalStateException("VOIR TODO"); //TODO CAN IT HAPPEN? I THINK YES ....
		}
		else if (!boundingAncestor.successors().contains(leftBoundingAncestor))
		{
			System.out.println("Left bounding ancestor is: " + leftBoundingAncestor);
			final AbstractSyntaxNode sequenceNode = AbstractSyntaxNodeFactory.newSequence();
			leftBoundingAncestor.predecessor().replaceSuccessor(leftBoundingAncestor, sequenceNode);
			sequenceNode.setPredecessor(leftBoundingAncestor.predecessor());
			sequenceNode.addSuccessor(leftBoundingAncestor);
			leftBoundingAncestor.setPredecessor(sequenceNode);
			sequenceNode.addSuccessor(nodeToInsert);
			nodeToInsert.setPredecessor(sequenceNode);
		}
		else if (!boundingAncestor.successors().contains(rightBoundingAncestor))
		{
			System.out.println("Right bounding ancestor is: " + rightBoundingAncestor);
			final AbstractSyntaxNode sequenceNode = AbstractSyntaxNodeFactory.newSequence();
			rightBoundingAncestor.predecessor().replaceSuccessor(rightBoundingAncestor, sequenceNode);
			sequenceNode.setPredecessor(rightBoundingAncestor.predecessor());
			sequenceNode.addSuccessor(nodeToInsert);
			nodeToInsert.setPredecessor(sequenceNode);
			sequenceNode.addSuccessor(rightBoundingAncestor);
			rightBoundingAncestor.setPredecessor(sequenceNode);
		}
		else
		{
			final ArrayList<AbstractSyntaxNode> betweenNodes = boundingAncestor.getAllNodesBetween(leftBoundingAncestor, rightBoundingAncestor);

			if (betweenNodes.isEmpty())
			{
				boundingAncestor.addSuccessor(boundingAncestor.getIndexOfSuccessorLeadingTo(rightBoundingAncestor), nodeToInsert);
				nodeToInsert.setPredecessor(boundingAncestor);
			}
			else
			{
				if (betweenNodes.size() == 1)
				{
					boundingAncestor.removeSuccessors(betweenNodes);
					final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
					subParallel.addSuccessor(betweenNodes.iterator().next());
					betweenNodes.iterator().next().setPredecessor(subParallel);
					subParallel.addSuccessor(nodeToInsert);
					nodeToInsert.setPredecessor(subParallel);
					boundingAncestor.addSuccessor(boundingAncestor.getIndexOfSuccessorLeadingTo(rightBoundingAncestor), subParallel);
					subParallel.setPredecessor(boundingAncestor);
				}
				else
				{
					boundingAncestor.removeSuccessors(betweenNodes);
					final AbstractSyntaxNode subSequence = AbstractSyntaxNodeFactory.newSequence();
					final AbstractSyntaxNode subParallel = AbstractSyntaxNodeFactory.newParallel();
					subParallel.addSuccessor(subSequence);
					subSequence.setPredecessor(subParallel);
					subParallel.addSuccessor(nodeToInsert);
					nodeToInsert.setPredecessor(subParallel);
					boundingAncestor.addSuccessor(boundingAncestor.getIndexOfSuccessorLeadingTo(rightBoundingAncestor), subParallel);
					subParallel.setPredecessor(boundingAncestor);

					for (AbstractSyntaxNode betweenNode : betweenNodes)
					{
						subSequence.addSuccessor(betweenNode);
						betweenNode.setPredecessor(subSequence);
					}
				}
			}
		}
	}

	private HashSet<AbstractSyntaxNode> computeLeftBoundingNodes(final Collection<Path<Node>> paths,
																 final Node node,
																 final AbstractSyntaxTree abstractSyntaxTree)
	{
		if (abstractSyntaxTree.isEmpty()) return new HashSet<>();

		final HashSet<AbstractSyntaxNode> leftBoundingNodes = new HashSet<>();

		for (Path<Node> path : paths)
		{
			if (path.contains(node))
			{
				for (Node currentNode : path.elements())
				{
					if (currentNode.equals(node))
					{
						break;
					}

					final AbstractSyntaxNode correspondingNode = abstractSyntaxTree.findNodeOfLabel(currentNode.bpmnObject().name());

					if (correspondingNode != null)
					{
						final AbstractSyntaxNode loopNode = correspondingNode.getFurthestNodeOfTypeBelow(AbstractType.LOOP, null);
						leftBoundingNodes.add(Objects.requireNonNullElse(loopNode, correspondingNode));
					}
				}
			}
		}

		return leftBoundingNodes;
	}

	private HashSet<AbstractSyntaxNode> computeLeftBoundingNodesV2(final Collection<Path<Node>> paths,
																   final Node node,
																   final AbstractSyntaxTree abstractSyntaxTree)
	{
		if (abstractSyntaxTree.isEmpty()) return new HashSet<>();

		final HashSet<Node> leftBoundingNodes = new HashSet<>();

		for (Path<Node> path : paths)
		{
			final int nodeIndex = path.indexOf(node);

			if (nodeIndex != -1)
			{
				for (int i = nodeIndex - 1; i >= 0; i--)
				{
					final Node currentNode = path.get(i);
					final AbstractSyntaxNode correspondingAbstractSyntaxNode = abstractSyntaxTree.findNodeOfLabel(currentNode.bpmnObject().name());

					if (correspondingAbstractSyntaxNode != null)
					{
						leftBoundingNodes.add(currentNode);
						break;
					}
				}
			}
		}

		final HashSet<AbstractSyntaxNode> abstractSyntaxNodes = new HashSet<>();

		for (Node boundingCandidate : leftBoundingNodes)
		{
			boolean hasAncestor = false;

			for (Node otherBoundingNode : leftBoundingNodes)
			{
				if (!boundingCandidate.equals(otherBoundingNode))
				{
					if (otherBoundingNode.hasAncestor(boundingCandidate))
					{
						hasAncestor = true;
						break;
					}
				}
			}

			if (!hasAncestor)
			{
				final AbstractSyntaxNode correspondingNode = abstractSyntaxTree.findNodeOfLabel(boundingCandidate.bpmnObject().name());

				if (correspondingNode != null)
				{
					final AbstractSyntaxNode loopNode = correspondingNode.getFurthestNodeOfTypeBelow(AbstractType.LOOP, null);
					abstractSyntaxNodes.add(Objects.requireNonNullElse(loopNode, correspondingNode));
				}
			}
		}

		return abstractSyntaxNodes;
	}

	private HashSet<AbstractSyntaxNode> computeRightBoundingNodes(final Collection<Path<Node>> paths,
																  final Node node,
																  final AbstractSyntaxTree abstractSyntaxTree)
	{
		final HashSet<AbstractSyntaxNode> rightBoundingNodes = new HashSet<>();

		for (Path<Node> path : paths)
		{
			if (path.contains(node))
			{
				boolean started = false;

				for (Node currentNode : path.elements())
				{
					if (currentNode.equals(node))
					{
						started = true;
					}

					if (started)
					{
						final AbstractSyntaxNode correspondingNode = abstractSyntaxTree.findNodeOfLabel(currentNode.bpmnObject().name());

						if (correspondingNode != null)
						{
							final AbstractSyntaxNode loopNode = correspondingNode.getFurthestNodeOfTypeBelow(AbstractType.LOOP, null);
							rightBoundingNodes.add(Objects.requireNonNullElse(loopNode, correspondingNode));
						}
					}
				}
			}
		}

		return rightBoundingNodes;
	}

	private HashSet<AbstractSyntaxNode> computeRightBoundingNodesV2(final Collection<Path<Node>> paths,
																	final Node node,
																    final AbstractSyntaxTree abstractSyntaxTree)
	{
		if (abstractSyntaxTree.isEmpty()) return new HashSet<>();

		final HashSet<Node> rightBoundingNodes = new HashSet<>();

		for (Path<Node> path : paths)
		{
			final int nodeIndex = path.indexOf(node);

			if (nodeIndex != -1)
			{
				for (int i = nodeIndex + 1; i < path.elements().size(); i++)
				{
					final Node currentNode = path.get(i);
					final AbstractSyntaxNode correspondingAbstractSyntaxNode = abstractSyntaxTree.findNodeOfLabel(currentNode.bpmnObject().name());

					if (correspondingAbstractSyntaxNode != null)
					{
						rightBoundingNodes.add(currentNode);
						break;
					}
				}
			}
		}

		final HashSet<AbstractSyntaxNode> abstractSyntaxNodes = new HashSet<>();

		for (Node boundingCandidate : rightBoundingNodes)
		{
			boolean hasSuccessor = false;

			for (Node otherBoundingNode : rightBoundingNodes)
			{
				if (!boundingCandidate.equals(otherBoundingNode))
				{
					if (otherBoundingNode.hasSuccessor(boundingCandidate))
					{
						hasSuccessor = true;
						break;
					}
				}
			}

			if (!hasSuccessor)
			{
				final AbstractSyntaxNode correspondingNode = abstractSyntaxTree.findNodeOfLabel(boundingCandidate.bpmnObject().name());

				if (correspondingNode != null)
				{
					final AbstractSyntaxNode loopNode = correspondingNode.getFurthestNodeOfTypeBelow(AbstractType.LOOP, null);
					abstractSyntaxNodes.add(Objects.requireNonNullElse(loopNode, correspondingNode));
				}
			}
		}

		return abstractSyntaxNodes;
	}

	private HashSet<Path<Node>> getMandatoryPaths(final LoopInformation loopInformation,
												  final Collection<Path<Node>> paths)
	{
		final HashSet<Path<Node>> mandatoryPaths = new HashSet<>();

		for (Path<Node> path : paths)
		{
			if (path.contains(loopInformation.getEntryNode()))
			{
				mandatoryPaths.add(path.truncateBetween(loopInformation.getEntryNode(), loopInformation.getExitNodes()));
			}
		}

		return mandatoryPaths;
	}

	private HashSet<Path<Node>> getOptionalPaths(final LoopInformation loopInformation,
												 final Collection<Path<Node>> paths)
	{
		final HashSet<Path<Node>> optionalPaths = new HashSet<>();

		for (Path<Node> path : paths)
		{
			for (Node exitNode : loopInformation.getExitNodes())
			{
				if (path.contains(exitNode))
				{
					//System.out.println("Eligible path: " + path);
					final Path<Node> subPath = path.truncateBefore(exitNode);
					//System.out.println("Sub path: " + subPath);

					if (subPath.isEmpty()) continue;

					subPath.removeFirst();

					if (subPath.isEmpty()) continue;

					if (subPath.getLast().equals(loopInformation.getEntryNode()))
					{
						subPath.removeLast();
					}

					if (!subPath.isEmpty())
					{
						boolean valid = true;

						for (Node node : subPath.elements())
						{
							if (!loopInformation.getLoopNodes().contains(node))
							{
								valid = false;
								break;
							}
						}

						if (valid)
						{
							optionalPaths.add(subPath);
						}
					}
				}
			}
		}

		return optionalPaths;
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
		final LoopInformation loopInfo = upperBound == null ? null : this.buildingLoopInformation.get(upperBound.predecessor());
		this.getRightBoundingNodes(node, rightBoundingNodes, tree, visitedNodes, loopInfo == null ? null : loopInfo.getMandatoryNodes());
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
		final LoopInformation loopInformation = this.buildingLoopInformation.get(upperBound.predecessor());

		if (loopInformation.getMandatoryNodes().contains(node))
		{
			//We can remove the bounding nodes that do not belong to the mandatory part of the loop
			for (Iterator<AbstractSyntaxNode> iterator = leftBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode leftBoundingNode = iterator.next();
				boolean found = false;

				for (Node mandatoryNode : loopInformation.getMandatoryNodes())
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

				for (Node mandatoryNode : loopInformation.getMandatoryNodes())
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
		else if (loopInformation.getOptionalNodes().contains(node))
		{
			//We can remove the bounding nodes that do not belong to the optional part of the loop
			for (Iterator<AbstractSyntaxNode> iterator = leftBoundingNodes.iterator(); iterator.hasNext(); )
			{
				final AbstractSyntaxNode leftBoundingNode = iterator.next();
				boolean found = false;

				for (Node optionalNode : loopInformation.getOptionalNodes())
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

				for (Node optionalNode : loopInformation.getOptionalNodes())
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
			final LoopInformation currentLoopInformation = this.buildingLoopInformation.get(currentLoopAncestor);

			if (currentLoopInformation == null) throw new IllegalStateException();

			if (currentLoopInformation.getOptionalNodes().contains(currentNode)
				|| currentLoopInformation.getMandatoryNodes().contains(currentNode))
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
					final LoopInformation loopInformation = this.buildingLoopInformation.get(leastCommonAncestor);

					if (loopInformation == null) throw new IllegalStateException();

					if (loopInformation.getMandatoryNodes().contains(node))
					{
						/*
							The current node is mandatory, thus it is necessarily before the right bounding
							node that is in the optional part of the loop
						 */
						return leftBoundingNodeSeqAncestor;
					}
					else if (loopInformation.getOptionalNodes().contains(node))
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

	private void extractLoopsV2()
	{
		while (true)
		{
			System.out.println("Dependency graph before loop extraction:\n\n" + this.dependencyGraph);

			Node loopEntryNode = null;

			for (Node initialNode : this.dependencyGraph.initialNodes())
			{
				final Node entryNode = this.getLoopEntryNode(initialNode);

				if (entryNode != null)
				{
					loopEntryNode = entryNode;
					break;
				}
			}

			if (loopEntryNode == null)
			{
				//No loop was found
				break;
			}

			this.loopEntryPoints.add(loopEntryNode);
			final LoopInformation currentLoopInformation = new LoopInformation(loopEntryNode);
			this.extractBasicLoopInformation(loopEntryNode, currentLoopInformation, new HashSet<>());

			this.loopInformation.put(currentLoopInformation.getEntryNode(), currentLoopInformation);

			//Check whether the detected loop is shared with another loop
			LoopInformation sharedLoop = null;

			for (LoopInformation loopInformationToCheck : this.loopInformation.values())
			{
				if (Utils.intersectionIsNotEmpty(currentLoopInformation.getLoopNodes(), loopInformationToCheck.getLoopNodes()))
				{
					/*
						Two loops share nodes.
						If one contains the other, it is fine (nested loop).
						Otherwise, it means that the same loop has different entry points.
						In this case, the information needs to be merged.
					 */
					if (!currentLoopInformation.getLoopNodes().containsAll(loopInformationToCheck.getLoopNodes())
						&& !loopInformationToCheck.getLoopNodes().containsAll(currentLoopInformation.getLoopNodes()))
					{
						//The intersection is not empty, and no loop contains the other => merge is required


						break;
					}
				}
			}

			//Find exit nodes candidates
			System.out.println("Loop nodes: " + currentLoopInformation.getLoopNodes());
			final HashSet<Node> possibleExitNodes = this.getPossibleExitNodes(currentLoopInformation.getLoopNodes());
			System.out.println("Possible exit nodes: " + possibleExitNodes);
			final boolean noExitNodesFound = possibleExitNodes.isEmpty();

			//Unplug loopy connections
			final HashSet<Node> unpluggedParents = new HashSet<>();

			if (currentLoopInformation.getLoopNodes().size() == 1)
			{
				currentLoopInformation.getEntryNode().removeParent(currentLoopInformation.getEntryNode());
				currentLoopInformation.getEntryNode().removeChildren(currentLoopInformation.getEntryNode());
			}
			else
			{
				for (Iterator<Node> iterator = loopEntryNode.parentNodes().iterator(); iterator.hasNext(); )
				{
					final Node parent = iterator.next();
					System.out.println("Current parent: " + parent);

					if (loopEntryNode.hasSuccessor(parent))
					{
						System.out.println("Node \"" + loopEntryNode + "\" has parent successor \"" + parent + "\".");
						//The parent is in the loop => remove the connection
						this.brokenConnections.add(new Pair<>(parent, loopEntryNode));
						iterator.remove();
						parent.removeChildren(loopEntryNode);
						unpluggedParents.add(parent);
					}
				}
			}

			//Get exit nodes
			final Node finalExitNode;

			if (currentLoopInformation.getLoopNodes().size() > 1)
			{
				if (!noExitNodesFound)
				{
					if (possibleExitNodes.size() > 1)
					{
						final HashSet<Node> realExitNodes = new HashSet<>();

						for (final Node currentNode : possibleExitNodes)
						{
							if (!currentNode.isAtLeastSuccessorOfOneNodeAmong(possibleExitNodes))
							{
								realExitNodes.add(currentNode);
							}
						}

						if (realExitNodes.isEmpty()) throw new IllegalStateException();

						if (realExitNodes.size() == 1)
						{
							finalExitNode = realExitNodes.iterator().next();
						}
						else
						{
							finalExitNode = new Node(BpmnProcessFactory.generateTask(LOOPY_DUMMY_EXIT + "_" + Utils.generateRandomIdentifier(15)));
							this.brokenConnections.add(new Pair<>(finalExitNode, loopEntryNode));

							for (Node node : realExitNodes)
							{
								node.addChild(finalExitNode);
								finalExitNode.addParent(node);
								this.brokenConnections.remove(new Pair<>(node, loopEntryNode));
							}
						}

						//Modify connections to have only one proper exit node
						for (Node node : possibleExitNodes)
						{
							if (!node.equals(finalExitNode))
							{
								for (Iterator<Node> iterator = node.childNodes().iterator(); iterator.hasNext(); )
								{
									final Node child = iterator.next();

									if (!currentLoopInformation.getLoopNodes().contains(child))
									{
										iterator.remove();
										child.removeParent(node);
										finalExitNode.addChild(child);
										child.addParent(finalExitNode);
									}
								}
							}
						}
					}
					else
					{
						finalExitNode = possibleExitNodes.iterator().next();
					}
				}
				else
				{
					if (unpluggedParents.size() > 1)
					{
						finalExitNode = new Node(BpmnProcessFactory.generateTask(LOOPY_DUMMY_EXIT + "_" + Utils.generateRandomIdentifier(15)));

						for (Node unpluggedParent : unpluggedParents)
						{
							this.brokenConnections.remove(new Pair<>(unpluggedParent, loopEntryNode));
							unpluggedParent.addChild(finalExitNode);
							finalExitNode.addParent(unpluggedParent);
						}

						this.brokenConnections.add(new Pair<>(finalExitNode, loopEntryNode));
					}
					else
					{
						finalExitNode = unpluggedParents.iterator().next();
					}
				}
			}
			else
			{
				finalExitNode = currentLoopInformation.getLoopNodes().iterator().next();
			}

			System.out.println("Final exit node is \"" + finalExitNode + "\".");
			currentLoopInformation.addExitNode(finalExitNode);

			//Modify connections to have only one proper entry node
			for (Node node : currentLoopInformation.getLoopNodes())
			{
				if (!node.equals(currentLoopInformation.getEntryNode()))
				{
					for (Iterator<Node> iterator = node.parentNodes().iterator(); iterator.hasNext(); )
					{
						final Node parent = iterator.next();

						if (!currentLoopInformation.getLoopNodes().contains(parent))
						{
							iterator.remove();
							parent.removeChildren(node);
							parent.addChild(currentLoopInformation.getEntryNode());
							currentLoopInformation.getEntryNode().addParent(parent);
						}
					}
				}
			}

			final HashSet<Node> mandatoryNodes = currentLoopInformation.getEntryNode().getAllSuccessorsUpTo(finalExitNode);
			mandatoryNodes.add(currentLoopInformation.getEntryNode());
			mandatoryNodes.add(finalExitNode);

			final HashSet<Node> optionalNodes = currentLoopInformation.getExitNodes().iterator().next().getAllSuccessorsUpTo(null);
			optionalNodes.removeIf(node -> !currentLoopInformation.getLoopNodes().contains(node));
			optionalNodes.removeIf(node -> currentLoopInformation.getMandatoryNodes().contains(node));
			optionalNodes.remove(loopEntryNode);
			optionalNodes.remove(finalExitNode);
			currentLoopInformation.addMandatoryNodes(mandatoryNodes);
			currentLoopInformation.addOptionalNodes(optionalNodes);

			System.out.println(currentLoopInformation);
			System.out.println("Dependency graph after loop extraction:\n\n" + this.dependencyGraph);
		}
	}

	private HashSet<Node> getPossibleExitNodes(final HashSet<Node> loopNodes)
	{
		final HashSet<Node> possibleExitNodes = new HashSet<>();

		for (Node loopNode : loopNodes)
		{
			for (Node child : loopNode.childNodes())
			{
				if (!child.hasSuccessor(loopNode))
				{
					possibleExitNodes.add(loopNode);
					break;
				}
			}
		}

		return possibleExitNodes;
	}

	private Node getLoopEntryNode(final Node currentNode)
	{
		if (currentNode.isInLoop())
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node childInLoop = this.getLoopEntryNode(child);

			if (childInLoop != null)
			{
				return childInLoop;
			}
		}

		return null;
	}

	private void extractBasicLoopInformation(final Node currentNode,
											 final LoopInformation loopInformation,
											 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.hasSuccessor(loopInformation.getEntryNode()))
		{
			//Current node is in current loop
			loopInformation.addLoopNode(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.extractBasicLoopInformation(child, loopInformation, visitedNodes);
		}
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
			LoopInformation loopInformation = null;

			for (LoopInformation currentLoop : this.loopInformation.values())
			{
				if (currentLoop.getMandatoryNodes().contains(mostExternalLoopNode)
					|| currentLoop.getOptionalNodes().contains(mostExternalLoopNode))
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
		final LoopInformation loopInformation = new LoopInformation(mostExternalLoopNode);
		loopInformation.addExitNodes(exitNodes);
		loopInformation.addMandatoryNodes(mandatoryNodes);
		loopInformation.addOptionalNodes(optionalNodes);
		this.loopInformation.put(mostExternalLoopNode, loopInformation);

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

			System.out.println("Managing loop containing " + classicalLoop.first());
			i++;
			final HashSet<String> loopNodesIds = classicalLoop.first();
			final HashSet<Node> loopNodes = this.dependencyGraph.getNodesFromID(loopNodesIds);
			final HashMap<Node, HashSet<Node>> reachableLoopNodes = this.computeReachableLoopNodes(loopNodes);
			System.out.println("Loop nodes: " + loopNodes);
			System.out.println("Reachable loop nodes: " + reachableLoopNodes);

			if (loopNodes.size() == 1)
			{
				/*
					Self-loop : cas un peu particulier car le noeud est considéré comme atteignable
					de lui-même sans nécessairement être dans une boucle.
				 */
				final Node loopNode = loopNodes.iterator().next();
				final LoopInformation selfLoopInformation = new LoopInformation(loopNode);
				selfLoopInformation.addExitNode(loopNode);
				selfLoopInformation.addMandatoryNode(loopNode);
				this.loopEntryPoints.add(loopNode);
				this.loopInformation.put(loopNode, selfLoopInformation);
				continue;
			}

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

			System.out.println("Current loop is already managed: " + loopIsAlreadyManaged);
			if (loopIsAlreadyManaged) continue;

			for (LoopInformation loopInfo : this.loopInformation.values())
			{
				if (Utils.intersectionIsNotEmpty(loopNodes, loopInfo.getExitNodes())
					|| Utils.intersectionIsNotEmpty(loopNodes, loopInfo.getMandatoryNodes())
					|| Utils.intersectionIsNotEmpty(loopNodes, loopInfo.getOptionalNodes()))
				{
					final HashSet<Node> implicitNodes = new HashSet<>();
					implicitNodes.addAll(loopInfo.getExitNodes());
					implicitNodes.addAll(loopInfo.getMandatoryNodes());
					implicitNodes.addAll(loopInfo.getOptionalNodes());
					loopIsAlreadyManaged = true;
					break;
					//throw new BadDependencyException("Can not merge explicit loop containing " + loopNodes + " and" +
					//		" implicit loop containing " + implicitNodes);
				}
			}

			System.out.println("Current loop is already managed: " + loopIsAlreadyManaged);
			if (loopIsAlreadyManaged) continue;


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
			final LoopInformation loopInformation = new LoopInformation(dummyLoopEntryPoint);
			loopInformation.addExitNodes(exitNodes);
			loopInformation.addMandatoryNodes(loopNodes);
			this.loopInformation.put(dummyLoopEntryPoint, loopInformation);
			this.loopEntryPoints.add(dummyLoopEntryPoint);

			System.out.println("Found classical loop " + loopInformation);
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

	//Sub-classes
	static class LoopInformation
	{
		private Node entryNode;
		private final HashSet<Node> loopNodes;
		private final HashSet<Node> exitNodes;
		private final HashSet<Node> mandatoryNodes;
		private final HashSet<Node> optionalNodes;

		public LoopInformation(final Node entryNode)
		{
			this.entryNode = entryNode;
			this.exitNodes = new HashSet<>();
			this.mandatoryNodes = new HashSet<>();
			this.optionalNodes = new HashSet<>();
			this.loopNodes = new HashSet<>();
		}

		public LoopInformation()
		{
			this.exitNodes = new HashSet<>();
			this.mandatoryNodes = new HashSet<>();
			this.optionalNodes = new HashSet<>();
			this.loopNodes = new HashSet<>();
		}

		public boolean isEmpty()
		{
			return this.entryNode == null;
		}

		public void setEntryNode(final Node entryNode)
		{
			this.entryNode = entryNode;
		}

		public Node getEntryNode()
		{
			return this.entryNode;
		}

		public void addExitNode(final Node node)
		{
			this.exitNodes.add(node);
		}

		public void addExitNodes(final Collection<Node> nodes)
		{
			this.exitNodes.addAll(nodes);
		}

		public HashSet<Node> getExitNodes()
		{
			return this.exitNodes;
		}

		public void addMandatoryNode(final Node node)
		{
			this.mandatoryNodes.add(node);
		}

		public void addMandatoryNodes(final Collection<Node> nodes)
		{
			this.mandatoryNodes.addAll(nodes);
		}

		public HashSet<Node> getMandatoryNodes()
		{
			return this.mandatoryNodes;
		}

		public void addOptionalNode(final Node node)
		{
			this.optionalNodes.add(node);
		}

		public void addOptionalNodes(final Collection<Node> nodes)
		{
			this.optionalNodes.addAll(nodes);
		}

		public HashSet<Node> getOptionalNodes()
		{
			return this.optionalNodes;
		}

		public void addLoopNode(final Node node)
		{
			this.loopNodes.add(node);
		}

		public HashSet<Node> getLoopNodes()
		{
			return this.loopNodes;
		}

		@Override
		public String toString()
		{

			return "Current loop has " + this.loopNodes.size() + " nodes. " +
					"It starts with " + this.entryNode + " and finishes with " + this.exitNodes.iterator().next() + ". " +
					"Its mandatory nodes are " + this.mandatoryNodes + " and its optional nodes are " + this.optionalNodes;
		}
	}
}
