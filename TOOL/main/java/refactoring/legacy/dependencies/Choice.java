package refactoring.legacy.dependencies;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;

import java.util.Arrays;
import java.util.HashSet;

public class Choice
{
	private final HashSet<Node> mutuallyExclusiveNodes;
	private Node exclusiveSplit;

	public Choice(Node... nodes)
	{
		this.mutuallyExclusiveNodes = new HashSet<>();
		this.mutuallyExclusiveNodes.addAll(Arrays.asList(nodes));
	}

	public Choice(HashSet<Node> nodes)
	{
		this.mutuallyExclusiveNodes = new HashSet<>(nodes);
	}

	public HashSet<Node> nodes()
	{
		return this.mutuallyExclusiveNodes;
	}

	public Choice copy()
	{
		return new Choice(this.mutuallyExclusiveNodes);
	}

	public boolean contains(Node node)
	{
		return this.mutuallyExclusiveNodes.contains(node);
	}

	public void build()
	{
		this.exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveGateway());

		for (Node node : this.mutuallyExclusiveNodes)
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow("",""));
			this.exclusiveSplit.addChild(sequenceFlow);
			sequenceFlow.addParent(this.exclusiveSplit);
			sequenceFlow.addChild(node);
			node.addParent(sequenceFlow);
		}
	}

	public Node getExclusiveSplit()
	{
		if (this.exclusiveSplit == null) throw new IllegalStateException();

		return this.exclusiveSplit;
	}

	public Node getCorrespondingNode(final String name)
	{
		for (Node node : this.mutuallyExclusiveNodes)
		{
			if (node.bpmnObject().name().equals(name)) return node;
		}

		throw new IllegalStateException();
	}

	//Overrides

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Choice))
		{
			return false;
		}

		return this.mutuallyExclusiveNodes.equals(((Choice) o).mutuallyExclusiveNodes);
	}

	@Override
	public int hashCode()
	{
		return this.mutuallyExclusiveNodes.hashCode();
	}

	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder("Current choice is composed of tasks [");
		String separator = "";

		for (Node node : this.mutuallyExclusiveNodes)
		{
			builder.append(separator)
					.append(node.bpmnObject().name());

			separator = ", ";
		}

		builder.append("].");

		return builder.toString();
	}
}
