package chat_gpt.ast_management;

import bpmn.graph.Node;
import chat_gpt.ast_management.constants.AbstractType;
import other.Pair;
import other.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class AbstractSyntaxNode
{
	private final String id;
	private AbstractType type;
	private final ArrayList<AbstractSyntaxNode> successors;
	private AbstractSyntaxNode predecessor;

	private boolean available;
	private String label;

	public AbstractSyntaxNode(final AbstractType type)
	{
		this.id = Utils.generateRandomIdentifier(15);
		this.type = type;
		this.successors = new ArrayList<>();
		this.label = "";
		this.available = false;
	}

	public AbstractSyntaxNode(final AbstractType type,
							  final String id)
	{
		if (id.isEmpty()) throw new IllegalStateException();

		this.id = id;
		this.type = type;
		this.successors = new ArrayList<>();
		this.label = "";
		this.available = false;
	}

	public Pair<ArrayList<AbstractSyntaxNode>, Integer> getAllNodesBetween(final Collection<AbstractSyntaxNode> leftBoundingNodes,
																		   final Collection<AbstractSyntaxNode> rightBoundingNodes)
	{
		int leftIndex = -1;
		int rightIndex = rightBoundingNodes.isEmpty() ? this.successors.size() : -1;

		for (int i = 0; i < this.successors().size(); i++)
		{
			final AbstractSyntaxNode successor = this.successors().get(i);

			for (AbstractSyntaxNode leftBoundingNode : leftBoundingNodes)
			{
				if (successor.hasDescendant(leftBoundingNode))
				{
					leftIndex = i;
					break;
				}
			}

			for (AbstractSyntaxNode rightBoundingNode : rightBoundingNodes)
			{
				if (rightIndex == -1)
				{
					if (successor.hasDescendant(rightBoundingNode))
					{
						rightIndex = i;
						break;
					}
				}
			}
		}

		/*if ((!leftBoundingNodes.isEmpty() && leftIndex == -1)
			|| rightIndex == -1
			|| (leftIndex >= rightIndex && (rightIndex != 0))) throw new IllegalStateException(
					"getAllNodesBetween() failed due to the left and right indices (resp. " + leftIndex + " and " + rightIndex + ")."
		);*/

		final ArrayList<AbstractSyntaxNode> parallelisableNodes = this.getSuccessorsBetweenIndices(
				leftIndex,
				rightIndex == -1 ? this.successors.size() : rightIndex
		);
		final int indexIfEmpty;

		if (leftBoundingNodes.isEmpty())
		{
			indexIfEmpty = 0;
		}
		else if (rightBoundingNodes.isEmpty())
		{
			indexIfEmpty = this.successors.size();
		}
		else
		{
			indexIfEmpty = rightIndex == -1 ? this.successors.size() : rightIndex;
		}

		return new Pair<>(parallelisableNodes, indexIfEmpty);
	}

	public AbstractSyntaxNode findClosestAncestorWithOperator(final AbstractType type)
	{
		AbstractSyntaxNode currentPredecessor = this.predecessor();

		while (currentPredecessor != null)
		{
			if (currentPredecessor.type() == type)
			{
				return currentPredecessor;
			}

			currentPredecessor = currentPredecessor.predecessor();
		}

		return null;
	}

	public int getIndexOfSuccessorLeadingTo(final AbstractSyntaxNode targetNode)
	{
		for (int i = 0; i < this.successors.size(); i++)
		{
			final AbstractSyntaxNode currentSuccessor = this.successors.get(i);

			if (currentSuccessor.hasDescendant(targetNode))
			{
				return i;
			}
		}

		return -1;
	}

	public AbstractSyntaxNode getSuccessorLeadingTo(final AbstractSyntaxNode targetNode)
	{
		final int successorIndex = this.getIndexOfSuccessorLeadingTo(targetNode);

		if (successorIndex == -1) return null;

		return this.successors.get(successorIndex);
	}

	public AbstractSyntaxNode getFurthestLoopBelow(final AbstractSyntaxNode boundNode)
	{
		AbstractSyntaxNode previousClosestLoop = null;
		AbstractSyntaxNode currentClosestLoop = this.findClosestAncestorWithOperator(AbstractType.LOOP);

		while (currentClosestLoop != null)
		{
			if (!currentClosestLoop.hasAncestor(boundNode))
			{
				return previousClosestLoop;
			}

			previousClosestLoop = currentClosestLoop;
			currentClosestLoop = this.findClosestAncestorWithOperator(AbstractType.LOOP);
		}

		return previousClosestLoop;
	}

	public AbstractSyntaxNode getFurthestNodeOfTypeBelow(final AbstractType type,
														 final AbstractSyntaxNode boundNode)
	{
		AbstractSyntaxNode nodeOfType = null;
		AbstractSyntaxNode currentNode = this;

		while (currentNode != null
				&& !currentNode.equals(boundNode))
		{
			if (currentNode.type() == type)
			{
				nodeOfType = currentNode;
			}

			currentNode = currentNode.predecessor();
		}

		return nodeOfType;
	}

	public AbstractSyntaxNode getClosestNodeOfTypeBelow(final AbstractType type,
														final AbstractSyntaxNode boundNode)
	{
		AbstractSyntaxNode currentNode = this;

		while (currentNode != null
				&& !currentNode.equals(boundNode))
		{
			if (currentNode.type() == type)
			{
				return currentNode;
			}

			currentNode = currentNode.predecessor();
		}

		return null;
	}

	public ArrayList<AbstractSyntaxNode> getAllNodesAfter(final AbstractSyntaxNode node)
	{
		final int indexOfSuccessorLeadingToNode = this.getIndexOfSuccessorLeadingTo(node);

		if (indexOfSuccessorLeadingToNode == -1)
		{
			throw new IllegalStateException();
		}

		return this.getSuccessorsBetweenIndices(indexOfSuccessorLeadingToNode, this.successors.size());
	}

	public ArrayList<AbstractSyntaxNode> getAllNodesBefore(final AbstractSyntaxNode node)
	{
		final int indexOfSuccessorLeadingToNode = this.getIndexOfSuccessorLeadingTo(node);

		if (indexOfSuccessorLeadingToNode == -1)
		{
			throw new IllegalStateException();
		}

		return this.getSuccessorsBetweenIndices(-1, indexOfSuccessorLeadingToNode);
	}

	public ArrayList<AbstractSyntaxNode> getAllNodesBetween(final AbstractSyntaxNode leftNode,
															final AbstractSyntaxNode rightNode)
	{
		final int indexOfSuccessorLeadingToLeftNode = this.getIndexOfSuccessorLeadingTo(leftNode);
		final int indexOfSuccessorLeadingToRightNode = this.getIndexOfSuccessorLeadingTo(rightNode);

		if (indexOfSuccessorLeadingToLeftNode == -1
			|| indexOfSuccessorLeadingToRightNode == -1)
		{
			throw new IllegalStateException();
		}

		return this.getSuccessorsBetweenIndices(indexOfSuccessorLeadingToLeftNode, indexOfSuccessorLeadingToRightNode);
	}

	public ArrayList<AbstractSyntaxNode> getAllNodesBetweenIfAny(final AbstractSyntaxNode leftNode,
																 final AbstractSyntaxNode rightNode)
	{
		final int indexOfSuccessorLeadingToLeftNode = leftNode == null ? -1 : this.getIndexOfSuccessorLeadingTo(leftNode);
		final int indexOfSuccessorLeadingToRightNode = rightNode == null ? -1 : this.getIndexOfSuccessorLeadingTo(rightNode);

		return this.getSuccessorsBetweenIndices(
				indexOfSuccessorLeadingToLeftNode,
				indexOfSuccessorLeadingToRightNode == -1 ? this.successors.size() : indexOfSuccessorLeadingToRightNode
		);
	}

	public ArrayList<AbstractSyntaxNode> getSuccessorsAfterIndex(final int index)
	{
		return this.getSuccessorsBetweenIndices(index, this.successors().size());
	}

	public ArrayList<AbstractSyntaxNode> getSuccessorsBeforeIndex(final int index)
	{
		return this.getSuccessorsBetweenIndices(-1, index);
	}

	public ArrayList<AbstractSyntaxNode> getSuccessorsBetweenIndices(final int exclusiveStartIndex,
																	 final int exclusiveEndIndex)
	{
		if (exclusiveStartIndex < -1
				|| exclusiveEndIndex > this.successors().size())
		{
			throw new IndexOutOfBoundsException();
		}

		final ArrayList<AbstractSyntaxNode> desiredSuccessors = new ArrayList<>();

		for (int i = exclusiveStartIndex + 1; i < exclusiveEndIndex; i++)
		{
			desiredSuccessors.add(this.successors().get(i));
		}

		return desiredSuccessors;
	}

	public AbstractSyntaxNode copy()
	{
		final AbstractSyntaxNode copiedNode = new AbstractSyntaxNode(this.type, this.id);
		copiedNode.setLabel(this.label);

		return copiedNode;
	}

	public AbstractSyntaxNode deepCopy()
	{
		final AbstractSyntaxTree tree = new AbstractSyntaxTree();
		tree.setRoot(this);

		return tree.copy().root();
	}

	public ArrayList<HashSet<AbstractSyntaxNode>> getOrderedBelowTasks()
	{
		final ArrayList<HashSet<AbstractSyntaxNode>> orderedBelowTasks = new ArrayList<>();

		for (AbstractSyntaxNode successor : this.successors())
		{
			final HashSet<AbstractSyntaxNode> currentBelowTasks = new HashSet<>();
			this.getOrderedBelowTasks(successor, currentBelowTasks);
			orderedBelowTasks.add(currentBelowTasks);
		}

		return orderedBelowTasks;
	}

	public HashSet<AbstractSyntaxNode> getAllTasksBelow()
	{
		if (this.type() == AbstractType.TASK) throw new RuntimeException();

		final HashSet<AbstractSyntaxNode> tasksBelow = new HashSet<>();
		this.getAllTasksBelow(this, tasksBelow);
		return tasksBelow;
	}

	public AbstractSyntaxNode findNodeOfLabel(final String label)
	{
		return this.findNodeOfLabel(label, this);
	}

	public AbstractSyntaxNode findNodeOfId(final String id)
	{
		return this.findNodeOfId(id, this);
	}

	public AbstractType type()
	{
		return this.type;
	}

	public String label()
	{
		return this.label;
	}

	public void setPredecessor(final AbstractSyntaxNode predecessor)
	{
		if (predecessor.equals(this)) throw new IllegalStateException("Tried to assign itself as predecessor.");

		this.predecessor = predecessor;
	}

	public void removeSuccessors()
	{
		this.successors.clear();
	}

	public void resetPredecessor()
	{
		this.predecessor = null;
	}

	public void switchType(final AbstractType type)
	{
		this.type = type;
	}

	public boolean removeSuccessor(AbstractSyntaxNode node)
	{
		return this.successors.remove(node);
	}

	public void removeSuccessor(int index)
	{
		if (index < 0
			|| index >= this.successors.size())
		{
			throw new IndexOutOfBoundsException(
					"The removal index should be between 0 and " + (this.successors.size() - 1) + ", got " + index + "."
			);
		}

		this.successors.remove(index);
	}

	public void removeSuccessors(final Collection<AbstractSyntaxNode> successors)
	{
		this.successors.removeAll(successors);
	}

	public void setSuccessor(final int index,
							 final AbstractSyntaxNode successor)
	{
		if (successor.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.set(index, successor);
	}

	public void replaceSuccessor(final AbstractSyntaxNode oldSuccessor,
								 final AbstractSyntaxNode newSuccessor)
	{
		final int index = this.successors.indexOf(oldSuccessor);

		if (index < 0 || index >= this.successors.size()) throw new IllegalStateException(
				this.successors.isEmpty()
				?
				"Current node has no successors."
				:
				"Successor index should be between 0 and " + (this.successors.size() - 1) + " but is " + index + "."
		);

		if (newSuccessor.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.set(index, newSuccessor);
	}

	public void replaceSuccessorAndForcePredecessor(final AbstractSyntaxNode oldSuccessor,
								 					final AbstractSyntaxNode newSuccessor)
	{
		final int index = this.successors.indexOf(oldSuccessor);

		if (index < 0 || index >= this.successors.size()) throw new IllegalStateException(
				this.successors.isEmpty()
						?
						"Current node has no successors."
						:
						"Successor index should be between 0 and " + (this.successors.size() - 1) + " but is " + index + "."
		);

		if (newSuccessor.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.set(index, newSuccessor);
		newSuccessor.setPredecessor(this);
	}

	public void addSuccessor(final AbstractSyntaxNode node)
	{
		if (node.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.add(node);
	}

	public void addSuccessorAndForcePredecessor(final AbstractSyntaxNode node)
	{
		if (node.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.add(node);
		node.setPredecessor(this);
	}

	public void addSuccessors(final Collection<AbstractSyntaxNode> nodes)
	{
		for (AbstractSyntaxNode node : nodes)
		{
			if (node.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");
		}

		this.successors.addAll(nodes);
	}

	public void addSuccessor(final int index,
							 final AbstractSyntaxNode node)
	{
		if (node.equals(this)) throw new IllegalStateException("Tried to assign itself as successor.");

		this.successors.add(index, node);
	}

	public boolean hasAncestor(final AbstractSyntaxNode node)
	{
		return this.hasAncestor(node, this.predecessor);
	}

	public boolean hasDescendant(final AbstractSyntaxNode node)
	{
		if (this.equals(node)) return true;

		if (this.successors().isEmpty()) return false;

		for (AbstractSyntaxNode successor : this.successors())
		{
			final boolean found = successor.hasDescendant(node);

			if (found)
			{
				return true;
			}
		}

		return false;
	}

	public boolean hasDescendants(final Collection<AbstractSyntaxNode> nodes)
	{
		for (AbstractSyntaxNode node : nodes)
		{
			if (!this.hasDescendant(node))
			{
				return false;
			}
		}

		return true;
	}

	public ArrayList<AbstractSyntaxNode> successors()
	{
		return this.successors;
	}

	public boolean hasSuccessors()
	{
		return !this.successors.isEmpty();
	}

	public void setLabel(final String label)
	{
		this.label = label;
	}

	public AbstractSyntaxNode predecessor()
	{
		return this.predecessor;
	}

	public String id()
	{
		return this.id;
	}

	public String stringify(final int depth)
	{
		final StringBuilder builder = new StringBuilder();

		builder.append("	".repeat(depth))
				.append("- Node \"")
				.append(this.label.isEmpty() ? this.type.symbol() : this.label)
				.append("\" (")
				.append(this.id)
				.append(") has ")
				.append(this.successors.isEmpty() ? "no" : this.successors.size())
				.append(" child")
				.append(this.successors.isEmpty() ? "." : ":")
				.append("\n");

		if (!this.successors.isEmpty())
		{
			for (AbstractSyntaxNode child : this.successors)
			{
				builder.append(child.stringify(depth + 1));
			}
		}

		return builder.toString();
	}

	public String stringifyReverse(final int depth)
	{
		final StringBuilder builder = new StringBuilder();

		builder.append("	".repeat(depth))
				.append("- Node \"")
				.append(this.label.isEmpty() ? this.type.symbol() : this.label)
				.append("\" has ")
				.append(this.predecessor == null ? "no" : "a")
				.append(" predecessor")
				.append(this.predecessor == null ? "." : ":")
				.append("\n");

		if (depth > 200) return builder.toString();

		if (this.predecessor != null)
		{
			builder.append(this.predecessor.stringifyReverse(depth + 1));
		}

		return builder.toString();
	}

	public void resetFlag()
	{
		this.available = false;
	}

	public void setAvailable()
	{
		this.available = true;
	}

	public boolean isAvailable()
	{
		return this.available;
	}

	//Private methods

	private AbstractSyntaxNode findNodeOfLabel(final String label,
											   final AbstractSyntaxNode currentNode)
	{
		if (currentNode.label().equals(label)) return currentNode;

		for (AbstractSyntaxNode child : currentNode.successors())
		{
			final AbstractSyntaxNode nodeWithLabel = this.findNodeOfLabel(label, child);

			if (nodeWithLabel != null) return nodeWithLabel;
		}

		return null;
	}

	private AbstractSyntaxNode findNodeOfId(final String id,
											final AbstractSyntaxNode currentNode)
	{
		if (currentNode.id().equals(id)) return currentNode;

		for (AbstractSyntaxNode child : currentNode.successors())
		{
			final AbstractSyntaxNode nodeWithLabel = this.findNodeOfId(id, child);

			if (nodeWithLabel != null) return nodeWithLabel;
		}

		return null;
	}

	private boolean hasAncestor(final AbstractSyntaxNode nodeToFind,
								final AbstractSyntaxNode currentNode)
	{
		if (currentNode == null) return false;

		if (currentNode.equals(nodeToFind)) return true;

		if (currentNode.predecessor() == null) return false;

		return this.hasAncestor(nodeToFind, currentNode.predecessor());
	}

	private void getOrderedBelowTasks(final AbstractSyntaxNode currentNode,
									  final HashSet<AbstractSyntaxNode> tasks)
	{
		if (currentNode.type() == AbstractType.TASK)
		{
			tasks.add(currentNode);
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			this.getOrderedBelowTasks(successor, tasks);
		}
	}

	private void getAllTasksBelow(final AbstractSyntaxNode currentNode,
								  final HashSet<AbstractSyntaxNode> tasksBelow)
	{
		if (currentNode.type() == AbstractType.TASK)
		{
			tasksBelow.add(currentNode);
		}

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			this.getAllTasksBelow(successor, tasksBelow);
		}
	}

	//Overrides

	@Override
	public String toString()
	{
		return "Node \"" + (this.label.isEmpty() ? this.id : this.label) + "\" is of type `" + (this.type.symbol().isEmpty() ? "TASK" : this.type.symbol()) + "'";
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AbstractSyntaxNode))
		{
			return false;
		}

		return this.id.equals(((AbstractSyntaxNode) o).id);
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
}
