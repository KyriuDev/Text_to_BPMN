package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.constants.IntegrityStatus;
import chat_gpt.ast_management.constants.MergeStatus;
import refactoring.legacy.partial_order_to_bpmn.AbstractNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ASTUtils
{
	private ASTUtils()
	{

	}

	public static void retrieveAllTasksFrom(final AbstractSyntaxNode node,
											final Collection<AbstractSyntaxNode> tasks)
	{
		if (node.type() == AbstractType.TASK)
		{
			tasks.add(node);
		}

		for (AbstractSyntaxNode child : node.successors())
		{
			ASTUtils.retrieveAllTasksFrom(child, tasks);
		}
	}

	public static AbstractSyntaxNode getLeastCommonAncestor(final AbstractSyntaxNode node1,
															final AbstractSyntaxNode node2)
	{
		final AbstractSyntaxTree tree = new AbstractSyntaxTree();
		tree.setRoot(node1);

		////System.out.println(node1.label());
		////System.out.println(node2.label());
		////System.out.println(tree.toString());

		long i = 0;

		while (tree.root() != null
				&& tree.findNodeOfLabel(node2.label()) == null)
		{
			final IntegrityStatus integrityStatus = ASTIntegrityVerifier.verifyIntegrity(tree, null);

			if (integrityStatus != IntegrityStatus.VALID)
			{
				//throw new IllegalStateException("INTEGRITY CHECK FAILED: " + integrityStatus.meaning());
			}

			tree.setRoot(tree.root().predecessor());
		}

		if (tree.root() == null) throw new IllegalStateException();

		return tree.root();
	}

	public static AbstractSyntaxNode getLeastCommonAncestor(final Collection<AbstractSyntaxNode> abstractNodes)
	{
		final Iterator<AbstractSyntaxNode> iterator = abstractNodes.iterator();
		final AbstractSyntaxTree tree = new AbstractSyntaxTree(iterator.next());

		////System.out.println(node1.label());
		////System.out.println(node2.label());
		////System.out.println(tree.toString());

		while (iterator.hasNext())
		{
			final AbstractSyntaxNode node = iterator.next();

			while (tree.root() != null
					&& tree.findNodeOfId(node.id()) == null)
			{
				tree.setRoot(tree.root().predecessor());
			}
		}

		if (tree.root() == null) throw new IllegalStateException("Nodes " + abstractNodes + " have no common ancestors!");

		return tree.root();
	}

	public static AbstractSyntaxNode getLeastCommonAncestorFromId(final AbstractSyntaxNode node1,
																  final AbstractSyntaxNode node2)
	{
		final AbstractSyntaxTree tree = new AbstractSyntaxTree();
		tree.setRoot(node1);

		////System.out.println(node1.label());
		////System.out.println(node2.label());
		////System.out.println(tree.toString());

		long i = 0;

		while (tree.root() != null
				&& tree.findNodeOfId(node2.id()) == null)
		{
			final IntegrityStatus integrityStatus = ASTIntegrityVerifier.verifyIntegrity(tree, null);

			if (integrityStatus != IntegrityStatus.VALID)
			{
				//throw new IllegalStateException("INTEGRITY CHECK FAILED: " + integrityStatus.meaning());
			}

			tree.setRoot(tree.root().predecessor());
		}

		if (tree.root() == null) throw new IllegalStateException();

		return tree.root();
	}

	public static AbstractSyntaxNode getLeastCommonAncestorFromId(final Collection<AbstractSyntaxNode> nodes1,
																  final AbstractSyntaxNode node2)
	{
		final Collection<AbstractSyntaxNode> nodes = new ArrayList<>(nodes1);
		nodes.add(node2);

		return ASTUtils.getLeastCommonAncestor(nodes);
	}

	public static MergeStatus getMergeStatus(final AbstractSyntaxTree fullTree,
											 final AbstractSyntaxTree sizeTwoTree)
	{
		if (sizeTwoTree.root().successors().size() < 2) throw new IllegalStateException("Current size two tree is:\n\n" + sizeTwoTree.toString());

		final AbstractType type = sizeTwoTree.root().type();
		final String leftNodeLabel = sizeTwoTree.root().successors().get(0).label();
		final String rightNodeLabel = sizeTwoTree.root().successors().get(1).label();

		final AbstractSyntaxNode leftNode = fullTree.findNodeOfLabel(leftNodeLabel);
		final AbstractSyntaxNode rightNode = fullTree.findNodeOfLabel(rightNodeLabel);
		////System.out.println(fullTree);

		if (leftNode == null
			&& rightNode == null)
		{
			return MergeStatus.POSSIBLE_WITHOUT_CORRESPONDENCES;
		}

		if (leftNode == null
			|| rightNode == null)
		{
			return MergeStatus.POSSIBLE;
		}

		////System.out.println("Left node has predecessor: " + (leftNode.predecessor() != null));
		////System.out.println("Right node has predecessor: " + (rightNode.predecessor() != null));

		////System.out.println("hello");

		final IntegrityStatus status = ASTIntegrityVerifier.verifyIntegrity(fullTree, null);

		if (status != IntegrityStatus.VALID)
		{
			//throw new IllegalStateException("INTEGRITY CHECK FAILED: " + status.meaning() + " in:\n\n" + fullTree.toString());
		}

		////System.out.println("Looking for nodes |" + leftNode.id() + "| and |" + rightNode.id() + "| in tree:\n\n" + fullTree);

		final AbstractSyntaxNode commonAncestor = ASTUtils.getLeastCommonAncestor(leftNode, rightNode);

		////System.out.println("salut");

		if (commonAncestor.type() == type)
		{
			if (type == AbstractType.SEQ)
			{
				//Nodes can be in reverse order
				int leftNodeIndex = -1;
				int rightNodeIndex = -1;

				for (int i = 0; i < commonAncestor.successors().size(); i++)
				{
					final AbstractSyntaxNode commonAncestorChild = commonAncestor.successors().get(i);

					final AbstractSyntaxTree tempTree = new AbstractSyntaxTree();
					tempTree.setRoot(commonAncestorChild);

					if (tempTree.findNodeOfLabel(leftNodeLabel) != null)
					{
						leftNodeIndex = i;
					}
					else if (tempTree.findNodeOfLabel(rightNodeLabel) != null)
					{
						rightNodeIndex = i;
					}
				}

				if (leftNodeIndex == -1
					|| rightNodeIndex == -1) throw new IllegalStateException();

				if (leftNodeIndex > rightNodeIndex)
				{
					return MergeStatus.NOT_POSSIBLE;
				}
				else
				{
					return MergeStatus.ALREADY_SATISFIED;
				}
			}
			else
			{
				return MergeStatus.ALREADY_SATISFIED;
			}
		}
		else
		{
			if (ASTUtils.typesAreCompatible(commonAncestor.type(), type))
			{
				return MergeStatus.POSSIBLE;
			}
			else
			{
				return MergeStatus.NOT_POSSIBLE;
			}
		}
	}

	public static boolean listContainsNode(final Collection<AbstractSyntaxNode> list,
										   final AbstractSyntaxNode node)
	{
		for (AbstractSyntaxNode nodeInList : list)
		{
			if (nodeInList.label().equals(node.label())) return true;
		}

		return false;
	}

	public static ArrayList<AbstractSyntaxTree> getProblematicTrees(final AbstractSyntaxTree tree,
																	final Collection<AbstractSyntaxTree> constraints)
	{
		final ArrayList<AbstractSyntaxTree> problematicTrees = new ArrayList<>();

		int i = 0;

		for (AbstractSyntaxTree treeToMerge : constraints)
		{
			if (treeToMerge.root().type() == AbstractType.SEQ
				|| treeToMerge.root().type() == AbstractType.XOR)
			{
				if (ASTUtils.getMergeStatus(tree, treeToMerge) == MergeStatus.NOT_POSSIBLE)
				{
					problematicTrees.add(treeToMerge);
				}
			}

			////System.out.println("Current iteration: " + ++i + "/" + constraints.size());
		}

		return problematicTrees;
	}

	/*public static AbstractSyntaxNode findClosestOperator(final AbstractSyntaxNode currentNode,
														 final AbstractType operator)
	{
		if (currentNode == null) return null;

		if (currentNode.type() == operator)
		{
			return currentNode;
		}

		return ASTUtils.findClosestOperator(currentNode.predecessor(), operator);
	}*/

	//Private methods

	private static boolean typesAreCompatible(final AbstractType type1,
											  final AbstractType type2)
	{
		return (type1 != AbstractType.SEQ || type2 != AbstractType.XOR)
				&& (type1 != AbstractType.XOR || type2 != AbstractType.SEQ);
	}
}
