package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;

public class ASTSplitter
{
	private ASTSplitter()
	{

	}

	public static ArrayList<AbstractSyntaxTree> split(final AbstractSyntaxTree tree)
	{
		tree.resetFlags();
		ASTReductor.reduce(tree);

		final ArrayList<AbstractSyntaxNode> leafs = new ArrayList<>();
		ASTSplitter.getLeafs(tree.root(), leafs);

		final ArrayList<AbstractSyntaxTree> orderedTrees = new ArrayList<>();

		for (AbstractSyntaxNode leaf : leafs)
		{
			ASTSplitter.generateOrderedSubTrees(leaf, orderedTrees);
		}

		return orderedTrees;
	}

	//Private methods

	private static void getLeafs(final AbstractSyntaxNode node,
								 final ArrayList<AbstractSyntaxNode> leafs)
	{
		if (node.type() == AbstractType.TASK
			&& node.successors().isEmpty())
		{
			node.setAvailable();
			leafs.add(node);
		}

		for (AbstractSyntaxNode child : node.successors())
		{
			ASTSplitter.getLeafs(child, leafs);
		}
	}

	private static void generateOrderedSubTrees(final AbstractSyntaxNode node,
												final ArrayList<AbstractSyntaxTree> orderedTrees)
	{
		if (node == null) return;

		if (node.type() == AbstractType.TASK)
		{
			ASTSplitter.generateOrderedSubTrees(node.predecessor(), orderedTrees);
			return;
		}

		if (node.isAvailable())
		{
			return;
		}

		boolean isAvailable = true;

		for (AbstractSyntaxNode child : node.successors())
		{
			if (!child.isAvailable())
			{
				isAvailable = false;
				break;
			}
		}

		if (!isAvailable) return;

		node.setAvailable();

		ASTSplitter.generateSubTrees(node.type(), node.successors(), orderedTrees);
		ASTSplitter.generateOrderedSubTrees(node.predecessor(), orderedTrees);
	}

	private static void generateSubTrees(final AbstractType type,
										 final ArrayList<AbstractSyntaxNode> nodes,
										 final ArrayList<AbstractSyntaxTree> splittedTrees)
	{
		if (type == AbstractType.SEQ)
		{
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				final AbstractSyntaxNode node1 = nodes.get(i);
				final AbstractSyntaxNode node2 = nodes.get(i + 1);
				ASTSplitter.generateSubTreesForTwo(type, node1, node2, splittedTrees);
			}
		}
		else
		{
			for (int i = 0; i < nodes.size(); i++)
			{
				final AbstractSyntaxNode node1 = nodes.get(i);

				for (int j = i + 1; j < nodes.size(); j++)
				{
					final AbstractSyntaxNode node2 = nodes.get(j);
					ASTSplitter.generateSubTreesForTwo(type, node1, node2, splittedTrees);
				}
			}
		}
	}

	private static void generateSubTreesForTwo(final AbstractType type,
											   final AbstractSyntaxNode leftNode,
											   final AbstractSyntaxNode rightNode,
											   final ArrayList<AbstractSyntaxTree> splittedTrees)
	{
		if (type == AbstractType.TASK) return;

		final ArrayList<AbstractSyntaxNode> leftTasks = new ArrayList<>();
		ASTSplitter.retrieveAllTasksFrom(leftNode, leftTasks);
		final ArrayList<AbstractSyntaxNode> rightTasks = new ArrayList<>();
		ASTSplitter.retrieveAllTasksFrom(rightNode, rightTasks);

		for (AbstractSyntaxNode leftTask : leftTasks)
		{
			for (AbstractSyntaxNode rightTask : rightTasks)
			{
				final AbstractSyntaxTree newTree = new AbstractSyntaxTree();
				final AbstractSyntaxNode newRoot = new AbstractSyntaxNode(type);
				newTree.setRoot(newRoot);

				final AbstractSyntaxNode newLeftTask = leftTask.copy();
				final AbstractSyntaxNode newRightTask = rightTask.copy();

				newRoot.addSuccessor(newLeftTask);
				newLeftTask.setPredecessor(newRoot);

				newRoot.addSuccessor(newRightTask);
				newRightTask.setPredecessor(newRoot);

				splittedTrees.add(newTree);
			}
		}
	}

	private static void retrieveAllTasksFrom(final AbstractSyntaxNode node,
											 final ArrayList<AbstractSyntaxNode> nextNodes)
	{
		if (node.type() == AbstractType.TASK)
		{
			nextNodes.add(node);
		}

		for (AbstractSyntaxNode child : node.successors())
		{
			ASTSplitter.retrieveAllTasksFrom(child, nextNodes);
		}
	}
}
