package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.constants.LoopSide;
import other.MyOwnLogger;
import other.Pair;

import java.util.ArrayList;
import java.util.HashSet;

public class ASTLoopExtractor
{
	private ASTLoopExtractor()
	{

	}

	public static ArrayList<Pair<HashSet<String>, HashSet<String>>> extract(final ArrayList<AbstractSyntaxTree> trees)
	{
		final ArrayList<Pair<HashSet<String>, HashSet<String>>> splitLoops = ASTLoopExtractor.extractLoops(trees);
		ASTLoopExtractor.mergeLoopSets(splitLoops);

		MyOwnLogger.append("Found " + splitLoops.size() + " independent loops");
		int i = 1;

		for (Pair<HashSet<String>, HashSet<String>> loop : splitLoops)
		{
			final StringBuilder builder = new StringBuilder("Loop " + i++ + " has the following nodes:\n- Mandatory: [");
			String separator = "";

			for (String s : loop.first())
			{
				builder.append(separator).append(s);
				separator = ", ";
			}

			separator = "";
			builder.append("]\n- Optional: [");

			for (String s : loop.second())
			{
				builder.append(separator).append(s);
				separator = ", ";
			}

			builder.append("]");

			MyOwnLogger.append(builder.toString());
		}

		MyOwnLogger.append("The trees without loop are:\n");

		for (AbstractSyntaxTree abstractSyntaxTree : trees)
		{
			MyOwnLogger.append(abstractSyntaxTree.toString());
		}

		return splitLoops;
	}

	//Private methods

	private static ArrayList<Pair<HashSet<String>, HashSet<String>>> extractLoops(final ArrayList<AbstractSyntaxTree> treesToMerge)
	{
		final ArrayList<Pair<HashSet<String>, HashSet<String>>> loops = new ArrayList<>();

		for (AbstractSyntaxTree tree : treesToMerge)
		{
			MyOwnLogger.append("Tree for loop:\n\n" + tree);
			ASTLoopExtractor.extractLoop(tree.root(), false, LoopSide.NONE, loops, null, tree);
		}

		return loops;
	}

	private static void extractLoop(final AbstractSyntaxNode currentNode,
									final boolean inLoop,
									final LoopSide loopSide,
									final ArrayList<Pair<HashSet<String>, HashSet<String>>> loops,
									final Pair<HashSet<String>, HashSet<String>> currentLoop,
									final AbstractSyntaxTree tree)
	{


		if (inLoop)
		{
			final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());
			final LoopSide newLoopSide;

			if (currentNode.type() == AbstractType.TASK)
			{
				if (loopSide == LoopSide.MANDATORY)
				{
					currentLoop.first().add(currentNode.label());
					newLoopSide = loopSide;
				}
				else if (loopSide == LoopSide.OPTIONAL)
				{
					currentLoop.second().add(currentNode.label());
					newLoopSide = loopSide;
				}
				else
				{
					//ChatGPT did not return the information of mandatory or optional, thus we suppose that it is mandatory
					currentLoop.first().add(currentNode.label());
					newLoopSide = LoopSide.MANDATORY;
					//throw new IllegalStateException();
				}
			}
			else if (currentNode.type() == AbstractType.LOOP)
			{
				ASTLoopExtractor.removeLoop(currentNode, tree);
				newLoopSide = loopSide;
			}
			else if (currentNode.type() == AbstractType.LOOP_MANDATORY)
			{
				ASTLoopExtractor.removeLoop(currentNode, tree);
				newLoopSide = LoopSide.MANDATORY;
			}
			else if (currentNode.type() == AbstractType.LOOP_OPTIONAL)
			{
				ASTLoopExtractor.removeLoop(currentNode, tree);
				newLoopSide = LoopSide.OPTIONAL;
			}
			else
			{
				newLoopSide = loopSide;
			}

			for (AbstractSyntaxNode child : childNodes)
			{
				ASTLoopExtractor.extractLoop(child, true, newLoopSide, loops, currentLoop, tree);
			}
		}
		else
		{
			if (currentNode.type() == AbstractType.LOOP_MANDATORY)
			{
				final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());
				ASTLoopExtractor.removeLoop(currentNode, tree);

				final Pair<HashSet<String>, HashSet<String>> loop = new Pair<>(new HashSet<>(), new HashSet<>());
				loops.add(loop);

				for (AbstractSyntaxNode child : childNodes)
				{
					ASTLoopExtractor.extractLoop(child, true, LoopSide.MANDATORY, loops, loop, tree);
				}
			}
			else if (currentNode.type() == AbstractType.LOOP_OPTIONAL)
			{
				final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());
				ASTLoopExtractor.removeLoop(currentNode, tree);

