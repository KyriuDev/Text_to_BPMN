package chat_gpt.ast_management;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Task;
import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.exceptions.BadAnswerException;
import exceptions.ExpectedException;
import other.Utils;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class ASTConstraintsExpander
{
	private ASTConstraintsExpander()
	{

	}

	public static ArrayList<AbstractSyntaxTree> expand(final Collection<DependencyGraph> dependencyGraphs,
													   final Collection<AbstractSyntaxTree> sizeTwoTrees)
	{
		final ArrayList<AbstractSyntaxTree> expandedTrees = new ArrayList<>();

		for (DependencyGraph dependencyGraph : dependencyGraphs)
		{
			//System.out.println("Dependency graph:\n\n" + dependencyGraph.stringify(0));
			//System.out.println("before");
			final HashMap<String, HashSet<String>> expandedDependencies = dependencyGraph.toExpandedDependencySet();
			//System.out.println("after (" + expandedDependencies.keySet().size() + ")");

			for (String key : expandedDependencies.keySet())
			{
				final HashSet<String> values = expandedDependencies.get(key);

				//System.out.println("Found " + values.size() + " dependent nodes");

				for (String value : values)
				{
					final AbstractSyntaxTree tree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));
					final AbstractSyntaxNode leftNode = new AbstractSyntaxNode(AbstractType.TASK);
					leftNode.setLabel(key);
					final AbstractSyntaxNode rightNode = new AbstractSyntaxNode(AbstractType.TASK);
					rightNode.setLabel(value);
					tree.root().addSuccessor(leftNode);
					leftNode.setPredecessor(tree.root());
					tree.root().addSuccessor(rightNode);
					rightNode.setPredecessor(tree.root());
					expandedTrees.add(tree);
				}
			}
		}

		for (AbstractSyntaxTree tree : sizeTwoTrees)
		{
			if (tree.root().type() == AbstractType.XOR)
			{
				expandedTrees.add(tree);
			}
		}

		return expandedTrees;
	}

	public static ArrayList<AbstractSyntaxTree> expand(final ArrayList<AbstractSyntaxTree> constraints,
													   final ArrayList<AbstractSyntaxTree> originalTrees) throws ExpectedException
	{
		final HashSet<Dependency> originalDependencies = ASTConstraintsExpander.transformToDependencies(constraints);
		final ArrayList<HashSet<Dependency>> splitDependencies = Utils.splitDependencies(originalDependencies);
		final ArrayList<AbstractSyntaxTree> expandedTrees = new ArrayList<>();

		for (HashSet<Dependency> splitDependency : splitDependencies)
		{
			final DependencyGraph dependencyGraph = Utils.buildDependencyGraph(splitDependency);
			//System.out.println("Dependency graph:\n\n" + dependencyGraph.stringify(0));
			//System.out.println("before");
			final HashMap<String, HashSet<String>> expandedDependencies = dependencyGraph.toExpandedDependencySet();
			//System.out.println("after (" + expandedDependencies.keySet().size() + ")");

			for (String key : expandedDependencies.keySet())
			{
				final HashSet<String> values = expandedDependencies.get(key);

				//System.out.println("Found " + values.size() + " dependent nodes");

				for (String value : values)
				{
					final AbstractSyntaxTree tree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));
					final AbstractSyntaxNode leftNode = new AbstractSyntaxNode(AbstractType.TASK);
					leftNode.setLabel(key);
					final AbstractSyntaxNode rightNode = new AbstractSyntaxNode(AbstractType.TASK);
					rightNode.setLabel(value);
					tree.root().addSuccessor(leftNode);
					leftNode.setPredecessor(tree.root());
					tree.root().addSuccessor(rightNode);
					rightNode.setPredecessor(tree.root());
					expandedTrees.add(tree);
				}
			}
		}

		for (AbstractSyntaxTree tree : originalTrees)
		{
			if (tree.root().type() == AbstractType.XOR)
			{
				expandedTrees.add(tree);
			}
		}

		for (AbstractSyntaxTree tree : constraints)
		{
			if (tree.root().type() == AbstractType.PAR)
			{
				expandedTrees.add(tree);
			}
		}

		return expandedTrees;
	}

	//Private methods

	private static HashSet<Dependency> transformToDependencies(final ArrayList<AbstractSyntaxTree> trees)
	{
		final HashSet<Dependency> dependencies = new HashSet<>();
		final HashMap<String, Node> correspondences = new HashMap<>();

		for (AbstractSyntaxTree tree : trees)
		{
			if (tree.root().type() == AbstractType.SEQ)
			{
				final String leftNodeLabel = tree.root().successors().get(0).label();
				final String rightNodeLabel = tree.root().successors().get(1).label();

				final Node leftNode = correspondences.computeIfAbsent(leftNodeLabel, n -> new Node(new Task(leftNodeLabel, BpmnProcessType.TASK, -1)));
				leftNode.bpmnObject().setName(leftNodeLabel);

				final Node rightNode = correspondences.computeIfAbsent(rightNodeLabel, n -> new Node(new Task(rightNodeLabel, BpmnProcessType.TASK, -1)));
				rightNode.bpmnObject().setName(rightNodeLabel);

				dependencies.add(new Dependency(leftNode, rightNode));
			}
		}

		return dependencies;
	}
}
