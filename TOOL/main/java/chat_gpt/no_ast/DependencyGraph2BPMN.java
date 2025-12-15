package chat_gpt.no_ast;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.*;
import chat_gpt.ast_management.AbstractSyntaxNode;
import chat_gpt.ast_management.AbstractSyntaxTree;
import chat_gpt.ast_management.ease.Path;
import chat_gpt.no_ast.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import other.Pair;
import other.Triple;
import other.Utils;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.*;

/**
 * 	TODO Is it meaningful to transitively reduce a dependency graph corresponding to a BPMN process????
 */

public class DependencyGraph2BPMN
{
	private static final boolean CHECK_MUTUAL_EXCLUSIONS_BREAKING = false; //TODO: Check disabled until implementing context-wise mutual exclusions
	private static final boolean PERFORM_DEADLOCK_DETECTION_ONLY = false;
	private static final Logger logger = LoggerFactory.getLogger(DependencyGraph2BPMN.class);
	private static final int NB_WORKING_GRAPHS_TO_KEEP = 1; //Any value < 0 will behave as an infinite bound
	private static final ParallelInsertionMode PARALLEL_INSERTION_MODE = ParallelInsertionMode.BEST_EFFORT;
	private static final ChoiceManagementMode CHOICE_MANAGEMENT_MODE = ChoiceManagementMode.LEAST_SUPERFLUOUS_MUTUAL_EXCLUSIONS;
	private static final MutualExclusionMode MUTUAL_EXCLUSION = MutualExclusionMode.WEAK_MUTUAL_EXCLUSION;
	private static final ReachabilityAnalysisMode REACHABILITY_ANALYSIS_MODE = ReachabilityAnalysisMode.NAIVE_REACHABILITY_ANALYSIS;
	private static final ExplicitLoopHandling EXPLICIT_LOOP_HANDLING = ExplicitLoopHandling.STRICT;
	private final ArrayList<HashSet<Node>> explicitLoops;
	private DependencyGraph dependencyGraph;
	private final HashMap<String, HashSet<String>> originalMutualExclusions;	//The ones given by the user
	private final HashMap<String, HashSet<String>> impossibleMutualExclusions;	//The ones non-satisfiable on the original graph
	private final HashMap<String, HashSet<String>> mutualExclusionsToEnsure;	//The ones that should be satisfied by the final graph
	private final HashMap<String, HashSet<String>> parallelNodes;
	private final HashMap<Node, HashSet<Node>> realMutuallyExclusiveNodes;
	private final HashMap<Node, HashSet<Node>> reachableNodes;
	private final HashSet<AbstractSyntaxTree> explicitChoices;
	private final HashSet<AbstractSyntaxTree> explicitParallels;

	public DependencyGraph2BPMN(final StructuredInformation exampleElements)
	{
		this.dependencyGraph = exampleElements.getMainGraph();
		this.explicitLoops = exampleElements.getExplicitLoops();
		this.explicitChoices = exampleElements.getExplicitChoices();
		this.explicitParallels = exampleElements.getExplicitParallels();
		this.originalMutualExclusions = new HashMap<>();
		this.impossibleMutualExclusions = new HashMap<>();
		this.mutualExclusionsToEnsure = new HashMap<>();
		this.parallelNodes = new HashMap<>();
		this.realMutuallyExclusiveNodes = new HashMap<>();
		this.reachableNodes = new HashMap<>();
	}

	public Graph translateToBpmn()
	{
		if (PERFORM_DEADLOCK_DETECTION_ONLY)
		{
			return this.performDeadlockDetectionOnly();
		}
		else
		{
			if (this.dependencyGraph.isEmpty())
			{
				if (this.explicitLoops.isEmpty())
				{
					return this.generateProcessHavingOnlyChoicesAndParallels();
				}
				else
				{
					return this.generateProcessHavingNoSequentialConstraints();
				}
			}
			else
			{
				if (this.dependencyGraph.initialNodes().isEmpty())
				{
					throw new RuntimeException("Initial nodes are empty!");
				}
				else if (this.dependencyGraph.endNodes().isEmpty())
				{
					throw new RuntimeException("End nodes are empty!");
				}

				this.addExclusiveGatewaysAndDummyStartEndsIfNeeded();

				return this.generateProcessWithStandardProcedure();
			}
		}
	}

	//Private methods
	private Graph performDeadlockDetectionOnly()
	{
		System.out.println("Dependency graph to verify:\n\n" + this.dependencyGraph);

		final Graph graph = this.convertToBpmnV1(this.dependencyGraph);
		final BpmnDeadlockDetector deadlockDetector = new BpmnDeadlockDetector(graph);
		deadlockDetector.detectDeadlocksAndLivelocks();

		if (deadlockDetector.getFailySimulations().isEmpty())
		{
			System.out.println("Deadlock detector found 0 deadlock/livelock in the process");
		}
		else
		{
			System.out.println("Deadlock detector found a deadlock/livelock in the process:");
			System.out.println(deadlockDetector.getFailySimulations().get(0).toString());

			/*System.out.println("This process went through " + deadlockDetector.getFailySimulations().get(0).alreadyKnownConfigurations().size() + " already known configurations:\n");
			for (BpmnDeadlockDetector.SimulationInfo simulationInfo : deadlockDetector.getFailySimulations().get(0).alreadyKnownConfigurations())
			{
				System.out.println(simulationInfo);
			}*/
		}

		return this.convertToBpmnV1();
	}

	private Graph generateProcessHavingNoSequentialConstraints()
	{
		final ArrayList<HashSet<Node>> biggestDisjointLoops = this.buildBiggestDisjointLoops();

		//Retrieve original mutual exclusions
		for (AbstractSyntaxTree choiceTree : this.explicitChoices)
		{
			final String leftNodeId = choiceTree.root().successors().get(0).label();
			final String rightNodeId = choiceTree.root().successors().get(1).label();
			final HashSet<String> leftNodeMutuallyExclusiveNodes = this.originalMutualExclusions.computeIfAbsent(leftNodeId, h -> new HashSet<>());
			final HashSet<String> rightNodeMutuallyExclusiveNodes = this.originalMutualExclusions.computeIfAbsent(rightNodeId, h -> new HashSet<>());
			leftNodeMutuallyExclusiveNodes.add(rightNodeId);
			rightNodeMutuallyExclusiveNodes.add(leftNodeId);
		}

		System.out.println("Original mutual exclusions: " + originalMutualExclusions);

		if (biggestDisjointLoops.size() == 1)
		{
			//Manage the loop
			final HashSet<Node> loop = biggestDisjointLoops.iterator().next();
			final HashSet<Node> loopMutuallyExclusiveNodes = new HashSet<>();

			for (Node loopNode : loop)
			{
				for (Node loopNode1 : loop)
				{
					if (this.originalMutualExclusions.computeIfAbsent(loopNode.bpmnObject().id(), h -> new HashSet<>()).contains(loopNode1.bpmnObject().id()))
					{
						loopMutuallyExclusiveNodes.add(loopNode);
						loopMutuallyExclusiveNodes.add(loopNode1);
					}
				}
			}

			final HashSet<Node> loopParallelNodes = new HashSet<>(loop);
			loopParallelNodes.removeAll(loopMutuallyExclusiveNodes);

			final Node loopEntryNode = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
			final Node loopExitNode = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
			//loopExitNode.addChildAndForceParent(loopEntryNode);
			final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
			final Node exclusiveMerge = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
			final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelSplitGateway());
			final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelMergeGateway());

			for (Node mutuallyExclusiveLoopNode : loopMutuallyExclusiveNodes)
			{
				exclusiveSplit.addChildAndForceParent(mutuallyExclusiveLoopNode);
				mutuallyExclusiveLoopNode.addChildAndForceParent(exclusiveMerge);
			}

			for (Node parallelNode : loopParallelNodes)
			{
				parallelSplit.addChildAndForceParent(parallelNode);
				parallelNode.addChildAndForceParent(parallelMerge);
			}

			if (parallelSplit.hasChildren())
			{
				loopEntryNode.addChildAndForceParent(parallelSplit);
				parallelMerge.addChildAndForceParent(loopExitNode);

				if (exclusiveSplit.hasChildren())
				{
					parallelSplit.addChildAndForceParent(exclusiveSplit);
					exclusiveMerge.addChildAndForceParent(parallelMerge);
				}
			}
			else
			{
				loopEntryNode.addChildAndForceParent(exclusiveSplit);
				exclusiveMerge.addChildAndForceParent(loopExitNode);
			}

			//Manage the remaining nodes
			final HashSet<Node> remainingNodesToAdd = new HashSet<>();

