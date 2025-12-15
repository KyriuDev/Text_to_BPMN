package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.TreeElectionStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class ASTElector
{
	private static final TreeElectionStrategy STRATEGY = TreeElectionStrategy.MOST_COMMON_FIRST;

	private ASTElector()
	{

	}

	public static AbstractSyntaxTree nextTree(final ArrayList<AbstractSyntaxTree> trees,
											  final AbstractSyntaxTree currentTree)
	{
		if (STRATEGY == TreeElectionStrategy.RANDOM)
		{
			return ASTElector.nextRandomTree(trees);
		}
		else if (STRATEGY == TreeElectionStrategy.MOST_COMMON_FIRST)
		{
			return ASTElector.nextMostCommonTree(trees, currentTree);
		}
		else
		{
			throw new IllegalStateException("Strategy |" + STRATEGY + "| not yet implemented.");
		}
	}

	//Private methods

	private static AbstractSyntaxTree nextRandomTree(final ArrayList<AbstractSyntaxTree> trees)
	{
		if (trees.isEmpty())
		{
			return null;
		}
		else if (trees.size() == 1)
		{
			return trees.get(0);
		}
		else
		{
			final Random random = new Random();
			return trees.get(random.nextInt(trees.size()));
		}
	}

	private static AbstractSyntaxTree nextMostCommonTree(final ArrayList<AbstractSyntaxTree> trees,
														 final AbstractSyntaxTree currentTree)
	{
		if (trees.isEmpty())
		{
			return null;
		}
		else if (trees.size() == 1)
		{
			return trees.get(0);
		}
		else
		{
			final HashSet<AbstractSyntaxNode> tasks = new HashSet<>();
			ASTUtils.retrieveAllTasksFrom(currentTree.root(), tasks);

			boolean tryFull = true;
			boolean tryHalf = true;
			AbstractSyntaxTree eligibleTree = null;

			while (true)
			{
				if (tryFull)
				{
					for (AbstractSyntaxTree tree : trees)
					{
						final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
						final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

						if (ASTUtils.listContainsNode(tasks, leftNode)
							&& ASTUtils.listContainsNode(tasks, rightNode))
						{
							eligibleTree = tree;
							break;
						}
					}

					if (eligibleTree != null) break;

					tryFull = false;
				}
				else if (tryHalf)
				{
					for (AbstractSyntaxTree tree : trees)
					{
						final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
						final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

						if (ASTUtils.listContainsNode(tasks, leftNode)
							 || ASTUtils.listContainsNode(tasks, rightNode))
						{
							eligibleTree = tree;
							break;
						}
					}

					if (eligibleTree != null) break;

					tryHalf = false;
				}
				else
				{
					eligibleTree = trees.get(0);
					break;
				}
			}

			if (eligibleTree == null) throw new IllegalStateException();

			return eligibleTree;
		}
	}
}
