package chat_gpt.no_ast.enums;

public enum MutualExclusionMode
{
	/*
		In this mode, choices must follow the strong mutual exclusion rule, meaning that two mutually exclusive nodes
		must not be reachable from the other.
		This mode is not really meaningful in BPMN, especially when loops are involved in the model.
	 */
	STRONG_MUTUAL_EXCLUSION,
	/*
		In this, mode choices are authorised to follow the weak mutual exclusion rule, meaning that two mutually
		exclusive nodes ca be reachable from the other, as long as they never appear in the same acyclic path of the
		graph.
		This mode is more suitable to BPMN, especially when loops are involved in the model.
	 */
	WEAK_MUTUAL_EXCLUSION
}
