package chat_gpt.no_ast.enums;

public enum ExplicitLoopHandling
{
	/*
		This mode allows explicit loops (i.e., the loops stated explicitly by the user, e.g., (A, B, C)*)
		to be leniently added to the graph.
		This means that the final graph will necessarily contain a loop containing elements A, B and C, but may also
		contain other elements of the graph if required not to break some initially stated constraints (mostly choices).
	 */
	LENIENT,
	/*
		This mode forces explicit loops to be added "as is" to the graph.
		As a consequence, the graph resulting from the addition of an explicit loop may either:
			- Contain the exact explicit loop without any other node inside
			- Not contain the explicit loop at all (or contain it inside a bigger already existing loop) if adding
			  this loop had to break any initially stated constraint (mostly choices).
		This is FOR THE MOMENT the default mode, as the LENIENT one requires some more thinking to be handled properly.
	 */
	STRICT
}
