package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.HashSet;

public class ASTLoop
{
	private final HashSet<String> loopNodes;
	private final HashSet<String> mandatoryNodes;
	private final HashSet<String> optionalNodes;
	private final AbstractSyntaxTree loop;
	private boolean addedToTree;

	public ASTLoop(final AbstractSyntaxTree loop,
				   final HashSet<String> mandatoryNodes,
				   final HashSet<String> optionalNodes)
	{
		System.out.println("Loop added:\n\n" + loop.toString());
		this.mandatoryNodes = mandatoryNodes;
		this.optionalNodes = optionalNodes;
		this.loopNodes = new HashSet<>(mandatoryNodes);
		this.loopNodes.addAll(optionalNodes);
		this.loop = loop;
		this.addedToTree = false;
		this.correctNodes(mandatoryNodes, optionalNodes);
		this.verifyLoop();
		this.setLabel();
	}

	public void addLoopNode(final AbstractSyntaxNode node)
	{
		this.loopNodes.add(node.label());
	}

	public void addLoopNode(final String node)
	{
		this.loopNodes.add(node);
	}

	public boolean nodeIsInLoop(final AbstractSyntaxNode node)
	{
		return this.loopNodes.contains(node.label());
	}

	public AbstractSyntaxTree loop()
	{
		return this.loop;
	}

	public HashSet<String> loopNodes()
	{
		return this.loopNodes;
	}

	public void markAsAdded()
	{
		this.addedToTree = true;
	}

	public boolean wasAddedToTree()
	{
		return this.addedToTree;
	}

	//Private method

	private void verifyLoop()
	{
		if (!this.mandatoryNodes.isEmpty())
		{
			final AbstractSyntaxNode mandatoryNode = this.loop.root().successors().get(0);

			for (String nodeLabel : this.mandatoryNodes)
			{
				if (mandatoryNode.findNodeOfLabel(nodeLabel) == null)
				{
					throw new IllegalStateException("The mandatory part of the loop misses node |" + nodeLabel + "|.");
				}
			}
		}

		if (!this.optionalNodes.isEmpty())
		{
			if (this.loop.root().successors().size() != 2) throw new IllegalStateException("Some nodes are optional but the loop does not have any optional part.");

			final AbstractSyntaxNode optionalNode = this.loop.root().successors().get(1);

			for (String nodeLabel : this.optionalNodes)
			{
				if (optionalNode.findNodeOfLabel(nodeLabel) == null)
				{
					throw new IllegalStateException("The optional part of the loop misses node |" + nodeLabel + "|.");
				}
			}
		}
	}

	private void setLabel()
	{
		final StringBuilder builder = new StringBuilder();

		for (String label : this.mandatoryNodes)
		{
			builder.append(label);
		}

		for (String label : this.optionalNodes)
		{
			builder.append(label);
		}

		this.loop.root().setLabel(builder.toString());
	}

	private void correctNodes(final HashSet<String> mandatoryNodes,
							  final HashSet<String> optionalNodes)
	{
		for (String mandatoryNode : mandatoryNodes)
		{
			optionalNodes.remove(mandatoryNode);
		}
	}
}