				final Pair<HashSet<String>, HashSet<String>> loop = new Pair<>(new HashSet<>(), new HashSet<>());
				loops.add(loop);

				for (AbstractSyntaxNode child : childNodes)
				{
					ASTLoopExtractor.extractLoop(child, true, LoopSide.OPTIONAL, loops, loop, tree);
				}
			}
			else if (currentNode.type() == AbstractType.LOOP)
			{
				final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());
				ASTLoopExtractor.removeLoop(currentNode, tree);

				final Pair<HashSet<String>, HashSet<String>> loop = new Pair<>(new HashSet<>(), new HashSet<>());
				loops.add(loop);

				for (AbstractSyntaxNode child : childNodes)
				{
					ASTLoopExtractor.extractLoop(child, true, LoopSide.NONE, loops, loop, tree);
				}
			}
			else if (currentNode.successors().size() == 2)
			{
				final AbstractSyntaxNode succ1 = currentNode.successors().get(0);
				final AbstractSyntaxNode succ2 = currentNode.successors().get(1);

				if ((succ1.type() == AbstractType.LOOP_MANDATORY
						&& succ2.type() == AbstractType.LOOP_OPTIONAL)
						|| (succ1.type() == AbstractType.LOOP_OPTIONAL
						&& succ2.type() == AbstractType.LOOP_MANDATORY))
				{
					final Pair<HashSet<String>, HashSet<String>> loop = new Pair<>(new HashSet<>(), new HashSet<>());
					loops.add(loop);

					final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());

					for (AbstractSyntaxNode child : childNodes)
					{
						ASTLoopExtractor.extractLoop(child, true, LoopSide.NONE, loops, loop, tree);
					}
				}
				else
				{
					final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());

					for (AbstractSyntaxNode child : childNodes)
					{
						ASTLoopExtractor.extractLoop(child, false, LoopSide.NONE, loops, null, tree);
					}
				}
			}
			else
			{
				final ArrayList<AbstractSyntaxNode> childNodes = new ArrayList<>(currentNode.successors());

				for (AbstractSyntaxNode child : childNodes)
				{
					ASTLoopExtractor.extractLoop(child, false, LoopSide.NONE, loops, null, tree);
				}
			}
		}
	}

	private static void removeLoop(final AbstractSyntaxNode currentNode,
								   final AbstractSyntaxTree tree)
	{
		MyOwnLogger.append("Current loop node: " + currentNode);
		MyOwnLogger.append("Corresponding tree:\n\n" + tree);

		final AbstractSyntaxNode parent = currentNode.predecessor();
		final ArrayList<AbstractSyntaxNode> newChildren = currentNode.successors();

		MyOwnLogger.append("Parent: " + parent);

		if (parent == null)
		{
			if (currentNode.successors().size() == 1)
			{
				final AbstractSyntaxNode newRoot = currentNode.successors().iterator().next();
				newRoot.resetPredecessor();
				currentNode.removeSuccessors();
				tree.setRoot(newRoot);
			}
			else
			{
				currentNode.switchType(AbstractType.PAR);
			}
		}
		else if (parent.successors().size() == 1)
		{
			if (newChildren.size() == 1)
			{
				final AbstractSyntaxNode grandParent = parent.predecessor();

				if (grandParent == null)
				{
					final AbstractSyntaxNode newRoot = newChildren.get(0);
					newRoot.resetPredecessor();
					tree.setRoot(newRoot);
				}
				else
				{
					grandParent.replaceSuccessor(parent, newChildren.get(0));
					newChildren.get(0).setPredecessor(grandParent);
				}
			}
			else
			{
				parent.removeSuccessors();
				currentNode.resetPredecessor();
				currentNode.removeSuccessors();

				for (AbstractSyntaxNode child : newChildren)
				{
					parent.addSuccessor(child);
					child.setPredecessor(parent);
				}
			}
		}
		else
		{
			//System.out.println("Number of brothers : " + parent.successors().size());

			if (currentNode.successors().isEmpty())
			{
				parent.removeSuccessor(currentNode);

				if (parent.successors().size() == 1)
				{
					final AbstractSyntaxNode grandParent = parent.predecessor();
					final AbstractSyntaxNode lastNode = parent.successors().get(0);

					if (grandParent == null)
					{
						lastNode.resetPredecessor();
						tree.setRoot(lastNode);
					}
					else
					{
						grandParent.replaceSuccessor(parent, lastNode);
						lastNode.setPredecessor(grandParent);
					}
				}
			}
			else if (currentNode.successors().size() == 1)
			{
				final AbstractSyntaxNode successor = currentNode.successors().iterator().next();
				parent.replaceSuccessor(currentNode, successor);
				successor.setPredecessor(parent);
			}
			else
			{
				currentNode.switchType(AbstractType.PAR);
			}
		}
	}

	private static void mergeLoopSets(final ArrayList<Pair<HashSet<String>, HashSet<String>>> loops)
	{
		final ArrayList<Pair<HashSet<String>, HashSet<String>>> previousLoops = new ArrayList<>(loops);
		final ArrayList<Pair<HashSet<String>, HashSet<String>>> newLoops = new ArrayList<>();
		boolean changed = true;

		while (changed)
		{
			changed = false;
			int index1 = -1;
			int index2 = -1;

			for (int i = 0; i < previousLoops.size(); i++)
			{
				final Pair<HashSet<String>, HashSet<String>> loop1 = previousLoops.get(i);

				for (int j = 0; j < previousLoops.size(); j++)
				{
					final Pair<HashSet<String>, HashSet<String>> loop2 = previousLoops.get(j);

					if (loop1 != loop2)
					{
						for (String s1 : loop1.first())
						{
							if (loop2.first().contains(s1))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}

							if (loop2.second().contains(s1))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}
						}

						if (changed) break;

						for (String s2 : loop2.first())
						{
							if (loop1.first().contains(s2))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}

							if (loop1.second().contains(s2))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}
						}

						if (changed) break;
					}
				}

				if (changed) break;
			}

			if (changed)
			{
				final int firstIndex = Math.max(index1, index2);
				final int secondIndex = index1 == firstIndex ? index2 : index1;

				final Pair<HashSet<String>, HashSet<String>> pair1 = previousLoops.remove(firstIndex);
				final Pair<HashSet<String>, HashSet<String>> pair2 = previousLoops.remove(secondIndex);
				final Pair<HashSet<String>, HashSet<String>> mergedSet = new Pair<>(new HashSet<>(), new HashSet<>());
				mergedSet.first().addAll(pair1.first());
				mergedSet.first().addAll(pair1.second());
				mergedSet.first().addAll(pair2.first());
				mergedSet.first().addAll(pair2.second());

				newLoops.addAll(previousLoops);
				newLoops.add(mergedSet);
				previousLoops.clear();
				previousLoops.addAll(newLoops);
				newLoops.clear();
			}
		}

		loops.clear();
		loops.addAll(previousLoops);
	}
}
