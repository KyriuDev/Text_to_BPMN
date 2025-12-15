package refactoring.legacy.dependencies;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import other.Utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class DependencyGraphToBPMN
{
	private DependencyGraphToBPMN()
	{

	}

	public static Graph convert(final Collection<DependencyGraph> dependencyGraphs,
								final HashSet<Node> freeTasks)
	{
		if (dependencyGraphs.isEmpty())
		{
			final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelGateway());
			final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
			((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
			final Node finalFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			startEvent.addChild(sequenceFlow);
			sequenceFlow.addParent(startEvent);
			sequenceFlow.addChild(parallelSplit);
			parallelSplit.addParent(sequenceFlow);
			parallelMerge.addChild(finalFlow);
			finalFlow.addParent(parallelMerge);
			finalFlow.addChild(endEvent);
			endEvent.addParent(finalFlow);
			
			for (Node freeTask : freeTasks)
			{
				final Node beforeFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				final Node afterFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				parallelSplit.addChild(beforeFlow);
				beforeFlow.addParent(parallelSplit);
				beforeFlow.addChild(freeTask);
				freeTask.addParent(beforeFlow);
				freeTask.addChild(afterFlow);
				afterFlow.addParent(freeTask);
				afterFlow.addChild(parallelMerge);
				parallelMerge.addParent(afterFlow);
			}
			
			return new Graph(startEvent);
		}
		else if (dependencyGraphs.size() == 1)
		{
			final Graph bpmnWithoutStartAndEnd = DependencyGraphToBPMN.convertDependencyGraphToBPMN(dependencyGraphs.iterator().next());
			final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			startEvent.addChild(sequenceFlow);
			sequenceFlow.addParent(startEvent);
			final HashSet<Node> leafs = new HashSet<>();
			DependencyGraphToBPMN.retrieveLeafs(bpmnWithoutStartAndEnd.initialNode(), leafs);
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());

			if (freeTasks.isEmpty())
			{
				sequenceFlow.addChild(bpmnWithoutStartAndEnd.initialNode());
				bpmnWithoutStartAndEnd.initialNode().addParent(sequenceFlow);

				if (leafs.isEmpty()) throw new IllegalStateException();

				if (leafs.size() == 1)
				{
					final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					leafs.iterator().next().addChild(lastFlow);
					lastFlow.addParent(leafs.iterator().next());
					lastFlow.addChild(endEvent);
					endEvent.addParent(lastFlow);
				}
				else
				{
					final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
					((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
					final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					parallelMerge.addChild(lastFlow);
					lastFlow.addParent(parallelMerge);
					lastFlow.addChild(endEvent);
					endEvent.addParent(lastFlow);

					for (Node leaf : leafs)
					{
						final Node currentFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						leaf.addChild(currentFlow);
						currentFlow.addParent(leaf);
						currentFlow.addChild(parallelMerge);
						parallelMerge.addParent(currentFlow);
					}
				}
			}
			else
			{
				final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
				((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
				final Node finalFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				parallelMerge.addChild(finalFlow);
				finalFlow.addParent(parallelMerge);
				finalFlow.addChild(endEvent);
				endEvent.addParent(finalFlow);

				if (bpmnWithoutStartAndEnd.initialNode().bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
				{
					sequenceFlow.addChild(bpmnWithoutStartAndEnd.initialNode());
					bpmnWithoutStartAndEnd.initialNode().addParent(sequenceFlow);

					for (Node task : freeTasks)
					{
						final Node splitFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						bpmnWithoutStartAndEnd.initialNode().addChild(splitFlow);
						splitFlow.addParent(bpmnWithoutStartAndEnd.initialNode());
						splitFlow.addChild(task);
						task.addParent(splitFlow);

						final Node mergeFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						task.addChild(mergeFlow);
						mergeFlow.addParent(task);
						mergeFlow.addChild(parallelMerge);
						parallelMerge.addParent(mergeFlow);
					}
				}
				else
				{
					final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelGateway());
					sequenceFlow.addChild(parallelSplit);
					parallelSplit.addParent(sequenceFlow);

					final Node mainSplitFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					parallelSplit.addChild(mainSplitFlow);
					mainSplitFlow.addParent(parallelSplit);
					mainSplitFlow.addChild(bpmnWithoutStartAndEnd.initialNode());
					bpmnWithoutStartAndEnd.initialNode().addParent(mainSplitFlow);

					for (Node task : freeTasks)
					{
						final Node splitFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						parallelSplit.addChild(splitFlow);
						splitFlow.addParent(parallelSplit);
						splitFlow.addChild(task);
						task.addParent(splitFlow);

						final Node mergeFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						task.addChild(mergeFlow);
						mergeFlow.addParent(task);
						mergeFlow.addChild(parallelMerge);
						parallelMerge.addParent(mergeFlow);
					}
				}

				for (Node leaf : leafs)
				{
					final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					leaf.addChild(lastFlow);
					lastFlow.addParent(leaf);
					lastFlow.addChild(parallelMerge);
					parallelMerge.addParent(lastFlow);
				}
			}
			return new Graph(startEvent);
		}
		else
		{
			final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
			final Node parallelSplitGateway = new Node(BpmnProcessFactory.generateParallelGateway());
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			startEvent.addChild(sequenceFlow);
			sequenceFlow.addParent(startEvent);
			sequenceFlow.addChild(parallelSplitGateway);
			parallelSplitGateway.addParent(sequenceFlow);
			final HashSet<Node> leafs = new HashSet<>();

			for (DependencyGraph dependencyGraph : dependencyGraphs)
			{
				final Graph correspondingGraph = DependencyGraphToBPMN.convertDependencyGraphToBPMN(dependencyGraph);
				DependencyGraphToBPMN.retrieveLeafs(correspondingGraph.initialNode(), leafs);
				final Node sequenceFlow2 = new Node(BpmnProcessFactory.generateSequenceFlow());
				parallelSplitGateway.addChild(sequenceFlow2);
				sequenceFlow2.addParent(parallelSplitGateway);
				sequenceFlow2.addChild(correspondingGraph.initialNode());
				correspondingGraph.initialNode().addParent(sequenceFlow2);
			}

			final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
			((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
			final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			parallelMerge.addChild(lastFlow);
			lastFlow.addParent(parallelMerge);
			lastFlow.addChild(endEvent);
			endEvent.addParent(lastFlow);

			for (Node leaf : leafs)
			{
				final Node flow = new Node(BpmnProcessFactory.generateSequenceFlow());
				leaf.addChild(flow);
				flow.addParent(leaf);
				flow.addChild(parallelMerge);
				parallelMerge.addParent(flow);
			}

			for (Node freeTask : freeTasks)
			{
				final Node splitFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				parallelSplitGateway.addChild(splitFlow);
				splitFlow.addParent(parallelSplitGateway);
				splitFlow.addChild(freeTask);
				freeTask.addParent(splitFlow);

				final Node mergeFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				freeTask.addChild(mergeFlow);
				mergeFlow.addParent(freeTask);
				mergeFlow.addChild(parallelMerge);
				parallelMerge.addParent(mergeFlow);
			}

			return new Graph(startEvent);
		}
	}

	//Private methods

	private static Graph convertDependencyGraphToBPMN(final DependencyGraph dependencyGraph)
	{
		final Graph graph;
		final Node firstNode;
		final HashMap<Node, Node> nextNodes = new HashMap<>();

		if (dependencyGraph.initialNodes().size() == 1)
		{
			firstNode = dependencyGraph.initialNodes().iterator().next().weakCopy();
			nextNodes.put(firstNode, dependencyGraph.initialNodes().iterator().next());
		}
		else
		{
			firstNode = new Node(BpmnProcessFactory.generateParallelGateway());

			for (Node node : dependencyGraph.initialNodes())
			{
				final Node initialNodeCopy = node.weakCopy();
				final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				firstNode.addChild(sequenceFlow);
				sequenceFlow.addParent(firstNode);
				sequenceFlow.addChild(initialNodeCopy);
				initialNodeCopy.addParent(sequenceFlow);
				nextNodes.put(initialNodeCopy, node);
			}
		}

		graph = new Graph(firstNode);
		final HashMap<Node, Node> correspondenceNodeParallelMerge = new HashMap<>();
		final HashMap<String, Node> correspondenceNodeCleanMerge = new HashMap<>();

		for (Node bpmnNode : nextNodes.keySet())
		{
			final Node dependencyNode = nextNodes.get(bpmnNode);
			DependencyGraphToBPMN.generateBPMNRecursively(bpmnNode, dependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
		}

		return graph;
	}

	private static void generateBPMNRecursively(final Node bpmnNode,
												final Node dependencyNode,
												final HashMap<Node, Node> correspondenceNodeParallelMerge,
												final HashMap<String, Node> correspondenceNodeCleanMerge)
	{
		if (dependencyNode.hasChildren())
		{
			if (dependencyNode.childNodes().size() == 1)
			{
				final Node nextDependencyNode = dependencyNode.childNodes().iterator().next();
				final Node nextBpmnNode = nextDependencyNode.weakCopy();

				if (nextDependencyNode.parentNodes().size() == 1)
				{
					//In this case, we can connect the nodes directly
					final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					bpmnNode.addChild(sequenceFlow);
					sequenceFlow.addParent(bpmnNode);
					sequenceFlow.addChild(nextBpmnNode);
					nextBpmnNode.addParent(sequenceFlow);

					DependencyGraphToBPMN.generateBPMNRecursively(nextBpmnNode, nextDependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
				}
				else
				{
					//Here, we need to add a parallel merge gateway to manage the multiple parents
					if (correspondenceNodeParallelMerge.containsKey(nextDependencyNode))
					{
						//If the parallel merge gateway already exists, we just connect it to the current node
						final Node parallelMerge = correspondenceNodeParallelMerge.get(nextDependencyNode);
						final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						bpmnNode.addChild(sequenceFlow);
						sequenceFlow.addParent(bpmnNode);
						sequenceFlow.addChild(parallelMerge);
						parallelMerge.addParent(sequenceFlow);
					}
					else
					{
						final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
						final Node sequenceFlow1 = new Node(BpmnProcessFactory.generateSequenceFlow());
						final Node sequenceFlow2 = new Node(BpmnProcessFactory.generateSequenceFlow());
						((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
						correspondenceNodeParallelMerge.put(nextDependencyNode, parallelMerge);
						bpmnNode.addChild(sequenceFlow1);
						sequenceFlow1.addParent(bpmnNode);
						sequenceFlow1.addChild(parallelMerge);
						parallelMerge.addParent(sequenceFlow1);

						parallelMerge.addChild(sequenceFlow2);
						sequenceFlow2.addParent(parallelMerge);
						sequenceFlow2.addChild(nextBpmnNode);
						nextBpmnNode.addParent(sequenceFlow2);

						DependencyGraphToBPMN.generateBPMNRecursively(nextBpmnNode, nextDependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
					}
				}
			}
			else
			{
				/*
					Here, we need to check whether the current branch can be properly closed or not, i.e., if
					all the successors of the predecessors of the successors of the current node are equal.
					If this is the case, we can insert a beautiful parallel merge instead of an ugly interleaving.
				*/

				boolean canBeMerged = true;
				final Set<Node> successors = dependencyNode.childNodes();
				final Set<Set<Node>> successorsPredecessors = new HashSet<>();

				for (Node node : successors)
				{
					successorsPredecessors.add(node.parentNodes());
				}

				if (Utils.setsAreEqual(successorsPredecessors))
				{
					final Set<Set<Node>> successorsPredecessorsSuccessors = new HashSet<>();

					//Works as they are all equal
					for (Node node : successorsPredecessors.iterator().next())
					{
						successorsPredecessorsSuccessors.add(node.childNodes());
					}

					if (!Utils.setsAreEqual(successorsPredecessorsSuccessors))
					{
						canBeMerged = false;
					}
				}
				else
				{
					canBeMerged = false;
				}

				if (canBeMerged && successorsPredecessors.iterator().next().size() > 1)
				{
					/*
						This means that succ(pred(succ(dependencyNode))) are always equal
						and that successors of the current node have several predecessors
					 */
					if (correspondenceNodeCleanMerge.containsKey(dependencyNode.bpmnObject().name()))
					{
						final Node mergeGateway = correspondenceNodeCleanMerge.get(dependencyNode.bpmnObject().name());
						final Node flow = new Node(BpmnProcessFactory.generateSequenceFlow());
						bpmnNode.addChild(flow);
						flow.addParent(bpmnNode);
						flow.addChild(mergeGateway);
						mergeGateway.addParent(flow);
					}
					else
					{
						final Set<Node> commonPredecessors = successorsPredecessors.iterator().next();
						final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
						((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();

						for (Node commonPredecessor : commonPredecessors)
						{
							correspondenceNodeCleanMerge.put(commonPredecessor.bpmnObject().name(), parallelMerge);
						}

						final Node flow = new Node(BpmnProcessFactory.generateSequenceFlow());
						bpmnNode.addChild(flow);
						flow.addParent(bpmnNode);
						flow.addChild(parallelMerge);
						parallelMerge.addParent(flow);

						final Node parallelSplit2 = new Node(BpmnProcessFactory.generateParallelGateway());
						final Node flow2 = new Node(BpmnProcessFactory.generateSequenceFlow());
						parallelMerge.addChild(flow2);
						flow2.addParent(parallelMerge);
						flow2.addChild(parallelSplit2);
						parallelSplit2.addParent(flow2);

						for (Node nextDependencyNode : dependencyNode.childNodes())
						{
							final Node nextBpmnNode = nextDependencyNode.weakCopy();
							final Node nextFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
							parallelSplit2.addChild(nextFlow);
							nextFlow.addParent(parallelSplit2);
							nextFlow.addChild(nextBpmnNode);
							nextBpmnNode.addParent(nextFlow);

							DependencyGraphToBPMN.generateBPMNRecursively(nextBpmnNode, nextDependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
						}
					}
				}
				else
				{
					//Here we need to add a parallel split gateway before each child
					final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelGateway());
					final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					bpmnNode.addChild(sequenceFlow);
					sequenceFlow.addParent(bpmnNode);
					sequenceFlow.addChild(parallelSplit);
					parallelSplit.addParent(sequenceFlow);

					for (Node nextDependencyNode : dependencyNode.childNodes())
					{
						final Node nextBpmnNode = nextDependencyNode.weakCopy();

						if (nextDependencyNode.parentNodes().size() == 1)
						{
							//In this case, we can connect the nodes directly
							final Node flow = new Node(BpmnProcessFactory.generateSequenceFlow());
							parallelSplit.addChild(flow);
							flow.addParent(parallelSplit);
							flow.addChild(nextBpmnNode);
							nextBpmnNode.addParent(flow);

							DependencyGraphToBPMN.generateBPMNRecursively(nextBpmnNode, nextDependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
						}
						else
						{
							//Here, we need to add a parallel merge gateway to manage the multiple parents
							if (correspondenceNodeParallelMerge.containsKey(nextDependencyNode))
							{
								//If the parallel merge gateway already exists, we just connect it to the current node
								final Node parallelMerge = correspondenceNodeParallelMerge.get(nextDependencyNode);
								final Node flow = new Node(BpmnProcessFactory.generateSequenceFlow());
								parallelSplit.addChild(flow);
								flow.addParent(parallelSplit);
								flow.addChild(parallelMerge);
								parallelMerge.addParent(flow);
							}
							else
							{
								final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
								final Node flow1 = new Node(BpmnProcessFactory.generateSequenceFlow());
								final Node flow2 = new Node(BpmnProcessFactory.generateSequenceFlow());
								((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();
								correspondenceNodeParallelMerge.put(nextDependencyNode, parallelMerge);
								parallelSplit.addChild(flow1);
								flow1.addParent(parallelSplit);
								flow1.addChild(parallelMerge);
								parallelMerge.addParent(flow1);

								parallelMerge.addChild(flow2);
								flow2.addParent(parallelMerge);
								flow2.addChild(nextBpmnNode);
								nextBpmnNode.addParent(flow2);

								DependencyGraphToBPMN.generateBPMNRecursively(nextBpmnNode, nextDependencyNode, correspondenceNodeParallelMerge, correspondenceNodeCleanMerge);
							}
						}
					}
				}
			}
		}
		/*else
		{
			final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			bpmnNode.addChild(sequenceFlow);
			sequenceFlow.addParent(bpmnNode);
			sequenceFlow.addChild(endEvent);
			endEvent.addParent(sequenceFlow);
		}*/
	}

	private static void retrieveLeafs(final Node currentNode,
									  final HashSet<Node> leafs)
	{
		if (!currentNode.hasChildren())
		{
			leafs.add(currentNode);
		}
		
		for (Node child : currentNode.childNodes())
		{
			DependencyGraphToBPMN.retrieveLeafs(child, leafs);
		}
	}
}
