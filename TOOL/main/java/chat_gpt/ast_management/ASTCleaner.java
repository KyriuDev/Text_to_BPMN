package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.HashSet;

public class ASTCleaner
{
	private ASTCleaner()
	{

	}

	public static void findCleanableNodes(final AbstractSyntaxTree abstractSyntaxTree)
	{
		final HashSet<AbstractSyntaxNode> cleanableNodes = new HashSet<>();

		do
		{
			cleanableNodes.clear();
			ASTCleaner.findCleanableNodes(abstractSyntaxTree.root(), abstractSyntaxTree, cleanableNodes);

			for (AbstractSyntaxNode cleanableNode : cleanableNodes)
			{
				final AbstractSyntaxNode predecessor = cleanableNode.predecessor();

				if (cleanableNode.successors().isEmpty())
				{
					cleanableNode.predecessor().successors().remove(cleanableNode);
					cleanableNode.resetPredecessor();
				}
				else if (cleanableNode.successors().size() == 1)
				{
					final AbstractSyntaxNode successor = cleanableNode.successors().get(0);

					if (predecessor == null)
					{
						successor.resetPredecessor();
						abstractSyntaxTree.setRoot(successor);
					}
					else
					{
						predecessor.replaceSuccessor(cleanableNode, successor);
						successor.setPredecessor(predecessor);
					}
				}
			}
		}
		while (!cleanableNodes.isEmpty());
	}

	//Private methods

	private static void findCleanableNodes(final AbstractSyntaxNode currentNode,
										   final AbstractSyntaxTree abstractSyntaxTree,
										   final HashSet<AbstractSyntaxNode> cleanableNodes)
	{
		if (currentNode.type() == AbstractType.SEQ
			|| currentNode.type() == AbstractType.PAR)
		{
			if (currentNode.successors().size() <= 1)
			{
				cleanableNodes.add(currentNode);
				return;
			}
		}

		for (AbstractSyntaxNode child : currentNode.successors())
		{
			ASTCleaner.findCleanableNodes(child, abstractSyntaxTree, cleanableNodes);
		}
	}
}
