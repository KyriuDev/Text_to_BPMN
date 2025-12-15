package chat_gpt.ast_management;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.*;
import chat_gpt.ast_management.constants.AbstractType;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.Pair;

import java.util.ArrayList;
import java.util.HashMap;

public class ASTToBPMN
{
	private ASTToBPMN()
	{

	}

	//Public methods

	public static Graph convert(final AbstractSyntaxTree tree) throws ExpectedException
	{
		final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
		final Graph graph = new Graph(startEvent);
		//final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

		//startEvent.addChild(sequenceFlow);
		//sequenceFlow.addParent(startEvent);

		final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());

		ASTToBPMN.convert(tree.root(), startEvent, endEvent, new HashMap<>());

		return graph;
	}

	public static Graph convert(final AbstractSyntaxTree tree,
								final HashMap<String, String> nameCorrespondences) throws ExpectedException
	{
		final Node startEvent = new Node(BpmnProcessFactory.generateStartEvent());
		final Graph graph = new Graph(startEvent);
		//final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

		//startEvent.addChild(sequenceFlow);
		//sequenceFlow.addParent(startEvent);

		final Node endEvent = new Node(BpmnProcessFactory.generateEndEvent());

		ASTToBPMN.convert(tree.root(), startEvent, endEvent, nameCorrespondences);

		return graph;
	}

	//Private methods

	private static Pair<Node, Node> convert(final AbstractSyntaxNode currentSyntaxNode,
											final Node currentNode,
											final Node branchingNode,
											final HashMap<String, String> nameCorrespondences) throws ExpectedException
	{
		if (currentSyntaxNode.type() == AbstractType.TASK)
		{
			final String originalName = nameCorrespondences.get(currentSyntaxNode.label());
			if (originalName == null) throw new IllegalStateException();
			final String taskName = ASTToBPMN.stripSpaces(originalName);
			final Task task = BpmnProcessFactory.generateTask(taskName);
			final Node taskNode = new Node(task);
			final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node secondFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			currentNode.addChild(firstFlow);
			firstFlow.addParent(currentNode);
			firstFlow.addChild(taskNode);
			taskNode.addParent(firstFlow);
			taskNode.addChild(secondFlow);
			secondFlow.addParent(taskNode);
			secondFlow.addChild(branchingNode);
			branchingNode.addParent(secondFlow);

			return new Pair<>(taskNode, taskNode);
		}
		else if (currentSyntaxNode.type() == AbstractType.PAR)
		{
			final Gateway splitGateway = (Gateway) BpmnProcessFactory.generateParallelGateway();
			final Gateway mergeGateway = (Gateway) BpmnProcessFactory.generateParallelGateway();
			mergeGateway.markAsMergeGateway();

			final Node splitGatewayNode = new Node(splitGateway);
			final Node mergeGatewayNode = new Node(mergeGateway);

			final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node secondFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

			currentNode.addChild(firstFlow);
			firstFlow.addParent(currentNode);
			firstFlow.addChild(splitGatewayNode);
			splitGatewayNode.addParent(firstFlow);
			mergeGatewayNode.addChild(secondFlow);
			secondFlow.addParent(mergeGatewayNode);
			secondFlow.addChild(branchingNode);
			branchingNode.addParent(secondFlow);

			for (AbstractSyntaxNode child : currentSyntaxNode.successors())
			{
				ASTToBPMN.convert(child, splitGatewayNode, mergeGatewayNode, nameCorrespondences);
			}

			return new Pair<>(splitGatewayNode, mergeGatewayNode);
		}
		else if (currentSyntaxNode.type() == AbstractType.LOOP)
		{
			final Gateway splitExclusiveGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
			final Gateway mergeExclusiveGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
			mergeExclusiveGateway.markAsMergeGateway();

			final Node splitExclusiveGatewayNode = new Node(splitExclusiveGateway);
			final Node mergeExclusiveGatewayNode = new Node(mergeExclusiveGateway);

			final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node secondFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

			currentNode.addChild(firstFlow);
			firstFlow.addParent(currentNode);
			firstFlow.addChild(mergeExclusiveGatewayNode);
			mergeExclusiveGatewayNode.addParent(firstFlow);
			splitExclusiveGatewayNode.addChild(secondFlow);
			secondFlow.addParent(splitExclusiveGatewayNode);
			secondFlow.addChild(branchingNode);
			branchingNode.addParent(secondFlow);

			if (currentSyntaxNode.successors().size() > 2) throw new ExpectedException(ExceptionStatus.AST_TO_BPMN_FAILED);

			final AbstractSyntaxNode mandatoryNode =
					currentSyntaxNode.successors().get(0).type() == AbstractType.LOOP_MANDATORY ?
					currentSyntaxNode.successors().get(0) :
					currentSyntaxNode.successors().size() > 1 && currentSyntaxNode.successors().get(1).type() == AbstractType.LOOP_MANDATORY ? currentSyntaxNode.successors().get(1) : null;

			final AbstractSyntaxNode optionalNode =
					currentSyntaxNode.successors().get(0).type() == AbstractType.LOOP_OPTIONAL ?
							currentSyntaxNode.successors().get(0) :
							currentSyntaxNode.successors().size() > 1 && currentSyntaxNode.successors().get(1).type() == AbstractType.LOOP_OPTIONAL ? currentSyntaxNode.successors().get(1) : null;

			if (mandatoryNode == null)
			{
				final Node mandatoryLoopFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

				mergeExclusiveGatewayNode.addChild(mandatoryLoopFlow);
				mandatoryLoopFlow.addParent(mergeExclusiveGatewayNode);
				mandatoryLoopFlow.addChild(splitExclusiveGatewayNode);
				splitExclusiveGatewayNode.addParent(mandatoryLoopFlow);
			}
			else
			{
				if (mandatoryNode.successors().isEmpty())
				{
					final Node mandatoryLoopFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

					mergeExclusiveGatewayNode.addChild(mandatoryLoopFlow);
					mandatoryLoopFlow.addParent(mergeExclusiveGatewayNode);
					mandatoryLoopFlow.addChild(splitExclusiveGatewayNode);
					splitExclusiveGatewayNode.addParent(mandatoryLoopFlow);
				}
				else if (mandatoryNode.successors().size() == 1)
				{
					ASTToBPMN.convert(mandatoryNode.successors().get(0), mergeExclusiveGatewayNode, splitExclusiveGatewayNode, nameCorrespondences);
				}
				else
				{
					final Gateway splitParallelGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
					final Gateway mergeParallelGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
					mergeParallelGateway.markAsMergeGateway();

					final Node splitParallelGatewayNode = new Node(splitParallelGateway);
					final Node mergeParallelGatewayNode = new Node(mergeParallelGateway);

					final Node connectingFlow1 = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node connectingFlow2 = new Node(BpmnProcessFactory.generateSequenceFlow());

					mergeExclusiveGatewayNode.addChild(connectingFlow1);
					connectingFlow1.addParent(mergeExclusiveGatewayNode);
					connectingFlow1.addChild(splitParallelGatewayNode);
					splitParallelGatewayNode.addParent(connectingFlow1);
					mergeParallelGatewayNode.addChild(connectingFlow2);
					connectingFlow2.addParent(mergeParallelGatewayNode);
					connectingFlow2.addChild(splitExclusiveGatewayNode);
					splitExclusiveGatewayNode.addParent(connectingFlow2);

					for (AbstractSyntaxNode child : mandatoryNode.successors())
					{
						ASTToBPMN.convert(child, splitParallelGatewayNode, mergeParallelGatewayNode, nameCorrespondences);
					}
				}
			}

			if (optionalNode == null)
			{
				final Node optionalLoopFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

				splitExclusiveGatewayNode.addChild(optionalLoopFlow);
				optionalLoopFlow.addParent(splitExclusiveGatewayNode);
				optionalLoopFlow.addChild(mergeExclusiveGatewayNode);
				mergeExclusiveGatewayNode.addParent(optionalLoopFlow);
			}
			else
			{
				if (optionalNode.successors().isEmpty())
				{
					final Node optionalLoopFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

					splitExclusiveGatewayNode.addChild(optionalLoopFlow);
					optionalLoopFlow.addParent(splitExclusiveGatewayNode);
					optionalLoopFlow.addChild(mergeExclusiveGatewayNode);
					mergeExclusiveGatewayNode.addParent(optionalLoopFlow);
				}
				else if (optionalNode.successors().size() == 1)
				{
					ASTToBPMN.convert(optionalNode.successors().get(0), splitExclusiveGatewayNode, mergeExclusiveGatewayNode, nameCorrespondences);
				}
				else
				{
					final Gateway splitParallelGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
					final Gateway mergeParallelGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
					mergeParallelGateway.markAsMergeGateway();

					final Node splitParallelGatewayNode = new Node(splitParallelGateway);
					final Node mergeParallelGatewayNode = new Node(mergeParallelGateway);

					final Node connectingFlow1 = new Node(BpmnProcessFactory.generateSequenceFlow());
					final Node connectingFlow2 = new Node(BpmnProcessFactory.generateSequenceFlow());

					splitExclusiveGatewayNode.addChild(connectingFlow1);
					connectingFlow1.addParent(splitExclusiveGatewayNode);
					connectingFlow1.addChild(splitParallelGatewayNode);
					splitParallelGatewayNode.addParent(connectingFlow1);
					mergeParallelGatewayNode.addChild(connectingFlow2);
					connectingFlow2.addParent(mergeParallelGatewayNode);
					connectingFlow2.addChild(mergeExclusiveGatewayNode);
					mergeExclusiveGatewayNode.addParent(connectingFlow2);

					for (AbstractSyntaxNode child : optionalNode.successors())
					{
						ASTToBPMN.convert(child, splitParallelGatewayNode, mergeParallelGatewayNode, nameCorrespondences);
					}
				}
			}

			return new Pair<>(mergeExclusiveGatewayNode, splitExclusiveGatewayNode);
		}
		else if (currentSyntaxNode.type() == AbstractType.SEQ)
		{
			final ArrayList<Pair<Node, Node>> connections = new ArrayList<>();
			final Node dummyFirst = new Node(new SequenceFlow("DummyFirst_" + BpmnProcessFactory.generateID()));
			final Node dummyLast = new Node(new SequenceFlow("DummyLast_" + BpmnProcessFactory.generateID()));

			//Retrieve first and last node of each block to connect
			for (AbstractSyntaxNode child : currentSyntaxNode.successors())
			{
				connections.add(ASTToBPMN.convert(child, dummyFirst, dummyLast, nameCorrespondences));
			}

			//Remove dummy links
			for (Node dummyChild : dummyFirst.childNodes())
			{
				dummyChild.childNodes().iterator().next().removeParents();
			}

			dummyFirst.removeChildren();

			for (Node dummyParent : dummyLast.parentNodes())
			{
				final Node dummyGrandParent = dummyParent.parentNodes().iterator().next();

				//Needed to preserve loops
				dummyGrandParent.childNodes().removeIf(dummyGrandParentChild -> dummyGrandParentChild.childNodes().contains(dummyLast));
				//dummyParent.parentNodes().iterator().next().removeChildren();
			}

			dummyLast.removeParents();

			//Connect current node to first node of first pair, last node of last pair to branching node, and each pair of nodes between them
			final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node firstSequentialNode = connections.get(0).first();
			final Node lastSequentialNode = connections.get(connections.size() - 1).second();

			currentNode.addChild(firstFlow);
			firstFlow.addParent(currentNode);
			firstFlow.addChild(firstSequentialNode);
			firstSequentialNode.addParent(firstFlow);
			lastSequentialNode.addChild(lastFlow);
			lastFlow.addParent(lastSequentialNode);
			lastFlow.addChild(branchingNode);
			branchingNode.addParent(lastFlow);

			for (int i = 0; i < connections.size() - 1; i++)
			{
				final Pair<Node, Node> currentPair = connections.get(i);
				final Pair<Node, Node> nextPair = connections.get(i + 1);

				final Node currentFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
				currentPair.second().addChild(currentFlow);
				currentFlow.addParent(currentPair.second());
				currentFlow.addChild(nextPair.first());
				nextPair.first().addParent(currentFlow);
			}

			return new Pair<>(firstSequentialNode, lastSequentialNode);
		}
		else if (currentSyntaxNode.type() == AbstractType.XOR)
		{
			final Gateway splitGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
			final Gateway mergeGateway = (Gateway) BpmnProcessFactory.generateExclusiveGateway();
			mergeGateway.markAsMergeGateway();

			final Node splitGatewayNode = new Node(splitGateway);
			final Node mergeGatewayNode = new Node(mergeGateway);

			final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow());
			final Node secondFlow = new Node(BpmnProcessFactory.generateSequenceFlow());

			currentNode.addChild(firstFlow);
			firstFlow.addParent(currentNode);
			firstFlow.addChild(splitGatewayNode);
			splitGatewayNode.addParent(firstFlow);
			mergeGatewayNode.addChild(secondFlow);
			secondFlow.addParent(mergeGatewayNode);
			secondFlow.addChild(branchingNode);
			branchingNode.addParent(secondFlow);

			for (AbstractSyntaxNode child : currentSyntaxNode.successors())
			{
				ASTToBPMN.convert(child, splitGatewayNode, mergeGatewayNode, nameCorrespondences);
			}

			return new Pair<>(splitGatewayNode, mergeGatewayNode);
		}
		else
		{
			throw new ExpectedException(ExceptionStatus.AST_TO_BPMN_FAILED);
		}
	}

	private static String stripSpaces(final String s)
	{
		return s.replace(" ", "");
	}
}
