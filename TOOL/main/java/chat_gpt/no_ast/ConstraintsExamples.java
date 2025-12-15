package chat_gpt.no_ast;

public class ConstraintsExamples
{
	private ConstraintsExamples()
	{

	}

	public static String EXAMPLE_1 = "- A < B < C\\n";
	public static String EXAMPLE_2 = "- A | B\\n";
	public static String EXAMPLE_3 = "- A & B\\n";
	public static String EXAMPLE_4 = "- (A, B)*\\n";
	public static String EXAMPLE_5 = "- (A | B)*\\n";
	public static String EXAMPLE_6 = "" +
			"- A < (B, C)\\n" +
			"- D < C";
}
