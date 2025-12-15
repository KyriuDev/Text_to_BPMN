package chat_gpt.no_ast;

public class BadMutualExclusionsException extends Exception
{
	public BadMutualExclusionsException()
	{
		super();
	}

	public BadMutualExclusionsException(final String msg)
	{
		super(msg);
	}
}
