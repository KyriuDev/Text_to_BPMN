package exceptions;

public class ExpectedException extends Exception
{
	private final int code;

	public ExpectedException()
	{
		super();
		this.code = ExceptionStatus.NO_CODE;
	}

	public ExpectedException(final String msg)
	{
		super(msg);
		this.code = ExceptionStatus.NO_CODE;
	}

	public ExpectedException(final int code)
	{
		super();
		this.code = code;
	}

	public ExpectedException(final String message,
							 final int code)
	{
		super(message);
		this.code = code;
	}

	public ExpectedException(final Exception e,
							 final int code)
	{
		super(e);
		this.code = code;
	}

	public ExpectedException(final Exception e,
							 final String message,
							 final int code)
	{
		super(e);
		this.code = code;
	}

	public int code()
	{
		return this.code;
	}
}
