package chat_gpt.ast_management;

import bpmn.graph.Node;
import chat_gpt.ast_management.constants.AbstractType;
import other.Utils;

import java.util.ArrayList;
import java.util.Collections;

public class AbstractSyntaxTree
{
	private AbstractSyntaxNode root;
	private String hash;
	private final String id;

	public AbstractSyntaxTree()
	{
		this.id = Utils.generateRandomIdentifier(15);
		this.hash = null;
		this.root = null;
	}

	public AbstractSyntaxTree(final String id)
	{
		this.id = id;
		this.hash = null;
		this.root = null;
	}

	public AbstractSyntaxTree(final AbstractSyntaxNode root)
	{
		this.root = root;
		this.id = Utils.generateRandomIdentifier(15);
		this.hash = null;
	}

	public AbstractSyntaxTree(final String id,
							  final AbstractSyntaxNode root)
	{
		this.root = root;
		this.id = id;
		this.hash = null;
	}

	public boolean isEmpty()
	{
		return this.root == null;
	}

	public AbstractSyntaxNode root()
	{
		return this.root;
	}

	public void setRoot(final AbstractSyntaxNode node)
	{
		this.root = node;
	}

	public boolean hasNodeOfLabel(final String label)
	{
		return this.findNodeOfLabel(label) != null;
	}

	public boolean hasNodeOfId(final String id)
	{
		return this.findNodeOfId(id) != null;
	}

	public AbstractSyntaxNode findFirstTask()
	{
		return this.findFirstTask(this.root, false);
	}

	public AbstractSyntaxNode findFirstTaskOrNull()
	{
		return this.findFirstTask(this.root, true);
	}

	private AbstractSyntaxNode findFirstTask(final AbstractSyntaxNode currentNode,
											 final boolean allowNull)
	{
		if (currentNode.type() == AbstractType.TASK)
		{
			return currentNode;
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			final AbstractSyntaxNode firstTask = this.findFirstTask(successor, allowNull);

			if (firstTask != null)
			{
				return firstTask;
			}
		}

		if (allowNull)
		{
			return null;
		}
		else
		{
			throw new RuntimeException();
		}
	}

	public AbstractSyntaxNode findNodeOfLabel(final String label)
	{
		if (this.root.label().equals(label)) return this.root;

		for (AbstractSyntaxNode child : this.root.successors())
		{
			final AbstractSyntaxNode nodeWithLabel = child.findNodeOfLabel(label);

			if (nodeWithLabel != null) return nodeWithLabel;
		}

		return null;
	}

	public AbstractSyntaxNode findNodeOfId(final String id)
	{
		if (this.root.id().equals(id)) return this.root;

		for (AbstractSyntaxNode child : this.root.successors())
		{
			final AbstractSyntaxNode nodeWithId = child.findNodeOfId(id);

			if (nodeWithId != null) return nodeWithId;
		}

		return null;
	}

	public AbstractSyntaxTree copy()
	{
		final AbstractSyntaxTree copiedTree = new AbstractSyntaxTree(this.id);
		final AbstractSyntaxNode copiedRoot = this.root.copy();
		copiedTree.setRoot(copiedRoot);
		copiedTree.setHash(this.hash);
		this.copy(copiedRoot, this.root);

		return copiedTree;
	}

	public AbstractSyntaxTree copyNewId()
	{
		final AbstractSyntaxTree copiedTree = new AbstractSyntaxTree();
		final AbstractSyntaxNode copiedRoot = this.root.copy();
		copiedTree.setRoot(copiedRoot);
		this.copy(copiedRoot, this.root);

		return copiedTree;
	}

	public String hash()
	{
		if (this.hash == null)
		{
			this.computeHash();
		}

		return this.hash;
	}

	public void resetFlags()
	{
		this.resetFlags(this.root);
	}

	public String stringifyReverseFromLabel(final String label)
	{
		return this.findNodeOfLabel(label).stringifyReverse(0);
	}

