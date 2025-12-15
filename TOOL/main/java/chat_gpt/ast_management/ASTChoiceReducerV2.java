package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ASTChoiceReducerV2
{
	private ASTChoiceReducerV2()
	{

	}

	public static void releaseConstraints(final AbstractSyntaxTree currentTree,
										  final List<AbstractSyntaxTree> constraints,
										  final AbstractSyntaxTree treeToMerge,
										  final AbstractSyntaxNode nodeToVerify)
	{
		System.out.println("AST before releasing choice constraints:\n\n" + currentTree.toString());
		/*System.out.println("--------BEFORE-----------");
		for (AbstractSyntaxTree constraint : constraints)
		{
			System.out.println(constraint.toString());
		}*/

		final AbstractSyntaxNode leftNodeToMerge = currentTree.findNodeOfLabel(treeToMerge.root().successors().get(0).label());
		final AbstractSyntaxNode rightNodeToMerge = currentTree.findNodeOfLabel(treeToMerge.root().successors().get(1).label());

		System.out.println("Tree to merge: " + treeToMerge.toString());
		System.out.println("Left node in tree to merge: " + treeToMerge.root().successors().get(0).id());
		System.out.println("Right node in tree to merge: " + treeToMerge.root().successors().get(1).id());

		if (leftNodeToMerge == null
			|| rightNodeToMerge == null) throw new IllegalStateException();

		final AbstractSyntaxNode leastCommonAncestor = ASTUtils.getLeastCommonAncestor(leftNodeToMerge, rightNodeToMerge);
		final AbstractSyntaxNode choiceToConsider = leastCommonAncestor.type() == AbstractType.XOR ? leastCommonAncestor : leastCommonAncestor.findClosestAncestorWithOperator(AbstractType.XOR);

		if (choiceToConsider == null) throw new IllegalStateException();

		if (choiceToConsider.successors().size() < 2) throw new IllegalStateException();

		if (choiceToConsider.successors().size() == 2)
		{
			return;
		}

		AbstractSyntaxNode successorLeadingToLeftNode = choiceToConsider.getSuccessorLeadingTo(leftNodeToMerge);
		final AbstractSyntaxNode successorLeadingToRightNode = choiceToConsider.getSuccessorLeadingTo(rightNodeToMerge);

		final HashSet<AbstractSyntaxNode> eligibleNodes = new HashSet<>();

		//Try to merge nodes with leftNode
		for (AbstractSyntaxNode choiceSuccessor : choiceToConsider.successors())
		{
			if (!choiceSuccessor.equals(successorLeadingToLeftNode)
				&& !choiceSuccessor.equals(successorLeadingToRightNode))
			{
				eligibleNodes.add(choiceSuccessor);
			}
		}

		final HashSet<AbstractSyntaxNode> mergeableNodes = new HashSet<>();

		for (AbstractSyntaxNode eligibleNode : eligibleNodes)
		{
			if (ASTChoiceReducerV2.nodesAreMergeable(eligibleNode, successorLeadingToLeftNode, constraints))
			{
				//System.out.println("nodes are mergeable");
				mergeableNodes.add(eligibleNode);
			}
		}

		if (mergeableNodes.isEmpty())
		{
			ASTChoiceReducerV2.releaseSubConstraints(eligibleNodes, successorLeadingToLeftNode, constraints);
		}
		else
		{
			choiceToConsider.removeSuccessor(successorLeadingToLeftNode);
			choiceToConsider.removeSuccessors(mergeableNodes);

			final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);

			if (mergeableNodes.size() == 1)
			{
				choiceToConsider.addSuccessor(parallelNode);
				parallelNode.setPredecessor(choiceToConsider);
				parallelNode.addSuccessor(successorLeadingToLeftNode);
				successorLeadingToLeftNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(mergeableNodes.iterator().next());
				mergeableNodes.iterator().next().setPredecessor(parallelNode);
			}
			else
			{
				final AbstractSyntaxNode choiceNode = new AbstractSyntaxNode(AbstractType.XOR);
				choiceToConsider.addSuccessor(parallelNode);
				parallelNode.setPredecessor(choiceToConsider);
				parallelNode.addSuccessor(choiceNode);
				choiceNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(successorLeadingToLeftNode);
				successorLeadingToLeftNode.setPredecessor(parallelNode);

				for (AbstractSyntaxNode mergeableNode : mergeableNodes)
				{
					choiceNode.addSuccessor(mergeableNode);
					mergeableNode.setPredecessor(choiceNode);
				}
			}
		}

		//Try to merge nodes with rightNode
		eligibleNodes.clear();
		successorLeadingToLeftNode = choiceToConsider.getSuccessorLeadingTo(leftNodeToMerge);

		for (AbstractSyntaxNode choiceSuccessor : choiceToConsider.successors())
		{
			if (!choiceSuccessor.equals(successorLeadingToLeftNode)
				&& !choiceSuccessor.equals(successorLeadingToRightNode))
			{
				eligibleNodes.add(choiceSuccessor);
			}
		}

		final HashSet<AbstractSyntaxNode> mergeableNodes2 = new HashSet<>();

		for (AbstractSyntaxNode eligibleNode : eligibleNodes)
		{
			if (ASTChoiceReducerV2.nodesAreMergeable(eligibleNode, successorLeadingToRightNode, constraints))
			{
				//System.out.println("nodes are mergeable");
				mergeableNodes2.add(eligibleNode);
			}
		}

		if (mergeableNodes2.isEmpty())
		{
			ASTChoiceReducerV2.releaseSubConstraints(eligibleNodes, successorLeadingToRightNode, constraints);
		}
		else
		{
			choiceToConsider.removeSuccessor(successorLeadingToRightNode);
			choiceToConsider.removeSuccessors(mergeableNodes2);

			final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);

			if (mergeableNodes2.size() == 1)
			{
				choiceToConsider.addSuccessor(parallelNode);
				parallelNode.setPredecessor(choiceToConsider);
				parallelNode.addSuccessor(successorLeadingToRightNode);
				successorLeadingToRightNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(mergeableNodes2.iterator().next());
				mergeableNodes2.iterator().next().setPredecessor(parallelNode);
			}
			else
			{
				final AbstractSyntaxNode choiceNode = new AbstractSyntaxNode(AbstractType.XOR);
				choiceToConsider.addSuccessor(parallelNode);
				parallelNode.setPredecessor(choiceToConsider);
				parallelNode.addSuccessor(choiceNode);
				choiceNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(successorLeadingToRightNode);
				successorLeadingToRightNode.setPredecessor(parallelNode);

				for (AbstractSyntaxNode mergeableNode : mergeableNodes2)
				{
					choiceNode.addSuccessor(mergeableNode);
					mergeableNode.setPredecessor(choiceNode);
				}
			}
		}

		/*System.out.println("--------AFTER-----------");
		for (AbstractSyntaxTree constraint : constraints)
		{
			System.out.println(constraint.toString());
		}*/

		//if (!ASTUtils.getProblematicTrees(currentTree, constraints).isEmpty()) throw new IllegalStateException();
	}

	//Private methods

	private static boolean releaseSubConstraints(final Collection<AbstractSyntaxNode> currentParents,
												 final AbstractSyntaxNode nodeToVerify,
												 final List<AbstractSyntaxTree> constraints)
	{
		boolean merged = false;

		for (AbstractSyntaxNode currentParent : currentParents)
		{
			if (currentParent.type() == AbstractType.SEQ
				|| currentParent.type() == AbstractType.LOOP) continue;

			final ArrayList<AbstractSyntaxNode> mergeableNodes = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> nonMergeableNodes = new ArrayList<>();

			for (AbstractSyntaxNode currentChild : currentParent.successors())
			{
				if (ASTChoiceReducerV2.nodesAreMergeable(currentChild, nodeToVerify, constraints))
				{
					mergeableNodes.add(currentChild);
				}
				else
				{
					nonMergeableNodes.add(currentChild);
				}
			}

			if (!mergeableNodes.isEmpty())
			{
				nodeToVerify.predecessor().removeSuccessor(nodeToVerify);

				final ArrayList<AbstractSyntaxNode> nodesToRelocate;
				final AbstractSyntaxNode mainNode;
				final AbstractSyntaxNode secondaryNode;

				if (currentParent.type() == AbstractType.PAR)
				{
					nodesToRelocate = mergeableNodes;
					mainNode = new AbstractSyntaxNode(AbstractType.XOR);
					secondaryNode = new AbstractSyntaxNode(AbstractType.PAR);
				}
				else if (currentParent.type() == AbstractType.XOR)
				{
					nodesToRelocate = nonMergeableNodes;
					mainNode = new AbstractSyntaxNode(AbstractType.PAR);
					secondaryNode = new AbstractSyntaxNode(AbstractType.XOR);
				}
				else
				{
					throw new IllegalStateException();
				}

				if (nodesToRelocate.size() == 1)
				{
					currentParent.removeSuccessors(nodesToRelocate);
					currentParent.addSuccessor(mainNode);
					mainNode.setPredecessor(currentParent);
					mainNode.addSuccessor(nodeToVerify);
					nodeToVerify.setPredecessor(mainNode);
					mainNode.addSuccessor(nodesToRelocate.get(0));
					nodesToRelocate.get(0).setPredecessor(mainNode);
				}
				else
				{
					currentParent.removeSuccessors(nodesToRelocate);
					currentParent.addSuccessor(mainNode);
					mainNode.setPredecessor(currentParent);
					mainNode.addSuccessor(nodeToVerify);
					nodeToVerify.setPredecessor(mainNode);
					mainNode.addSuccessor(secondaryNode);
					secondaryNode.setPredecessor(mainNode);

					for (AbstractSyntaxNode nodeToRelocate : nodesToRelocate)
					{
						secondaryNode.addSuccessor(nodeToRelocate);
						nodeToRelocate.setPredecessor(secondaryNode);
					}
				}

				merged = true;
			}
		}

		if (merged)
		{
			return true;
		}
		else
		{
			for (AbstractSyntaxNode currentParent : currentParents)
			{
				final boolean childMerged = ASTChoiceReducerV2.releaseSubConstraints(currentParent.successors(), nodeToVerify, constraints);

				if (childMerged)
				{
					return true;
				}
			}
		}

		return false;
	}

	private static boolean nodesAreMergeable(final AbstractSyntaxNode node1,
											 final AbstractSyntaxNode node2,
											 final List<AbstractSyntaxTree> constraints)
	{
		final ArrayList<AbstractSyntaxNode> tasks1 = new ArrayList<>();
		final ArrayList<AbstractSyntaxNode> tasks2 = new ArrayList<>();
		ASTUtils.retrieveAllTasksFrom(node1, tasks1);
		ASTUtils.retrieveAllTasksFrom(node2, tasks2);

		for (AbstractSyntaxNode task1 : tasks1)
		{
			for (AbstractSyntaxNode task2 : tasks2)
			{
				final String dummyHash1 = task1.label() + "|" + task2.label();
				final String dummyHash2 = task2.label() + "|" + task1.label();

				for (AbstractSyntaxTree constraint : constraints)
				{
					//System.out.println("Constraint hash: " + constraint.hash() + " VS dummy hash: " + dummyHash1 + "/" + dummyHash2);

					if (constraint.hash().equals(dummyHash1)
						|| constraint.hash().equals(dummyHash2))
					{
						//System.out.println("Nodes are not mergeable");
						return false;
					}
				}
			}
		}

		return true;
	}
}
