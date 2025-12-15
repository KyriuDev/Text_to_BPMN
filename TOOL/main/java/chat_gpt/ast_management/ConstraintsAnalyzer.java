package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;
import java.util.HashSet;

public class ConstraintsAnalyzer
{
	private ConstraintsAnalyzer()
	{

	}

	public static boolean verifyConstraints(final ArrayList<AbstractSyntaxTree> trees)
	{
		final HashSet<String> treesHashes = new HashSet<>();

		for (AbstractSyntaxTree tree : trees)
		{
			treesHashes.add(tree.hash());
		}

		for (AbstractSyntaxTree tree : trees)
		{
			System.out.println("Tree:\n\n" + tree.toString());
			final ArrayList<String> problematicTreeHashes = ConstraintsAnalyzer.computeProblematicTrees(tree);
			System.out.println("has the following problematic trees: " + problematicTreeHashes);

			if (problematicTreeHashes == null) return false; //One of the constraints is not a par, a seq or a xor

			for (String problematicHash : problematicTreeHashes)
			{
				if (treesHashes.contains(problematicHash))
				{
					System.out.println();
					return false;
				}
			}
		}

		return true;
	}

	//Private methods

	private static ArrayList<String> computeProblematicTrees(final AbstractSyntaxTree tree)
	{
		final AbstractType type = tree.root().type();

		if (type == AbstractType.PAR)
		{
			return new ArrayList<>();
		}
		else if (type == AbstractType.SEQ)
		{
			final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
			final AbstractSyntaxNode rightNode = tree.root().successors().get(1);
			final ArrayList<String> problematicHashes = new ArrayList<>();
			//problematicHashes.add(rightNode.label() + "<" + leftNode.label()); 	TEMPORARY REMOVAL
			problematicHashes.add(leftNode.label() + "|" + rightNode.label());
			problematicHashes.add(rightNode.label() + "|" + leftNode.label());

			return problematicHashes;
		}
		else if (type == AbstractType.XOR)
		{
			final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
			final AbstractSyntaxNode rightNode = tree.root().successors().get(1);
			final ArrayList<String> problematicHashes = new ArrayList<>();
			problematicHashes.add(leftNode.label() + "<" + rightNode.label());
			problematicHashes.add(rightNode.label() + "<" + leftNode.label());

			return problematicHashes;
		}
		else
		{
			return null;
		}
	}
}
