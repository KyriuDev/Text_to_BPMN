package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;
import java.util.HashMap;

public class ASTSequenceReducer
{
	private ASTSequenceReducer()
	{
		
	}
	
	public static void releaseConstraints(final AbstractSyntaxTree currentTree,
										  final ArrayList<AbstractSyntaxTree> dependencies)
	{
		System.out.println("Tree before sequence reduction:\n\n" + currentTree.toString());

		final ArrayList<AbstractSyntaxTree> sequenceDependencies = ASTSequenceReducer.getSequences(dependencies);
		ASTSequenceReducer.releaseConstraints(currentTree.root(), sequenceDependencies, currentTree);

		System.out.println("Tree after sequence reduction:\n\n" + currentTree.toString());
	}
	
	//Private methods

	private static ArrayList<AbstractSyntaxTree> getSequences(final ArrayList<AbstractSyntaxTree> dependencies)
	{
		final ArrayList<AbstractSyntaxTree> sequences = new ArrayList<>();

		for (AbstractSyntaxTree tree : dependencies)
		{
			if (tree.root().type() == AbstractType.SEQ)
			{
				sequences.add(tree);
			}
		}

		return sequences;
	}

	private static void releaseConstraints(final AbstractSyntaxNode currentNode,
										   final ArrayList<AbstractSyntaxTree> dependencies,
										   final AbstractSyntaxTree currentTree)
	{
		final ArrayList<AbstractSyntaxNode> originalSuccessors = new ArrayList<>(currentNode.successors());

		if (currentNode.type() == AbstractType.SEQ)
		{
			//Retrieve all the tasks contained in each subtree of the current seq node
			final HashMap<AbstractSyntaxNode, ArrayList<AbstractSyntaxNode>> tasksPerChild = new HashMap<>();

			for (AbstractSyntaxNode child : currentNode.successors())
			{
				final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
				ASTSequenceReducer.retrieveAllTasksFrom(child, childTasks);
				tasksPerChild.put(child, childTasks);
			}

			//Iterate over the children until reaching a fixed point, meaning that no more children can be merged
			boolean hasChanged = true;

			while (hasChanged)
			{
				hasChanged = false;

				for (int i = 0; i < currentNode.successors().size() - 1; i++)
				{
					final AbstractSyntaxNode currentChild = currentNode.successors().get(i);
					final AbstractSyntaxNode nextChild = currentNode.successors().get(i + 1);

					final ArrayList<AbstractSyntaxNode> leftTasks = tasksPerChild.get(currentChild);
					final ArrayList<AbstractSyntaxNode> rightTasks = tasksPerChild.get(nextChild);

					if (ASTSequenceReducer.nodesCanBeMerged(leftTasks, rightTasks, dependencies))
					{
						ASTSequenceReducer.mergeNodes(currentNode, currentChild, nextChild);
						ASTReductor.reduce(currentTree);
						hasChanged = true;
						break;
					}
				}

				tasksPerChild.clear();

				for (AbstractSyntaxNode child : currentNode.successors())
				{
					final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
					ASTSequenceReducer.retrieveAllTasksFrom(child, childTasks);
					tasksPerChild.put(child, childTasks);
				}
			}
		}

		for (AbstractSyntaxNode child : originalSuccessors)
		{
			ASTSequenceReducer.releaseConstraints(child, dependencies, currentTree);
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
			ASTSequenceReducer.retrieveAllTasksFrom(child, nextNodes);
		}
	}

	private static boolean nodesCanBeMerged(final ArrayList<AbstractSyntaxNode> nodes1,
											final ArrayList<AbstractSyntaxNode> nodes2,
											final ArrayList<AbstractSyntaxTree> mandatorySequences)
	{
		for (AbstractSyntaxTree tree : mandatorySequences)
		{
			final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
			final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

			if (ASTSequenceReducer.listContainsNode(nodes1, leftNode)
					&& ASTSequenceReducer.listContainsNode(nodes2, rightNode))
			{
				return false;
			}

			if (ASTSequenceReducer.listContainsNode(nodes2, leftNode)
					&& ASTSequenceReducer.listContainsNode(nodes1, rightNode))
			{
				throw new IllegalStateException();
			}
		}

		return true;
	}

	private static void mergeNodes(final AbstractSyntaxNode sequenceNode,
								   final AbstractSyntaxNode childToMerge1,
								   final AbstractSyntaxNode childToMerge2)
	{
		sequenceNode.successors().remove(childToMerge1);
		//sequenceNode.successors().remove(childToMerge2);

		if (sequenceNode.successors().size() == 1) throw new IllegalStateException();

		final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
		sequenceNode.replaceSuccessor(childToMerge2, parallelNode);
		parallelNode.setPredecessor(sequenceNode);
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