	public String simpleHash()
	{
		final StringBuilder builder = new StringBuilder();

		if (this.root.type() == AbstractType.PAR)
		{
			final ArrayList<String> childHashes = new ArrayList<>();

			for (AbstractSyntaxNode child : this.root.successors())
			{
				final AbstractSyntaxTree subTree = new AbstractSyntaxTree(child);
				childHashes.add(subTree.simpleHash());
			}

			Collections.sort(childHashes);
			String separator = "";

			for (String childHash : childHashes)
			{
				builder.append(separator);

				if (childHash.contains("|")
					|| childHash.contains("&"))
				{
					builder.append("(")
							.append(childHash)
							.append(")");
				}
				else
				{
					builder.append(childHash);
				}

				separator = "&";
			}
		}
		else if (this.root.type() == AbstractType.XOR)
		{
			final ArrayList<String> childHashes = new ArrayList<>();

			for (AbstractSyntaxNode child : this.root.successors())
			{
				final AbstractSyntaxTree subTree = new AbstractSyntaxTree(child);
				childHashes.add(subTree.simpleHash());
			}

			Collections.sort(childHashes);
			String separator = "";

			for (String childHash : childHashes)
			{
				builder.append(separator);

				if (childHash.contains("|")
					|| childHash.contains("&"))
				{
					builder.append("(")
							.append(childHash)
							.append(")");
				}
				else
				{
					builder.append(childHash);
				}

				separator = "|";
			}
		}
		else if (this.root.type() == AbstractType.TASK)
		{
			builder.append(this.root.label());
		}
		else
		{
			throw new RuntimeException();
		}

		return builder.toString();
	}

	//Private methods

	private void resetFlags(final AbstractSyntaxNode node)
	{
		node.resetFlag();

		for (AbstractSyntaxNode child : node.successors())
		{
			this.resetFlags(child);
		}
	}

	private void computeHash()
	{
		final StringBuilder hashBuilder = new StringBuilder();

		if (this.root.type() == AbstractType.TASK)
		{
			hashBuilder.append(this.root.label());
			this.hash = hashBuilder.toString();
			return;
		}

		if (this.root.successors().size() == 1)
		{
			final AbstractSyntaxNode successor = this.root.successors().get(0);

			if (successor.type() == AbstractType.TASK)
			{
				hashBuilder.append(successor.label())
						.append(this.root.type().symbol());
			}
			else
			{
				//Loops with only mandatory or optional children
				final AbstractSyntaxTree subTree = new AbstractSyntaxTree();
				subTree.setRoot(successor);

				hashBuilder.append("(")
						.append(subTree.hash())
						.append(")")
						.append(this.root.type().symbol());
			}
		}
		else
		{
			if (this.root.type() == AbstractType.LOOP)
			{
				//Loops with both mandatory and optional children
				final AbstractSyntaxNode firstSuccessor = this.root.successors().get(0);
				final AbstractSyntaxNode secondSuccessor = this.root.successors().get(1);
				final AbstractSyntaxNode mandatoryNode = firstSuccessor.type() == AbstractType.LOOP_MANDATORY ? firstSuccessor : secondSuccessor;
				final AbstractSyntaxNode optionalNode = firstSuccessor.type() == AbstractType.LOOP_OPTIONAL ? firstSuccessor : secondSuccessor;

				final AbstractSyntaxTree mandatorySubTree = new AbstractSyntaxTree(mandatoryNode);
				final AbstractSyntaxTree optionalSubTree = new AbstractSyntaxTree(optionalNode);

				hashBuilder.append("(")
						.append(mandatorySubTree.hash())
						.append(",")
						.append(optionalSubTree.hash())
						.append(")*");
			}
			else
			{
				String separator = "";

				for (AbstractSyntaxNode child : this.root.successors())
				{
					if (child.type() == AbstractType.TASK)
					{
						hashBuilder.append(separator)
								.append(child.label());
					}
					else
					{
						final AbstractSyntaxTree subTree = new AbstractSyntaxTree();
						subTree.setRoot(child);

						hashBuilder.append(separator)
								.append("(")
								.append(subTree.hash())
								.append(")");
					}

					separator = this.root.type().symbol();
				}
			}
		}

		this.hash = hashBuilder.toString();
	}

	private void copy(final AbstractSyntaxNode currentCopy,
					  final AbstractSyntaxNode currentReal)
	{
		for (AbstractSyntaxNode child : currentReal.successors())
		{
			final AbstractSyntaxNode copiedChild = child.copy();
			currentCopy.addSuccessor(copiedChild);
			copiedChild.setPredecessor(currentCopy);
			this.copy(copiedChild, child);
		}
	}

	private void setHash(final String hash)
	{
		this.hash = hash;
	}

	//Override

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AbstractSyntaxTree)) return false;

		return this.id.equals(((AbstractSyntaxTree) o).id);
	}

	@Override
	public int hashCode()
	{
		int hash = 7;

		for (int i = 0; i < this.id.length(); i++)
		{
			hash = hash * 31 + this.id.charAt(i);
		}

		return hash;
	}

	@Override
	public String toString()
	{
		return this.root.stringify(0);
	}
}
