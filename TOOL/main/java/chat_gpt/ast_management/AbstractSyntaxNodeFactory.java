package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

public class AbstractSyntaxNodeFactory
{
	private AbstractSyntaxNodeFactory()
	{

	}

	public static AbstractSyntaxNode newTask(final String id,
											 final String name)
	{
		final AbstractSyntaxNode abstractSyntaxNode;

		if (id == null
			|| id.isEmpty())
		{
			abstractSyntaxNode = new AbstractSyntaxNode(AbstractType.TASK);
		}
		else
		{
			abstractSyntaxNode = new AbstractSyntaxNode(AbstractType.TASK, id);
		}

		if (name == null
			|| name.isEmpty())
		{
			abstractSyntaxNode.setLabel(abstractSyntaxNode.id());
		}
		else
		{
			abstractSyntaxNode.setLabel(name);
		}

		return abstractSyntaxNode;
	}

	public static AbstractSyntaxNode newTask(final String id)
	{
		return AbstractSyntaxNodeFactory.newTask(id, id);
	}

	public static AbstractSyntaxNode newTask(final char id)
	{
		return AbstractSyntaxNodeFactory.newTask(String.valueOf(id));
	}

	public static AbstractSyntaxNode newTask()
	{
		return AbstractSyntaxNodeFactory.newTask(null);
	}

	public static AbstractSyntaxNode newChoice()
	{
		return new AbstractSyntaxNode(AbstractType.XOR);
	}

	public static AbstractSyntaxNode newChoice(final String id)
	{
		return new AbstractSyntaxNode(AbstractType.XOR, id);
	}

	public static AbstractSyntaxNode newParallel()
	{
		return new AbstractSyntaxNode(AbstractType.PAR);
	}

	public static AbstractSyntaxNode newParallel(final String id)
	{
		return new AbstractSyntaxNode(AbstractType.PAR, id);
	}

	public static AbstractSyntaxNode newSequence()
	{
		return new AbstractSyntaxNode(AbstractType.SEQ);
	}

	public static AbstractSyntaxNode newSequence(final String id)
	{
		return new AbstractSyntaxNode(AbstractType.SEQ, id);
	}
}
