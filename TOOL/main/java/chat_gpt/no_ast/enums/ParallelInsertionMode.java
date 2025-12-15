package chat_gpt.no_ast.enums;

public enum ParallelInsertionMode
{
	/*
		This mode inserts the parallel tasks in a best-effort mode, that is, giving the most chances
		for the task to be put in parallel to effectively appear in parallel in the final process.
		However, there is no guarantee that this task will indeed be in parallel of its expected parallelisable
		tasks in the resulting process.
	 */
	BEST_EFFORT,
	/*

	 */
	GUARANTEED
}
