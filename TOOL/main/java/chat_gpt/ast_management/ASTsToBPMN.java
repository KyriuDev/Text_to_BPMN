package chat_gpt.ast_management;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Task;
import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.exceptions.BadAnswerException;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.Pair;
import other.Utils;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.dependencies.DependencyGraph;
import refactoring.legacy.dependencies.DependencyGraphToBPMN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ASTsToBPMN
{
	private ASTsToBPMN()
	{

	}

	public static Graph transform(final ArrayList<AbstractSyntaxTree> constraints) throws ExpectedException
	{
		//Separate sequential and parallel constraints
		final Pair<ArrayList<AbstractSyntaxTree>, ArrayList<AbstractSyntaxTree>> separatedConstraints = ASTsToBPMN.separate(constraints);
		final ArrayList<AbstractSyntaxTree> seqConstraints = separatedConstraints.first();
		final ArrayList<AbstractSyntaxTree> parConstraints = separatedConstraints.second();

		//Generate dependency graphs
		final HashSet<Dependency> dependencies = ASTsToBPMN.buildInitialDependencies(seqConstraints);
		final HashSet<Dependency> immutableDependencies = new HashSet<>(dependencies);
		final ArrayList<HashSet<Dependency>> splitDependenciesList = Utils.splitDependencies(dependencies);
		final ArrayList<DependencyGraph> dependencyGraphs = new ArrayList<>();
		final HashSet<Node> freeTasks = new HashSet<>();

		for (HashSet<Dependency> splitDependencies : splitDependenciesList)
		{
			final DependencyGraph dependencyGraph = Utils.buildDependencyGraph(splitDependencies);
			dependencyGraphs.add(dependencyGraph);
		}

		//Remove all the parallel tasks involved in constraints
		for (final AbstractSyntaxTree currentTree : parConstraints)
		{
			final String leftLabel = currentTree.root().successors().get(0).label();
			final String rightLabel = currentTree.root().successors().get(1).label();
			boolean leftTaskIsConstrained = false;
			boolean rightTaskIsConstrained = false;

			for (Dependency dependency : immutableDependencies)
			{
				final String leftId = dependency.firstNode().bpmnObject().id();
				final String rightId = dependency.secondNode().bpmnObject().id();

				if (leftLabel.equals(leftId)
					|| leftLabel.equals(rightId))
				{
					//The left parallel task is in fact constrained
					leftTaskIsConstrained = true;
				}

				if (rightLabel.equals(leftId)
					|| rightLabel.equals(rightId))
				{
					//The right parallel task is in fact constrained
					rightTaskIsConstrained = true;
				}
			}

			if (!leftTaskIsConstrained)
			{
				final Node leftTask = new Node(new Task(leftLabel, BpmnProcessType.TASK, -1));
				leftTask.bpmnObject().setName(leftLabel);
				freeTasks.add(leftTask);
			}

			if (!rightTaskIsConstrained)
			{
				final Node rightTask = new Node(new Task(rightLabel, BpmnProcessType.TASK, -1));
				rightTask.bpmnObject().setName(rightLabel);
				freeTasks.add(rightTask);
			}
		}

		if (dependencyGraphs.isEmpty()
			&& freeTasks.isEmpty()) throw new ExpectedException(ExceptionStatus.AST_TO_BPMN_FAILED);

		//Build BPMN process
		return DependencyGraphToBPMN.convert(dependencyGraphs, freeTasks);
	}

	//Private methods

	private static Pair<ArrayList<AbstractSyntaxTree>, ArrayList<AbstractSyntaxTree>> separate(final ArrayList<AbstractSyntaxTree> constraints)
	{
		final ArrayList<AbstractSyntaxTree> seqConstraints = new ArrayList<>();
		final ArrayList<AbstractSyntaxTree> parConstraints = new ArrayList<>();

		for (AbstractSyntaxTree tree : constraints)
		{
			if (tree.root().type() == AbstractType.PAR)
			{
				parConstraints.add(tree);
			}
			else if (tree.root().type() == AbstractType.SEQ)
			{
				seqConstraints.add(tree);
			}
			else
			{
				throw new IllegalStateException();
			}
		}

		return new Pair<>(seqConstraints, parConstraints);
	}

	private static HashSet<Dependency> buildInitialDependencies(final ArrayList<AbstractSyntaxTree> originalConstraints)
	{
		final HashMap<String, Node> correspondences = new HashMap<>();
		final HashSet<Dependency> originalDependencies = new HashSet<>();

		for (AbstractSyntaxTree tree : originalConstraints)
		{
			final String leftLabel = tree.root().successors().get(0).label();
			final String rightLabel = tree.root().successors().get(1).label();
			final Node leftNode = correspondences.computeIfAbsent(leftLabel, n -> new Node(new Task(leftLabel, BpmnProcessType.TASK, -1)));
			leftNode.bpmnObject().setName(leftLabel);
			final Node rightNode = correspondences.computeIfAbsent(rightLabel, n -> new Node(new Task(rightLabel, BpmnProcessType.TASK, -1)));
			rightNode.bpmnObject().setName(rightLabel);

			originalDependencies.add(new Dependency(leftNode, rightNode));
		}

		return originalDependencies;
	}
}
