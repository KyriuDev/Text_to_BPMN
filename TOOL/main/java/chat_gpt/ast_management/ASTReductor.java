package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;

public class ASTReductor
{
	private ASTReductor()
	{

	}

	//Public methods

	public static void reduce(final AbstractSyntaxTree tree)
	{
		ASTReductor.reduce(tree.root());
	}

	//Private methods

	private static void reduce(final AbstractSyntaxNode node)
	{
		for (AbstractSyntaxNode child : node.successors())
		{
			ASTReductor.reduce(child);
		}

		if (node.type() == AbstractType.TASK) return;

		final ArrayList<AbstractSyntaxNode> newSuccessors = new ArrayList<>();

		for (AbstractSyntaxNode child : node.successors())
		{
			if (child.type() == node.type())
			{
				newSuccessors.addAll(child.successors());
			}
			else
			{
				newSuccessors.add(child);
			}
		}

		node.removeSuccessors();

		for (AbstractSyntaxNode newSuccessor : newSuccessors)
		{
			newSuccessor.resetPredecessor();
			node.addSuccessor(newSuccessor);
			newSuccessor.setPredecessor(node);
		}
	}
}
