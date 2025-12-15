package chat_gpt.ast_management.constants;

public enum AbstractType
{
	XOR("|"),
	PAR("&"),
	SEQ("<"),
	LOOP("*"),
	LOOP_MANDATORY("+"),
	LOOP_OPTIONAL("?"),
	TASK("");

	private final String symbol;
	AbstractType(final String symbol)
	{
		this.symbol = symbol;
	}

	public String symbol()
	{
		return this.symbol;
	}
}
