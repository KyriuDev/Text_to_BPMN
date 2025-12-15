package chat_gpt.tests;

import other.Pair;

public class TreeMergingTestSet
{
	public static final Pair<String, String> EX_1 = new Pair<>("A < B", "B < C");
	public static final Pair<String, String> EX_1_REV = new Pair<>(EX_1.second(), EX_1.first());
	public static final Pair<String, String> EX_2 = new Pair<>("(D & E) | F", "(((D < E) | C) < B) | A");
	public static final Pair<String, String> EX_2_REV = new Pair<>(EX_2.second(), EX_2.first());

	public static final Pair<String, String> EX_3 = new Pair<>("((A & I) < D) | (B & F)", "(F < G) & D & H & I");
	public static final Pair<String, String> EX_3_REV = new Pair<>(EX_3.second(), EX_3.first());
	public static final Pair<String, String> IMPOSSIBLE_1 = new Pair<>("A < B", "B < A");
	public static final Pair<String, String> IMPOSSIBLE_1_REV = new Pair<>("B < A", "A < B");

	private TreeMergingTestSet()
	{

	}
}
