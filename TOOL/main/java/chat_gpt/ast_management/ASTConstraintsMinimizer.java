package chat_gpt.ast_management;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Task;
import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.exceptions.BadAnswerException;
import exceptions.ExpectedException;
import other.Pair;
import other.Utils;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class ASTConstraintsMinimizer
{
	private static final boolean USEFUL_TO_SPLIT = false;

	private ASTConstraintsMinimizer()
	{

	}

	public static ArrayList<ArrayList<AbstractSyntaxTree>> minimize(final ArrayList<AbstractSyntaxTree> originalConstraints) throws ExpectedException
	{
		if (USEFUL_TO_SPLIT)
		{
			return ASTConstraintsMinimizer.minimizeWithSplit(originalConstraints);
		}
		else
		{
			return ASTConstraintsMinimizer.minimizeWithoutSplit(originalConstraints);
		}
	}

	public static HashSet<DependencyGraph> computeMinimalDependencyGraph(final ArrayList<AbstractSyntaxTree> originalConstraints) throws ExpectedException
	{
		final HashSet<Dependency> originalDependencies = ASTConstraintsMinimizer.buildInitialDependencies(originalConstraints);
		final ArrayList<HashSet<Dependency>> splitDependencies = Utils.splitDependencies(originalDependencies);
		final HashSet<DependencyGraph> dependencyGraphs = new HashSet<>();
		final HashSet<AbstractSyntaxTree> newConstraints = new HashSet<>();

		for (HashSet<Dependency> splitDependenciesSet : splitDependencies)
		{
			final Pair<HashSet<AbstractSyntaxTree>, DependencyGraph> graphAndDependencies = Utils.buildDependencyGraph(splitDependenciesSet, true);
			newConstraints.addAll(graphAndDependencies.first());
			dependencyGraphs.add(graphAndDependencies.second());
		}

		originalConstraints.addAll(newConstraints);

		return dependencyGraphs;
	}

	//Private methods

	private static ArrayList<ArrayList<AbstractSyntaxTree>> minimizeWithSplit(final ArrayList<AbstractSyntaxTree> originalConstraints) throws ExpectedException
	{
		final HashSet<Dependency> originalDependencies = ASTConstraintsMinimizer.buildInitialDependencies(originalConstraints);
		final ArrayList<HashSet<Dependency>> splitDependencies = Utils.splitDependencies(originalDependencies);
		final ArrayList<ArrayList<AbstractSyntaxTree>> isolatedTreesList = new ArrayList<>();

		for (HashSet<Dependency> splitDependenciesSet : splitDependencies)
		{
			final DependencyGraph originalGraph = Utils.buildDependencyGraph(splitDependenciesSet);
			originalGraph.reduce();
			final ArrayList<AbstractSyntaxTree> isolatedTrees = new ArrayList<>();
			isolatedTreesList.add(isolatedTrees);

			for (Dependency dependency : originalGraph.toDependencySet())
			{
				final AbstractSyntaxTree isolatedTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));

				final AbstractSyntaxNode leftNode = new AbstractSyntaxNode(AbstractType.TASK);
				leftNode.setLabel(dependency.firstNode().bpmnObject().name());

				final AbstractSyntaxNode rightNode = new AbstractSyntaxNode(AbstractType.TASK);
				rightNode.setLabel(dependency.secondNode().bpmnObject().name());

				isolatedTree.root().addSuccessor(leftNode);
				leftNode.setPredecessor(isolatedTree.root());
				isolatedTree.root().addSuccessor(rightNode);
				rightNode.setPredecessor(isolatedTree.root());

				isolatedTrees.add(isolatedTree);
			}
		}

		return isolatedTreesList;
	}

	private static ArrayList<ArrayList<AbstractSyntaxTree>> minimizeWithoutSplit(final ArrayList<AbstractSyntaxTree> originalConstraints) throws ExpectedException
	{
		final HashSet<Dependency> originalDependencies = ASTConstraintsMinimizer.buildInitialDependencies(originalConstraints);
		final ArrayList<HashSet<Dependency>> splitDependencies = Utils.splitDependencies(originalDependencies);
		final ArrayList<ArrayList<AbstractSyntaxTree>> isolatedTreesList = new ArrayList<>();
		final ArrayList<AbstractSyntaxTree> isolatedTrees = new ArrayList<>();
		isolatedTreesList.add(isolatedTrees);

		for (HashSet<Dependency> splitDependenciesSet : splitDependencies)
		{
			final DependencyGraph originalGraph = Utils.buildDependencyGraph(splitDependenciesSet);
			originalGraph.reduce();

			for (Dependency dependency : originalGraph.toDependencySet())
			{
				final AbstractSyntaxTree isolatedTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));

				final AbstractSyntaxNode leftNode = new AbstractSyntaxNode(AbstractType.TASK);
				leftNode.setLabel(dependency.firstNode().bpmnObject().name());

				final AbstractSyntaxNode rightNode = new AbstractSyntaxNode(AbstractType.TASK);
				rightNode.setLabel(dependency.secondNode().bpmnObject().name());

				isolatedTree.root().addSuccessor(leftNode);
				leftNode.setPredecessor(isolatedTree.root());
				isolatedTree.root().addSuccessor(rightNode);
				rightNode.setPredecessor(isolatedTree.root());

				isolatedTrees.add(isolatedTree);
			}
		}

		for (AbstractSyntaxTree tree : originalConstraints)
		{
			if (tree.root().type() == AbstractType.PAR)
			{
				isolatedTrees.add(tree);
			}
		}

		return isolatedTreesList;
	}

	private static HashSet<Dependency> buildInitialDependencies(final ArrayList<AbstractSyntaxTree> originalConstraints)
	{
		final HashMap<String, Node> correspondences = new HashMap<>();
		final HashSet<Dependency> originalDependencies = new HashSet<>();

		for (AbstractSyntaxTree tree : originalConstraints)
		{
			if (tree.root().type() == AbstractType.SEQ)
			{
				final String leftLabel = tree.root().successors().get(0).label();
				final String rightLabel = tree.root().successors().get(1).label();
				final Node leftNode = correspondences.computeIfAbsent(leftLabel, n -> new Node(new Task(leftLabel, BpmnProcessType.TASK, -1)));
				leftNode.bpmnObject().setName(leftLabel);
				final Node rightNode = correspondences.computeIfAbsent(rightLabel, n -> new Node(new Task(rightLabel, BpmnProcessType.TASK, -1)));
				rightNode.bpmnObject().setName(rightLabel);

				originalDependencies.add(new Dependency(leftNode, rightNode));
			}
		}

		return originalDependencies;
	}

	private static void computeUselessDependencies(final Node currentNode,
												   final HashSet<Node> visitedNodes,
												   final HashSet<Dependency> dependenciesToRemove)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.childNodes().size() <= 1) return;

		for (Node pivotChild : currentNode.childNodes())
		{
			for (Node otherChild : currentNode.childNodes())
			{
				if (!pivotChild.equals(otherChild))
				{
					if (otherChild.hasSuccessor(pivotChild))
					{
						dependenciesToRemove.add(new Dependency(currentNode, pivotChild));
					}
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			ASTConstraintsMinimizer.computeUselessDependencies(child, visitedNodes, dependenciesToRemove);
		}
	}
}
