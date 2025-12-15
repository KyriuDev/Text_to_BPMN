package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.exceptions.ContradictoryValuesException;
import exceptions.ExpectedException;
import other.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class ASTChoiceGenerator
{
	private ASTChoiceGenerator()
	{

	}

	public static Pair<AbstractSyntaxTree, AbstractSyntaxTree> generateInitialTree(final ArrayList<AbstractSyntaxTree> treesToMerge,
																				   final List<AbstractSyntaxTree> constraints,
																				   final Pair<HashSet<String>, HashSet<String>> loop) throws ExpectedException
	{
		final ArrayList<AbstractSyntaxTree> choiceDependencies = ASTChoiceGenerator.extractChoices(treesToMerge);

		if (choiceDependencies.isEmpty()) return null;

		if (loop == null)
		{
			final ArrayList<ArrayList<AbstractSyntaxTree>> separatedTrees = ASTChoiceGenerator.separateTrees(choiceDependencies);
			final ArrayList<AbstractSyntaxTree> choiceTrees = new ArrayList<>();

			//System.out.println("Found " + separatedTrees.size() + " distinct choices");

			for (ArrayList<AbstractSyntaxTree> trees : separatedTrees)
			{
				final AbstractSyntaxTree mainTree = trees.remove(0).copy();
				final ASTMerger merger = new ASTMerger(mainTree, constraints, trees, null, true);
				final AbstractSyntaxTree mergedTree = merger.merge();
				choiceTrees.add(mergedTree);
			}

			if (choiceTrees.isEmpty()) throw new IllegalStateException();

			if (choiceTrees.size() == 1)
			{
				return new Pair<>(choiceTrees.get(0), null);
			}
			else
			{
				final AbstractSyntaxTree mainTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.PAR));

				for (AbstractSyntaxTree tree : choiceTrees)
				{
					mainTree.root().addSuccessor(tree.root());
					tree.root().setPredecessor(mainTree.root());
				}

				return new Pair<>(mainTree, null);
			}
		}
		else
		{
			final HashSet<String> mandatoryPart = loop.first();
			final HashSet<String> optionalPart = loop.second();

			final ArrayList<ArrayList<AbstractSyntaxTree>> separatedTreesMandatory = ASTChoiceGenerator.separateTrees(choiceDependencies);
			final ArrayList<ArrayList<AbstractSyntaxTree>> separatedTreesOptional = ASTChoiceGenerator.separateTrees(choiceDependencies);
			final ArrayList<AbstractSyntaxTree> mandatoryChoiceTrees = new ArrayList<>();
			final ArrayList<AbstractSyntaxTree> optionalChoiceTrees = new ArrayList<>();
			final AbstractSyntaxTree mandatoryChoiceTree;
			final AbstractSyntaxTree optionalChoiceTree;

			//Create mandatory choice
			for (Iterator<ArrayList<AbstractSyntaxTree>> iterator1 = separatedTreesMandatory.iterator(); iterator1.hasNext(); )
			{
				final ArrayList<AbstractSyntaxTree> trees = iterator1.next();

				for (Iterator<AbstractSyntaxTree> iterator = trees.iterator(); iterator.hasNext(); )
				{
					final AbstractSyntaxTree tree = iterator.next();
					final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
					final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

					if (!mandatoryPart.contains(leftNode.label())
						|| !mandatoryPart.contains(rightNode.label()))
					{
						iterator.remove();
					}
				}

				if (trees.isEmpty()) iterator1.remove();
			}

			for (ArrayList<AbstractSyntaxTree> trees : separatedTreesMandatory)
			{
				final AbstractSyntaxTree mainTree = trees.remove(0).copy();

				if (trees.isEmpty())
				{
					mandatoryChoiceTrees.add(mainTree);
				}
				else
				{
					final ASTMerger merger = new ASTMerger(mainTree, constraints, trees, null, true);
					final AbstractSyntaxTree mergedTree = merger.merge();
					mandatoryChoiceTrees.add(mergedTree);
				}
			}

			if (mandatoryChoiceTrees.isEmpty())
			{
				mandatoryChoiceTree = null;
			}
			else if (mandatoryChoiceTrees.size() == 1)
			{
				mandatoryChoiceTree = mandatoryChoiceTrees.get(0);
			}
			else
			{
				final AbstractSyntaxTree mainTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.PAR));

				for (AbstractSyntaxTree tree : mandatoryChoiceTrees)
				{
					mainTree.root().addSuccessor(tree.root());
					tree.root().setPredecessor(mainTree.root());
				}

				mandatoryChoiceTree = mainTree;
			}

			//Create optional choice
			for (final Iterator<ArrayList<AbstractSyntaxTree>> iterator1 = separatedTreesOptional.iterator(); iterator1.hasNext(); )
			{
				final ArrayList<AbstractSyntaxTree> trees = iterator1.next();

				for (Iterator<AbstractSyntaxTree> iterator = trees.iterator(); iterator.hasNext(); )
				{
					final AbstractSyntaxTree tree = iterator.next();
					final AbstractSyntaxNode leftNode = tree.root().successors().get(0);
					final AbstractSyntaxNode rightNode = tree.root().successors().get(1);

					if (!optionalPart.contains(leftNode.label())
						|| !optionalPart.contains(rightNode.label()))
					{
						iterator.remove();
					}
				}

				if (trees.isEmpty()) iterator1.remove();
			}

			for (ArrayList<AbstractSyntaxTree> trees : separatedTreesOptional)
			{
				final AbstractSyntaxTree mainTree = trees.remove(0).copy();

				if (trees.isEmpty())
				{
					optionalChoiceTrees.add(mainTree);
				}
				else
				{
					final ASTMerger merger = new ASTMerger(mainTree, constraints, trees, null,true);
					final AbstractSyntaxTree mergedTree = merger.merge();
					optionalChoiceTrees.add(mergedTree);
				}
			}

			if (optionalChoiceTrees.isEmpty())
			{
				optionalChoiceTree = null;
			}
			else if (optionalChoiceTrees.size() == 1)
			{
				optionalChoiceTree = mandatoryChoiceTrees.get(0);
			}
			else
			{
				final AbstractSyntaxTree mainTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.PAR));

				for (AbstractSyntaxTree tree : optionalChoiceTrees)
				{
					mainTree.root().addSuccessor(tree.root());
					tree.root().setPredecessor(mainTree.root());
				}

				optionalChoiceTree = mainTree;
			}

			if (mandatoryChoiceTree == null
				&& optionalChoiceTree == null)
			{
				return null;
			}
			else
			{
				return new Pair<>(mandatoryChoiceTree, optionalChoiceTree);
			}
		}
	}

	public static Pair<AbstractSyntaxTree, AbstractSyntaxTree> generateInitialTree(final ArrayList<AbstractSyntaxTree> treesToMerge,
																				   final List<AbstractSyntaxTree> constraints) throws ExpectedException
	{
		return ASTChoiceGenerator.generateInitialTree(treesToMerge, constraints, null);
	}

	//Private methods

	private static ArrayList<AbstractSyntaxTree> extractChoices(final ArrayList<AbstractSyntaxTree> dependencies)
	{
		final ArrayList<AbstractSyntaxTree> choices = new ArrayList<>();

		for (final AbstractSyntaxTree currentTree : dependencies)
		{
			if (currentTree.root().type() == AbstractType.XOR)
			{
				choices.add(currentTree);
			}
		}

		return choices;
	}

	private static ArrayList<ArrayList<AbstractSyntaxTree>> separateTrees(final ArrayList<AbstractSyntaxTree> trees)
	{
		final ArrayList<ArrayList<AbstractSyntaxTree>> separatedTrees = new ArrayList<>();

		for (AbstractSyntaxTree currentTree : trees)
		{
			boolean treeWasMerged = false;

			final String currentTreeLabel1 = currentTree.root().successors().get(0).label();
			final String currentTreeLabel2 = currentTree.root().successors().get(1).label();

			for (ArrayList<AbstractSyntaxTree> separatedList : separatedTrees)
			{
				for (AbstractSyntaxTree separatedTree : separatedList)
				{
					final String label1 = separatedTree.root().successors().get(0).label();
					final String label2 = separatedTree.root().successors().get(1).label();

					if (label1.equals(currentTreeLabel1)
						|| label1.equals(currentTreeLabel2)
						|| label2.equals(currentTreeLabel1)
						|| label2.equals(currentTreeLabel2))
					{
						separatedList.add(currentTree);
						treeWasMerged = true;
						break;
					}
				}

				if (treeWasMerged) break;
			}

			if (!treeWasMerged)
			{
				final ArrayList<AbstractSyntaxTree> newSeparatedList = new ArrayList<>();
				newSeparatedList.add(currentTree);
				separatedTrees.add(newSeparatedList);
			}
		}

		boolean iterate = true;

		while (iterate)
		{
			iterate = false;
			int i,j = -1;

			for (i = 0; i < separatedTrees.size(); i++)
			{
				final ArrayList<AbstractSyntaxTree> list1 = separatedTrees.get(i);

				for (j = i + 1; j < separatedTrees.size(); j++)
				{
					final ArrayList<AbstractSyntaxTree> list2 = separatedTrees.get(j);

					if (ASTChoiceGenerator.listsHaveCommonElements(list1, list2))
					{
						iterate = true;
						break;
					}
				}

				if (iterate) break;
			}

			if (iterate)
			{
				if (i == -1 || j == -1) throw new IllegalStateException();

				final ArrayList<AbstractSyntaxTree> list1 = separatedTrees.get(i);
				final ArrayList<AbstractSyntaxTree> list2 = separatedTrees.remove(j);
				list1.addAll(list2);
			}
		}

		return separatedTrees;
	}

	private static boolean listsHaveCommonElements(final ArrayList<AbstractSyntaxTree> list1,
												   final ArrayList<AbstractSyntaxTree> list2)
	{
		for (AbstractSyntaxTree tree1 : list1)
		{
			final String label11 = tree1.root().successors().get(0).label();
			final String label12 = tree1.root().successors().get(1).label();

			for (AbstractSyntaxTree tree2 : list2)
			{
				final String label21 = tree2.root().successors().get(0).label();
				final String label22 = tree2.root().successors().get(1).label();

				if (label11.equals(label21)
					|| label11.equals(label22)
					|| label12.equals(label21)
					|| label12.equals(label22))
				{
					return true;
				}
			}
		}

		return false;
	}
}
