package chat_gpt.no_ast.enums;

public enum ChoiceManagementMode
{
	/*
		In this mode, the added choice node has no output nodes, which reduces the number of connections in the graph,
		but increases the number of (undesired) mutual exclusions.
	 */
	LEAST_ADDITIONAL_CONNECTIONS,
	/*
		In this mode, the added choice node has as many output nodes, which reduces the number of superfluous mutual
		exclusions, but increases the number of (potentially undesired ?) connections between the nodes of the graph.
	 */
	LEAST_SUPERFLUOUS_MUTUAL_EXCLUSIONS
}