			for (AbstractSyntaxTree abstractSyntaxTree : this.explicitChoices)
			{
				final Node leftTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(0).label()));
				final Node rightTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(1).label()));
				remainingNodesToAdd.add(leftTask);
				remainingNodesToAdd.add(rightTask);
			}

			for (AbstractSyntaxTree abstractSyntaxTree : this.explicitParallels)
			{
				final Node leftTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(0).label()));
				final Node rightTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(1).label()));
				remainingNodesToAdd.add(leftTask);
				remainingNodesToAdd.add(rightTask);
			}

			remainingNodesToAdd.removeAll(loop);
			final Node startNode;

			if (!remainingNodesToAdd.isEmpty())
			{
				final HashSet<Node> exclusiveRemainingNodes = new HashSet<>();
				final HashSet<Node> remainingNodesExclusiveWithLoop = new HashSet<>();

				for (Node node : remainingNodesToAdd)
				{
					for (Node node1 : remainingNodesToAdd)
					{
						if (this.originalMutualExclusions.computeIfAbsent(node.bpmnObject().id(), h -> new HashSet<>()).contains(node1.bpmnObject().id()))
						{
							exclusiveRemainingNodes.add(node);
							exclusiveRemainingNodes.add(node1);
						}
					}

					for (Node loopNode : loop)
					{
						if (this.originalMutualExclusions.computeIfAbsent(node.bpmnObject().id(), h -> new HashSet<>()).contains(loopNode.bpmnObject().id()))
						{
							remainingNodesExclusiveWithLoop.add(node);
						}
					}
				}

				final HashSet<Node> parallelRemainingNodes = new HashSet<>(remainingNodesToAdd);
				parallelRemainingNodes.removeAll(exclusiveRemainingNodes);
				parallelRemainingNodes.removeAll(remainingNodesExclusiveWithLoop);

				if (!remainingNodesExclusiveWithLoop.isEmpty())
				{
					final Node remainingNodesSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
					remainingNodesSplit.addChildAndForceParent(loopEntryNode);

					for (Node remainingExclusive : remainingNodesExclusiveWithLoop)
					{
						remainingNodesSplit.addChildAndForceParent(remainingExclusive);
					}

					if (!exclusiveRemainingNodes.isEmpty())
					{
						Node mainParallelSplit = null;

						if (Utils.intersectionIsNotEmpty(exclusiveRemainingNodes, remainingNodesExclusiveWithLoop))
						{
							exclusiveRemainingNodes.removeAll(remainingNodesExclusiveWithLoop);

							for (Node exclusiveRemainingNode : exclusiveRemainingNodes)
							{
								remainingNodesSplit.addChildAndForceParent(exclusiveRemainingNode);
							}
						}
						else
						{
							final Node remainingNodesSplit2 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());

							for (Node exclusiveRemainingNode : exclusiveRemainingNodes)
							{
								remainingNodesSplit2.addChildAndForceParent(exclusiveRemainingNode);
							}

							mainParallelSplit = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							mainParallelSplit.addChildAndForceParent(remainingNodesSplit2);
							mainParallelSplit.addChildAndForceParent(remainingNodesSplit);
						}

						if (parallelRemainingNodes.isEmpty())
						{
							startNode = mainParallelSplit == null ? remainingNodesSplit : mainParallelSplit;
						}
						else
						{
							mainParallelSplit = mainParallelSplit == null ? new Node(BpmnProcessFactory.generateParallelSplitGateway()) : mainParallelSplit;

							for (Node parallelNode : parallelRemainingNodes)
							{
								mainParallelSplit.addChildAndForceParent(parallelNode);
							}

							startNode = mainParallelSplit;
						}
					}
					else
					{
						if (parallelRemainingNodes.isEmpty())
						{
							startNode = remainingNodesSplit;
						}
						else
						{
							startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							startNode.addChildAndForceParent(remainingNodesSplit);

							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
				}
				else
				{
					if (exclusiveRemainingNodes.isEmpty())
					{
						if (parallelRemainingNodes.isEmpty())
						{
							startNode = loopEntryNode;
						}
						else
						{
							startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							startNode.addChildAndForceParent(loopEntryNode);

							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
					else
					{
						startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
						final Node remainingExclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
						startNode.addChildAndForceParent(remainingExclusiveSplit);
						startNode.addChildAndForceParent(loopEntryNode);

						for (Node exclusiveRemaining : exclusiveRemainingNodes)
						{
							remainingExclusiveSplit.addChildAndForceParent(exclusiveRemaining);
						}

						if (!parallelRemainingNodes.isEmpty())
						{
							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
				}
			}
			else
			{
				startNode = loopEntryNode;
			}

			loopExitNode.addChildAndForceParent(loopEntryNode);

			final DependencyGraph dependencyGraph = new DependencyGraph();
			dependencyGraph.addInitialNode(startNode);
			dependencyGraph.addEndNode(loopExitNode);

			System.out.println(dependencyGraph.toString());

			return this.convertToBpmnV1(dependencyGraph).cleanGateways();
		}
		else
		{
			//Check which loops are mutually exclusive and which ones are parallelisable
			final boolean[] booleans = this.computeMutualExclusionsForLoops(biggestDisjointLoops);

			//Build the loops
			final ArrayList<DependencyGraph> loops = new ArrayList<>();

			for (final HashSet<Node> disjointLoop : biggestDisjointLoops)
			{
				final HashSet<Node> mutuallyExclusiveNodes = new HashSet<>();

				for (Node node : disjointLoop)
				{
					for (Node node2 : disjointLoop)
					{
						if (this.originalMutualExclusions.computeIfAbsent(node.bpmnObject().id(), h -> new HashSet<>()).contains(node2.bpmnObject().id()))
						{
							mutuallyExclusiveNodes.add(node);
							mutuallyExclusiveNodes.add(node2);
						}
					}
				}

				final HashSet<Node> parallelNodes = new HashSet<>(disjointLoop);
				parallelNodes.removeAll(mutuallyExclusiveNodes);

				final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
				final Node exclusiveMerge = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());

				for (Node node : mutuallyExclusiveNodes)
				{
					exclusiveSplit.addChildAndForceParent(node);
					node.addChildAndForceParent(exclusiveMerge);
				}

				final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelSplitGateway());
				final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelMergeGateway());

				for (Node node : parallelNodes)
				{
					parallelSplit.addChildAndForceParent(node);
					node.addChildAndForceParent(parallelMerge);
				}

				final Node loopEntry = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
				final Node loopExit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());

				if (parallelSplit.hasChildren())
				{
					loopEntry.addChildAndForceParent(parallelSplit);
					parallelMerge.addChildAndForceParent(loopExit);

					if (exclusiveSplit.hasChildren())
					{
						parallelSplit.addChildAndForceParent(exclusiveSplit);
						exclusiveMerge.addChildAndForceParent(parallelMerge);
					}
				}
				else
				{
					loopEntry.addChildAndForceParent(exclusiveSplit);
					exclusiveMerge.addChildAndForceParent(loopExit);
				}

				final DependencyGraph loop = new DependencyGraph();
				loop.addInitialNode(loopEntry);
				loop.addEndNode(loopExit);
				loops.add(loop);
			}

			//Separate mutually exclusive loops and parallelisable loops
			final ArrayList<DependencyGraph> mutuallyExclusiveLoops = new ArrayList<>();
			final ArrayList<DependencyGraph> parallelLoops = new ArrayList<>();

			for (int i = 0; i < loops.size(); i++)
			{
				final DependencyGraph loop = loops.get(i);

				if (booleans[i])
				{
					parallelLoops.add(loop);
				}
				else
				{
					mutuallyExclusiveLoops.add(loop);
				}
			}

			//Connect the loops
			final Node loopsExclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
			final Node loopsParallelSplit = new Node(BpmnProcessFactory.generateParallelSplitGateway());

			for (DependencyGraph exclusiveLoop : mutuallyExclusiveLoops)
			{
				loopsExclusiveSplit.addChildAndForceParent(exclusiveLoop.initialNodes().iterator().next());
			}

			for (DependencyGraph parallelLoop : parallelLoops)
			{
				loopsParallelSplit.addChildAndForceParent(parallelLoop.initialNodes().iterator().next());
			}

			//Create the start node for the loops (link for the eventual remaining nodes)
			final Node loopsStartNode;

			if (loopsParallelSplit.hasChildren())
			{
				loopsStartNode = loopsParallelSplit;

				if (loopsExclusiveSplit.hasChildren())
				{
					loopsStartNode.addChildAndForceParent(loopsExclusiveSplit);
				}
			}
			else
			{
				loopsStartNode = loopsExclusiveSplit;
			}

			//Manage the remaining nodes
			final HashSet<Node> remainingNodesToAdd = new HashSet<>();

			for (AbstractSyntaxTree abstractSyntaxTree : this.explicitChoices)
			{
				final Node leftTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(0).label()));
				final Node rightTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(1).label()));
				remainingNodesToAdd.add(leftTask);
				remainingNodesToAdd.add(rightTask);
			}

			for (AbstractSyntaxTree abstractSyntaxTree : this.explicitParallels)
			{
				final Node leftTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(0).label()));
				final Node rightTask = new Node(BpmnProcessFactory.generateTask(abstractSyntaxTree.root().successors().get(1).label()));
				remainingNodesToAdd.add(leftTask);
				remainingNodesToAdd.add(rightTask);
			}

			for (HashSet<Node> loop : biggestDisjointLoops)
			{
				remainingNodesToAdd.removeAll(loop);
			}

			final Node startNode;

			if (remainingNodesToAdd.isEmpty())
			{
				startNode = loopsStartNode;
			}
			else
			{
				final HashSet<Node> remainingExclusiveNodes = new HashSet<>();
				final HashSet<Node> remainingNodesExclusiveWithLoop = new HashSet<>();

				for (Node remainingNode : remainingNodesToAdd)
				{
					for (Node remainingNode2 : remainingNodesToAdd)
					{
						if (this.originalMutualExclusions.computeIfAbsent(remainingNode.bpmnObject().id(), h -> new HashSet<>()).contains(remainingNode2.bpmnObject().id()))
						{
							remainingExclusiveNodes.add(remainingNode);
							remainingExclusiveNodes.add(remainingNode2);
						}
					}

					for (HashSet<Node> loop : biggestDisjointLoops)
					{
						for (Node loopNode : loop)
						{
							if (this.originalMutualExclusions.computeIfAbsent(remainingNode.bpmnObject().id(), h -> new HashSet<>()).contains(loopNode.bpmnObject().id()))
							{
								remainingNodesExclusiveWithLoop.add(remainingNode);
							}
						}
					}
				}

				final HashSet<Node> parallelRemainingNodes = new HashSet<>(remainingNodesToAdd);
				parallelRemainingNodes.removeAll(remainingExclusiveNodes);
				parallelRemainingNodes.removeAll(remainingNodesExclusiveWithLoop);

				if (!remainingNodesExclusiveWithLoop.isEmpty())
				{
					final Node remainingNodesSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
					remainingNodesSplit.addChildAndForceParent(loopsStartNode);

					for (Node remainingExclusive : remainingNodesExclusiveWithLoop)
					{
						remainingNodesSplit.addChildAndForceParent(remainingExclusive);
					}

					if (!remainingExclusiveNodes.isEmpty())
					{
						Node mainParallelSplit = null;

						if (Utils.intersectionIsNotEmpty(remainingExclusiveNodes, remainingNodesExclusiveWithLoop))
						{
							remainingExclusiveNodes.removeAll(remainingNodesExclusiveWithLoop);

							for (Node exclusiveRemainingNode : remainingExclusiveNodes)
							{
								remainingNodesSplit.addChildAndForceParent(exclusiveRemainingNode);
							}
						}
						else
						{
							final Node remainingNodesSplit2 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());

							for (Node exclusiveRemainingNode : remainingExclusiveNodes)
							{
								remainingNodesSplit2.addChildAndForceParent(exclusiveRemainingNode);
							}

							mainParallelSplit = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							mainParallelSplit.addChildAndForceParent(remainingNodesSplit2);
							mainParallelSplit.addChildAndForceParent(remainingNodesSplit);
						}

						if (parallelRemainingNodes.isEmpty())
						{
							startNode = mainParallelSplit == null ? remainingNodesSplit : mainParallelSplit;
						}
						else
						{
							mainParallelSplit = mainParallelSplit == null ? new Node(BpmnProcessFactory.generateParallelSplitGateway()) : mainParallelSplit;

							for (Node parallelNode : parallelRemainingNodes)
							{
								mainParallelSplit.addChildAndForceParent(parallelNode);
							}

							startNode = mainParallelSplit;
						}
					}
					else
					{
						if (parallelRemainingNodes.isEmpty())
						{
							startNode = remainingNodesSplit;
						}
						else
						{
							startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							startNode.addChildAndForceParent(remainingNodesSplit);

							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
				}
				else
				{
					if (remainingExclusiveNodes.isEmpty())
					{
						if (parallelRemainingNodes.isEmpty())
						{
							startNode = loopsStartNode;
						}
						else
						{
							startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
							startNode.addChildAndForceParent(loopsStartNode);

							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
					else
					{
						startNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
						final Node remainingExclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
						startNode.addChildAndForceParent(remainingExclusiveSplit);
						startNode.addChildAndForceParent(loopsStartNode);

						for (Node exclusiveRemaining : remainingExclusiveNodes)
						{
							remainingExclusiveSplit.addChildAndForceParent(exclusiveRemaining);
						}

						if (!parallelRemainingNodes.isEmpty())
						{
							for (Node parallelNode : parallelRemainingNodes)
							{
								startNode.addChildAndForceParent(parallelNode);
							}
						}
					}
				}
			}

			final DependencyGraph finalGraph = new DependencyGraph();
			finalGraph.addInitialNode(startNode);

			for (DependencyGraph loop : loops)
			{
				finalGraph.addEndNode(loop.endNodes().iterator().next());
				loop.endNodes().iterator().next().addChildAndForceParent(loop.initialNodes().iterator().next());
			}

			System.out.println(finalGraph.toString());

			return this.convertToBpmnV1(finalGraph).cleanGateways();
		}
	}

	private boolean[] computeMutualExclusionsForLoops(final ArrayList<HashSet<Node>> disjointLoops)
	{
		final boolean[] loopMutualExclusions = new boolean[disjointLoops.size()];

		for (int i = 0; i < disjointLoops.size(); i++)
		{
			loopMutualExclusions[i] = true;
		}

		for (int i = 0; i < disjointLoops.size(); i++)
		{
			final HashSet<Node> disjointLoop1 = disjointLoops.get(i);

			for (int j = i + 1; j < disjointLoops.size(); j++)
			{
				final HashSet<Node> disjointLoop2 = disjointLoops.get(j);

				for (Node node1 : disjointLoop1)
				{
					final HashSet<String> mutuallyExclusiveNodes = this.originalMutualExclusions.get(node1.bpmnObject().id());

					for (Node node2 : disjointLoop2)
					{
						if (mutuallyExclusiveNodes.contains(node2.bpmnObject().id()))
						{
							loopMutualExclusions[i] = loopMutualExclusions[j] = false;
							break;
						}
					}

					if (!loopMutualExclusions[i])
					{
						break;
					}
				}
			}
		}

		return loopMutualExclusions;
	}

	private Graph generateProcessHavingOnlyChoicesAndParallels()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final Node initialNode;

		if (this.explicitParallels.isEmpty())
		{
			initialNode = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
			final HashSet<String> alreadyAddedNodes = new HashSet<>();

			for (AbstractSyntaxTree choice : this.explicitChoices)
			{
				final AbstractSyntaxNode leftTask = choice.root().successors().get(0);
				final AbstractSyntaxNode rightTask = choice.root().successors().get(1);

				if (!alreadyAddedNodes.contains(leftTask.label()))
				{
					alreadyAddedNodes.add(leftTask.label());
					initialNode.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(leftTask.label())));
				}
				if (!alreadyAddedNodes.contains(rightTask.label()))
				{
					alreadyAddedNodes.add(rightTask.label());
					initialNode.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(rightTask.label())));
				}
			}
		}
		else
		{
			initialNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());

			final HashSet<String> alreadyAddedNodes = new HashSet<>();
			final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());

			for (AbstractSyntaxTree choice : this.explicitChoices)
			{
				final AbstractSyntaxNode leftTask = choice.root().successors().get(0);
				final AbstractSyntaxNode rightTask = choice.root().successors().get(1);

				if (!alreadyAddedNodes.contains(leftTask.label()))
				{
					alreadyAddedNodes.add(leftTask.label());
					exclusiveSplit.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(leftTask.label())));
				}
				if (!alreadyAddedNodes.contains(rightTask.label()))
				{
					alreadyAddedNodes.add(rightTask.label());
					exclusiveSplit.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(rightTask.label())));
				}
			}

			if (exclusiveSplit.hasChildren())
			{
				initialNode.addChild(exclusiveSplit);
			}

			for (AbstractSyntaxTree choice : this.explicitParallels)
			{
				final AbstractSyntaxNode leftTask = choice.root().successors().get(0);
				final AbstractSyntaxNode rightTask = choice.root().successors().get(1);

				if (!alreadyAddedNodes.contains(leftTask.label()))
				{
					alreadyAddedNodes.add(leftTask.label());
					initialNode.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(leftTask.label())));
				}
				if (!alreadyAddedNodes.contains(rightTask.label()))
				{
					alreadyAddedNodes.add(rightTask.label());
					initialNode.addChildAndForceParent(new Node(BpmnProcessFactory.generateTask(rightTask.label())));
				}
			}
		}

		dependencyGraph.addInitialNode(initialNode);
		return this.convertToBpmnV1(dependencyGraph);
	}

	private Graph generateProcessWithStandardProcedure()
	{
		//Compute all nodes and reachabilities
		this.computeReachableNodes();

		//Store all expected mutual exclusions, along with the impossible ones (i.e., the ones that are incompatible with some sequential constraints)
		this.computeChoicesInformation();
		this.computeParallelsInformation();

		//Add all the choices that contain tasks not belonging to the graph
		this.addAllIncompleteChoices();
		final HashSet<Node> synchroNodes = this.dependencyGraph.synchronizeFlowsV2();

		//this.assertMutualExclusions(this.dependencyGraph);

		//Add real loops
		this.manageExplicitLoops();
		final HashSet<Node> synchroNodes2 = this.dependencyGraph.synchronizeFlowsV2();

		System.out.println("The dependency graph contains the following nodes: " + this.dependencyGraph.toSet());
		System.out.println();

		System.out.println("The dependency graph contains the following paths:\n");
		for (Path<Node> path : this.computeAllPaths(this.dependencyGraph))
		{
			System.out.println("- " + path.toString());
		}

		//Compute all the mutually exclusive nodes
		this.computeAllMutuallyExclusiveNodes();

		System.out.println();
		System.out.println("The dependency graph contains the following mutually exclusive nodes:\n");

		for (Node node : this.realMutuallyExclusiveNodes.keySet())
		{
			final HashSet<Node> mutuallyExclusiveNodes = this.realMutuallyExclusiveNodes.get(node);

			if (!mutuallyExclusiveNodes.isEmpty())
			{
				System.out.println("- " + node.bpmnObject().name() + " is mutually exclusive with " + mutuallyExclusiveNodes.toString());
			}
		}

		//Compute all parallelisable splits
		final HashSet<Node> synchroNodes3 = this.dependencyGraph.synchronizeFlowsV2();
		this.markParallelisableSplitsV2();

		System.out.println();
		System.out.println("The dependency graph contains the following parallelisable splits:\n");

		for (Node node : this.dependencyGraph.toSet())
		{
			if (node.isSplit())
			{
				final HashSet<Node> parallelisableNodes = node.parallelisableNodes();

				if (!parallelisableNodes.isEmpty())
				{
					System.out.println("- Children " + parallelisableNodes + " of " + node.bpmnObject().name());
				}
			}
		}

		System.out.println();
		System.out.println("Final graph before conversion:\n\n" + this.dependencyGraph.toString());

		return this.convertToBpmnV1().cleanDummyTasks().cleanGateways();
	}

	private ArrayList<Path<Node>> computeAllPaths(final DependencyGraph dependencyGraph)
	{
		final ArrayList<Path<Node>> allPaths = new ArrayList<>();

		for (Node initialNode : dependencyGraph.initialNodes())
		{
			final Path<Node> path = new Path<>();
			//path.add(initialNode);
			allPaths.add(path);
			this.computeAllPaths(new HashSet<>(), allPaths, path, initialNode);
		}

		return allPaths;
	}

	private void computeAllPaths(final HashSet<Node> visitedNodes,
								 final ArrayList<Path<Node>> allPaths,
								 final Path<Node> currentPath,
								 final Node currentNode)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);
		currentPath.add(currentNode);

		final ArrayList<Path<Node>> nextPaths = new ArrayList<>();
		final ArrayList<HashSet<Node>> nextVisitedNodes = new ArrayList<>();

		for (int i = 1; i < currentNode.childNodes().size(); i++)
		{
			final Path<Node> nextPath = currentPath.copy();
			allPaths.add(nextPath);
			nextPaths.add(nextPath);
			nextVisitedNodes.add(new HashSet<>(visitedNodes));
		}

		boolean first = true;
		int nextIndex = 0;

		for (Node child : currentNode.childNodes())
		{
			if (first)
			{
				first = false;
				this.computeAllPaths(visitedNodes, allPaths, currentPath, child);
			}
			else
			{
				this.computeAllPaths(nextVisitedNodes.get(nextIndex), allPaths, nextPaths.get(nextIndex), child);
				nextIndex++;
			}
		}
	}

	private ArrayList<HashSet<Node>> buildBiggestDisjointLoops()
	{
		final ArrayList<HashSet<Node>> disjointLoops = new ArrayList<>(this.explicitLoops);
		int nbDisjointLoops;

		do
		{
			nbDisjointLoops = disjointLoops.size();
			final HashSet<Node> mergedLoop = new HashSet<>();
			int i;
			int j = 0;

			for (i = 0; i < disjointLoops.size(); i ++)
			{
				final HashSet<Node> disjointLoop1 = disjointLoops.get(i);

				for (j = i + 1; j < disjointLoops.size(); j++)
				{
					final HashSet<Node> disjointLoop2 = disjointLoops.get(j);

					if (Utils.intersectionIsNotEmpty(disjointLoop1, disjointLoop2))
					{
						mergedLoop.addAll(disjointLoop1);
						mergedLoop.addAll(disjointLoop2);
						break;
					}
				}

				if (!mergedLoop.isEmpty())
				{
					break;
				}
			}

			if (!mergedLoop.isEmpty())
			{
				disjointLoops.remove(i);
				disjointLoops.remove(i < j ? j - 1 : j);
				disjointLoops.add(new HashSet<>(mergedLoop));
			}
		}
		while (nbDisjointLoops != disjointLoops.size());

		return disjointLoops;
	}

	private void computeAllNodes(final Node currentNode,
								 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.computeAllNodes(child, visitedNodes);
		}
	}

	private void computeAllMutuallyExclusiveNodes()
	{
		// We initialise the map with all the possible nodes.
		for (Node node : this.dependencyGraph.toSet())
		{
			this.realMutuallyExclusiveNodes.put(node, new HashSet<>(this.dependencyGraph.toSet()));
		}

		if (MUTUAL_EXCLUSION == MutualExclusionMode.WEAK_MUTUAL_EXCLUSION)
		{
			/*
				Most common case where two tasks are considered to be mutually exclusive if there is no path
				containing both tasks.
				Computing this basically consists in iterating over the paths once and removing the whole path
				from the list of mutually exclusive nodes for each task found in the path.
			 */
			for (Path<Node> path : this.computeAllPaths(this.dependencyGraph))
			{
				for (Node node : path.unsortedElements())
				{
					this.realMutuallyExclusiveNodes.get(node).removeAll(path.unsortedElements());
				}
			}
		}
		else
		{
			//TODO
		}
	}

	private void computeReachableNodes()
	{
		this.reachableNodes.clear();

		if (REACHABILITY_ANALYSIS_MODE == ReachabilityAnalysisMode.NAIVE_REACHABILITY_ANALYSIS)
		{
			for (Node node : this.dependencyGraph.toSet())
			{
				final HashSet<Node> reachableNodes = this.reachableNodes.computeIfAbsent(node, h -> new HashSet<>());
				this.computeReachableNodes(node, reachableNodes);
			}
		}
		else
		{
			//TODO
		}
	}

	private void computeReachableNodes(final Node currentNode,
									   final HashSet<Node> reachableNodes)
	{
		if (reachableNodes.contains(currentNode))
		{
			return;
		}

		reachableNodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.computeReachableNodes(child, reachableNodes);
		}
	}

	/*
		This method is used to compute whether a xor split can be (at least partially)
		converted into a parallel split.
		The only constraints to do so are:
			1) Do not violate mutual exclusion constraints
			2) Do not create anti-patterns
		Constraint 1) is ensured by the verification of the nodes involved in the parallel gateway in construction
		Constraint 2) is ensured by the analysis of the tokens emitted by the split being analysed
	 */
	private void markParallelisableSplits()
	{
		//To handle properly the initial nodes, we add a dummy node before them
		/*if (this.dependencyGraph.initialNodes().size() > 1)
		{
			final Node dummyInitial = new Node(BpmnProcessFactory.generateTask("DUMMY_TASK_" + Utils.generateRandomIdentifier()));

			for (Node initialNode : this.dependencyGraph.initialNodes())
			{
				dummyInitial.addChildAndForceParent(initialNode);
			}

			this.dependencyGraph.initialNodes().clear();
			this.dependencyGraph.addInitialNode(dummyInitial);
			this.allNodes.add(dummyInitial);
			this.allNodeIds.add(dummyInitial.bpmnObject().id());
		}*/

		boolean changed = true;

		while (changed)
		{
			//First, retrieve all the information about the split nodes of the process
			final HashMap<Node, Pair<Graph, HashMap<Node, Pair<Node, Boolean>>>> splitInformation = new HashMap<>();

			for (Node node : this.dependencyGraph.toSet())
			{
				if (node.isSplit()
					&& node.bpmnObject().type() != BpmnProcessType.PARALLEL_GATEWAY)
				{
					splitInformation.put(node, this.tryToParallelizeV2(node));
				}
			}

			//Then, modify the original graph to add these information
			changed = this.adaptGraph(splitInformation).isEmpty();
		}
	}

	private void markParallelisableSplitsV2()
	{
		//First, retrieve all the information about the split nodes of the process
		final HashMap<Node, Pair<Graph, HashMap<Node, Pair<Node, Boolean>>>> splitInformation = new HashMap<>();

		for (Node node : this.dependencyGraph.toSet())
		{
			if (node.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
				&& ((Gateway) node.bpmnObject()).isSplitGateway())
			{
				splitInformation.put(node, this.tryToParallelizeV2(node));
			}
		}

		//Then we add the splits/merges to the graph
		final HashMap<Node, Node> mandatoryMergePerSplit = this.adaptGraph(splitInformation);

		System.out.println("Dep graph after inserting parallel splits:\n\n" + this.dependencyGraph.toString());

		//if (true) throw new RuntimeException();

		//Then, we analyse each remaining exclusive merge to see whether it can (at least partially) be transformed into a parallel merge
		final HashMap<Node, Collection<Collection<Node>>> possibleCombinationsOfMerges = this.getPossibleCombinationsOfMerges();
		System.out.println("Possible combinations of merges: " + possibleCombinationsOfMerges);

		//Then, we create all the graphs resulting from the cartesian product of these combinations
		final ArrayList<DependencyGraph> possibleGraphs = this.buildPossibleGraphs(possibleCombinationsOfMerges);

		for (DependencyGraph dependencyGraph1 : possibleGraphs)
		{
			System.out.println("Possible graph before:\n\n" + dependencyGraph1.toString());
		}

		if (possibleGraphs.isEmpty()) throw new RuntimeException("No graph was generated during the combination phase!");

		/*if (true)
		{
			System.out.println("Found " + possibleGraphs.size() + " possible graphs.");
			throw new RuntimeException();
		}*/

		this.dependencyGraph = this.buildFinalGraph(possibleGraphs, mandatoryMergePerSplit);
	}

	private DependencyGraph buildFinalGraph(final ArrayList<DependencyGraph> possibleGraphs,
											final HashMap<Node, Node> mandatorySplitPerMerge)
	{
		int bestScore = -1;
		int bestNbParallelGateways = -1;
		DependencyGraph bestGraph = null;

		for (final DependencyGraph possibleGraph : possibleGraphs)
		{
			System.out.println("Iterating");
			final Graph graph = this.convertToBpmnV1(possibleGraph.weakCopy());
			final BpmnDeadlockDetector deadlockDetector = new BpmnDeadlockDetector(graph);
			deadlockDetector.detectDeadlocksAndLivelocks();

			if (bestGraph != null)
			{
				final int parallelismScore = this.computeParallelismScore(possibleGraph);
				final int nbParallelGateways = this.nbParallelGateways(possibleGraph);

				if (parallelismScore < bestScore
					|| (parallelismScore == bestScore
						&& nbParallelGateways <= bestNbParallelGateways))
				{
					continue;
				}
			}

			if (deadlockDetector.simulationEndedWithDeadlocksOrLivelocks())
			{
				//We remove one parallel gateway from the graph
				final ArrayList<DependencyGraph> graphsWithOneLessParallelElements = this.getGraphsWithOneLessParallelElement(possibleGraph, mandatorySplitPerMerge);

				if (graphsWithOneLessParallelElements.isEmpty())
				{
					System.out.println("The following graph should not contain any deadlock/livelock:\n\n" + possibleGraph);
					System.out.println("But contains one " + (deadlockDetector.getFailySimulations().get(0).hasDeadlock() ? "deadlock" : "livelock") + ": " + deadlockDetector.getFailySimulations().get(0));
					return possibleGraph;
				}

				final DependencyGraph finalGraph = this.buildFinalGraph(graphsWithOneLessParallelElements, mandatorySplitPerMerge);
				final int finalScore = this.computeParallelismScore(finalGraph);
				final int finalNbParallelGateways = this.nbParallelGateways(finalGraph);

				if (bestGraph == null)
				{
					bestGraph = finalGraph;
					bestScore = finalScore;
					bestNbParallelGateways = finalNbParallelGateways;
				}
				else
				{
					if (finalScore > bestScore
						|| (finalScore == bestScore
							&& finalNbParallelGateways > bestNbParallelGateways))
					{
						bestGraph = finalGraph;
						bestScore = finalScore;
						bestNbParallelGateways = finalNbParallelGateways;
					}
				}
			}
			else
			{
				//We keep the graph and break the recursive chain
				final int parallelismScore = this.computeParallelismScore(possibleGraph);
				final int nbParallelGateways = this.nbParallelGateways(possibleGraph);

				if (bestGraph == null)
				{
					bestGraph = possibleGraph;
					bestScore = parallelismScore;
					bestNbParallelGateways = nbParallelGateways;
				}
				else
				{
					if (parallelismScore > bestScore
						|| (parallelismScore == bestScore
						&& nbParallelGateways > bestNbParallelGateways))
					{
						bestGraph = possibleGraph;
						bestScore = parallelismScore;
						bestNbParallelGateways = nbParallelGateways;
					}
				}
			}
		}

		if (bestGraph == null) throw new RuntimeException();

		return bestGraph;
	}

	private ArrayList<DependencyGraph> getGraphsWithOneLessParallelElement(final DependencyGraph dependencyGraph,
																		   final HashMap<Node, Node> mandatorySplitPerMerge)
	{
		final ArrayList<DependencyGraph> resultingGraphs = new ArrayList<>();
		final HashSet<Node> graphNodes = dependencyGraph.toSet();

		for (Node node : graphNodes)
		{
			if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
			{
				final Gateway gateway = (Gateway) node.bpmnObject();
				final DependencyGraph copy = dependencyGraph.deepCopy();
				resultingGraphs.add(copy);

				((Gateway) copy.getNodeFromID(gateway.id()).bpmnObject()).switchToGatewayType(BpmnProcessType.EXCLUSIVE_GATEWAY);

				if (gateway.isMergeGateway())
				{
					final Node correspondingSplit = mandatorySplitPerMerge.get(node);

					if (correspondingSplit != null)
					{
						((Gateway) copy.getNodeFromID(correspondingSplit.bpmnObject().id()).bpmnObject()).switchToGatewayType(BpmnProcessType.EXCLUSIVE_GATEWAY);
					}
				}
			}
		}

		return resultingGraphs;
	}

	private Triple<DependencyGraph, Integer, Integer> electBestGraph(final ArrayList<DependencyGraph> possibleGraphs)
	{
		int bestScore = -1;
		int bestNbParallelGateways = -1;
		DependencyGraph bestGraph = null;

		for (DependencyGraph dependencyGraph : possibleGraphs)
		{
			final int parallelismScore = this.computeParallelismScore(dependencyGraph);
			final int nbParallelGateways = this.nbParallelGateways(dependencyGraph);

			if (bestGraph == null)
			{
				bestGraph = dependencyGraph;
				bestScore = parallelismScore;
				bestNbParallelGateways = nbParallelGateways;
			}
			else
			{
				if (parallelismScore > bestScore
					|| (parallelismScore == bestScore
						&& nbParallelGateways > bestNbParallelGateways))
				{
					bestGraph = dependencyGraph;
					bestScore = parallelismScore;
					bestNbParallelGateways = nbParallelGateways;
				}
			}
		}

		assert bestGraph != null;
		return new Triple<>(bestGraph, bestScore, bestNbParallelGateways);
	}

	private Triple<DependencyGraph, Integer, Integer> electBestGraph(final DependencyGraph... possibleGraphs)
	{
		int bestScore = -1;
		int bestNbParallelGateways = -1;
		DependencyGraph bestGraph = null;

		for (DependencyGraph dependencyGraph : possibleGraphs)
		{
			final int parallelismScore = this.computeParallelismScore(dependencyGraph);
			final int nbParallelGateways = this.nbParallelGateways(dependencyGraph);

			if (bestGraph == null)
			{
				bestGraph = dependencyGraph;
				bestScore = parallelismScore;
				bestNbParallelGateways = nbParallelGateways;
			}
			else
			{
				if (parallelismScore > bestScore
						|| (parallelismScore == bestScore
						&& nbParallelGateways > bestNbParallelGateways))
				{
					bestGraph = dependencyGraph;
					bestScore = parallelismScore;
					bestNbParallelGateways = nbParallelGateways;
				}
			}
		}

		assert bestGraph != null;
		return new Triple<>(bestGraph, bestScore, bestNbParallelGateways);
	}

	private int nbParallelGateways(final DependencyGraph graph)
	{
		return this.getParallelGatewaysOf(graph).size();
	}

	private HashSet<Node> getParallelGatewaysOf(final DependencyGraph dependencyGraph)
	{
		final HashSet<Node> parallelGateways = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node child : dependencyGraph.initialNodes())
		{
			this.getParallelGatewaysOf(child, parallelGateways, visitedNodes);
		}

		return parallelGateways;
	}

	private void getParallelGatewaysOf(final Node currentNode,
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
			this.getParallelGatewaysOf(child, parallelGateways, visitedNodes);
		}
	}

	/**
	 *
	 *
	 * @param dependencyGraph
	 * @return
	 */
	private int computeParallelismScore(final DependencyGraph dependencyGraph)
	{
		final HashSet<Node> nodes = dependencyGraph.toSet();
		int parallelismScore = 0;

		for (Node node : nodes)
		{
			if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
				&& ((Gateway) node.bpmnObject()).isSplitGateway())
			{
				final ArrayList<Node> children = new ArrayList<>(node.childNodes());

				for (int i = 0; i < children.size(); i++)
				{
					final Node node1 = children.get(i);
					final ArrayList<Path<Node>> pathsFromNode1 = this.getAllPathsWithoutLoopsStartingFrom(node1);

					for (int j = i + 1; j < children.size(); j++)
					{
						final Node node2 = children.get(j);
						final ArrayList<Path<Node>> pathsFromNode2 = this.getAllPathsWithoutLoopsStartingFrom(node2);
						final HashSet<Node> commonNodes = this.getCommonNodes(pathsFromNode1, pathsFromNode2);
						final int nbParallelTasksPath1 = this.computeNumberOfTasksUpTo(commonNodes, pathsFromNode1);
						final int nbParallelTasksPath2 = this.computeNumberOfTasksUpTo(commonNodes, pathsFromNode2);
						parallelismScore += nbParallelTasksPath1 * nbParallelTasksPath2;
					}
				}
			}
		}

		return parallelismScore;
	}

	final HashSet<Node> getCommonNodes(final ArrayList<Path<Node>> paths1,
									   final ArrayList<Path<Node>> paths2)
	{
		final HashSet<Node> commonNodes = new HashSet<>();

		for (Path<Node> path1 : paths1)
		{
			for (Path<Node> path2 : paths2)
			{
				commonNodes.addAll(Utils.getIntersectionOf(path1.elements(), path2.elements()));
			}
		}

		return commonNodes;
	}

	final int computeNumberOfTasksUpTo(final HashSet<Node> bounds,
									   final ArrayList<Path<Node>> paths)
	{
		int nbTasks = 0;

		for (Path<Node> path : paths)
		{
			for (Node node : path.elements())
			{
				if (bounds.contains(node))
				{
					break;
				}

				if (node.bpmnObject().type() == BpmnProcessType.TASK)
				{
					nbTasks++;
				}
			}
		}

		return nbTasks;
	}

	/**
	 * This method is used to alter the structure of the graph by replacing 0 or 1 parallel merge gateway (one
	 * creating a deadlock in the process) by an exclusive merge gateway, and all the parallel split gateways
	 * creating livelocks by exclusive splits.
	 *
	 * @param simulationInfo
	 * @param graph
	 * @return
	 */
	private boolean adaptGraph(final DeadlockDetector.SimulationInfo simulationInfo,
							   final DependencyGraph graph,
							   final HashMap<Node, Node> mandatoryMergePerSplit)
	{
		boolean graphChanged = false;
		final Node nonTriggerableMergeGateway = simulationInfo.getNonTriggerableMergeIfAny();

		if (nonTriggerableMergeGateway != null)
		{
			graphChanged = true;
			((Gateway) nonTriggerableMergeGateway.bpmnObject()).switchToGatewayType(BpmnProcessType.EXCLUSIVE_GATEWAY);

			if (mandatoryMergePerSplit.containsKey(nonTriggerableMergeGateway))
			{
				final Node splitToSwitch = mandatoryMergePerSplit.get(nonTriggerableMergeGateway);
				((Gateway) splitToSwitch.bpmnObject()).switchToGatewayType(BpmnProcessType.EXCLUSIVE_GATEWAY);
			}
		}

		for (Node node : graph.toSet())
		{
			if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
				&& ((Gateway) node.bpmnObject()).isSplitGateway())
			{
				if (this.gatewayCreatesInfiniteRecursion(node))
				{
					graphChanged = true;
					((Gateway) node.bpmnObject()).switchToGatewayType(BpmnProcessType.EXCLUSIVE_GATEWAY);
				}
			}
		}

		return graphChanged;
	}

	private boolean gatewayCreatesInfiniteRecursion(final Node parallelSplit)
	{
		final ArrayList<Path<Node>> pathsFromParallelSplit = getAllPathsWithoutLoopsStartingFrom(parallelSplit);
		final ArrayList<Node> synchroPoints = this.getSynchroPointsOf(pathsFromParallelSplit);

		if (synchroPoints.isEmpty())
		{
			//The children paths of the split are not synchronised, and it can reach itself => livelock
			return parallelSplit.hasSuccessor(parallelSplit);
		}
		else
		{
			for (Node synchroPoint : synchroPoints)
			{
				if (parallelSplit.mayReachItselfBefore(synchroPoint))
				{
					//The children paths of the split are synchronised, but the split can reach itself before the synchro => livelock
					return true;
				}

				if (synchroPoint.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
					&& ((Gateway) synchroPoint.bpmnObject()).isMergeGateway())
				{
					return parallelSplit.mayReachItselfBefore(synchroPoint);
				}
			}
		}

		//No synchro point is a parallel merge => livelock
		return true;
	}

	private ArrayList<DependencyGraph> buildPossibleGraphs(final HashMap<Node, Collection<Collection<Node>>> possibleCombinations)
	{
		final ArrayList<DependencyGraph> possibleGraphs = new ArrayList<>();
		possibleGraphs.add(this.dependencyGraph);

		for (Node exclusiveMerge : possibleCombinations.keySet())
		{
			if (possibleCombinations.get(exclusiveMerge).isEmpty()) continue;

			final ArrayList<DependencyGraph> nextGraphs = new ArrayList<>();

			for (DependencyGraph possibleGraph : possibleGraphs)
			{
				for (Collection<Node> possibleCombination : possibleCombinations.get(exclusiveMerge))
				{
					final DependencyGraph newGraph = possibleGraph.deepCopy();
					nextGraphs.add(newGraph);

					final Node newExclusiveMerge = newGraph.getNodeFromName(exclusiveMerge.bpmnObject().name());

					if (possibleCombination.size() == exclusiveMerge.parentNodes().size())
					{
						//The combination is all the parents => just switch the type of the gateway
						((Gateway) newExclusiveMerge.bpmnObject()).switchToGatewayType(BpmnProcessType.PARALLEL_GATEWAY);
					}
					else
					{
						//The combination is part of the parents => connect these parents to a parallel merge and this merge to the exclusive one
						final Node newParallelMerge = new Node(BpmnProcessFactory.generateParallelMergeGateway());
						newParallelMerge.addChildAndForceParent(newExclusiveMerge);

						for (Node exclusiveMergeParentToDisconnect : possibleCombination)
						{
							final Node newExclusiveMergeParentToDisconnect = newGraph.getNodeFromName(exclusiveMergeParentToDisconnect.bpmnObject().name());
							newExclusiveMergeParentToDisconnect.removeChildAndForceParent(newExclusiveMerge);
							newExclusiveMergeParentToDisconnect.addChildAndForceParent(newParallelMerge);
						}
					}

					System.out.println("New graph:\n\n" + newGraph);
				}
			}

			possibleGraphs.clear();
			possibleGraphs.addAll(nextGraphs);
		}

		return possibleGraphs;
	}

	private HashMap<Node, Collection<Collection<Node>>> getPossibleCombinationsOfMerges()
	{
		final HashMap<Node, Collection<Collection<Node>>> possibleCombinations = new HashMap<>();

		//Retrieve all exclusive merges
		final HashMap<Node, HashSet<Node>> candidateParentsPerNode = new HashMap<>();

		for (Node node : this.dependencyGraph.toSet())
		{
			if (node.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
				&& ((Gateway) node.bpmnObject()).isMergeGateway())
			{
				candidateParentsPerNode.put(node, new HashSet<>(node.parentNodes()));
			}
		}

		/*
			Compute all combinations of merges that are valid candidates.
			In particular, discard all combinations in which:
				1) The closest common ancestor split gateway of the parent nodes is null or is not a parallel gateway
				2) A parent node is not reachable from the initial node(s)
		 */

		for (Node exclusiveMerge : candidateParentsPerNode.keySet())
		{
			for (Node parent : exclusiveMerge.parentNodes())
			{
				final Node closestCommonAncestorSplit = this.getClosestCommonAncestorSplit(parent.parentNodes());

				if (closestCommonAncestorSplit == null
					|| (closestCommonAncestorSplit.bpmnObject().type() != BpmnProcessType.PARALLEL_GATEWAY))
				{
					//TODO: See if works (maybe it can remove some valid candidates)
					System.out.println("Parent \"" + parent.bpmnObject().name() + "\" does not (necessarily) come from a parallel split");
					candidateParentsPerNode.get(exclusiveMerge).remove(parent);
				}
			}

			System.out.println("Candidates before: " + candidateParentsPerNode.get(exclusiveMerge));
			candidateParentsPerNode.get(exclusiveMerge).removeAll(this.exclusiveMergeFlowIsUnreachable(exclusiveMerge));
			System.out.println("Candidates after: " + candidateParentsPerNode.get(exclusiveMerge));
		}

		for (Node exclusiveMerge : candidateParentsPerNode.keySet())
		{
			final HashSet<Node> eligibleCandidates = candidateParentsPerNode.get(exclusiveMerge);
			final Collection<Collection<Node>> combinations = Utils.getCombinationsOf(eligibleCandidates);
			combinations.removeIf(collection -> collection.size() < 2);
			System.out.println("Combinations of \"" + exclusiveMerge.bpmnObject().name() + "\" : " + combinations);
			possibleCombinations.put(exclusiveMerge, combinations);
		}

		System.out.println("Found " + possibleCombinations.size() + " possible combinations.");

		return possibleCombinations;
	}

	private HashSet<Node> exclusiveMergeFlowIsUnreachable(final Node exclusiveMerge)
	{
		final HashSet<Node> unreachableFlows = new HashSet<>();
		final ArrayList<Path<Node>> allPaths = this.computeAllPaths(this.dependencyGraph);
		System.out.println("Dependency graph:\n\n" + dependencyGraph.toString());
		System.out.println(allPaths);
		final HashSet<Node> lastParallelMerges = new HashSet<>();
		int lastParallelMergesNumber;
		final HashSet<Node> triggerableMerges = new HashSet<>();

		do
		{
			lastParallelMergesNumber = lastParallelMerges.size();

			for (Path<Node> path : allPaths)
			{
				for (Node node : path.elements())
				{
					if (node.equals(exclusiveMerge))
					{
						lastParallelMerges.add(node);
						break;
					}
					else if (!triggerableMerges.contains(node)
							&& node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
							&& ((Gateway) node.bpmnObject()).isMergeGateway())
					{
						lastParallelMerges.add(node);
						break;
					}
				}
			}

			final HashSet<Node> mergesToConsider = new HashSet<>(lastParallelMerges);
			int nbTriggerableMerges;

			do
			{
				nbTriggerableMerges = triggerableMerges.size();
				mergesToConsider.removeAll(triggerableMerges);

				for (Node mergeToConsider : mergesToConsider)
				{
					boolean mergeIsTriggerable = true;

					for (Node parent : mergeToConsider.parentNodes())
					{
						boolean parentCanBeReached = false;

						for (Path<Node> path : allPaths)
						{
							if (path.containsBeforeAParallelMerge(parent, triggerableMerges))
							{
								parentCanBeReached = true;
								break;
							}
						}

						if (!parentCanBeReached)
						{
							mergeIsTriggerable = false;
							break;
						}
					}

					if (mergeIsTriggerable)
					{
						triggerableMerges.add(mergeToConsider);
					}
				}
			}
			while (nbTriggerableMerges != triggerableMerges.size());
		}
		while (lastParallelMergesNumber != lastParallelMerges.size());

		/*
			triggerableMerges now contain all the triggerable merges of the process.
			If flowToCheck is not in any path truncated at the lastParallelMerges, then it is not reachable.
		 */

		for (Node flowToCheck : exclusiveMerge.parentNodes())
		{
			boolean flowFound = false;

			for (Path<Node> path : allPaths)
			{
				if (path.containsBeforeAParallelMerge(flowToCheck, triggerableMerges))
				{
					flowFound = true;
					break;
				}
			}

			if (!flowFound)
			{
				unreachableFlows.add(flowToCheck);
			}
		}

		return unreachableFlows;
	}

	private Node getClosestCommonAncestorSplit(final Collection<Node> nodes)
	{
		//Remove all nodes being successors of others
		final HashSet<Node> successorsToRemove = new HashSet<>();

		for (Node node1 : nodes)
		{
			for (Node node2 : nodes)
			{
				if (!node1.equals(node2))
				{
					if (node1.hasSuccessor(node2))
					{
						successorsToRemove.add(node2);
					}
				}
			}
		}

		final HashSet<Node> nodesToKeep = new HashSet<>(nodes);
		nodesToKeep.removeAll(successorsToRemove);

		if (nodesToKeep.isEmpty())
		{
			//Happens for loops, is it an issue?
			return null;
		}
		else if (nodesToKeep.size() == 1)
		{
			Node closestAncestorSplit = nodesToKeep.iterator().next();

			while (closestAncestorSplit != null
				&& (!(closestAncestorSplit.bpmnObject() instanceof Gateway)
				|| !((Gateway) closestAncestorSplit.bpmnObject()).isSplitGateway()))
			{
				closestAncestorSplit = closestAncestorSplit.hasParents() ? closestAncestorSplit.parentNodes().iterator().next() : null;
			}

			return closestAncestorSplit;
		}
		else
		{
			Node pivot = nodesToKeep.iterator().next();
			nodesToKeep.remove(pivot);

			for (Node node : nodesToKeep)
			{
				while (!node.hasAncestor(pivot))
				{
					pivot = pivot.hasParents() ? pivot.parentNodes().iterator().next() : null;

					if (pivot == null)
					{
						throw new RuntimeException();
					}
				}
			}

			Node closestAncestorSplit = pivot;

			while (closestAncestorSplit != null
					&& (!(closestAncestorSplit.bpmnObject() instanceof Gateway)
					|| !((Gateway) closestAncestorSplit.bpmnObject()).isSplitGateway()))
			{
				closestAncestorSplit = closestAncestorSplit.hasParents() ? closestAncestorSplit.parentNodes().iterator().next() : null;
			}

			return closestAncestorSplit;
		}
	}

	private Pair<Graph, HashMap<Node, Pair<Node, Boolean>>> tryToParallelizeV2(final Node node)
	{
		final ArrayList<Path<Node>> pathsWithoutLoops = this.getAllPathsWithoutLoopsStartingFrom(node);
		System.out.println("Paths without loops:\n" + pathsWithoutLoops);
		System.out.println();
		final HashMap<Node, ArrayList<Path<Node>>> pathsPerNode = new HashMap<>();

		//Retrieve all the paths starting with child nodes of the current nodes and sort them by starting node
		for (Node child : node.childNodes())
		{
			for (Path<Node> path : pathsWithoutLoops)
			{
				if (path.get(1).equals(child))
				{
					System.out.println("Path " + path + " belongs to node \"" + child.bpmnObject().name() + "\".");
					final ArrayList<Path<Node>> pathsList = pathsPerNode.computeIfAbsent(child, a -> new ArrayList<>());
					final Path<Node> pathCopy = path.copy();
					final Node firstNode = pathCopy.removeFirst();

					if (node.hasSuccessor(firstNode))
					{
						pathCopy.add(firstNode);
					}

					pathsList.add(pathCopy);
				}
			}
		}

		System.out.println("Paths per node:\n" + pathsPerNode);
		System.out.println();

		//Compute the minimum parallelisable nodes for each two couple of child nodes of node
		final HashMap<HashSet<Node>, Pair<HashSet<Node>, HashSet<Node>>> minimumParallelisableNodesPerPairOfNodes = new HashMap<>();

		for (Node node1 : pathsPerNode.keySet())
		{
			for (Node node2 : pathsPerNode.keySet())
			{
				if (!node1.equals(node2))
				{
					final HashSet<Node> currentCouple = new HashSet<>();
					currentCouple.add(node1);
					currentCouple.add(node2);

					if (minimumParallelisableNodesPerPairOfNodes.containsKey(currentCouple)) continue;

					final ArrayList<Path<Node>> allPaths = new ArrayList<>();
					allPaths.addAll(pathsPerNode.get(node1));
					allPaths.addAll(pathsPerNode.get(node2));

					final Triple<Node, Boolean, Boolean> synchroPoint = this.getSynchroPoint(allPaths, node, currentCouple);
					final Node bound;

					if (synchroPoint != null
						&& synchroPoint.second()
						&& synchroPoint.first() != null)
					{
						/*
							If both nodes synchronise at some point, the minimum nodes impacted by putting
							them in parallel are the ones between them and synchroPoint.
						 */
						bound = synchroPoint.first();
					}
					else
					{
						/*
							If the two nodes do not synchronise, the minimum nodes impacted by putting them
							in parallel are all the nodes that they can reach.
						 */
						bound = null;
					}

					final HashSet<Node> impactedNodes1 = node1.getReachableNodesUpTo(bound);
					System.out.println("Reachable nodes of \"" + node1.bpmnObject().name() + "\" up to \"" + (bound == null ? null : bound.bpmnObject().name()) + ": " + impactedNodes1);
					final HashSet<Node> impactedNodes2 = node2.getReachableNodesUpTo(bound);
					System.out.println("Reachable nodes of \"" + node2.bpmnObject().name() + "\" up to \"" + (bound == null ? null : bound.bpmnObject().name()) + ": " + impactedNodes2);

					minimumParallelisableNodesPerPairOfNodes.put(currentCouple, new Pair<>(impactedNodes1, impactedNodes2));
				}
			}
		}

		System.out.println();
		System.out.println("Minimum parallelisable nodes per couple:\n" + minimumParallelisableNodesPerPairOfNodes);
		System.out.println();

		//Compute the set of non parallelisable nodes of each child node
		final HashMap<Node, HashSet<Node>> nonParallelisableNodes = new HashMap<>();

		for (HashSet<Node> couple : minimumParallelisableNodesPerPairOfNodes.keySet())
		{
			final Pair<HashSet<Node>, HashSet<Node>> impactedNodes = minimumParallelisableNodesPerPairOfNodes.get(couple);

			for (Node node1 : impactedNodes.first())
			{
				for (Node node2 : impactedNodes.second())
				{
					if (this.mutualExclusionsToEnsure.containsKey(node1.bpmnObject().id()))
					{
						if (this.mutualExclusionsToEnsure.get(node1.bpmnObject().id()).contains(node2.bpmnObject().id()))
						{
							final HashSet<Node> hashSet1 = nonParallelisableNodes.computeIfAbsent(node1, h -> new HashSet<>());
							final HashSet<Node> hashSet2 = nonParallelisableNodes.computeIfAbsent(node2, h -> new HashSet<>());
							hashSet1.add(node2);
							hashSet2.add(node1);
						}
					}
					else if (this.mutualExclusionsToEnsure.containsKey(node2.bpmnObject().id()))
					{
						if (this.mutualExclusionsToEnsure.get(node2.bpmnObject().id()).contains(node1.bpmnObject().id()))
						{
							final HashSet<Node> hashSet1 = nonParallelisableNodes.computeIfAbsent(node1, h -> new HashSet<>());
							final HashSet<Node> hashSet2 = nonParallelisableNodes.computeIfAbsent(node2, h -> new HashSet<>());
							hashSet1.add(node2);
							hashSet2.add(node1);
						}
					}
				}
			}
		}

		System.out.println("Non parallelisable nodes per node:\n" + nonParallelisableNodes);
		System.out.println();

		//Get the possible combinations of parallelism/mutual exclusion between all the child nodes.
		final ArrayList<Graph> possibleCombinations = this.getCombinationsOf(Utils.getCombinationsOf(node.childNodes()), nonParallelisableNodes);

		for (Iterator<Graph> iterator = possibleCombinations.iterator(); iterator.hasNext(); )
		{
			final Graph possibleCombination = iterator.next();

			for (Node child : node.childNodes())
			{
				if (!possibleCombination.hasNodeNamed(child.bpmnObject().name()))
				{
					System.out.println("Combination \"" + possibleCombination + "\" has been removed.");
					iterator.remove();
					break;
				}
			}
		}

		System.out.println("Combinations found:\n\n" + possibleCombinations);
		//possibleCombinations.removeIf(graph -> graph.nbTasks() < node.childNodes().size()); //Safety check

		/*
			Check every constructed graph to see whether it could be a valid representation of the final process,
			rank them by distance to the original constraints, and return the "most optimal" one.
		 */
		return this.verifyAndElectMostSuitableGraphAmong(node, possibleCombinations, pathsPerNode);
	}

	private HashMap<Node, Node> adaptGraph(final HashMap<Node, Pair<Graph, HashMap<Node, Pair<Node, Boolean>>>> splitInformation)
	{
		System.out.println("Dependency graph before adaptation:\n\n" + this.dependencyGraph.toString());

		final HashMap<Node, Node> mandatorySplitPerMerge = new HashMap<>();
		boolean changed = false;

		for (Node splitNode : splitInformation.keySet())
		{
			final HashMap<Node, Node> graphNodesToRealNodesCorrespondences = new HashMap<>();
			final Pair<Graph, HashMap<Node, Pair<Node, Boolean>>> dispositionAndParallelElements = splitInformation.get(splitNode);
			final Graph disposition = dispositionAndParallelElements.first();
			final HashMap<Node, Pair<Node, Boolean>> parallelElements = dispositionAndParallelElements.second();

			System.out.println("Disposition:\n\n" + disposition.toString());

			final HashSet<Node> gateways = this.getGatewaysOf(disposition, splitNode.childNodes());

			if (gateways.isEmpty()
				|| (gateways.size() == 1 && gateways.iterator().next().bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY))
			{
				//The current split has nothing to be put in parallel => just go to the next one
				continue;
			}

			while (!gateways.isEmpty())
			{
				for (Iterator<Node> iterator = gateways.iterator(); iterator.hasNext(); )
				{
					final Node gateway = iterator.next();

					if (this.gatewayIsEligible(gateway, gateways))
					{
						//The gateway can be managed
						iterator.remove();

						final Node splitToAdd;

						if (gateway.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
						{
							splitToAdd = new Node(BpmnProcessFactory.generateExclusiveGateway());
						}
						else
						{
							splitToAdd = new Node(BpmnProcessFactory.generateParallelGateway());
						}

						graphNodesToRealNodesCorrespondences.put(gateway, splitToAdd);
						splitNode.addChildAndForceParent(splitToAdd);

						for (Node child : gateway.childNodes())
						{
							final Node realChild;

							if (graphNodesToRealNodesCorrespondences.containsKey(child))
							{
								realChild = graphNodesToRealNodesCorrespondences.get(child);
							}
							else
							{
								realChild = this.dependencyGraph.getNodeFromName(child.bpmnObject().name());
								//assert realChild.bpmnObject().type() == BpmnProcessType.TASK;
								graphNodesToRealNodesCorrespondences.put(child, realChild);
							}

							splitNode.removeChildAndForceParent(realChild);
							splitToAdd.addChildAndForceParent(realChild);
						}

						if (gateway.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
						{
							changed = true;

							//Add the merge if any
							final Node mergeNode = parallelElements.get(gateway).first();
							System.out.println("Merge node: " + mergeNode);

							if (mergeNode != null)
							{
								final Node realMergeNode;

								if (graphNodesToRealNodesCorrespondences.containsKey(mergeNode))
								{
									realMergeNode = graphNodesToRealNodesCorrespondences.get(mergeNode);
								}
								else
								{
									realMergeNode = this.dependencyGraph.getNodeFromName(mergeNode.bpmnObject().name());
									//assert realMergeNode.bpmnObject().type() == BpmnProcessType.TASK;
									graphNodesToRealNodesCorrespondences.put(mergeNode, realMergeNode);
								}

								final HashSet<Node> realChildren = new HashSet<>();

								for (Node child : gateway.childNodes())
								{
									if (graphNodesToRealNodesCorrespondences.containsKey(child))
									{
										realChildren.add(graphNodesToRealNodesCorrespondences.get(child));
									}
									else
									{
										final Node realNode = this.dependencyGraph.getNodeFromName(child.bpmnObject().name());
										//assert realNode.bpmnObject().type() == BpmnProcessType.TASK;
										graphNodesToRealNodesCorrespondences.put(child, realNode);
										realChildren.add(realNode);
									}
								}

								System.out.println("Real merge node: " + realMergeNode);
								System.out.println("Real children: " + realChildren);

								final HashSet<Node> parentsReachingNodes = realMergeNode.getParentsReaching(realChildren);
								final Node realMergeGateway = new Node(BpmnProcessFactory.generateParallelGateway(splitToAdd.bpmnObject().id().replace("Gateway_", "") + "_merge"));
								((Gateway) realMergeGateway.bpmnObject()).markAsMergeGateway();
								realMergeGateway.addChildAndForceParent(realMergeNode);

								mandatorySplitPerMerge.put(realMergeGateway, splitToAdd);

								for (Node parentReachingNodes : parentsReachingNodes)
								{
									parentReachingNodes.removeChildAndForceParent(realMergeNode);
									parentReachingNodes.addChildAndForceParent(realMergeGateway);
								}
							}
						}
					}
				}
			}
		}

		System.out.println("Dependency graph after adaptation:\n\n" + this.dependencyGraph.toString());

		this.removeUselessGateways();

		System.out.println("Dependency graph after useless gateways removal:\n\n" + this.dependencyGraph.toString());

		return mandatorySplitPerMerge;
	}

	//TODO Voir si besoin d'itérer
	private void removeUselessGateways()
	{
		final HashSet<Node> uselessGateways = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.dependencyGraph.initialNodes())
		{
			this.getUselessGateways(initialNode, visitedNodes, uselessGateways);
		}

		for (Node uselessGateway : uselessGateways)
		{
			final Gateway gateway = (Gateway) uselessGateway.bpmnObject();

			if (gateway.isSplitGateway())
			{
				if (uselessGateway.hasParents())
				{
					final HashSet<Node> parents = new HashSet<>(uselessGateway.parentNodes());
					final Node child = uselessGateway.childNodes().iterator().next();
					uselessGateway.removeChildAndForceParent(child);

					for (Node parent : parents)
					{
						parent.removeChildAndForceParent(uselessGateway);
						parent.addChildAndForceParent(child);
					}
				}
				else
				{
					for (Node child : uselessGateway.childNodes())
					{
						child.removeParent(uselessGateway);
						this.dependencyGraph.addInitialNode(child);
					}

					this.dependencyGraph.removeInitialNode(uselessGateway);
					uselessGateway.removeChildren();
				}
			}
			else
			{
				if (uselessGateway.hasChildren())
				{
					final HashSet<Node> children = new HashSet<>(uselessGateway.childNodes());
					final Node parent = uselessGateway.parentNodes().iterator().next();
					parent.removeChildAndForceParent(uselessGateway);

					for (Node child : children)
					{
						uselessGateway.removeChildAndForceParent(child);
						parent.addChildAndForceParent(child);
					}
				}
				else
				{
					for (Node parent : uselessGateway.parentNodes())
					{
						parent.removeChildren(uselessGateway);
						this.dependencyGraph.addEndNode(parent);
					}

					this.dependencyGraph.removeEndNode(uselessGateway);
					uselessGateway.removeParents();
				}
			}
		}
	}

	private void getUselessGateways(final Node currentNode,
									final HashSet<Node> visitedNodes,
									final HashSet<Node> uselessGateways)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject() instanceof Gateway)
		{
			final Gateway gateway = (Gateway) currentNode.bpmnObject();

			if (gateway.isSplitGateway())
			{
				if (currentNode.childNodes().size() == 1)
				{
					uselessGateways.add(currentNode);
				}
			}
			else
			{
				if (currentNode.parentNodes().size() == 1)
				{
					uselessGateways.add(currentNode);
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.getUselessGateways(child, visitedNodes, uselessGateways);
		}
	}

	/**
	 * A parallel gateway is eligible to be managed if it only contains either:
	 * 		- Tasks
	 * 		- Exclusive gateways
	 * 		- Already managed parallel gateways
	 *
	 * @param node
	 * @return
	 */
	private boolean gatewayIsEligible(final Node node,
									  final HashSet<Node> nonManagedGateways)
	{
		for (Node child : node.childNodes())
		{
			if (!gatewayIsEligibleRec(child, nonManagedGateways))
			{
				return false;
			}
		}

		return true;
	}

	private boolean gatewayIsEligibleRec(final Node currentNode,
										 final HashSet<Node> nonManagedGateways)
	{
		if (currentNode.bpmnObject() instanceof Gateway)
		{
			if (nonManagedGateways.contains(currentNode))
			{
				return false;
			}
		}

		for (Node child : currentNode.childNodes())
		{
			final boolean gatewayIsEligible = this.gatewayIsEligibleRec(child, nonManagedGateways);

			if (!gatewayIsEligible)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * This method is used to verify whether one of the structures given as input is a valid representation
	 * of the considered node or not.
	 * If the structure is a valid representation, a score is attributed to it.
	 * In the end, the structure with the highest score is returned, as it is supposed to represent as closely as
	 * possible the original constraints.
	 *
	 * @param candidates the list of candidate structures
	 * @param pathsPerNode the list of paths starting from each node of the structures
	 * @return the structure being the closest to the original constraints.
	 */
	private Pair<Graph, HashMap<Node, Pair<Node, Boolean>>> verifyAndElectMostSuitableGraphAmong(final Node splitNode,
																								 final ArrayList<Graph> candidates,
																								 final HashMap<Node, ArrayList<Path<Node>>> pathsPerNode)
	{
		assert !candidates.isEmpty();
		final HashMap<Graph, HashMap<Node, Pair<Node, Boolean>>> graphParallelGatewayInformation = new HashMap<>();
		Graph highestScoreGraph = null;
		int highestScore = -1;
		int nbParallel = -1;

		for (final Iterator<Graph> iterator = candidates.iterator(); iterator.hasNext(); )
		{
			final Graph candidate = iterator.next();
			final HashSet<Node> parallelGateways = this.getParallelGatewaysOf(candidate);

			if (!parallelGateways.isEmpty())
			{
				boolean shouldContinue = false;

				//We need to check that each theoretical parallel gateway can effectively be translated into a parallel gateway
				for (Node parallelGateway : parallelGateways)
				{
					final Triple<Node, Boolean, Boolean> gatewayInformation = this.verifyGateway(splitNode, parallelGateway, pathsPerNode);

					if (gatewayInformation == null
						|| !gatewayInformation.second())
					{
						//This means that the nodes composing the gateway cannot be properly put in parallel.
						iterator.remove();
						shouldContinue = true;
						break;
					}

					final HashMap<Node, Pair<Node, Boolean>> parallelInfos = graphParallelGatewayInformation.computeIfAbsent(candidate, h -> new HashMap<>());
					parallelInfos.put(parallelGateway, new Pair<>(gatewayInformation.first(), gatewayInformation.third()));
				}

				if (shouldContinue)
				{
					continue;
				}
			}

			final int graphScore = this.computeGraphScore(candidate, splitNode, graphParallelGatewayInformation.get(candidate));
			System.out.println("Graph score: " + graphScore);

			if (highestScoreGraph == null)
			{
				highestScoreGraph = candidate;
				highestScore = graphScore;
				nbParallel = this.getNumberOfParallelSplits(candidate);
			}
			else
			{
				if (graphScore > highestScore)
				{
					highestScoreGraph = candidate;
					highestScore = graphScore;
					nbParallel = this.getNumberOfParallelSplits(candidate);
				}
				else if (graphScore == highestScore
						&& this.getNumberOfParallelSplits(candidate) > nbParallel)
				{
					highestScoreGraph = candidate;
					nbParallel = this.getNumberOfParallelSplits(candidate);
				}
			}
		}

		System.out.println();
		System.out.println("Highest score graph:\n\n" + highestScoreGraph.toString());

		/*for (Node child : splitNode.childNodes())
		{
			if (child.bpmnObject().name().equals("Create New Feature Branch"))
			{
				throw new RuntimeException();
			}
		}*/

		return new Pair<>(highestScoreGraph, graphParallelGatewayInformation.get(highestScoreGraph));
	}

	private int getNumberOfParallelSplits(final Graph graph)
	{
		return this.getNumberOfParallelSplits(graph.initialNode());
	}

	private int getNumberOfParallelSplits(final Node node)
	{
		int nbParallelSplits = 0;

		if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
			&& ((Gateway) node.bpmnObject()).isSplitGateway())
		{
			nbParallelSplits++;
		}

		for (Node child : node.childNodes())
		{
			nbParallelSplits += this.getNumberOfParallelSplits(child);
		}

		return nbParallelSplits;
	}

	private Triple<Node, Boolean, Boolean> verifyGateway(final Node splitNode,
														 final Node gateway,
														 final HashMap<Node, ArrayList<Path<Node>>> pathsPerNode)
	{
		final HashSet<Node> gatewayTasks = this.getTasksFrom(gateway);
		System.out.println("Gateway tasks: " + gatewayTasks);

		final HashSet<Node> gatewayElements = this.getChildrenFrom(gateway, splitNode.childNodes());
		System.out.println("Gateway elements: " + gatewayElements);

		final ArrayList<Path<Node>> pathsToConsider = new ArrayList<>();
		System.out.println("Paths per node: " + pathsPerNode);
		final HashSet<Node> childrenToConsider = new HashSet<>();

		for (Node gatewayElement : gatewayElements)
		{
			if (!pathsPerNode.containsKey(gatewayElement))
			{
				//TODO A voir : on n'a pas de chemin en cas de boucle uniquement, mais est-ce nécessairement un problème ?
				return null;
			}

			for (Node child : splitNode.childNodes())
			{
				if (gatewayElement.equals(child))
				{
					childrenToConsider.add(child);
				}
			}

			pathsToConsider.addAll(pathsPerNode.get(gatewayElement));
		}

		final Triple<Node, Boolean, Boolean> result = this.getSynchroPoint(pathsToConsider, splitNode, childrenToConsider);
		System.out.println("Synchro point: " + result);

		if (result != null
			&& result.second())
		{
			final Collection<Collection<Node>> tasksPerPath = this.getTasksPerPath(gateway);

			if (this.parallelSplitDoesNotBreakMutualExclusionsToPreserveV2(tasksPerPath, result.first()))
				//&& this.parallelGatewayCannotBeEnteredFromOutside(splitNode, childrenToConsider, result.first()))
			{
				System.out.println("Parallel split does not break any mutual exclusion to preserve!");
				return result;
			}
			else
			{
				System.out.println("Parallel split breaks some mutual exclusions to preserve :-(");
				return null;
			}
		}

		return null;
	}

	private Collection<Collection<Node>> getTasksPerPath(final Node gateway)
	{
		final HashSet<Collection<Node>> tasksPerPath = new HashSet<>();

		for (Node child : gateway.childNodes())
		{
			tasksPerPath.add(this.getTasksFrom(child));
		}

		return tasksPerPath;
	}

	private int computeGraphScore(final Graph graph,
								  final Node originalNode,
								  final HashMap<Node, Pair<Node, Boolean>> parallelInformation)
	{
		final HashMap<Node, HashSet<Node>> parallelNodes = new HashMap<>();
		this.getParallelElements(graph.initialNode(), originalNode.childNodes(), parallelInformation, parallelNodes);

		int nbParallelElements = 0;
		int desiredParallels = 0;

		for (HashSet<Node> parallelNodesValues : parallelNodes.values())
		{
			nbParallelElements += parallelNodesValues.size();
		}

		for (AbstractSyntaxTree tree : this.explicitParallels)
		{
			final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
			final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

			for (Node node : parallelNodes.keySet())
			{
				if (node.bpmnObject().name().equals(leftNode.label()))
				{
					for (Node node2 : parallelNodes.keySet())
					{
						if (node2.bpmnObject().name().equals(rightNode.label()))
						{
							if (parallelNodes.get(node2).contains(node))
							{
								desiredParallels++;
							}
						}
					}
				}
			}
		}

		return nbParallelElements + desiredParallels * 5;
	}

	private void getParallelElements(final Node currentNode,
									 final Set<Node> childNodes,
									 final HashMap<Node, Pair<Node, Boolean>> parallelInformation,
									 final HashMap<Node, HashSet<Node>> parallelNodes)
	{
		if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			final Node mergeNode = parallelInformation.get(currentNode).first();
			final Collection<Collection<Node>> tasksPerPaths = this.getTasksPerPath(currentNode);
			final HashMap<HashSet<Node>, HashSet<Node>> reachableTasksPerParallelPath = new HashMap<>();

			for (Collection<Node> currentPathTasks : tasksPerPaths)
			{
				final HashSet<Node> currentPathTasksSet = new HashSet<>(currentPathTasks);
				final HashSet<Node> reachableTasks = reachableTasksPerParallelPath.computeIfAbsent(currentPathTasksSet, h -> new HashSet<>());

				for (Node currentPathTask : currentPathTasks)
				{
					for (Node childNode : childNodes)
					{
						if (currentPathTask.equals(childNode))
						{
							reachableTasks.addAll(childNode.getReachableNodesUpTo(mergeNode));
						}
					}
				}
			}

			for (HashSet<Node> parallelPath1 : reachableTasksPerParallelPath.keySet())
			{
				for (HashSet<Node> parallelPath2 : reachableTasksPerParallelPath.keySet())
				{
					if (!parallelPath1.equals(parallelPath2))
					{
						for (Node element1 : parallelPath1)
						{
							final HashSet<Node> currentParallelNodes = parallelNodes.computeIfAbsent(element1, h -> new HashSet<>());
							currentParallelNodes.addAll(parallelPath2);
						}
					}
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.getParallelElements(child, childNodes, parallelInformation, parallelNodes);
		}
	}

	private HashSet<Node> getChildrenFrom(final Node gateway,
										  final Set<Node> consideredChildren)
	{
		final HashSet<Node> elements = new HashSet<>();
		this.getChildrenFrom(gateway, consideredChildren, elements);
		return elements;
	}

	private void getChildrenFrom(final Node currentNode,
								 final Set<Node> consideredChildren,
								 final HashSet<Node> elements)
	{
		for (Node node : consideredChildren)
		{
			if (node.bpmnObject().name().equals(currentNode.bpmnObject().name()))
			{
				elements.add(currentNode);
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.getChildrenFrom(child, consideredChildren, elements);
		}
	}

	private HashSet<Node> getTasksFrom(final Node node)
	{
		final HashSet<Node> tasks = new HashSet<>();
		this.getTasksFrom(node, tasks);
		return tasks;
	}

	private void getTasksFrom(final Node currentNode,
							  final HashSet<Node> tasks)
	{
		if (currentNode.bpmnObject().type() == BpmnProcessType.TASK)
		{
			tasks.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.getTasksFrom(child, tasks);
		}
	}

	private HashSet<Node> getGatewaysOf(final Graph graph,
										final Set<Node> childrenToAvoid)
	{
		final HashSet<Node> parallelGateways = new HashSet<>();
		this.getGatewaysOf(graph.initialNode(), parallelGateways, childrenToAvoid);
		return parallelGateways;
	}

	private void getGatewaysOf(final Node currentNode,
							   final HashSet<Node> parallelGateways,
							   final Set<Node> childrenToAvoid)
	{
		if (!childrenToAvoid.contains(currentNode)
			&& currentNode.bpmnObject() instanceof Gateway)
		{
			parallelGateways.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.getGatewaysOf(child, parallelGateways, childrenToAvoid);
		}
	}

	private HashSet<Node> getParallelGatewaysOf(final Graph graph)
	{
		final HashSet<Node> parallelGateways = new HashSet<>();
		this.getParallelGatewaysOf(graph.initialNode(), parallelGateways);
		return parallelGateways;
	}

	private void getParallelGatewaysOf(final Node currentNode,
									   final HashSet<Node> parallelGateways)
	{
		if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			parallelGateways.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.getParallelGatewaysOf(child, parallelGateways);
		}
	}

	private ArrayList<Graph> getCombinationsOf(final Collection<Collection<Node>> combinations,
											   final HashMap<Node, HashSet<Node>> nonParallelisableNodes)
	{
		final HashMap<Integer, Collection<Collection<Node>>> combinationsMap = this.computeCombinationsMap(combinations);
		//System.out.println(combinationsMap);
		final HashSet<String> alreadyBuiltGraphs = new HashSet<>();
		final ArrayList<Graph> generatedCombinations = new ArrayList<>();

		for (int i = 0; i <= combinationsMap.size() / 2; i++)
		{
			final int combinations1Size = i;
			final int combinations2Size = combinationsMap.size() - i - 1;
			final Collection<Collection<Node>> combinations1 = combinationsMap.get(combinations1Size);
			final Collection<Collection<Node>> combinations2 = combinationsMap.get(combinations2Size);

			if (i == 0)
			{
				//combinations1 is empty while combinations2 is complete
				if (combinations2.size() != 1) throw new RuntimeException("Found several combinations of maximum size!!!");

				final Collection<Node> combination = combinations2.iterator().next();

				if (combination.size() == 1)
				{
					final Graph generatedGraph = new Graph(combination.iterator().next().weakCopy());
					final String graphHash = this.getSimpleHashOf(generatedGraph);

					if (!alreadyBuiltGraphs.contains(graphHash))
					{
						generatedCombinations.add(generatedGraph);
						alreadyBuiltGraphs.add(graphHash);
					}
				}
				else
				{
					final Graph exclusiveGraph = new Graph(new Node(BpmnProcessFactory.generateExclusiveGateway()));
					final ArrayList<Node>[] branches = new ArrayList[combination.size()];
					int index = 0;

					for (Node node : combination)
					{
						final ArrayList<Node> branch = new ArrayList<>(1);
						branch.add(node);
						branches[index++] = branch;

						exclusiveGraph.initialNode().addChildAndForceParent(node.weakCopy());
					}

					final String exclusiveGraphHash = this.getSimpleHashOf(exclusiveGraph);

					if (!alreadyBuiltGraphs.contains(exclusiveGraphHash))
					{
						alreadyBuiltGraphs.add(exclusiveGraphHash);
						generatedCombinations.add(exclusiveGraph);
					}

					//Avoid generating useless combinations if nodes are already known as non-parallelisable
					if (this.branchesAreParallelisable(nonParallelisableNodes, branches))
					{
						final Graph parallelGraph = new Graph(new Node(BpmnProcessFactory.generateParallelGateway()));

						for (Node node : combination)
						{
							parallelGraph.initialNode().addChildAndForceParent(node.weakCopy());
						}

						final String parallelGraphHash = this.getSimpleHashOf(parallelGraph);

						if (!alreadyBuiltGraphs.contains(parallelGraphHash))
						{
							alreadyBuiltGraphs.add(parallelGraphHash);
							generatedCombinations.add(parallelGraph);
						}
					}
				}
			}
			else if (combinations1Size == 1
					&& combinations2Size == 1)
			{
				//TODO Might be useless to differentiate this case
				final HashSet<Node> usedNodes = new HashSet<>();

				for (Collection<Node> combination1 : combinations1)
				{
					final Node node1 = combination1.iterator().next();

					for (Collection<Node> combination2 : combinations2)
					{
						final Node node2 = combination2.iterator().next();

						if (!usedNodes.contains(node2)
							&& !node1.equals(node2))
						{
							final Graph exclusiveGraph = new Graph(new Node(BpmnProcessFactory.generateExclusiveGateway()));
							exclusiveGraph.initialNode().addChildAndForceParent(node1.weakCopy());
							exclusiveGraph.initialNode().addChildAndForceParent(node2.weakCopy());

							final String exclusiveGraphHash = this.getSimpleHashOf(exclusiveGraph);

							if (!alreadyBuiltGraphs.contains(exclusiveGraphHash))
							{
								alreadyBuiltGraphs.add(exclusiveGraphHash);
								generatedCombinations.add(exclusiveGraph);
							}

							//Avoid generating useless combinations if nodes are already known as non-parallelisable

							if (this.branchesAreParallelisable(nonParallelisableNodes, combination1, combination2))
							{
								final Graph parallelGraph = new Graph(new Node(BpmnProcessFactory.generateParallelGateway()));
								parallelGraph.initialNode().addChildAndForceParent(node1.weakCopy());
								parallelGraph.initialNode().addChildAndForceParent(node2.weakCopy());

								final String parallelGraphHash = this.getSimpleHashOf(parallelGraph);

								if (!alreadyBuiltGraphs.contains(parallelGraphHash))
								{
									alreadyBuiltGraphs.add(parallelGraphHash);
									generatedCombinations.add(parallelGraph);
								}
							}
						}
					}

					usedNodes.add(node1);
				}
			}
			else
			{
				for (Collection<Node> combination1 : combinations1)
				{
					for (Collection<Node> combination2 : combinations2)
					{
						//We do not want to have several occurrences of the same task in different branches
						if (Utils.getIntersectionOf(combination1, combination2).isEmpty())
						{
							final ArrayList<Graph> graphsGeneratedFromCombination1 = this.getCombinationsOf(Utils.getCombinationsOf(combination1), nonParallelisableNodes);
							final ArrayList<Graph> graphsGeneratedFromCombination2 = this.getCombinationsOf(Utils.getCombinationsOf(combination2), nonParallelisableNodes);

							for (Graph graphGeneratedFromCombination1 : graphsGeneratedFromCombination1)
							{
								for (Graph graphGeneratedFromCombination2 : graphsGeneratedFromCombination2)
								{
									final Graph exclusiveGraph = new Graph(new Node(BpmnProcessFactory.generateExclusiveGateway()));
									exclusiveGraph.initialNode().addChildAndForceParent(graphGeneratedFromCombination1.weakCopy().initialNode());
									exclusiveGraph.initialNode().addChildAndForceParent(graphGeneratedFromCombination2.weakCopy().initialNode());
									this.reduceGraph(exclusiveGraph);

									final String exclusiveGraphHash = this.getSimpleHashOf(exclusiveGraph);

									if (!alreadyBuiltGraphs.contains(exclusiveGraphHash))
									{
										alreadyBuiltGraphs.add(exclusiveGraphHash);
										generatedCombinations.add(exclusiveGraph);
									}

									//Avoid generating useless combinations if nodes are already known as non-parallelisable
									if (this.branchesAreParallelisable(nonParallelisableNodes, combination1, combination2))
									{
										final Graph parallelGraph = new Graph(new Node(BpmnProcessFactory.generateParallelGateway()));
										parallelGraph.initialNode().addChildAndForceParent(graphGeneratedFromCombination1.weakCopy().initialNode());
										parallelGraph.initialNode().addChildAndForceParent(graphGeneratedFromCombination2.weakCopy().initialNode());
										this.reduceGraph(parallelGraph);

										final String parallelGraphHash = this.getSimpleHashOf(parallelGraph);

										if (!alreadyBuiltGraphs.contains(parallelGraphHash))
										{
											alreadyBuiltGraphs.add(parallelGraphHash);
											generatedCombinations.add(parallelGraph);
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return generatedCombinations;
	}

	@SafeVarargs
	private boolean branchesAreParallelisable(final HashMap<Node, HashSet<Node>> nonParallelisableNodes,
											  final Collection<Node>... branches)
	{
		System.out.println("Branches to check: " + Arrays.toString(branches));

		if (branches.length <= 1)
		{
			return true;
		}

		for (int i = 0; i < branches.length; i++)
		{
			final Collection<Node> branchI = branches[i];

			for (int j = i + 1; j < branches.length; j++)
			{
				final Collection<Node> branchJ = branches[j];

				for (Node nodeI : branchI)
				{
					if (nonParallelisableNodes.containsKey(nodeI))
					{
						final HashSet<Node> nonParallelisableNodesOfI = nonParallelisableNodes.get(nodeI);

						for (Node nodeJ : branchJ)
						{
							//TODO See if necessary to do the symmetrical operation (i think not)
							if (nonParallelisableNodesOfI.contains(nodeJ))
							{
								return false;
							}
						}
					}
				}
			}
		}

		return true;
	}

	private HashMap<Integer, Collection<Collection<Node>>> computeCombinationsMap(final Collection<Collection<Node>> collections)
	{
		final HashMap<Integer, Collection<Collection<Node>>> combinationsMap = new HashMap<>();
		combinationsMap.put(0, new ArrayList<>());

		for (Collection<Node> collection : collections)
		{
			final Collection<Collection<Node>> currentCollection = combinationsMap.computeIfAbsent(collection.size(), a -> new ArrayList<>());
			currentCollection.add(collection);
		}

		return combinationsMap;
	}

	private void reduceGraph(final Graph graph)
	{
		boolean reduced = true;

		while (reduced)
		{
			final Pair<Node, Node> reducibleNodes = this.getReducibleNodes(graph.initialNode());
			reduced = reducibleNodes != null;

			if (reduced)
			{
				reducibleNodes.first().removeChildAndForceParent(reducibleNodes.second());
				final Set<Node> childrenToReplug = reducibleNodes.second().childNodes();
				reducibleNodes.second().removeChildren();

				for (Node childToReplug : childrenToReplug)
				{
					reducibleNodes.first().addChildAndForceParent(childToReplug);
				}
			}
		}
	}

	private Pair<Node, Node> getReducibleNodes(final Node currentNode)
	{
		for (Node child : currentNode.childNodes())
		{
			if (currentNode.bpmnObject() instanceof Gateway
				&& child.bpmnObject() instanceof Gateway
				&& child.bpmnObject().type() == currentNode.bpmnObject().type())
			{
				return new Pair<>(currentNode, child);
			}

			final Pair<Node, Node> reducibleNodes = this.getReducibleNodes(child);

			if (reducibleNodes != null)
			{
				return reducibleNodes;
			}
		}

		return null;
	}

	private String getSimpleHashOf(final Graph graph)
	{
		final StringBuilder builder = new StringBuilder();

		if (graph.initialNode().bpmnObject().type() == BpmnProcessType.TASK)
		{
			builder.append(graph.initialNode().bpmnObject().name());
		}
		else if (graph.initialNode().bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			final ArrayList<String> childTasksHashes = new ArrayList<>();
			final ArrayList<String> childParallelGatewaysHashes = new ArrayList<>();

			for (Node child : graph.initialNode().childNodes())
			{
				if (child.bpmnObject().type() == BpmnProcessType.TASK
					|| child.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
				{
					childTasksHashes.add(this.getSimpleHashOf(new Graph(child)));
				}
				else if (child.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
				{
					childParallelGatewaysHashes.add(this.getSimpleHashOf(new Graph(child)));
				}
				else
				{
					throw new RuntimeException();
				}
			}

			Collections.sort(childTasksHashes);
			Collections.sort(childParallelGatewaysHashes);
			String separator = "";

			for (String taskHash : childTasksHashes)
			{
				builder.append(separator)
						.append(taskHash);
				separator = "|";
			}

			for (String parallelGatewayHash : childParallelGatewaysHashes)
			{
				builder.append(separator)
						.append("(")
						.append(parallelGatewayHash)
						.append(")");
				separator = "|";
			}
		}
		else if (graph.initialNode().bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			final ArrayList<String> childTasksHashes = new ArrayList<>();
			final ArrayList<String> childExclusiveGatewaysHashes = new ArrayList<>();

			for (Node child : graph.initialNode().childNodes())
			{
				if (child.bpmnObject().type() == BpmnProcessType.TASK
					|| child.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
				{
					childTasksHashes.add(this.getSimpleHashOf(new Graph(child)));
				}
				else if (child.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
				{
					childExclusiveGatewaysHashes.add(this.getSimpleHashOf(new Graph(child)));
				}
				else
				{
					throw new RuntimeException();
				}
			}

			Collections.sort(childTasksHashes);
			Collections.sort(childExclusiveGatewaysHashes);
			String separator = "";

			for (String taskHash : childTasksHashes)
			{
				builder.append(separator)
						.append(taskHash);
				separator = "&";
			}

			for (String parallelGatewayHash : childExclusiveGatewaysHashes)
			{
				builder.append(separator)
						.append("(")
						.append(parallelGatewayHash)
						.append(")");
				separator = "&";
			}
		}
		else
		{
			throw new RuntimeException();
		}

		return builder.toString();
	}

	private boolean parallelGatewayCannotBeEnteredFromOutside(final Node splitNode,
															  final HashSet<Node> childrenToConsider,
															  final Node synchroPoint)
	{
		if (synchroPoint == null)
		{
			//TODO Is this necessarily true?
			return true;
		}

		final HashSet<Node> nodesBetween = new HashSet<>();

		for (Node childToConsider : childrenToConsider)
		{
			nodesBetween.addAll(childToConsider.getAllSuccessorsUpTo(synchroPoint));
			nodesBetween.add(childToConsider);
		}

		System.out.println("Between nodes: " + nodesBetween);

		for (Node betweenNode : nodesBetween)
		{
			if (betweenNode.canReachStartBefore(splitNode))
			{
				//One of the nodes belonging to the parallel gateway can be accessed without accessing the parallel split first
				return false;
			}
		}

		return true;
	}

	/**
	 * This method retrieves all the nodes that one can find between two given nodes (excluding both).
	 * Note that, depending on the structure of the graph, this method may return all the graph.
	 * It is thus up to the user to use this method properly.
	 *
	 * @param firstNode
	 * @param lastNode
	 * @return
	 */
	private HashSet<Node> getAllNodesBetween(final Node firstNode,
											 final Node lastNode)
	{
		final HashSet<Node> nodesBetween = new HashSet<>();

		for (Node child : firstNode.childNodes())
		{
			this.getAllNodesBetween(child, lastNode, nodesBetween);
		}

		return nodesBetween;
	}

	private void getAllNodesBetween(final Node currentNode,
									final Node lastNode,
									final HashSet<Node> nodesBetween)
	{
		if (nodesBetween.contains(currentNode))
		{
			return;
		}

		if (currentNode.equals(lastNode))
		{
			return;
		}

		nodesBetween.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.getAllNodesBetween(child, lastNode, nodesBetween);
		}
	}

	private boolean parallelSplitDoesNotBreakMutualExclusionsToPreserve(final Collection<Node> startingNodes,
																		final Node mergeNode)
	{
		//Compute all the nodes that are reachable from the starting nodes (up to the merging node if any)
		final HashMap<Node, HashSet<Node>> reachableNodesPerParallelisableNode = new HashMap<>();

		for (Node startingNode : startingNodes)
		{
			reachableNodesPerParallelisableNode.put(startingNode, startingNode.getReachableNodesUpTo(mergeNode));
		}

		//Compute all the generated parallelisms
		final HashMap<Node, HashSet<Node>> parallelNodes = new HashMap<>();

		for (Node startingNode : startingNodes)
		{
			final HashSet<Node> reachableNodes = reachableNodesPerParallelisableNode.get(startingNode);

			for (Node reachableNode : reachableNodes)
			{
				final HashSet<Node> parallelNodesOfReachableNode = parallelNodes.computeIfAbsent(reachableNode, h -> new HashSet<>());

				for (Node startingNode2 : startingNodes)
				{
					if (!startingNode.equals(startingNode2))
					{
						final HashSet<Node> reachableNodes2 = reachableNodesPerParallelisableNode.get(startingNode2);
						parallelNodesOfReachableNode.addAll(reachableNodes2);
					}
				}
			}
		}

		/*
			The map contains all the nodes that will be in parallel of the others in the resulting process.
			Thus, they must not be mutually exclusive.
			Note that a single mutual exclusions between these nodes prevent the exclusive split from becoming
			a parallel split.
		 */
		for (Node node : parallelNodes.keySet())
		{
			final HashSet<Node> parallelNodesOfNode = parallelNodes.get(node);
			final HashSet<String> mutualExclusionsOfNode = this.mutualExclusionsToEnsure.get(node.bpmnObject().id());

			if (mutualExclusionsOfNode != null)
			{
				for (Node parallelNodeOfNode : parallelNodesOfNode)
				{
					if (mutualExclusionsOfNode.contains(parallelNodeOfNode.bpmnObject().id()))
					{
						//One of the parallel node should be mutually exclusive => impossible parallel
						return false;
					}
				}
			}
		}

		return true;
	}

	private boolean parallelSplitDoesNotBreakMutualExclusionsToPreserveV2(final Collection<Collection<Node>> parallelSplitFirstNodes,
																		  final Node mergeNode)
	{
		//Compute all the nodes that are reachable from the starting nodes (up to the merging node if any)
		final HashMap<HashSet<Node>, HashSet<Node>> reachableNodesPerFirstSplitNodes = new HashMap<>();

		for (Collection<Node> currentFirstNodes : parallelSplitFirstNodes)
		{
			final HashSet<Node> currentFirstNodesSet = new HashSet<>(currentFirstNodes);
			final HashSet<Node> currentReachableNodes = new HashSet<>();

			for (Node currentFirstNode : currentFirstNodes)
			{
				currentReachableNodes.addAll(currentFirstNode.getReachableNodesUpTo(mergeNode));
			}

			reachableNodesPerFirstSplitNodes.put(currentFirstNodesSet, currentReachableNodes);
		}

		//Compute all the generated parallelisms
		final HashMap<Node, HashSet<Node>> parallelNodesPerNode = new HashMap<>();

		for (HashSet<Node> firstSplitNodes1 : reachableNodesPerFirstSplitNodes.keySet())
		{
			for (HashSet<Node> firstSplitNodes2 : reachableNodesPerFirstSplitNodes.keySet())
			{
				if (!firstSplitNodes1.equals(firstSplitNodes2))
				{
					final HashSet<Node> reachableNodes1 = reachableNodesPerFirstSplitNodes.get(firstSplitNodes1);
					final HashSet<Node> reachableNodes2 = reachableNodesPerFirstSplitNodes.get(firstSplitNodes2);

					for (Node reachableNode1 : reachableNodes1)
					{
						final HashSet<Node> parallelNodes1 = parallelNodesPerNode.computeIfAbsent(reachableNode1, h -> new HashSet<>());
						parallelNodes1.addAll(reachableNodes2);
					}

					for (Node reachableNode2 : reachableNodes2)
					{
						final HashSet<Node> parallelNodes2 = parallelNodesPerNode.computeIfAbsent(reachableNode2, h -> new HashSet<>());
						parallelNodes2.addAll(reachableNodes1);
					}
				}
			}
		}

		/*
			The map contains all the nodes that will be in parallel of the others in the resulting process.
			Thus, they must not be mutually exclusive.
			Note that a single mutual exclusions between these nodes prevent the exclusive split from becoming
			a parallel split.
		 */
		for (Node node : parallelNodesPerNode.keySet())
		{
			final HashSet<Node> parallelNodesOfNode = parallelNodesPerNode.get(node);
			final HashSet<String> mutualExclusionsOfNode = this.mutualExclusionsToEnsure.get(node.bpmnObject().id());

			if (mutualExclusionsOfNode != null)
			{
				for (Node parallelNodeOfNode : parallelNodesOfNode)
				{
					if (mutualExclusionsOfNode.contains(parallelNodeOfNode.bpmnObject().id()))
					{
						//One of the parallel node should be mutually exclusive => impossible parallel
						return false;
					}
				}
			}
		}

		return true;
	}

	private Triple<Node, Boolean, Boolean> getSynchroPoint(final ArrayList<Path<Node>> pathsWithoutLoops,
														   final Node node,
														   final HashSet<Node> consideredChildren)
	{
		final ArrayList<Node> synchroPoints = this.getSynchroPointsOf(pathsWithoutLoops);
		System.out.println("All synchro points: " + synchroPoints);
		System.out.println("Considered children: " + consideredChildren);

		if (synchroPoints.isEmpty())
		{
			/*
				Check whether the children of "node" can reach "node".
				If not, we can have a split that will just not be closed.
				Otherwise, we cannot have a parallel split without infinite recursion.
			 */
			boolean canReachNode = false;

			for (Node child : consideredChildren)
			{
				if (child.hasSuccessor(node))
				{
					canReachNode = true;
					break;
				}
			}

			return new Triple<>(null, !canReachNode, false);
		}
		else
		{
			/*
				Check whether the current synchro point is a valid candidate.
				To be a valid candidate, it has to:
					1) Be traversed by every token sent by "node" before they reach "node" again, except
					   self-loops (avoids infinite recursion)
					2) Not be reachable from itself without having reached "node" before (avoids deadlocks)
				If these two conditions are fulfilled, the synchro point is a valid candidate.
			 */
			for (Node childToConsider : consideredChildren)
			{
				if (synchroPoints.isEmpty()) break;

				System.out.println(childToConsider + " may reach itself before reaching " + synchroPoints.iterator().next() + " : " + childToConsider.mayReachItselfBefore(synchroPoints.iterator().next()));
				System.out.println(synchroPoints.iterator().next() + " may reach itself before reaching " + node + " : " + synchroPoints.iterator().next().mayReachItselfBefore(node));

				synchroPoints.removeIf(synchroPoint -> childToConsider.mayReachItselfBefore(synchroPoint));
						//|| synchroPoint.mayReachItselfBefore(node));
			}

			if (synchroPoints.isEmpty())
			{
				return null;
			}
			else
			{
				final HashSet<Node> firstReachableSynchroPoints = this.getFirstReachableNodesAmong(synchroPoints);
				final Node finalSynchroPoint = firstReachableSynchroPoints.iterator().next();

				if (firstReachableSynchroPoints.size() > 1)
				{
					System.out.println("WARNING: Several synchro points were found at the same depth (strange)");
					logger.warn("Several synchro points were found at the same depth (strange, should not happen)");
				}

				final boolean mandatorySynchroPoint;

				if (finalSynchroPoint.hasSuccessor(node))
				{
					//The synchro point is mandatory to avoid infinite recursion => TODO Is it true? Do we need to be more fine-grained?
					mandatorySynchroPoint = true;
				}
				else
				{
					//The synchro point is not mandatory to ensure infinite recursion
					mandatorySynchroPoint = false;
				}

				return new Triple<>(finalSynchroPoint, true, mandatorySynchroPoint);
			}
		}
	}

	private ArrayList<Node> getSynchroPointsOf(final ArrayList<Path<Node>> paths)
	{
		ArrayList<Node> intersection = null;

		for (Path<Node> path : paths)
		{
			if (intersection == null)
			{
				intersection = new ArrayList<>(path.elements());
			}
			else
			{
				final Collection<Node> newIntersection = Utils.getIntersectionOf(intersection, path.elements());

				intersection.clear();

				if (newIntersection.isEmpty())
				{
					break;
				};

				intersection.addAll(newIntersection);
			}
		}

		return intersection;
	}

	private ArrayList<Path<Node>> getAllPathsWithoutLoopsStartingFrom(final Node node)
	{
		final ArrayList<Path<Node>> pathsWithoutLoops = new ArrayList<>();

		for (Node child : node.childNodes())
		{
			final HashSet<Node> visitedNodes = new HashSet<>();
			visitedNodes.add(node);
			final Path<Node> firstPath = new Path<>();
			firstPath.add(node);
			pathsWithoutLoops.add(firstPath);
			this.getAllPathsWithoutLoopsStartingFrom(pathsWithoutLoops, firstPath, child, visitedNodes);
		}

		return pathsWithoutLoops;
	}

	private void getAllPathsWithoutLoopsStartingFrom(final ArrayList<Path<Node>> paths,
													 final Path<Node> currentPath,
													 final Node currentNode,
													 final HashSet<Node> visitedNodes)
	{
		currentPath.add(currentNode);

		if (visitedNodes.contains(currentNode))
		{
			paths.remove(currentPath);
			return;
		}

		visitedNodes.add(currentNode);

		final ArrayList<Path<Node>> nextPaths = new ArrayList<>();
		final ArrayList<HashSet<Node>> nextVisitedNodes = new ArrayList<>();

		for (int i = 1; i < currentNode.childNodes().size(); i++)
		{
			final Path<Node> currentPathCopy = currentPath.copy();
			nextPaths.add(currentPathCopy);
			paths.add(currentPathCopy);
			nextVisitedNodes.add(new HashSet<>(visitedNodes));
		}

		int i = 0;

		for (Node child : currentNode.childNodes())
		{
			if (i == 0)
			{
				this.getAllPathsWithoutLoopsStartingFrom(paths, currentPath, child, visitedNodes);
			}
			else
			{
				this.getAllPathsWithoutLoopsStartingFrom(paths, nextPaths.get(i - 1), child, nextVisitedNodes.get(i - 1));
			}

			i++;
		}
	}

	/*
		Explicit loops are loops returned by GPT as (T1, T2, ..., Tn)* expressions.
		This method basically consists in:
			- Doing nothing if the specified tasks are already in a loop containing only themselves or being minimal
			  in terms of included tasks (i.e., there is no other graph containing a loop with less "useless" elements
			  modulo transitive reduction)
			- Adding additional flows to the dependency graph if all the tasks are already in the graph but do not
			  belong to the same loop or there is a possibility of creating a new loop that is minimal in terms of
			  included tasks
			- Adding additional tasks and flows to the dependency graph if at least one of the task of the loop is not
			  yet in the dependency graph.
	 */
	private void manageExplicitLoops()
	{
		for (HashSet<Node> realLoop : this.explicitLoops)
		{
			boolean goToNextLoop = false;

			if (realLoop.isEmpty()) continue;

			if (realLoop.size() == 1)
			{
				final Node loopNode = realLoop.iterator().next();
				loopNode.addChildAndForceParent(loopNode);
				/*
					Self-loops can always be handled, independently of EXPLICIT_LOOP_HANDLING.
					The only difference comes from the appearance of the loop node in the graph or not.
				 */
				if (!this.dependencyGraph.toSet().contains(loopNode))
				{
					/*
						If the node does not exist, it remains only parallelisable nodes at this stage, thus
						add this node to the list of initial nodes of the graph.
					 */
					this.dependencyGraph.addInitialNode(loopNode);
				}

				continue;
			}

			final HashSet<Node> nodesNotYetInTheGraph = new HashSet<>(realLoop);
			nodesNotYetInTheGraph.removeAll(this.dependencyGraph.toSet());
			final HashSet<Node> nodesAlreadyInTheGraph = new HashSet<>(realLoop);
			nodesAlreadyInTheGraph.removeAll(nodesNotYetInTheGraph);

			/*
				Check whether there is already a loop containing only these elements.
				If yes,do nothing.
				Otherwise, add the necessary sequence flows needed to create this loop.
			*/
			final ArrayList<SubGraphInformation> eligibleSubGraphsInformation = this.getBiggestDirectlyConnectedGraph(nodesAlreadyInTheGraph);
			final SubGraphInformation eligibleSubGraphInformation = eligibleSubGraphsInformation.get(0);
			final DependencyGraph eligibleSubGraph = eligibleSubGraphInformation.dependencyGraph();

			if (eligibleSubGraph.size() == nodesAlreadyInTheGraph.size())
			{
				//The loop may be already handled properly
				final HashSet<Node> initialNodes = eligibleSubGraph.initialNodes();
				final HashSet<Node> finalNodes = eligibleSubGraph.finalNodes();

				if (!finalNodes.isEmpty())
				{
					//If the loop is not already handled, we try to connect the final nodes to the nodes that can reach every node of the graph
					final HashSet<Node> fullyCoveringNodes = this.getFullyCoveringNodes(eligibleSubGraph);
					final ArrayList<DependencyGraph> eligibleDependencyGraphs = new ArrayList<>();
					eligibleDependencyGraphs.add(this.dependencyGraph.weakCopy());

					for (Node finalNode : finalNodes)
					{
						final ArrayList<DependencyGraph> nextDependencyGraphs = new ArrayList<>();

						for (DependencyGraph currentDependencyGraph : eligibleDependencyGraphs)
						{
							for (Node fullyCoveringNode : fullyCoveringNodes)
							{
								final DependencyGraph currentDependencyGraphCopy = currentDependencyGraph.weakCopy();
								final Node realFinalNode = currentDependencyGraphCopy.getNodeFromID(finalNode.bpmnObject().id());
								final Node realFullyCoveringNode = currentDependencyGraphCopy.getNodeFromID(fullyCoveringNode.bpmnObject().id());

								realFinalNode.addChildAndForceParent(realFullyCoveringNode);

								if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
								{
									try
									{
										this.assertMutualExclusionsUncaught(currentDependencyGraphCopy);
									}
									catch (BadMutualExclusionsException e)
									{
										//The connection broke some necessary mutual exclusions => go to the next one
										continue;
									}
								}

								//The connection did not break any necessary mutual exclusion => add it to the list
								nextDependencyGraphs.add(currentDependencyGraphCopy);
							}
						}

						if (nextDependencyGraphs.isEmpty())
						{
							/*
								If the final node has no valid branching, then it cannot be connected properly.
								Thus, the loop cannot be handled
							 */
							System.out.println("Loop \"" + realLoop + "\" cannot be handled properly in strict mode" +
									" because node \"" + finalNode + "\" could not be connected to any fully covering" +
									" node (" + fullyCoveringNodes + ") without breaking one or several mutual exclusions.");
							goToNextLoop = true;
							break;
						}

						eligibleDependencyGraphs.clear();
						eligibleDependencyGraphs.addAll(nextDependencyGraphs);
					}

					if (goToNextLoop)
					{
						continue;
					}

					//Note that there might be many versions handling the loop, but for now we consider only one of them
					this.dependencyGraph = eligibleDependencyGraphs.get(0);
					//System.out.println("Explicit loop containing elements \"" + realLoop + "\" has been handled!");
				}
				else
				{
					//System.out.println("Explicit loop containing elements \"" + realLoop + "\" is already handled properly.");
				}

				//System.out.println();
			}
			else
			{
				/*
					There are several disjoint portions of the loop to handle.
					The algorithm used to connect them is the following:
						- Identify each disjoint largest subgraph
						- For each subgraph, compute its end nodes and its fully covering nodes
						- Try to connect each end node of each subgraph to each fully covering node of
						  each other subgraph, until having connected each subgraph together or reaching an
						  impossible position (that is, a position in which you necessarily break one or several
						  required mutual exclusions)
						- Pick the first connected graph that does not break any mandatory mutual exclusion
						  (TODO see if useful to chose smartly)
				 */
				final ArrayList<SubGraphInformation> biggestDisjointSubGraphs = this.getAllBiggestDisjointSubGraphs(nodesAlreadyInTheGraph);
				final ArrayList<Pair<ArrayList<Integer>, ArrayList<DependencyGraph>>> currentPossiblyGoodGraphs = new ArrayList<>();
				final Pair<ArrayList<Integer>, ArrayList<DependencyGraph>> initialPair = new Pair<>(new ArrayList<>(), new ArrayList<>());
				initialPair.second().add(this.dependencyGraph);
				currentPossiblyGoodGraphs.add(initialPair);

				while (true)
				{
					final ArrayList<Pair<ArrayList<Integer>, ArrayList<DependencyGraph>>> nextPossiblyGoodGraphs = new ArrayList<>();

					for (Pair<ArrayList<Integer>, ArrayList<DependencyGraph>> pair : currentPossiblyGoodGraphs)
					{
						final ArrayList<Integer> connectedSubgraphsIndices = pair.first();

						if (connectedSubgraphsIndices.isEmpty())
						{
							//The list of indices should be empty only at the first iteration
							assert currentPossiblyGoodGraphs.size() == 1;
							assert currentPossiblyGoodGraphs.get(0).second().size() == 1;
							final DependencyGraph baseGraph = currentPossiblyGoodGraphs.get(0).second().get(0);

							//We have to connect all combinations of two subgraphs
							for (int i = 0; i < biggestDisjointSubGraphs.size(); i++)
							{
								final DependencyGraph currentPivot = biggestDisjointSubGraphs.get(i).dependencyGraph();
								final HashSet<Node> currentPivotEndNodes = currentPivot.finalNodes();

								for (int j = 0; j < biggestDisjointSubGraphs.size(); j++)
								{
									if (i != j)
									{
										final Pair<ArrayList<Integer>, ArrayList<DependencyGraph>> newPair = new Pair<>(new ArrayList<>(), new ArrayList<>());
										newPair.first().add(i);
										newPair.first().add(j);

										final DependencyGraph currentAdditionalGraph = biggestDisjointSubGraphs.get(j).dependencyGraph();
										final HashSet<Node> currentFullyCoveringNodes = this.getFullyCoveringNodes(currentAdditionalGraph);

										for (Node fullyCoveringNode : currentFullyCoveringNodes)
										{
											if (currentPivotEndNodes.isEmpty())
											{
												//All the nodes are reachable from the others (sub-loop)
												for (Node graphNode : currentPivot.toSet())
												{
													//Connect each graph node to a fully covering node
													final DependencyGraph copy = baseGraph.weakCopy();
													final Node realGraphNode = copy.getNodeFromID(graphNode.bpmnObject().id());
													final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());
													realGraphNode.addChildAndForceParent(realFullyCoveringNode);

													if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
													{
														try
														{
															this.assertMutualExclusionsUncaught(copy);
														}
														catch (BadMutualExclusionsException e)
														{
															//The connection broke some necessary mutual exclusions => go to the next one
															continue;
														}
													}

													System.out.println("Successfully connected node \"" + realGraphNode.bpmnObject().id() + "\" to node \"" + realFullyCoveringNode.bpmnObject().id() + "\".");
													newPair.second().add(copy);
												}
											}
											else
											{
												//There are some end nodes
												final DependencyGraph copy = baseGraph.weakCopy();

												for (Node endNode : currentPivotEndNodes)
												{
													final Node realEndNode = copy.getNodeFromID(endNode.bpmnObject().id());
													final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());
													realEndNode.addChildAndForceParent(realFullyCoveringNode);
												}

												if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
												{
													try
													{
														this.assertMutualExclusionsUncaught(copy);
													}
													catch (BadMutualExclusionsException e)
													{
														//The connection broke some necessary mutual exclusions => go to the next one
														continue;
													}
												}

												System.out.println("Successfully connected nodes \"" + currentPivotEndNodes + "\" to node \"" + fullyCoveringNode.bpmnObject().id() + "\".");
												newPair.second().add(copy);
											}
										}

										if (!newPair.second().isEmpty())
										{
											nextPossiblyGoodGraphs.add(newPair);
										}
									}
								}
							}
						}
						else
						{
							//We have to connect all non-previously connected subgraphs
							for (int i = 0; i < biggestDisjointSubGraphs.size(); i++)
							{
								if (!connectedSubgraphsIndices.contains(i))
								{
									//The subgraph can be added at the end of the last generated ones
									final ArrayList<Integer> nextSubgraphsIndices = new ArrayList<>(pair.first());
									nextSubgraphsIndices.add(i);
									final Pair<ArrayList<Integer>, ArrayList<DependencyGraph>> newPair = new Pair<>(nextSubgraphsIndices, new ArrayList<>());
									final DependencyGraph lastAlreadyConnectedGraph = biggestDisjointSubGraphs.get(connectedSubgraphsIndices.get(connectedSubgraphsIndices.size() - 1)).dependencyGraph();
									final DependencyGraph graphToConnect = biggestDisjointSubGraphs.get(i).dependencyGraph();

									final HashSet<Node> finalNodesOfLastAlreadyConnectedGraph = lastAlreadyConnectedGraph.finalNodes();
									final HashSet<Node> fullyCoveringNodesOfGraphToConnect = this.getFullyCoveringNodes(graphToConnect);

									for (DependencyGraph lastDependencyGraph : pair.second())
									{
										if (finalNodesOfLastAlreadyConnectedGraph.isEmpty())
										{
											for (Node graphNode : lastAlreadyConnectedGraph.toSet())
											{
												for (Node fullyCoveringNode : fullyCoveringNodesOfGraphToConnect)
												{
													final DependencyGraph copy = lastDependencyGraph.weakCopy();
													final Node realGraphNode = copy.getNodeFromID(graphNode.bpmnObject().id());
													final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());
													realGraphNode.addChildAndForceParent(realFullyCoveringNode);

													if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
													{
														try
														{
															this.assertMutualExclusionsUncaught(copy);
														}
														catch (BadMutualExclusionsException e)
														{
															//The connection broke some necessary mutual exclusions => go to the next one
															continue;
														}
													}

													newPair.second().add(copy);
												}
											}
										}
										else
										{
											for (Node fullyCoveringNode : fullyCoveringNodesOfGraphToConnect)
											{
												final DependencyGraph copy = lastDependencyGraph.weakCopy();

												for (Node endNode : finalNodesOfLastAlreadyConnectedGraph)
												{
													final Node realEndNode = copy.getNodeFromID(endNode.bpmnObject().id());
													final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());
													realEndNode.addChildAndForceParent(realFullyCoveringNode);
												}

												if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
												{
													try
													{
														this.assertMutualExclusionsUncaught(copy);
													}
													catch (BadMutualExclusionsException e)
													{
														//The connection broke some necessary mutual exclusions => go to the next one
														continue;
													}
												}

												newPair.second().add(copy);
											}
										}
									}

									if (!newPair.second().isEmpty())
									{
										nextPossiblyGoodGraphs.add(newPair);
									}
								}
							}
						}
					}

					if (nextPossiblyGoodGraphs.isEmpty())
					{
						break;
					}

					currentPossiblyGoodGraphs.clear();
					currentPossiblyGoodGraphs.addAll(nextPossiblyGoodGraphs);
				}

				if (currentPossiblyGoodGraphs.isEmpty()
					|| currentPossiblyGoodGraphs.get(0).first().size() != biggestDisjointSubGraphs.size())
				{
					System.out.println("No connection between the subgraphs composing the loop \"" + realLoop + "\"" +
							" was found in the graph without breaking one or several existing mutual exclusions." +
							" Thus, the loop was not added to the graph.");
					return;
				}

				//Ultimately, we have to connect the last subgraph of the list to the initial one
				final ArrayList<DependencyGraph> fullyConnectedGraphs = new ArrayList<>();

				for (Pair<ArrayList<Integer>, ArrayList<DependencyGraph>> pair : currentPossiblyGoodGraphs)
				{
					final ArrayList<Integer> connectionOrder = pair.first();
					final ArrayList<DependencyGraph> almostFullyConnectedGraphs = pair.second();

					if (connectionOrder.size() == biggestDisjointSubGraphs.size())
					{
						final int firstSubGraphIndex = connectionOrder.get(0);
						final int lastSubGraphIndex = connectionOrder.get(connectionOrder.size() - 1);

						final DependencyGraph lastSubGraph = biggestDisjointSubGraphs.get(lastSubGraphIndex).dependencyGraph();
						final DependencyGraph firstSubGraph = biggestDisjointSubGraphs.get(firstSubGraphIndex).dependencyGraph();

						final HashSet<Node> lastSubGraphEndNodes = lastSubGraph.finalNodes();
						final HashSet<Node> firstSubGraphFullyCoveringNodes = this.getFullyCoveringNodes(firstSubGraph);

						for (DependencyGraph almostFullyConnectGraph : almostFullyConnectedGraphs)
						{
							for (Node fullyCoveringNode : firstSubGraphFullyCoveringNodes)
							{
								if (lastSubGraphEndNodes.isEmpty())
								{
									for (Node graphNode : lastSubGraph.toSet())
									{
										final DependencyGraph copy = almostFullyConnectGraph.weakCopy();
										final Node realGraphNode = copy.getNodeFromID(graphNode.bpmnObject().id());
										final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());
										realGraphNode.addChildAndForceParent(realFullyCoveringNode);

										if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
										{
											try
											{
												this.assertMutualExclusionsUncaught(copy);
											}
											catch (BadMutualExclusionsException e)
											{
												//The connection broke some necessary mutual exclusions => go to the next one
												continue;
											}
										}

										fullyConnectedGraphs.add(copy);

										if (fullyConnectedGraphs.size() == NB_WORKING_GRAPHS_TO_KEEP)
										{
											break;
										}
									}
								}
								else
								{
									final DependencyGraph copy = almostFullyConnectGraph.weakCopy();
									final Node realFullyCoveringNode = copy.getNodeFromID(fullyCoveringNode.bpmnObject().id());

									for (Node endNode : lastSubGraphEndNodes)
									{
										final Node realEndNode = copy.getNodeFromID(endNode.bpmnObject().id());
										realEndNode.addChildAndForceParent(realFullyCoveringNode);
									}

									if (CHECK_MUTUAL_EXCLUSIONS_BREAKING)
									{
										try
										{
											this.assertMutualExclusionsUncaught(copy);
										}
										catch (BadMutualExclusionsException e)
										{
											//The connection broke some necessary mutual exclusions => go to the next one
											continue;
										}
									}

									fullyConnectedGraphs.add(copy);

									if (fullyConnectedGraphs.size() == NB_WORKING_GRAPHS_TO_KEEP)
									{
										break;
									}
								}

								if (fullyConnectedGraphs.size() == NB_WORKING_GRAPHS_TO_KEEP)
								{
									break;
								}
							}

							if (fullyConnectedGraphs.size() == NB_WORKING_GRAPHS_TO_KEEP)
							{
								break;
							}
						}
					}

					if (fullyConnectedGraphs.size() == NB_WORKING_GRAPHS_TO_KEEP)
					{
						break;
					}
				}

				if (fullyConnectedGraphs.isEmpty())
				{
					System.out.println("No connection between the subgraphs composing the loop \"" + realLoop + "\"" +
							" was found in the graph without breaking one or several existing mutual exclusions." +
							" Thus, the loop was not added to the graph.");
					return;
				}

				//Pick the first valid dependency graph as new graph //TODO See if need of being smart here
				this.dependencyGraph = fullyConnectedGraphs.get(0);

				System.out.println("Found " + fullyConnectedGraphs.size() + " possibly good graphs");
				System.out.println("Explicit loop containing elements \"" + realLoop + "\" has been handled!");
				//System.out.println("Dependency graph chosen:\n" + this.dependencyGraph.stringify(0));
			}


			if (!nodesNotYetInTheGraph.isEmpty())
			{
				/*
					Some nodes are not yet in the graph. At this stage, they are necessarily parallel nodes.
					For the moment, the idea is to add these nodes in parallel of the whole loop.
				 */
				final HashSet<Node> firstReachableNodes = this.getFirstReachableNodesAmong(nodesAlreadyInTheGraph);
				final Node firstLoopNode = firstReachableNodes.iterator().next();
				final HashSet<Node> parentsNotInLoop = new HashSet<>();
				final HashSet<Node> parentsInLoop = new HashSet<>();

				for (Node parent : firstLoopNode.parentNodes())
				{
					if (!realLoop.contains(parent))
					{
						parentsNotInLoop.add(parent);
					}
					else
					{
						parentsInLoop.add(parent);
					}
				}

				final String id = BpmnProcessFactory.generateID();
				final Node dummySplit = new Node(BpmnProcessFactory.generateTask("DUMMY_SPLIT_" + id));
				final Node dummyMerge = new Node(BpmnProcessFactory.generateTask("DUMMY_MERGE_" + id));
				dummySplit.addChildAndForceParent(firstLoopNode);
				dummyMerge.addChildAndForceParent(dummySplit);

				if (parentsNotInLoop.isEmpty())
				{
					this.dependencyGraph.addInitialNode(dummySplit);
				}
				else
				{
					for (Node parentNotInLoop : parentsNotInLoop)
					{
						parentNotInLoop.removeChildAndForceParent(firstLoopNode);
						parentNotInLoop.addChildAndForceParent(dummySplit);
					}
				}

				for (Node nodeNotYetInTheGraph : nodesNotYetInTheGraph)
				{
					dummySplit.addChildAndForceParent(nodeNotYetInTheGraph);
					nodeNotYetInTheGraph.addChildAndForceParent(dummyMerge);
				}

				for (Node parentInLoop : parentsInLoop)
				{
					parentInLoop.removeChildAndForceParent(firstLoopNode);
					parentInLoop.addChildAndForceParent(dummyMerge);
				}
			}
		}

		System.out.println();
	}

	private HashSet<Node> getFirstReachableNodesAmong(final Collection<Node> nodes)
	{
		if (nodes.size() <= 1)
		{
			return new HashSet<>(nodes);
		}

		final HashSet<Node> firstReachableNodes = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.dependencyGraph.initialNodes())
		{
			this.getFirstReachableNodesAmong(initialNode, nodes, firstReachableNodes, visitedNodes);
		}

		return firstReachableNodes;
	}

	private void getFirstReachableNodesAmong(final Node currentNode,
											 final Collection<Node> originalSet,
											 final HashSet<Node> firstReachableNodes,
											 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (originalSet.contains(currentNode))
		{
			firstReachableNodes.add(currentNode);
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.getFirstReachableNodesAmong(child, originalSet, firstReachableNodes, visitedNodes);
		}
	}

	private ArrayList<SubGraphInformation> getAllBiggestDisjointSubGraphs(final HashSet<Node> realLoop)
	{
		final ArrayList<SubGraphInformation> biggestDisjointSubgraphs = new ArrayList<>();
		final HashSet<Node> removableLoopNodes = new HashSet<>(realLoop);

		while (!removableLoopNodes.isEmpty())
		{
			final SubGraphInformation currentBiggestSubgraph = this.getBiggestDirectlyConnectedGraph(removableLoopNodes).get(0);
			biggestDisjointSubgraphs.add(currentBiggestSubgraph);
			removableLoopNodes.removeAll(currentBiggestSubgraph.dependencyGraph().toSet());
		}

		return biggestDisjointSubgraphs;
	}

	private HashSet<Node> getFullyCoveringNodes(final DependencyGraph dependencyGraph)
	{
		return this.getFullyCoveringNodes(dependencyGraph.toSet());
	}

	private HashSet<Node> getFullyCoveringNodes(final HashSet<Node> nodes)
	{
		final HashMap<Node, Integer> nodesCoverage = new HashMap<>();

		for (Node node : nodes)
		{
			this.getNodeCoverageOf(node, node, nodesCoverage, new HashSet<>());
		}

		final HashSet<Node> fullyCoveringNodes = new HashSet<>();

		for (Node node : nodesCoverage.keySet())
		{
			if (nodesCoverage.get(node) == nodes.size())
			{
				fullyCoveringNodes.add(node);
			}
		}

		return fullyCoveringNodes;
	}

	private void getNodeCoverageOf(final Node nodeToStudy,
								   final Node currentNode,
								   final HashMap<Node, Integer> nodesCoverage,
								   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		final int nodeToStudyCoverage = nodesCoverage.getOrDefault(nodeToStudy, 0) + 1;
		nodesCoverage.put(nodeToStudy, nodeToStudyCoverage);

		for (Node child : currentNode.childNodes())
		{
			this.getNodeCoverageOf(nodeToStudy, child, nodesCoverage, visitedNodes);
		}
	}

	private ArrayList<SubGraphInformation> getBiggestDirectlyConnectedGraph(final HashSet<Node> nodes)
	{
		final ArrayList<SubGraphInformation> biggestSubGraphs = new ArrayList<>();
		int maxSize = -1;

		for (Node node : nodes)
		{
			final Node nodeCopy = node.weakCopy();
			final DependencyGraph subGraph = new DependencyGraph();
			subGraph.addInitialNode(nodeCopy);
			final SubGraphInformation subGraphInformation = new SubGraphInformation(subGraph);
			subGraphInformation.addCorrespondence(nodeCopy, node);
			this.getBiggestDirectlyConnectedGraph(nodes, node, nodeCopy, subGraphInformation, new HashSet<>());

			if (biggestSubGraphs.isEmpty())
			{
				biggestSubGraphs.add(subGraphInformation);
				maxSize = subGraph.size();
			}
			else
			{
				if (subGraph.size() > maxSize)
				{
					maxSize = subGraph.size();
					biggestSubGraphs.clear();
					biggestSubGraphs.add(subGraphInformation);
				}
				else if (subGraph.size() == maxSize)
				{
					biggestSubGraphs.add(subGraphInformation);
				}
			}
		}

		return biggestSubGraphs;
	}

	/*
		This method basically creates a dependency graph for tasks that are directly connected.
	 */
	private void getBiggestDirectlyConnectedGraph(final HashSet<Node> allNodes,
												  final Node currentNode,
												  final Node currentNodeCopy,
												  final SubGraphInformation subGraphInformation,
												  final HashSet<Node> alreadyVisitedNodes)
	{
		if (alreadyVisitedNodes.contains(currentNode))
		{
			return;
		}

		alreadyVisitedNodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			if (allNodes.contains(child))
			{
				final Node childCopy = child.weakCopy();
				subGraphInformation.addCorrespondence(childCopy, child);
				currentNodeCopy.addChild(childCopy);
				childCopy.addParent(currentNodeCopy);
				this.getBiggestDirectlyConnectedGraph(allNodes, child, childCopy, subGraphInformation, alreadyVisitedNodes);
			}
		}
	}

	private boolean assertLoopPresence(final HashSet<Node> loop)
	{
		final ArrayList<SubGraphInformation> eligibleSubGraphsInformation = this.getBiggestDirectlyConnectedGraph(loop);
		final SubGraphInformation eligibleSubGraphInformation = eligibleSubGraphsInformation.get(0);
		final DependencyGraph eligibleSubGraph = eligibleSubGraphInformation.dependencyGraph();

		if (eligibleSubGraph.size() != loop.size())
		{
			return false;
		}

		//The loop may be already handled properly
		final HashSet<Node> initialNodes = eligibleSubGraph.initialNodes();
		final HashSet<Node> finalNodes = eligibleSubGraph.finalNodes();
		boolean handledProperly = true;

		for (Node finalNode : finalNodes)
		{
			for (Node initialNode : initialNodes)
			{
				if (!finalNode.hasChild(initialNode))
				{
					handledProperly = false;
					break;
				}
			}

			if (!handledProperly)
			{
				break;
			}
		}

		return handledProperly;
	}

	private Graph convertToBpmnV1()
	{
		final HashMap<Node, Node> correspondingMergeGateways = new HashMap<>();
		final HashMap<Node, Node> tasksCorrespondences = new HashMap<>();
		final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
		final Graph graph = new Graph(startEvent);

		final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
		startEvent.addChild(firstFlow);
		firstFlow.addParent(startEvent);

		if (this.dependencyGraph.initialNodes().size() > 1)
		{
			final Node xorSplitGateway = new Node(BpmnProcessFactory.generateExclusiveGateway());
			firstFlow.addChild(xorSplitGateway);
			xorSplitGateway.addParent(firstFlow);
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node node : this.dependencyGraph.initialNodes())
			{
				final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				childFlow.addParent(xorSplitGateway);
				xorSplitGateway.addChild(childFlow);
				this.convertToBpmnV1(node, childFlow, visitedNodes, correspondingMergeGateways, tasksCorrespondences);
			}
		}
		else
		{
			//System.out.println(this.dependencyGraph.stringify(0));
			this.convertToBpmnV2(
				this.dependencyGraph.initialNodes().iterator().next(),
				firstFlow,
				new HashSet<>(),
				correspondingMergeGateways,
				tasksCorrespondences,
				this.dependencyGraph.endNodes()
			);
		}

		return graph;
	}

	private Graph convertToBpmnV1(final DependencyGraph dependencyGraph)
	{
		final HashMap<Node, Node> correspondingMergeGateways = new HashMap<>();
		final HashMap<Node, Node> tasksCorrespondences = new HashMap<>();
		final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
		final Graph graph = new Graph(startEvent);

		final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
		startEvent.addChild(firstFlow);
		firstFlow.addParent(startEvent);

		if (dependencyGraph.initialNodes().size() > 1)
		{
			final Node xorSplitGateway = new Node(BpmnProcessFactory.generateExclusiveGateway());
			firstFlow.addChild(xorSplitGateway);
			xorSplitGateway.addParent(firstFlow);
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node node : dependencyGraph.initialNodes())
			{
				final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				childFlow.addParent(xorSplitGateway);
				xorSplitGateway.addChild(childFlow);
				this.convertToBpmnV1(node, childFlow, visitedNodes, correspondingMergeGateways, tasksCorrespondences);
			}
		}
		else
		{
			//System.out.println(this.dependencyGraph.stringify(0));
			this.convertToBpmnV2(
				dependencyGraph.initialNodes().iterator().next(),
				firstFlow,
				new HashSet<>(),
				correspondingMergeGateways,
				tasksCorrespondences,
				dependencyGraph.endNodes()
			);
		}

		graph.computeEndNodes();
		return graph;
	}

	private void convertToBpmnV1(final Node currentDependencyGraphNode,
								 final Node currentBpmnNode,
								 final HashSet<Node> visitedNodes,
								 final HashMap<Node, Node> correspondingMergeGateways,
								 final HashMap<Node, Node> taskCorrespondences)
	{
		if (visitedNodes.contains(currentDependencyGraphNode))
		{
			//Finalise the loop
			final Node correspondingTask = taskCorrespondences.get(currentDependencyGraphNode);
			final Node nodeToConnect = correspondingMergeGateways.getOrDefault(correspondingTask, correspondingTask);
			currentBpmnNode.addChild(nodeToConnect);
			nodeToConnect.addParent(currentBpmnNode);

			return;
		}

		visitedNodes.add(currentDependencyGraphNode);

		final Node flowToNode;

		if (currentDependencyGraphNode.parentNodes().size() > 1)
		{
			//We should merge before connecting
			final Node mergeGateway;

			if (correspondingMergeGateways.containsKey(currentDependencyGraphNode))
			{
				//The gateway has already been added => connect to it and return
				mergeGateway = correspondingMergeGateways.get(currentDependencyGraphNode);
				currentBpmnNode.addChild(mergeGateway);
				mergeGateway.addParent(currentBpmnNode);
				return;
			}
			else
			{
				//The gateway has not been added yet => create it properly
				mergeGateway = new Node(BpmnProcessFactory.generateExclusiveGateway());
				((Gateway) mergeGateway.bpmnObject()).markAsMergeGateway();
				correspondingMergeGateways.put(currentDependencyGraphNode, mergeGateway);
				currentBpmnNode.addChild(mergeGateway);
				mergeGateway.addParent(currentBpmnNode);
				flowToNode = new Node(BpmnProcessFactory.generateSequenceFlow());
				mergeGateway.addChild(flowToNode);
				flowToNode.addParent(mergeGateway);
			}
		}
		else
		{
			flowToNode = currentBpmnNode;
		}

		//Connect the flow to the node
		final Node bpmnTask = taskCorrespondences.computeIfAbsent(currentDependencyGraphNode, n -> new Node(BpmnProcessFactory.generateTask(currentDependencyGraphNode.bpmnObject().id(), currentDependencyGraphNode.bpmnObject().name())));
		flowToNode.addChild(bpmnTask);
		bpmnTask.addParent(flowToNode);

		//Manage the children
		if (currentDependencyGraphNode.childNodes().size() > 1)
		{
			final Node splitGatewayNode = new Node(BpmnProcessFactory.generateExclusiveGateway());
			final Node splitIncFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			bpmnTask.addChild(splitIncFlow);
			splitIncFlow.addParent(bpmnTask);
			splitIncFlow.addChild(splitGatewayNode);
			splitGatewayNode.addParent(splitIncFlow);

			for (Node childNode : currentDependencyGraphNode.childNodes())
			{
				final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				splitGatewayNode.addChild(childFlow);
				childFlow.addParent(splitGatewayNode);
				this.convertToBpmnV1(childNode, childFlow, visitedNodes, correspondingMergeGateways, taskCorrespondences);
			}
		}
		else if (currentDependencyGraphNode.childNodes().size() == 1)
		{
			final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			bpmnTask.addChild(childFlow);
			childFlow.addParent(bpmnTask);
			this.convertToBpmnV1(currentDependencyGraphNode.childNodes().iterator().next(), childFlow, visitedNodes, correspondingMergeGateways, taskCorrespondences);
		}
		else
		{
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
			final Node endFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			bpmnTask.addChild(endFlow);
			endFlow.addParent(bpmnTask);
			endFlow.addChild(endEvent);
			endEvent.addParent(endFlow);
		}
	}

	private void convertToBpmnV2(final Node currentDependencyGraphNode,
								 final Node currentBpmnNode,
								 final HashSet<Node> visitedNodes,
								 final HashMap<Node, Node> correspondingMergeGateways,
								 final HashMap<Node, Node> taskCorrespondences,
								 final HashSet<Node> endNodes)
	{
		if (visitedNodes.contains(currentDependencyGraphNode))
		{
			//Finalise the loop
			final Node correspondingTask = taskCorrespondences.get(currentDependencyGraphNode);
			final Node nodeToConnect = correspondingMergeGateways.getOrDefault(correspondingTask, correspondingTask);
			currentBpmnNode.addChild(nodeToConnect);
			nodeToConnect.addParent(currentBpmnNode);

			return;
		}

		visitedNodes.add(currentDependencyGraphNode);

		final Node flowToNode;

		if (currentDependencyGraphNode.parentNodes().size() > 1)
		{
			//System.out.println("Node with multiple parents: " + currentDependencyGraphNode);
			//We should merge before connecting
			if (currentDependencyGraphNode.bpmnObject() instanceof Gateway
				&& ((Gateway) currentDependencyGraphNode.bpmnObject()).isMergeGateway())
			{
				flowToNode = currentBpmnNode;
			}
			else
			{
				final Node mergeGateway;

				if (correspondingMergeGateways.containsKey(currentDependencyGraphNode))
				{
					//The gateway has already been added => connect to it and return
					mergeGateway = correspondingMergeGateways.get(currentDependencyGraphNode);
					currentBpmnNode.addChild(mergeGateway);
					mergeGateway.addParent(currentBpmnNode);
					return;
				}
				else
				{
					//The gateway has not been added yet => create it properly
					mergeGateway = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
					correspondingMergeGateways.put(currentDependencyGraphNode, mergeGateway);
					currentBpmnNode.addChild(mergeGateway);
					mergeGateway.addParent(currentBpmnNode);
					flowToNode = new Node(BpmnProcessFactory.generateSequenceFlow());
					mergeGateway.addChild(flowToNode);
					flowToNode.addParent(mergeGateway);
				}
			}
		}
		else
		{
			flowToNode = currentBpmnNode;
		}

		//Connect the flow to the node
		final Node bpmnNode;

		if (taskCorrespondences.containsKey(currentDependencyGraphNode))
		{
			bpmnNode = taskCorrespondences.get(currentDependencyGraphNode);
		}
		else
		{
			if (currentDependencyGraphNode.bpmnObject() instanceof Task)
			{
				bpmnNode = new Node(BpmnProcessFactory.generateTask(currentDependencyGraphNode.bpmnObject().id(), currentDependencyGraphNode.bpmnObject().name()));
			}
			else
			{
				if (currentDependencyGraphNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
				{
					if (((Gateway) currentDependencyGraphNode.bpmnObject()).isSplitGateway())
					{
						bpmnNode = new Node(BpmnProcessFactory.generateParallelSplitGateway());
					}
					else
					{
						bpmnNode = new Node(BpmnProcessFactory.generateParallelMergeGateway());
					}
				}
				else
				{
					if (((Gateway) currentDependencyGraphNode.bpmnObject()).isSplitGateway())
					{
						bpmnNode = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
					}
					else
					{
						bpmnNode = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
					}
				}
			}

			taskCorrespondences.put(currentDependencyGraphNode, bpmnNode);
		}

		flowToNode.addChild(bpmnNode);
		bpmnNode.addParent(flowToNode);

		//Manage the children
		if (currentDependencyGraphNode.childNodes().size() > 1)
		{
			if (currentDependencyGraphNode.bpmnObject() instanceof Task
				|| ((Gateway) currentDependencyGraphNode.bpmnObject()).isMergeGateway())
			{
				final Node splitGatewayNode = new Node(BpmnProcessFactory.generateExclusiveGateway());
				final Node splitIncFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				bpmnNode.addChild(splitIncFlow);
				splitIncFlow.addParent(bpmnNode);
				splitIncFlow.addChild(splitGatewayNode);
				splitGatewayNode.addParent(splitIncFlow);

				if (endNodes.contains(currentDependencyGraphNode))
				{
					//TODO Check if works
					//The current node is an end node => add an end event after the split
					final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
					final Node endFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					splitGatewayNode.addChildAndForceParent(endFlow);
					endFlow.addChildAndForceParent(endEvent);
				}

				for (Node childNode : currentDependencyGraphNode.childNodes())
				{
					final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					splitGatewayNode.addChild(childFlow);
					childFlow.addParent(splitGatewayNode);
					this.convertToBpmnV2(childNode, childFlow, visitedNodes, correspondingMergeGateways, taskCorrespondences, endNodes);
				}
			}
			else
			{
				//We are already on a split gateway => no need to add a new one
				for (Node childNode : currentDependencyGraphNode.childNodes())
				{
					final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					bpmnNode.addChild(childFlow);
					childFlow.addParent(bpmnNode);
					this.convertToBpmnV2(childNode, childFlow, visitedNodes, correspondingMergeGateways, taskCorrespondences, endNodes);
				}
			}
		}
		else if (currentDependencyGraphNode.childNodes().size() == 1)
		{
			if (endNodes.contains(currentDependencyGraphNode))
			{
				//TODO Check if works
				//The current node is an end node => add an exclusive split gateway + an end event after the split
				final Node outSplitFlowLoop;

				if (currentDependencyGraphNode.bpmnObject() instanceof Gateway
					&& ((Gateway) currentDependencyGraphNode.bpmnObject()).isSplitGateway())
				{
					//We are already on a split
					outSplitFlowLoop = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node outSplitFlowEnd = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());

					bpmnNode.addChildAndForceParent(outSplitFlowLoop);
					bpmnNode.addChildAndForceParent(outSplitFlowEnd);
					outSplitFlowEnd.addChildAndForceParent(endEvent);
				}
				else
				{
					//The current node is an end node => add an exclusive split gateway + an end event after the split
					final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
					final Node incSplitFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					outSplitFlowLoop = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node outSplitFlowEnd = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());

					bpmnNode.addChildAndForceParent(incSplitFlow);
					incSplitFlow.addChildAndForceParent(exclusiveSplit);
					exclusiveSplit.addChildAndForceParent(outSplitFlowLoop);
					exclusiveSplit.addChildAndForceParent(outSplitFlowEnd);
					outSplitFlowEnd.addChildAndForceParent(endEvent);
				}

				this.convertToBpmnV2(currentDependencyGraphNode.childNodes().iterator().next(), outSplitFlowLoop, visitedNodes, correspondingMergeGateways, taskCorrespondences, endNodes);
			}
			else
			{
				final Node childFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				bpmnNode.addChild(childFlow);
				childFlow.addParent(bpmnNode);
				this.convertToBpmnV2(currentDependencyGraphNode.childNodes().iterator().next(), childFlow, visitedNodes, correspondingMergeGateways, taskCorrespondences, endNodes);
			}
		}
		else
		{
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
			final Node endFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			bpmnNode.addChild(endFlow);
			endFlow.addParent(bpmnNode);
			endFlow.addChild(endEvent);
			endEvent.addParent(endFlow);
		}
	}

	private void addAllIncompleteChoices()
	{
		//Compute all the mutual exclusions
		final HashMap<String, HashSet<String>> mutualExclusionToAppear = new HashMap<>();

		for (AbstractSyntaxTree choice : this.explicitChoices)
		{
			final AbstractSyntaxNode leftNode = choice.root().successors().get(0);
			final AbstractSyntaxNode rightNode = choice.root().successors().get(1);
			final HashSet<String> mutuallyExclusiveNodesOfLeft = mutualExclusionToAppear.computeIfAbsent(leftNode.label(), h -> new HashSet<>());
			final HashSet<String> mutuallyExclusiveNodesOfRight = mutualExclusionToAppear.computeIfAbsent(rightNode.label(), h -> new HashSet<>());
			mutuallyExclusiveNodesOfLeft.add(rightNode.label());
			mutuallyExclusiveNodesOfRight.add(leftNode.label());
		}

		//Remove all the ones having all their nodes already in the graph (~ already handled)
		for (Iterator<String> iterator = mutualExclusionToAppear.keySet().iterator(); iterator.hasNext(); )
		{
			final String nodeId = iterator.next();
			int nbMutuallyExclusiveNodesNotInTheGraph = 0;
			final HashSet<String> currentMutualExclusions = mutualExclusionToAppear.get(nodeId);

			if (!this.dependencyGraph.toSet().contains(nodeId))
			{
				nbMutuallyExclusiveNodesNotInTheGraph++;
			}

			for (String mutuallyExclusiveNode : currentMutualExclusions)
			{
				if (!this.dependencyGraph.nodesIds().contains(mutuallyExclusiveNode))
				{
					nbMutuallyExclusiveNodesNotInTheGraph++;
				}
			}

			if (nbMutuallyExclusiveNodesNotInTheGraph == 0)
			{
				//All the mutually exclusive nodes of the current node + the current node are already in the graph
				iterator.remove();
			}
		}

		/*
			Iterate over all the choices until having handled all of them.
			Choices are handled from the least number of additional tasks to be put in the graph to the most
		 */
		while (!mutualExclusionToAppear.isEmpty())
		{
			String nodeHavingTheMostAlreadyPresentMutualExclusions = null;
			int maxNumberOfHandledMutualExclusions = -1;
			final HashSet<String> alreadyHandledChoices = new HashSet<>();

			for (final String key : mutualExclusionToAppear.keySet())
			{
				final HashSet<String> mutualExclusions = mutualExclusionToAppear.get(key);
				int nbHandledMutualExclusions = 0;

				if (this.dependencyGraph.nodesIds().contains(key))
				{
					if (this.dependencyGraph.nodesIds().containsAll(mutualExclusionToAppear.get(key)))
					{
						alreadyHandledChoices.add(key);
					}

					continue;
				}

				for (String id : mutualExclusions)
				{
					if (this.dependencyGraph.nodesIds().contains(id))
					{
						nbHandledMutualExclusions++;
					}
				}

				if (maxNumberOfHandledMutualExclusions == -1)
				{
					maxNumberOfHandledMutualExclusions = nbHandledMutualExclusions;
					nodeHavingTheMostAlreadyPresentMutualExclusions = key;
				}
				else
				{
					if (nbHandledMutualExclusions > maxNumberOfHandledMutualExclusions)
					{
						maxNumberOfHandledMutualExclusions = nbHandledMutualExclusions;
						nodeHavingTheMostAlreadyPresentMutualExclusions = key;
					}
				}
			}

			for (String key : alreadyHandledChoices)
			{
				mutualExclusionToAppear.remove(key);
			}

			if (nodeHavingTheMostAlreadyPresentMutualExclusions != null)
			{
				this.addChoiceToGraph(nodeHavingTheMostAlreadyPresentMutualExclusions, mutualExclusionToAppear.get(nodeHavingTheMostAlreadyPresentMutualExclusions));
			}
		}
	}

	private void addChoiceToGraph(final String nodeToAddId,
								  final HashSet<String> mutualExclusionsOfNodeToAdd)
	{
		final HashSet<String> originalMutualExclusions = new HashSet<>(mutualExclusionsOfNodeToAdd);

		//Remove all the mutual exclusions that are not in the graph
		mutualExclusionsOfNodeToAdd.removeIf(id -> !this.dependencyGraph.nodesIds().contains(id));
		System.out.println(mutualExclusionsOfNodeToAdd);

		final Node nodeToAdd = new Node(BpmnProcessFactory.generateTask(nodeToAddId));
		final HashSet<Node> mutuallyExclusiveNodes = new HashSet<>();

		if (mutualExclusionsOfNodeToAdd.isEmpty())
		{
			//None of the mutual exclusions of the current node appear in the graph => add all of them at the beginning
			this.dependencyGraph.addInitialNode(nodeToAdd);

			for (String mutualExclusion : originalMutualExclusions)
			{
				final Node mutualExclusionOfNodeToAdd = new Node(BpmnProcessFactory.generateTask(mutualExclusion));
				this.dependencyGraph.addInitialNode(mutualExclusionOfNodeToAdd);
			}
		}
		else
		{
			for (Node node : this.dependencyGraph.toSet())
			{
				if (mutualExclusionsOfNodeToAdd.contains(node.bpmnObject().id()))
				{
					mutuallyExclusiveNodes.add(node);
				}
			}

			final HashSet<Node> firstReachableMutuallyExclusiveNodes = this.getFirstReachableMutuallyExclusiveNodes(mutuallyExclusiveNodes);
			final HashMap<Node, HashSet<Node>> firstReachableMutuallyExclusiveNodesParents = this.getFirstReachableParentsOf(firstReachableMutuallyExclusiveNodes);
			this.connectFirstReachableParentsToNodeToAdd(firstReachableMutuallyExclusiveNodesParents, nodeToAdd);
			final HashSet<Node> closestBranchableNodes = this.findClosestBranchableNodes(mutuallyExclusiveNodes, firstReachableMutuallyExclusiveNodesParents, nodeToAdd);

			for (Node closestBranchableNode : closestBranchableNodes)
			{
				nodeToAdd.addChildAndForceParent(closestBranchableNode);
			}
		}
	}

	/**
	 * This method computes a set of nodes that are the first nodes reachable from the initial nodes
	 * of the dependency graph among the mutually exclusive nodes given as input.
	 * The returned set might be identical to the input one if all the nodes are reachable "at the same time",
	 * meaning that they are not in sequence and can both be reached by following a path of the graph that is not
	 * a loop.
	 *
	 * @param mutuallyExclusiveNodes the list of nodes among which the selection should be performed
	 * @return the set of earliest nodes found
	 */
	private HashSet<Node> getFirstReachableMutuallyExclusiveNodes(final HashSet<Node> mutuallyExclusiveNodes)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();
		final HashSet<Node> firstReachableMutuallyExclusivesNodes = new HashSet<>();

		for (Node initialNode : this.dependencyGraph.initialNodes())
		{
			this.getFirstReachableMutuallyExclusiveNodes(initialNode, mutuallyExclusiveNodes, firstReachableMutuallyExclusivesNodes, visitedNodes);
		}

		return firstReachableMutuallyExclusivesNodes;
	}

	private void getFirstReachableMutuallyExclusiveNodes(final Node currentNode,
														 final HashSet<Node> mutuallyExclusiveNodes,
														 final HashSet<Node> firstReachableMutuallyExclusivesNodes,
														 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (mutuallyExclusiveNodes.contains(currentNode))
		{
			firstReachableMutuallyExclusivesNodes.add(currentNode);
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.getFirstReachableMutuallyExclusiveNodes(child, mutuallyExclusiveNodes, firstReachableMutuallyExclusivesNodes, visitedNodes);
		}
	}

	/**
	 * This method computes the set of parent nodes of a given set of nodes that are reachable first,
	 * considering the logical execution flow of the process.
	 * The result is returned as map containing as keys the nodes given as input and as values their
	 * corresponding set of first reachable parents.
	 *
	 * @param nodes the nodes to analyse
	 * @return the map containing the first reachable parents of @nodes
	 */
	private HashMap<Node, HashSet<Node>> getFirstReachableParentsOf(final HashSet<Node> nodes)
	{
		final HashMap<Node, HashSet<Node>> firstReachableParents = new HashMap<>();

		for (Node node : nodes)
		{
			final HashSet<Node> firstReachableParentsOfNode = firstReachableParents.computeIfAbsent(node, h -> new HashSet<>());
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node initialNode : this.dependencyGraph.initialNodes())
			{
				this.getFirstReachableParentsOf(initialNode, node.parentNodes(), firstReachableParentsOfNode, visitedNodes);
			}
		}

		return firstReachableParents;
	}

	private void getFirstReachableParentsOf(final Node currentNode,
											final Set<Node> parentNodes,
											final HashSet<Node> firstReachableParents,
											final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (parentNodes.contains(currentNode))
		{
			firstReachableParents.add(currentNode);
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.getFirstReachableParentsOf(child, parentNodes, firstReachableParents, visitedNodes);
		}
	}

	/**
	 * This method is used to connect each first reachable parent of each first reachable mutually exclusive node
	 * to the node to add, thus ensuring mutual exclusion between them.
	 * In some cases, the set of first reachable parents of a node may be empty (for instance, if the mutually
	 * exclusive node is an initial node of the graph.
	 * In this case, the node to add becomes an initial node of the graph, which implies that it will eventually
	 * be preceded by an exclusive split gateway when the graph will be transformed to BPMN.
	 * //TODO See if this works or if there is a need to introduce dummy nodes
	 *
	 * @param firstReachableParents the map of first reachable parents of each mutually exclusive node
	 * @param nodeToAdd the node to add to the graph
	 */
	private void connectFirstReachableParentsToNodeToAdd(final HashMap<Node, HashSet<Node>> firstReachableParents,
														 final Node nodeToAdd)
	{
		for (Node mutuallyExclusiveNode : firstReachableParents.keySet())
		{
			final HashSet<Node> firstReachableParentsOfCurrentNode = firstReachableParents.get(mutuallyExclusiveNode);

			if (firstReachableParentsOfCurrentNode.isEmpty())
			{
				//The node to add becomes an initial node
				this.dependencyGraph.addInitialNode(nodeToAdd);
			}
			else
			{
				for (Node firstReachableParent : firstReachableParentsOfCurrentNode)
				{
					//The node to add becomes a child of each first reachable parent
					firstReachableParent.addChildAndForceParent(nodeToAdd);
				}
			}
		}
	}

	private HashSet<Node> findClosestBranchableNodes(final HashSet<Node> mutuallyExclusiveNodes,
												     final HashMap<Node, HashSet<Node>> firstReachableParents,
													 final Node nodeToAdd)
	{
		final HashSet<Node> branchableNodes = new HashSet<>();
		final HashSet<Node> candidateNodes = new HashSet<>(this.dependencyGraph.toSet());
		final HashSet<Node> firstReachableParentsMerged = new HashSet<>();
		candidateNodes.removeAll(mutuallyExclusiveNodes);
		candidateNodes.remove(nodeToAdd);

		for (HashSet<Node> firstReachableParentsSet : firstReachableParents.values())
		{
			candidateNodes.removeAll(firstReachableParentsSet);
			firstReachableParentsMerged.addAll(firstReachableParentsSet);
		}

		for (Node candidate : candidateNodes)
		{
			boolean validCandidate = true;

			for (Node nodeToAddParent : nodeToAdd.parentNodes())
			{
				if (this.nodeCanReachMutuallyExclusiveNodeBeforeFiringParent(candidate, nodeToAddParent, mutuallyExclusiveNodes))
				{
					validCandidate = false;
					break;
				}
			}

			if (validCandidate)
			{
				branchableNodes.add(candidate);
			}
		}

		//Ultimately, verify that inserting the smallest version of the current choice does not have the side effect of destroying a previously added choice
		final HashSet<Node> currentBranchableNodes = this.keepOnlyFirstReachableNodesFromParentsAmong(branchableNodes, firstReachableParentsMerged);
		boolean impossibleBranching = true;

		while (impossibleBranching)
		{
			impossibleBranching = false;
			final DependencyGraph dependencyGraphCopy = this.dependencyGraph.weakCopy();
			final Node nodeToAddCopy = dependencyGraphCopy.getNodeFromID(nodeToAdd.bpmnObject().id());

			for (Node branchableNode : currentBranchableNodes)
			{
				nodeToAddCopy.addChildAndForceParent(branchableNode);

				try
				{
					this.assertMutualExclusionsUncaught(dependencyGraphCopy);
				}
				catch (BadMutualExclusionsException e)
				{
					impossibleBranching = true;
					branchableNodes.remove(branchableNode);
					break;
				}
			}

			currentBranchableNodes.clear();
			currentBranchableNodes.addAll(this.keepOnlyFirstReachableNodesFromParentsAmong(branchableNodes, firstReachableParentsMerged));
		}

		return currentBranchableNodes;
	}

	/**
	 * This method analyses a set of nodes in order to keep only the ones that are reached first
	 * in the order of execution of the process, starting from the first reachable parents.
	 *
	 * @param nodes the set of nodes to analyse
	 * @param firstReachableParents the set of first reachable parents of the node to add
	 * @return a subset of the original set of nodes containing only firstly reachable nodes
	 */
	private HashSet<Node> keepOnlyFirstReachableNodesFromParentsAmong(final HashSet<Node> nodes,
																	  final HashSet<Node> firstReachableParents)
	{
		if (nodes.size() <= 1)
		{
			return nodes;
		}

		final HashSet<Node> firstReachableNodes = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node startNode : (firstReachableParents.isEmpty() ? this.dependencyGraph.initialNodes() : firstReachableParents))
		{
			this.keepOnlyFirstReachableNodesFromParentsAmong(startNode, nodes, firstReachableNodes, visitedNodes);
		}

		return firstReachableNodes;
	}

	private void keepOnlyFirstReachableNodesFromParentsAmong(final Node currentNode,
															 final HashSet<Node> allNodes,
															 final HashSet<Node> firstReachableNodes,
															 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (allNodes.contains(currentNode))
		{
			firstReachableNodes.add(currentNode);
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.keepOnlyFirstReachableNodesFromParentsAmong(child, allNodes, firstReachableNodes, visitedNodes);
		}
	}

	/**
	 * This method performs a depth-first graph traversal to verify whether the node to add can reach
	 * at least one of its mutually exclusive nodes without reaching its firing/triggering parent first.
	 * If this is the case, weak mutual exclusion is not ensured; thus the candidate node is not valid.
	 * This algorithm is an on-the-fly algorithm because as soon as a mutually exclusive node is reached,
	 * the algorithm returns immediately.
	 *
	 * @param candidate the candidate node for branching
	 * @param parent the parent triggering the node to add
	 * @param mutuallyExclusiveNodes the set of mutually exclusive nodes of the node to add
	 * @return
	 */
	private boolean nodeCanReachMutuallyExclusiveNodeBeforeFiringParent(final Node candidate,
																		final Node parent,
																		final HashSet<Node> mutuallyExclusiveNodes)
	{
		return this.nodeCanReachMutuallyExclusiveNodeBeforeFiringParent(candidate, parent, mutuallyExclusiveNodes, new HashSet<>());
	}

	private boolean nodeCanReachMutuallyExclusiveNodeBeforeFiringParent(final Node currentNode,
																		final Node triggeringParent,
																		final HashSet<Node> mutuallyExclusiveNodes,
																		final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			//We already checked this node, thus we are in a loop in which we did not find any mutually exclusive node yet
			return false;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(triggeringParent))
		{
			//We reached back our triggering parent, thus we are safe for the next visited nodes
			return false;
		}

		if (mutuallyExclusiveNodes.contains(currentNode))
		{
			//We reached a mutually exclusive node of ours without reaching our triggering parent first, thus there is an issue
			return true;
		}

		for (Node child : currentNode.childNodes())
		{
			final boolean candidateFailedForChild = this.nodeCanReachMutuallyExclusiveNodeBeforeFiringParent(child, triggeringParent, mutuallyExclusiveNodes, visitedNodes);

			if (candidateFailedForChild)
			{
				//A recursive call found a mutually exclusive node before the triggering parent => propagate the info to upper layers
				return true;
			}
		}

		//None of the recursive calls on the children of the current node failurily reached an exclusive node => propagate the info to upper layers
		return false;
	}

	private void computeParallelsInformation()
	{
		//Retrieve original mutual exclusions
		for (AbstractSyntaxTree choiceTree : this.explicitParallels)
		{
			final String leftNodeId = choiceTree.root().successors().get(0).label();
			final String rightNodeId = choiceTree.root().successors().get(1).label();
			final HashSet<String> leftNodeParallelNodes = this.parallelNodes.computeIfAbsent(leftNodeId, h -> new HashSet<>());
			final HashSet<String> rightNodeParallelNodes = this.parallelNodes.computeIfAbsent(rightNodeId, h -> new HashSet<>());
			leftNodeParallelNodes.add(rightNodeId);
			rightNodeParallelNodes.add(leftNodeId);
		}
	}

	private void computeChoicesInformation()
	{
		//Retrieve original mutual exclusions
		for (AbstractSyntaxTree choiceTree : this.explicitChoices)
		{
			final String leftNodeId = choiceTree.root().successors().get(0).label();
			final String rightNodeId = choiceTree.root().successors().get(1).label();
			final HashSet<String> leftNodeMutuallyExclusiveNodes = this.originalMutualExclusions.computeIfAbsent(leftNodeId, h -> new HashSet<>());
			final HashSet<String> rightNodeMutuallyExclusiveNodes = this.originalMutualExclusions.computeIfAbsent(rightNodeId, h -> new HashSet<>());
			leftNodeMutuallyExclusiveNodes.add(rightNodeId);
			rightNodeMutuallyExclusiveNodes.add(leftNodeId);
		}

		System.out.println();
		System.out.println("Original mutual exclusions: " + this.originalMutualExclusions);
		//System.out.println();

		//Retrieve impossible mutual exclusions (all choices - ones concerning not added nodes - already handled ones)
		final HashMap<String, HashSet<String>> currentMutualExclusions = this.getCurrentMutuallyExclusiveNodes(this.dependencyGraph.nodesIds(), this.computeAllPaths(this.dependencyGraph));

		for (String nodeId : this.originalMutualExclusions.keySet())
		{
			//Do not consider nodes that are not already in the graph
			if (this.dependencyGraph.nodesIds().contains(nodeId))
			{
				final HashSet<String> expectedMutuallyExclusiveNodes = this.originalMutualExclusions.get(nodeId);
				final HashSet<String> effectiveMutuallyExclusiveNodes = currentMutualExclusions.get(nodeId);
				final HashSet<String> impossiblyMutuallyExclusiveNodes = new HashSet<>(expectedMutuallyExclusiveNodes);
				//impossible = expected - effective - not added yet
				impossiblyMutuallyExclusiveNodes.removeAll(effectiveMutuallyExclusiveNodes);
				impossiblyMutuallyExclusiveNodes.removeIf(s -> !this.dependencyGraph.nodesIds().contains(s));

				if (!impossiblyMutuallyExclusiveNodes.isEmpty())
				{
					this.impossibleMutualExclusions.put(nodeId, impossiblyMutuallyExclusiveNodes);
				}
			}
		}

		System.out.println("Impossible mutual exclusions: " + this.impossibleMutualExclusions);
		//System.out.println();

		//Retrieve possible mutual exclusions (original ones - impossible ones)
		for (String nodeId : this.originalMutualExclusions.keySet())
		{
			final HashSet<String> possibleMutualExclusions = new HashSet<>(this.originalMutualExclusions.get(nodeId));
			final HashSet<String> impossibleMutualExclusions = this.impossibleMutualExclusions.get(nodeId);

			if (impossibleMutualExclusions != null)
			{
				possibleMutualExclusions.removeAll(impossibleMutualExclusions);
			}

			if (!possibleMutualExclusions.isEmpty())
			{
				this.mutualExclusionsToEnsure.put(nodeId, possibleMutualExclusions);
			}
		}

		System.out.println("Mutual exclusions to ensure: " + this.mutualExclusionsToEnsure);
		System.out.println();
	}

	private void assertMutualExclusions(final DependencyGraph dependencyGraph)
	{
		try
		{
			assertMutualExclusionsUncaught(dependencyGraph);
		}
		catch (BadMutualExclusionsException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void assertMutualExclusionsUncaught(final DependencyGraph dependencyGraph) throws BadMutualExclusionsException
	{
		final ArrayList<Path<Node>> allPaths = this.computeAllPaths(dependencyGraph);
		final HashMap<String, HashSet<String>> currentMutuallyExclusiveNodes = this.getCurrentMutuallyExclusiveNodes(this.dependencyGraph.nodesIds(), allPaths);

		for (String nodeId : this.mutualExclusionsToEnsure.keySet())
		{
			final HashSet<String> mutualExclusionsOfCurrentNode = this.mutualExclusionsToEnsure.get(nodeId);

			if (!currentMutuallyExclusiveNodes.get(nodeId).containsAll(mutualExclusionsOfCurrentNode))
			{
				throw new BadMutualExclusionsException("Node \"" + nodeId + "\" is supposed to be in mutual exclusions with nodes " +
						currentMutuallyExclusiveNodes.get(nodeId) + " but is only in mutual exclusions with nodes " +
						mutualExclusionsOfCurrentNode + " !!!!");
			}
		}
	}

	private HashMap<String, HashSet<String>> getCurrentMutuallyExclusiveNodes(final HashSet<String> nodeIds,
																			  final ArrayList<Path<Node>> allPaths)
	{
		final HashMap<String, HashSet<String>> currentMutuallyExclusiveNodes = new HashMap<>();

		for (String nodeId : nodeIds)
		{
			currentMutuallyExclusiveNodes.put(nodeId, new HashSet<>(nodeIds));
		}

		if (MUTUAL_EXCLUSION == MutualExclusionMode.WEAK_MUTUAL_EXCLUSION)
		{
			/*
				Most common case where two tasks are considered to be mutually exclusive if there is no path
				containing both tasks.
				Computing this basically consists in iterating over the paths once and removing the whole path
				from the list of mutually exclusive nodes for each task found in the path.
			 */
			for (Path<Node> path : allPaths)
			{
				final HashSet<String> pathNodesIds = new HashSet<>();

				for (Node node : path.unsortedElements())
				{
					pathNodesIds.add(node.bpmnObject().id());
				}

				for (Node node : path.unsortedElements())
				{
					currentMutuallyExclusiveNodes.get(node.bpmnObject().id()).removeAll(pathNodesIds);
				}
			}
		}
		else
		{
			//TODO
		}

		return currentMutuallyExclusiveNodes;
	}

	private Node getNodeOfId(final String id)
	{
		for (Node node : this.dependencyGraph.toSet())
		{
			if (node.bpmnObject().id().equals(id))
			{
				return node;
			}
		}

		throw new RuntimeException("No node of id \"" + id + "\" found in the list of nodes!");
	}

	private void addExclusiveGatewaysAndDummyStartEndsIfNeeded()
	{
		final HashSet<Node> implicitSplits = new HashSet<>();
		final HashSet<Node> implicitMerges = new HashSet<>();
		final HashSet<Node> visitedNodes = new HashSet<>();

		//Add an exclusive split before the initial nodes
		if (this.dependencyGraph.initialNodes().size() > 1)
		{
			final Node explicitSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());

			for (Node initialNode : this.dependencyGraph.initialNodes())
			{
				explicitSplit.addChildAndForceParent(initialNode);
			}

			this.dependencyGraph.initialNodes().clear();
			this.dependencyGraph.addInitialNode(explicitSplit);
		}

		//Add an exclusive merge after the end nodes
		if (this.dependencyGraph.endNodes().size() > 1)
		{
			final Node explicitMerge = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
			//System.out.println("Explicit merge: " + explicitMerge.bpmnObject().name());

			for (Node endNode : this.dependencyGraph.endNodes())
			{
				endNode.addChildAndForceParent(explicitMerge);
			}

			this.dependencyGraph.endNodes().clear();
			this.dependencyGraph.addEndNode(explicitMerge);
		}

		for (Node initialNode : this.dependencyGraph.initialNodes())
		{
			this.retrieveImplicitExclusivePositions(initialNode, visitedNodes, implicitSplits, implicitMerges);
		}

		for (Node implicitSplit : implicitSplits)
		{
			final Node explicitSplit = new Node(BpmnProcessFactory.generateExclusiveSplitGateway());
			final Set<Node> childNodes = new HashSet<>(implicitSplit.childNodes());

			for (Node child : childNodes)
			{
				implicitSplit.removeChildAndForceParent(child);
				explicitSplit.addChildAndForceParent(child);
			}

			implicitSplit.addChildAndForceParent(explicitSplit);
		}

		for (Node implicitMerge : implicitMerges)
		{
			final Node explicitMerge = new Node(BpmnProcessFactory.generateExclusiveMergeGateway());
			final Set<Node> parentNodes = new HashSet<>(implicitMerge.parentNodes());

			for (Node parent : parentNodes)
			{
				parent.removeChildAndForceParent(implicitMerge);
				parent.addChildAndForceParent(explicitMerge);
			}

			explicitMerge.addChildAndForceParent(implicitMerge);
		}
	}

	private void retrieveImplicitExclusivePositions(final Node currentNode,
									  				final HashSet<Node> visitedNodes,
													final HashSet<Node> implicitSplits,
													final HashSet<Node> implicitMerges)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			if (currentNode.parentNodes().size() > 1)
			{
				implicitMerges.add(currentNode);
			}

			if (currentNode.childNodes().size() > 1)
			{
				implicitSplits.add(currentNode);
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.retrieveImplicitExclusivePositions(child, visitedNodes, implicitSplits, implicitMerges);
		}
	}

	//Sub-classes

	private static class SubGraphInformation
	{
		private final DependencyGraph dependencyGraph;
		private final HashMap<Node, Node> copyToOriginalCorrespondences;

		SubGraphInformation(final DependencyGraph dependencyGraph)
		{
			this.dependencyGraph = dependencyGraph;
			this.copyToOriginalCorrespondences = new HashMap<>();
		}

		public void addCorrespondence(final Node copyNode,
									  final Node originalNode)
		{
			this.copyToOriginalCorrespondences.put(copyNode, originalNode);
		}

		public DependencyGraph dependencyGraph()
		{
			return this.dependencyGraph;
		}

		public HashMap<Node, Node> getCopyToOriginalCorrespondences()
		{
			return this.copyToOriginalCorrespondences;
		}

		public Node getOriginalVersionOf(final Node node)
		{
			return this.copyToOriginalCorrespondences.get(node);
		}
	}
}
