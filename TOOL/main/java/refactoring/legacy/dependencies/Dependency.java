package refactoring.legacy.dependencies;

import bpmn.graph.Node;

public class Dependency
{
	private final Node firstNode;
	private Node secondNode;

	public Dependency(final Node firstNode,
					  final Node secondNode)
	{
		this.firstNode = firstNode;
		this.secondNode = secondNode;
	}

	public void setSecondNode(final Node node)
	{
		this.secondNode = node;
	}

	public Node firstNode()
	{
		return this.firstNode;
	}

	public Node secondNode()
	{
		return this.secondNode;
	}

	public String stringify(final int depth)
	{
		return "	".repeat(depth) +
				"Dependency between |" +
				this.firstNode.bpmnObject().id() +
				"| and |" +
				this.secondNode.bpmnObject().id() +
				"|.";
	}

	public Dependency copy()
	{
		return new Dependency(this.firstNode, this.secondNode);
	}

	//Overrides

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Dependency))
		{
			return false;
		}

		return this.firstNode.equals(((Dependency) o).firstNode)
				&& this.secondNode.equals(((Dependency) o).secondNode);
	}

	@Override
	public int hashCode()
	{
		return this.firstNode.hashCode() + this.secondNode.hashCode();
	}

	@Override
	public String toString()
	{
		return "(" +
				this.firstNode.bpmnObject().name() +
				"," +
				this.secondNode.bpmnObject().name() +
				")";
	}
}
