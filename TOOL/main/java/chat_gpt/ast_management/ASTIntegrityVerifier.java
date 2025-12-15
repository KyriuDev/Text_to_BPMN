package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.constants.IntegrityStatus;

import java.util.ArrayList;
import java.util.HashSet;

public class ASTIntegrityVerifier
{
	private ASTIntegrityVerifier()
	{

	}

	public static IntegrityStatus verifyIntegrity(final AbstractSyntaxTree tree,
												  final ArrayList<ASTLoop> loops)
	{
		//System.out.println(tree.toString());
		if (!ASTIntegrityVerifier.verifyOperators(tree.root())) return IntegrityStatus.INVALID_OPERATORS;

		if (!ASTIntegrityVerifier.verifyLoops(tree, loops)) return IntegrityStatus.MODIFIED_LOOPS;

		final HashSet<AbstractSyntaxNode> topBottomNodes = new HashSet<>();
		final IntegrityStatus topToBottomNodesStatus = ASTIntegrityVerifier.getTopToBottomNodes(tree.root(), topBottomNodes);

		if (topToBottomNodesStatus != IntegrityStatus.VALID) return topToBottomNodesStatus;

		final HashSet<AbstractSyntaxNode> leafs = ASTIntegrityVerifier.getLeafs(topBottomNodes);
		final HashSet<AbstractSyntaxNode> bottomTopNodes = new HashSet<>();
		//System.out.println("Found " + leafs.size() + " leafs in tree:\n\n" + tree.toString());

		for (AbstractSyntaxNode leaf : leafs)
		{
			if (!ASTIntegrityVerifier.getBottomToTopNodes(leaf, bottomTopNodes, tree.root())) return IntegrityStatus.NODE_AND_PREDECESSOR_EQUALITY;
		}

		if (topBottomNodes.equals(bottomTopNodes))
		{
			return IntegrityStatus.VALID;
		}
		else
		{
			return IntegrityStatus.FULL_NODES_INEQUALITY;
		}
	}

	//Private methods

	private static boolean verifyOperators(final AbstractSyntaxNode node)
	{
		if (node.type() == AbstractType.LOOP
			|| node.type() == AbstractType.LOOP_MANDATORY
			|| node.type() == AbstractType.LOOP_OPTIONAL)
		{
			if (node.type() != AbstractType.LOOP_OPTIONAL)
			{
				if (node.successors().isEmpty()) return false;
			}
		}
		else if (node.type() == AbstractType.TASK)
		{
			if (!node.successors().isEmpty()) return false;
		}
		else
		{
			if (node.successors().size() < 2) return false;
		}

		for (AbstractSyntaxNode child : node.successors())
		{
			final boolean typeVerified = ASTIntegrityVerifier.verifyOperators(child);

			if (!typeVerified)
			{
				return false;
			}
		}

		return true;
	}

	private static IntegrityStatus getTopToBottomNodes(final AbstractSyntaxNode node,
													   final HashSet<AbstractSyntaxNode> nodes)
	{
		if (nodes.contains(node)) return IntegrityStatus.DUPLICATED_NODES;

		nodes.add(node);

		for (AbstractSyntaxNode successor : node.successors())
		{
			if (node.equals(successor)) return IntegrityStatus.NODE_AND_SUCCESSOR_EQUALITY;

			ASTIntegrityVerifier.getTopToBottomNodes(successor, nodes);
		}

		return IntegrityStatus.VALID;
	}

	private static boolean getBottomToTopNodes(final AbstractSyntaxNode node,
											   final HashSet<AbstractSyntaxNode> nodes,
											   final AbstractSyntaxNode bound)
	{
		if (node == null) return true;

		nodes.add(node);

		if (node.equals(node.predecessor())) return false;
		if (node.equals(bound)) return true;

		ASTIntegrityVerifier.getBottomToTopNodes(node.predecessor(), nodes, bound);

		return true;
	}

	private static HashSet<AbstractSyntaxNode> getLeafs(final HashSet<AbstractSyntaxNode> nodes)
	{
		final HashSet<AbstractSyntaxNode> leafs = new HashSet<>();

		for (AbstractSyntaxNode node : nodes)
		{
			if (node.successors().isEmpty())
			{
				leafs.add(node);
			}
		}

		return leafs;
	}

	private static boolean verifyLoops(final AbstractSyntaxTree tree,
									   final ArrayList<ASTLoop> loops)
	{
		if (loops == null) return true;

		for (ASTLoop loop : loops)
		{
			final AbstractSyntaxNode correspondingNode = tree.findNodeOfLabel(loop.loop().root().label());

			if (correspondingNode != null)
			{
				final HashSet<AbstractSyntaxNode> reachableTasks = new HashSet<>();
				ASTUtils.retrieveAllTasksFrom(correspondingNode, reachableTasks);
				final HashSet<String> nodeNames = new HashSet<>();

				for (AbstractSyntaxNode node : reachableTasks)
				{
					nodeNames.add(node.label());
				}

				if (!loop.loopNodes().equals(nodeNames)) return false;
			}
		}

		return true;
	}
}
