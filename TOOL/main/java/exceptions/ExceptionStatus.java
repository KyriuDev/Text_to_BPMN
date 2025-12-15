package exceptions;

public class ExceptionStatus
{
	private ExceptionStatus()
	{

	}

	public static final int NO_CODE = 1;
	public static final int CHATGPT_IO = 2;
	public static final int FILE_GENERATION_FAILED = 3;
	public static final int NON_PARSABLE_ANSWER = 4;
	public static final int NO_AST_GENERATED = 5;
	public static final int CONTRADICTORY_VALUES = 6;
	public static final int AST_TO_BPMN_FAILED = 7;
	public static final int NO_CONSTRAINTS_GENERATED = 8;
	public static final int INTEGRITY_CHECK_FAILED = 9;
	public static final int BADLY_FORMED_BPMN_PROCESS = 10;
	public static final int GRAPHICAL_GENERATION_FAILED = 11;

}
