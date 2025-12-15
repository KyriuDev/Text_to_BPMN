package chat_gpt.ast_management;

import bpmn.graph.Node;
import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class ASTNormalizer
{
	private ASTNormalizer()
	{

	}

	public static HashMap<String, String> normalize(final Collection<AbstractSyntaxTree> abstractSyntaxTrees)
	{
		final ArrayList<AbstractSyntaxTree> normalizedTrees = new ArrayList<>();
		final HashMap<AbstractSyntaxNode, AbstractSyntaxNode> correspondences = new HashMap<>();
		final HashMap<String, String> nameCorrespondences = new HashMap<>();

		for (AbstractSyntaxTree abstractSyntaxTree : abstractSyntaxTrees)
		{
			final AbstractSyntaxTree normalizedTree = ASTNormalizer.getNormalizedVersionOf(abstractSyntaxTree, correspondences, nameCorrespondences);
			normalizedTrees.add(normalizedTree);
		}

		abstractSyntaxTrees.clear();
		abstractSyntaxTrees.addAll(normalizedTrees);

		return nameCorrespondences;
	}

	public static Collection<AbstractSyntaxTree> getNormalizedVersionOf(final Collection<AbstractSyntaxTree> abstractSyntaxTrees)
	{
		final ArrayList<AbstractSyntaxTree> normalizedTrees = new ArrayList<>();
		final HashMap<AbstractSyntaxNode, AbstractSyntaxNode> correspondences = new HashMap<>();
		final HashMap<String, String> nameCorrespondences = new HashMap<>();

		for (AbstractSyntaxTree abstractSyntaxTree : abstractSyntaxTrees)
		{
			final AbstractSyntaxTree normalizedTree = ASTNormalizer.getNormalizedVersionOf(abstractSyntaxTree, correspondences, nameCorrespondences);
			normalizedTrees.add(normalizedTree);
		}

		return normalizedTrees;
	}

	//Private methods

	private static AbstractSyntaxTree getNormalizedVersionOf(final AbstractSyntaxTree abstractSyntaxTree,
															 final HashMap<AbstractSyntaxNode, AbstractSyntaxNode> correspondences,
															 final HashMap<String, String> nameCorrespondences)
	{
		if (abstractSyntaxTree.root().type() == AbstractType.TASK)
		{
			final String normalizedName = ASTNormalizer.getNormalizedName(correspondences.size());
			final AbstractSyntaxNode correspondence = correspondences.computeIfAbsent(abstractSyntaxTree.root(), a -> AbstractSyntaxNodeFactory.newTask(normalizedName));
			nameCorrespondences.put(normalizedName, abstractSyntaxTree.root().label());
			return new AbstractSyntaxTree(correspondence.copy());
		}

		final AbstractSyntaxTree normalizedTree = new AbstractSyntaxTree(abstractSyntaxTree.root().copy());
		ASTNormalizer.normalize(normalizedTree.root(), abstractSyntaxTree.root(), correspondences, nameCorrespondences);
		return normalizedTree;
	}

	private static void normalize(final AbstractSyntaxNode normalizedNode,
								  final AbstractSyntaxNode currentNode,
							      final HashMap<AbstractSyntaxNode, AbstractSyntaxNode> correspondences,
								  final HashMap<String, String> nameCorrespondences)
	{
		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			final AbstractSyntaxNode normalizedSuccessor;

			if (successor.type() == AbstractType.TASK)
			{
				final String normalizedName = ASTNormalizer.getNormalizedName(correspondences.size());
				final AbstractSyntaxNode correspondence = correspondences.computeIfAbsent(successor, a -> AbstractSyntaxNodeFactory.newTask(normalizedName));
				nameCorrespondences.put(normalizedName, successor.label());
				normalizedSuccessor = correspondence.copy();
			}
			else
			{
				normalizedSuccessor = successor.copy();
			}

			normalizedNode.addSuccessor(normalizedSuccessor);
			normalizedSuccessor.setPredecessor(normalizedNode);

			ASTNormalizer.normalize(normalizedSuccessor, successor, correspondences, nameCorrespondences);
		}
	}

	private static String getNormalizedName(final int nbCorresp)
	{
		int currentValue = nbCorresp;
		final StringBuilder builder = new StringBuilder();

		while (currentValue >= 26)
		{
			builder.append('A');
			currentValue -= 26;
		}

		final char finalChar = (char) ('A' + currentValue);
		builder.append(finalChar);

		return builder.toString();
	}
}
