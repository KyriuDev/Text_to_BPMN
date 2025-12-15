package chat_gpt.exceptions;

public class BadAnswerException extends Exception
{
	public BadAnswerException()
	{
		super();
	}

	public BadAnswerException(final String message)
	{
		super(message);
	}
}
