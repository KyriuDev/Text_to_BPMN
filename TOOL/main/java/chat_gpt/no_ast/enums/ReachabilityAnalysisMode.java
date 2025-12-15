package chat_gpt.no_ast.enums;

public enum ReachabilityAnalysisMode
{
	/*
		This mode performs a naive reachability analysis, i.e., the graph is entirely traversed in a depth-first way
		for each of its nodes to compute all the reachable nodes.
	 */
	NAIVE_REACHABILITY_ANALYSIS,
	/*
		This mode performs a smart(er?) reachability analysis, i.e., TODO
	 */
	SMART_REACHABILITY_ANALYSIS
}
