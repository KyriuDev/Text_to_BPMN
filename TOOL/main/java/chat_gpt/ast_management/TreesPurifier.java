package chat_gpt.ast_management;

import chat_gpt.ast_management.AbstractSyntaxTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class TreesPurifier
{
	private TreesPurifier()
	{

	}

	public static void purify(final ArrayList<AbstractSyntaxTree> originalTrees)
	{
		final HashSet<String> alreadySeen = new HashSet<>();

		for (Iterator<AbstractSyntaxTree> iterator = originalTrees.iterator(); iterator.hasNext(); )
		{
			final AbstractSyntaxTree tree = iterator.next();

			if (alreadySeen.contains(tree.hash()))
			{
				iterator.remove();
			}
			else
			{
				alreadySeen.add(tree.hash());
			}
		}
	}
}
