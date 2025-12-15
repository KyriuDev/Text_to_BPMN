package chat_gpt.exceptions;

public class UnparsableSequenceException extends Exception
{
	public UnparsableSequenceException()
	{
		super();
	}

	public UnparsableSequenceException(final String message)
	{
		super(message);
	}
}
