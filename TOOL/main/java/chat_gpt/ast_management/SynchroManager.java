package chat_gpt.ast_management;

import java.util.HashSet;

public class SynchroManager
{
	private SynchroManager()
	{

	}

	public static void removeSynchroNodes(final AbstractSyntaxTree tree)
	{
		final HashSet<AbstractSyntaxNode> synchroNodes = new HashSet<>();
		SynchroManager.retrieveSynchroNodes(tree.root(), synchroNodes);

		for (AbstractSyntaxNode synchroNode : synchroNodes)
		{
			synchroNode.predecessor().removeSuccessor(synchroNode);
			synchroNode.resetPredecessor();
		}
	}

	//Private methods

	private static void retrieveSynchroNodes(final AbstractSyntaxNode currentNode,
											 final HashSet<AbstractSyntaxNode> synchroNodes)
	{
		if (currentNode.id().contains("SYNCHRONIZATION_"))
		{
			synchroNodes.add(currentNode);
		}

		for (AbstractSyntaxNode child : currentNode.successors())
		{
			SynchroManager.retrieveSynchroNodes(child, synchroNodes);
		}
	}
}
