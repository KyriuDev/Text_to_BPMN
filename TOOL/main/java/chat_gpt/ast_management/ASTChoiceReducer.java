package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;
import java.util.HashMap;

public class ASTChoiceReducer
{
	private ASTChoiceReducer()
	{

	}

	public static void releaseConstraints(final AbstractSyntaxTree currentTree,
										  final ArrayList<AbstractSyntaxTree> dependencies)
	{
		final ArrayList<AbstractSyntaxTree> choiceDependencies = ASTChoiceReducer.getChoices(dependencies);
		ASTChoiceReducer.releaseConstraints(currentTree.root(), choiceDependencies, currentTree);
	}

	//Private methods

	private static ArrayList<AbstractSyntaxTree> getChoices(final ArrayList<AbstractSyntaxTree> dependencies)
	{
		final ArrayList<AbstractSyntaxTree> choices = new ArrayList<>();

		for (AbstractSyntaxTree tree : dependencies)
		{
			if (tree.root().type() == AbstractType.XOR)
			{
				choices.add(tree);
			}
		}

		return choices;
	}

	private static void releaseConstraints(final AbstractSyntaxNode currentNode,
										   final ArrayList<AbstractSyntaxTree> dependencies,
										   final AbstractSyntaxTree currentTree)
	{
		final ArrayList<AbstractSyntaxNode> originalSuccessors = new ArrayList<>(currentNode.successors());

		if (currentNode.type() == AbstractType.XOR)
		{
			//Retrieve all the tasks contained in each subtree of the current choice node
			final HashMap<AbstractSyntaxNode, ArrayList<AbstractSyntaxNode>> tasksPerChild = new HashMap<>();

			for (AbstractSyntaxNode child : currentNode.successors())
			{
				final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
				ASTChoiceReducer.retrieveAllTasksFrom(child, childTasks);
				tasksPerChild.put(child, childTasks);
			}

			//Iterate over the children until reaching a fixed point, meaning that no more children can be merged
			boolean hasChanged = true;

			while (hasChanged)
			{
				hasChanged = false;

				for (AbstractSyntaxNode child1 : tasksPerChild.keySet())
				{
					for (AbstractSyntaxNode child2 : tasksPerChild.keySet())
					{
						if (!child1.equals(child2))
						{
							if (ASTChoiceReducer.nodesCanBeMerged(tasksPerChild.get(child1), tasksPerChild.get(child2), dependencies))
							{
								ASTChoiceReducer.mergeNodes(currentNode, child1, child2);
								ASTReductor.reduce(currentTree);
								hasChanged = true;
								break;
							}
						}
					}

					if (hasChanged) break;
				}

				tasksPerChild.clear();

				for (AbstractSyntaxNode child : currentNode.successors())
				{
					final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
					ASTChoiceReducer.retrieveAllTasksFrom(child, childTasks);
					tasksPerChild.put(child, childTasks);
				}
			}
		}

		for (AbstractSyntaxNode child : originalSuccessors)
		{
			ASTChoiceReducer.releaseConstraints(child, dependencies, currentTree);
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
			ASTChoiceReducer.retrieveAllTasksFrom(child, nextNodes);
		}
	}

	private static boolean nodesCanBeMerged(final ArrayList<AbstractSyntaxNode> nodes1,
											final ArrayList<AbstractSyntaxNode> nodes2,
											final ArrayList<AbstractSyntaxTree> mandatoryChoices)
	{
		for (AbstractSyntaxTree tree : mandatoryChoices)
		{
			final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
			final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

			if ((ASTChoiceReducer.listContainsNode(nodes1, leftNode) && ASTChoiceReducer.listContainsNode(nodes2, rightNode))
				|| (ASTChoiceReducer.listContainsNode(nodes2, leftNode) && ASTChoiceReducer.listContainsNode(nodes1, rightNode)))
			{
				return false;
			}
		}

		return true;
	}

	private static void mergeNodes(final AbstractSyntaxNode choiceNode,
								   final AbstractSyntaxNode childToMerge1,
								   final AbstractSyntaxNode childToMerge2)
	{
		choiceNode.successors().remove(childToMerge1);
		choiceNode.successors().remove(childToMerge2);

		if (choiceNode.successors().isEmpty()) throw new IllegalStateException();

		final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
		choiceNode.addSuccessor(parallelNode);
		parallelNode.setPredecessor(choiceNode);
		parallelNode.addSuccessor(childToMerge1);
		childToMerge1.setPredecessor(parallelNode);
		parallelNode.addSuccessor(childToMerge2);
		childToMerge2.setPredecessor(parallelNode);
	}

	private static boolean listContainsNode(final ArrayList<AbstractSyntaxNode> list,
											final AbstractSyntaxNode node)
	{
		for (AbstractSyntaxNode nodeInList : list)
		{
			if (nodeInList.label().equals(node.label())) return true;
		}

		return false;
	}
}
