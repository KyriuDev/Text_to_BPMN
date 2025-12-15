package chat_gpt.no_ast;

import bpmn.graph.Node;
import chat_gpt.ast_management.AbstractSyntaxTree;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class StructuredInformation
{
	private final DependencyGraph mainGraph;
	private final ArrayList<HashSet<Node>> explicitLoops;
	private final HashSet<AbstractSyntaxTree> explicitChoices;
	private final HashSet<AbstractSyntaxTree> explicitParallels;

	public StructuredInformation(final DependencyGraph dependencyGraph)
	{
		this.mainGraph = dependencyGraph;
		this.explicitLoops = new ArrayList<>();
		this.explicitChoices = new HashSet<>();
		this.explicitParallels = new HashSet<>();
	}

	public DependencyGraph getMainGraph()
	{
		return mainGraph;
	}

	public void addExplicitLoop(final HashSet<Node> explicitLoop)
	{
		this.explicitLoops.add(explicitLoop);
	}

	public void addExplicitLoops(final Collection<HashSet<Node>> explicitLoops)
	{
		this.explicitLoops.addAll(explicitLoops);
	}

	public ArrayList<HashSet<Node>> getExplicitLoops()
	{
		return explicitLoops;
	}

	public void addExplicitChoice(final AbstractSyntaxTree explicitChoice)
	{
		this.explicitChoices.add(explicitChoice);
	}

	public void addExplicitChoices(final Collection<AbstractSyntaxTree> explicitChoices)
	{
		this.explicitChoices.addAll(explicitChoices);
	}

	public HashSet<AbstractSyntaxTree> getExplicitChoices()
	{
		return explicitChoices;
	}

	public void addExplicitParallel(final AbstractSyntaxTree explicitParallel)
	{
		this.explicitParallels.add(explicitParallel);
	}

	public void addExplicitParallels(final Collection<AbstractSyntaxTree> explicitParallels)
	{
		this.explicitParallels.addAll(explicitParallels);
	}

	public HashSet<AbstractSyntaxTree> getExplicitParallels()
	{
		return explicitParallels;
	}
}
