package chat_gpt.ast_management;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import bpmn.types.process.Task;
import bpmn.types.process.events.Event;
import other.Pair;

import java.util.HashSet;
import java.util.Set;

public class EndEventsManager
{
	private static final boolean WAIT_LOOP_EXIT_BEFORE_TERMINATING = false;

	private EndEventsManager()
	{

	}

	public static void manage(final Graph bpmnProcess)
	{
		final HashSet<Node> endTasks = new HashSet<>();
		EndEventsManager.computeEndTasks(bpmnProcess.initialNode(), endTasks, new HashSet<>());
		EndEventsManager.replaceEndTasksByEndEvents(endTasks, bpmnProcess.initialNode());
		EndEventsManager.removeDeadNodes(bpmnProcess);
		EndEventsManager.collapseGateways(bpmnProcess);
	}

	//Private methods

	private static void computeEndTasks(final Node currentNode,
										final HashSet<Node> endTasks,
										final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return;
		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject() instanceof Task
			&& currentNode.bpmnObject().id().toLowerCase().contains("endevent"))
		{
			endTasks.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			EndEventsManager.computeEndTasks(child, endTasks, visitedNodes);
		}
	}

	private static void replaceEndTasksByEndEvents(final HashSet<Node> endTasks,
												   final Node initialNode)
	{
		for (Node endTask : endTasks)
		{
			if (endTask.isInLoop())
			{
				/*
					If the end task is in loop we could either break the loop with the end event,
					or wait for the loop to complete before terminating.
					I don't know what is the best option here.
				 */
				if (WAIT_LOOP_EXIT_BEFORE_TERMINATING)
				{
					//We put the end event after the execution of the loop and we unplug the task
					final Node loopExit = EndEventsManager.findLoopExit(endTask, new HashSet<>());
					if (loopExit == null) throw new IllegalStateException();

					final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
					final Node endEventFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					loopExit.addChild(endEventFlow);
					endEventFlow.addParent(loopExit);
					endEventFlow.addChild(endEvent);
					endEvent.addParent(endEventFlow);

					final Node endTaskParentFlow = endTask.parentNodes().iterator().next();
					final Node endTaskChildFlow = endTask.childNodes().iterator().next();
					final Node endTaskChildFlowChild = endTaskChildFlow.childNodes().iterator().next();

					//parentFlow --X--> endTask
					endTaskParentFlow.removeChildren(endTask);
					endTask.removeParent(endTaskParentFlow);
					//childFlow --X--> childFlowChild
					endTaskChildFlowChild.removeParent(endTaskChildFlow);
					endTaskChildFlow.removeChildren(endTaskChildFlowChild);
					//parentFlow ----> childFlowChild
					endTaskParentFlow.addChild(endTaskChildFlowChild);
					endTaskChildFlowChild.addParent(endTaskParentFlow);
				}
				else
				{
					//We replace the end task by an end event without waiting for the loop to terminate
					final Node exclusiveSplitGateway = new Node(BpmnProcessFactory.generateExclusiveGateway());
					final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
					final Node gatewayToEndEventFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node gatewayToChildFlowChildFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
					exclusiveSplitGateway.addChild(gatewayToEndEventFlow);
					gatewayToEndEventFlow.addParent(exclusiveSplitGateway);
					gatewayToEndEventFlow.addChild(endEvent);
					endEvent.addParent(gatewayToEndEventFlow);
					exclusiveSplitGateway.addChild(gatewayToChildFlowChildFlow);
					gatewayToChildFlowChildFlow.addParent(exclusiveSplitGateway);

					final Node endTaskParentFlow = endTask.parentNodes().iterator().next();
					final Node endTaskChildFlow = endTask.childNodes().iterator().next();
					final Node endTaskChildFlowChild = endTaskChildFlow.childNodes().iterator().next();

					//parentFlow --X--> endTask
					endTaskParentFlow.removeChildren(endTask);
					endTask.removeParent(endTaskParentFlow);
					//childFlow --X--> childFlowChild
					endTaskChildFlowChild.removeParent(endTaskChildFlow);
					endTaskChildFlow.removeChildren(endTaskChildFlowChild);
					//gatewayToChildFlowChildFlow ----> endTaskChildFlowChild
					gatewayToChildFlowChildFlow.addChild(endTaskChildFlowChild);
					endTaskChildFlowChild.addParent(gatewayToChildFlowChildFlow);
					//parentFlow ----> exclusiveSplitGateway
					endTaskParentFlow.addChild(exclusiveSplitGateway);
					exclusiveSplitGateway.addParent(endTaskParentFlow);
				}
			}
			else
			{
				final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());
				final Node endEventIncomingFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				final Node endTaskParentFlow = endTask.parentNodes().iterator().next();
				final Node endTaskChildFlow = endTask.childNodes().iterator().next();
				final Node endTaskChildFlowChild = endTaskChildFlow.childNodes().iterator().next();
				final HashSet<Node> visitedNodes = new HashSet<>();
				visitedNodes.add(endTask);
				final Node closestNextTask = EndEventsManager.getClosestNextTask(endTaskChildFlow, visitedNodes);

				if (closestNextTask == null)
				{
					//There is no task after our end task => we can safely replace it by an end event, whatever happens after
					endTaskParentFlow.removeChildren(endTask);
					endTask.removeParent(endTaskParentFlow);
					endTaskParentFlow.addChild(endEvent);
					endEvent.addParent(endTaskParentFlow);
				}
				else
				{
					/*
						There is a task after our end task => We must check whether that task can reach the initial
						event of the process while removing the end task.
						If yes, the end task can safely be replaced by an end event.
						Otherwise, we have to replace the end task by a choice between pursuing the execution of the
						process or ending it. (SHOULD NEVER HAPPEN IF THE PROCESS IS BUILT CORRECTLY!!!)
					 */
					endTaskChildFlow.removeParent(endTask);
					endTask.removeChildren(endTaskChildFlow);
					final boolean closestNextTaskCanReachInitialEvent = closestNextTask.hasAncestor(initialNode);
					endTaskChildFlow.addParent(endTask);
					endTask.addChild(endTaskChildFlow);

					if (closestNextTaskCanReachInitialEvent)
					{
						endTaskParentFlow.removeChildren(endTask);
						endTask.removeParent(endTaskParentFlow);
						endTaskParentFlow.addChild(endEvent);
						endEvent.addParent(endTaskParentFlow);
					}
					else
					{
						final Node exclusiveSplitGateway = new Node(BpmnProcessFactory.generateExclusiveGateway());
						final Node gatewayToEndEventFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						final Node gatewayToChildFlowChildFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
						exclusiveSplitGateway.addChild(gatewayToEndEventFlow);
						gatewayToEndEventFlow.addParent(exclusiveSplitGateway);
						gatewayToEndEventFlow.addChild(endEvent);
						endEvent.addParent(gatewayToEndEventFlow);
						exclusiveSplitGateway.addChild(gatewayToChildFlowChildFlow);
						gatewayToChildFlowChildFlow.addParent(exclusiveSplitGateway);

						//parentFlow --X--> endTask
						endTaskParentFlow.removeChildren(endTask);
						endTask.removeParent(endTaskParentFlow);
						//childFlow --X--> childFlowChild
						endTaskChildFlowChild.removeParent(endTaskChildFlow);
						endTaskChildFlow.removeChildren(endTaskChildFlowChild);
						//gatewayToChildFlowChildFlow ----> endTaskChildFlowChild
						gatewayToChildFlowChildFlow.addChild(endTaskChildFlowChild);
						endTaskChildFlowChild.addParent(gatewayToChildFlowChildFlow);
						//parentFlow ----> exclusiveSplitGateway
						endTaskParentFlow.addChild(exclusiveSplitGateway);
						exclusiveSplitGateway.addParent(endTaskParentFlow);
					}
				}
			}
		}
	}

	/**
	 * This method traverses the graph and removes all the empty paths of the gateways.
	 * It can even remove the gateway if there is no path to be removed.
	 */
	private static void clearGateways(final Graph graph)
	{
		final HashSet<Node> clearedGateways = new HashSet<>();

		do
		{
			clearedGateways.clear();
			EndEventsManager.clearGateways(graph.initialNode(), clearedGateways, new HashSet<>());
		}
		while (!clearedGateways.isEmpty());
	}

	private static void clearGateways(final Node currentNode,
									  final HashSet<Node> clearedGateways,
									  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return;
		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject() instanceof Gateway)
		{
			final Gateway gateway = (Gateway) currentNode.bpmnObject();

			if (gateway.isSplitGateway())
			{
				final HashSet<Node> removableFlows = new HashSet<>();

				for (Node flow : currentNode.childNodes())
				{
					final Node child = flow.childNodes().iterator().next();

					if (child.bpmnObject() instanceof Gateway
						&& child.bpmnObject().type() == gateway.type()
						&& ((Gateway) child.bpmnObject()).isMergeGateway())
					{
						clearedGateways.add(currentNode);
						removableFlows.add(child);
					}
				}

				if (currentNode.childNodes().size() - removableFlows.size() > 1)
				{
					final HashSet<Node> mergeGateways = new HashSet<>();

					//Keep the gateway
					for (Node removableFlow : removableFlows)
					{
						final Node mergeGateway = removableFlow.childNodes().iterator().next();
						currentNode.removeChildren(removableFlow);
						mergeGateway.removeParent(removableFlow);
						mergeGateways.add(mergeGateway);
					}

					for (Node mergeGateway : mergeGateways)
					{
						if (mergeGateway.parentNodes().size() <= 1)
						{
							//Remove the merge gateway
							final Node gatewayParentFlow = mergeGateway.parentNodes().iterator().next();
							final Node gatewayChildFlow = mergeGateway.childNodes().iterator().next();
							final Node gatewayChildFlowChild = gatewayChildFlow.childNodes().iterator().next();
							gatewayParentFlow.removeChildren();
							mergeGateway.removeParents();
							gatewayChildFlowChild.removeParents();
							gatewayParentFlow.addChild(gatewayChildFlowChild);
							gatewayChildFlowChild.addParent(gatewayParentFlow);
						}
					}
				}
				else
				{
					//Remove the gateway
					final HashSet<Node> flowsToKeep = new HashSet<>(currentNode.childNodes());
					flowsToKeep.removeAll(removableFlows);
					final Node parentFlow = currentNode.parentNodes().iterator().next();

					final HashSet<Node> mergeGateways = new HashSet<>();

					for (Node removableFlow : removableFlows)
					{
						mergeGateways.add(removableFlow.childNodes().iterator().next());
					}

					final HashSet<Node> nodesAfterMerge = new HashSet<>();

					for (Node mergeGateway : mergeGateways)
					{
						nodesAfterMerge.add(mergeGateway.childNodes().iterator().next().childNodes().iterator().next());
					}

					if (mergeGateways.size() <= 1)
					{
						final Node nodeAfterMerge = nodesAfterMerge.iterator().next();
						parentFlow.removeChildren();
						nodeAfterMerge.removeParents();
						parentFlow.addChild(nodeAfterMerge);
						nodeAfterMerge.addParent(parentFlow);
					}
					else
					{
						if (nodesAfterMerge.size() <= 1)
						{
							final Node nodeAfterMerge = nodesAfterMerge.iterator().next();
							parentFlow.removeChildren();
							nodeAfterMerge.removeParents();
							parentFlow.addChild(nodeAfterMerge);
							nodeAfterMerge.addParent(parentFlow);
						}
						else
						{
							//TODO
							//currentNode.rem
						}
					}
				}
			}
		}
	}

	/**
	 * This method traverses the graph and collapses all directly connected gateways into
	 * a single one (semantic equivalence)
	 */
	private static void collapseGateways(final Graph graph)
	{
		final HashSet<Node> collapsedGateways = new HashSet<>();

		do
		{
			collapsedGateways.clear();
			EndEventsManager.collapseGateways(graph.initialNode(), collapsedGateways, new HashSet<>());
		}
		while (!collapsedGateways.isEmpty());
	}

	private static void collapseGateways(final Node currentNode,
										 final HashSet<Node> collapsedGateways,
										 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return;
		visitedNodes.add(currentNode);

		final HashSet<Node> mergeAbleChildren = new HashSet<>();

		if (currentNode.bpmnObject() instanceof Gateway)
		{
			final Gateway gateway = (Gateway) currentNode.bpmnObject();

			if (gateway.isSplitGateway())
			{
				for (Node child : currentNode.childNodes())
				{
					final Node nextRealNode = child.childNodes().iterator().next();

					if (nextRealNode.bpmnObject() instanceof Gateway)
					{
						final Gateway nextGateway = (Gateway) nextRealNode.bpmnObject();

						if (nextGateway.isSplitGateway()
							&& nextGateway.type() == gateway.type())
						{
							//gateway and nextGateway can be merged
							collapsedGateways.add(nextRealNode);
							mergeAbleChildren.add(child);
						}
					}
				}

				for (Node mergeableChild : mergeAbleChildren)
				{
					currentNode.removeChildren(mergeableChild);
					mergeableChild.removeParent(currentNode);
					final Set<Node> flowsToReplug = mergeableChild.childNodes().iterator().next().childNodes();

					for (Node flowToReplug : flowsToReplug)
					{
						flowToReplug.removeParents();
						currentNode.addChild(flowToReplug);
						flowToReplug.addParent(currentNode);
					}
				}
			}
			else
			{
				for (Node child : currentNode.childNodes())
				{
					final Node nextRealNode = child.childNodes().iterator().next();

					if (nextRealNode.bpmnObject() instanceof Gateway)
					{
						final Gateway nextGateway = (Gateway) nextRealNode.bpmnObject();

						if (nextGateway.isMergeGateway()
							&& nextGateway.type() == gateway.type())
						{
							//gateway and nextGateway can be merged
							collapsedGateways.add(nextRealNode);
							mergeAbleChildren.add(child);
						}
					}
				}

				for (Node mergeableChild : mergeAbleChildren)
				{
					final Node nextGateway = mergeableChild.childNodes().iterator().next();
					nextGateway.removeParent(mergeableChild);
					mergeableChild.removeChildren(nextGateway);

					final Set<Node> flowsToReplug = currentNode.parentNodes();

					for (Node flowToReplug : flowsToReplug)
					{
						flowToReplug.removeChildren();
						nextGateway.addParent(flowToReplug);
						flowToReplug.addChild(nextGateway);
					}
				}
			}
		}

		if (collapsedGateways.isEmpty())
		{
			for (Node child : currentNode.childNodes())
			{
				EndEventsManager.collapseGateways(child, collapsedGateways, visitedNodes);
			}
		}
	}

	/**
	 * This method analyses all the end events of the process and merges the ones that are
	 * outgoing from the same node
	 */
	private static void factorizeEndEvents()
	{

	}

	/**
	 * This method traverses the graph backward from the end events and removes all the nodes
	 * that cannot reach the initial event.
	 * These nodes are residual nodes coming from the replacement of the end tasks by their
	 * corresponding end events.
	 */
	private static void removeDeadNodes(final Graph graph)
	{
		final HashSet<Node> endEvents = new HashSet<>();
		EndEventsManager.getAllEndEvents(graph.initialNode(), endEvents, new HashSet<>());
		final HashSet<Pair<Node, Node>> pairsToRemove = new HashSet<>();

		for (Node endEvent : endEvents)
		{
			EndEventsManager.getPairsToRemove(endEvent, graph.initialNode(), pairsToRemove, new HashSet<>());
		}

		for (Pair<Node, Node> pairToRemove : pairsToRemove)
		{
			pairToRemove.first().removeChildren(pairToRemove.second());
			pairToRemove.second().removeParent(pairToRemove.first());
		}
	}

	private static void getPairsToRemove(final Node currentNode,
										 final Node initialNode,
										 final HashSet<Pair<Node, Node>> pairsToRemove,
										 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return;
		visitedNodes.add(currentNode);

		for (Node parent : currentNode.parentNodes())
		{
			if (parent.hasAncestor(initialNode))
			{
				EndEventsManager.getPairsToRemove(parent, initialNode, pairsToRemove, visitedNodes);
			}
			else
			{
				pairsToRemove.add(new Pair<>(parent, currentNode));
			}
		}
	}

	/**
	 * This method verifies that all the end tasks have been correctly replaced by their
	 * corresponding end events in the process.
	 *
	 * @param node the node on which the analysis is currently performed
	 * @param visitedNodes the list of already visited nodes (recursion breaker)
	 * @return true if all the end tasks have successfully been removed, false otherwise
	 */
	private static boolean checkAllEndTasksRemoved(final Node node,
												   final HashSet<Node> visitedNodes)
	{
		//TODO
		return true;
	}

	private static Node findLoopExit(final Node node,
									 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(node)) return null;
		visitedNodes.add(node);

		if (node.isInLoop()
			&& node.bpmnObject() instanceof Gateway)
		{
			final Gateway gateway = (Gateway) node.bpmnObject();

			if (gateway.type() == BpmnProcessType.EXCLUSIVE_GATEWAY
				&& gateway.isSplitGateway())
			{
				for (Node gatewayOutFlow : node.childNodes())
				{
					if (!gatewayOutFlow.isInLoop())
					{
						//We found the exit node
						return node;
					}
				}
			}
		}

		for (Node child : node.childNodes())
		{
			final Node loopExit = EndEventsManager.findLoopExit(child, visitedNodes);

			if (loopExit != null)
			{
				return loopExit;
			}
		}

		return null;
	}

	private static Node getClosestNextTask(final Node currentNode,
										   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return null;
		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject() instanceof Task)
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node closestNextTask = EndEventsManager.getClosestNextTask(child, visitedNodes);

			if (closestNextTask != null)
			{
				return closestNextTask;
			}
		}

		return null;
	}

	private static void getAllEndEvents(final Node currentNode,
										final HashSet<Node> endEvents,
										final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode)) return;
		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == BpmnProcessType.END_EVENT)
		{
			endEvents.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			EndEventsManager.getAllEndEvents(child, endEvents, visitedNodes);
		}
	}
}
