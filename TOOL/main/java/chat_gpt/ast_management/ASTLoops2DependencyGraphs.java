package chat_gpt.ast_management;

import bpmn.graph.Node;
import chat_gpt.ast_management.constants.AbstractType;
import other.Utils;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.ArrayList;
import java.util.HashSet;

public class ASTLoops2DependencyGraphs
{
	private ASTLoops2DependencyGraphs()
	{

	}

	public static HashSet<AbstractSyntaxTree> convert(final ArrayList<AbstractSyntaxTree> trees)
	{
		final HashSet<AbstractSyntaxTree> dependencyGraphs = new HashSet<>();

		for (AbstractSyntaxTree tree : trees)
		{
			dependencyGraphs.addAll(ASTLoops2DependencyGraphs.convert(tree));
		}

		return dependencyGraphs;
	}

	//Private methods

	private static HashSet<AbstractSyntaxTree> convert(final AbstractSyntaxTree tree)
	{
		final HashSet<AbstractSyntaxTree> dependencyGraphs = new HashSet<>();
		ASTLoops2DependencyGraphs.convert(tree.root(), dependencyGraphs);
		return dependencyGraphs;
	}

	private static  void convert(final AbstractSyntaxNode currentNode,
								 final HashSet<AbstractSyntaxTree> newTrees)
	{
		if (currentNode.type() == AbstractType.LOOP)
		{
			if (currentNode.successors().isEmpty()) throw new IllegalStateException();

			if (currentNode.successors().size() == 1)
			{
				final AbstractSyntaxNode successor = currentNode.successors().iterator().next();

				if (successor.type() == AbstractType.SEQ)
				{
					/*
						Simple case: put all children of the last successor before the
						children of the first successor
					 */
					final AbstractSyntaxNode firstChild = successor.successors().get(0);
					final AbstractSyntaxNode lastChild = successor.successors().get(successor.successors().size() - 1);
					final HashSet<AbstractSyntaxNode> firstChildTasks = new HashSet<>();
					final HashSet<AbstractSyntaxNode> lastChildTasks = new HashSet<>();
					ASTUtils.retrieveAllTasksFrom(firstChild, firstChildTasks);
					ASTUtils.retrieveAllTasksFrom(lastChild, lastChildTasks);

					for (AbstractSyntaxNode lastChildTask : lastChildTasks)
					{
						for (AbstractSyntaxNode firstChildTask : firstChildTasks)
						{
							final AbstractSyntaxTree abstractSyntaxTree = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));
							final AbstractSyntaxNode lastChildTaskCopy = lastChildTask.copy();
							final AbstractSyntaxNode firstChildTaskCopy = firstChildTask.copy();
							abstractSyntaxTree.root().addSuccessor(lastChildTaskCopy);
							lastChildTaskCopy.setPredecessor(abstractSyntaxTree.root());
							abstractSyntaxTree.root().addSuccessor(firstChildTaskCopy);
							firstChildTaskCopy.setPredecessor(abstractSyntaxTree.root());

							newTrees.add(abstractSyntaxTree);
						}
					}
				}
				else
				{
					/*
						The components of the loop are not sequentially ordered,
						thus we need to take some precautions.
						What we do is that we create a dummy node that we put
						at the beginning of the loop.
						By doing so, each loop node must happen after and before
						this node, which ensures a single loop with all the desired
						elements.
					 */
					final HashSet<AbstractSyntaxNode> loopTasks = new HashSet<>();
					ASTUtils.retrieveAllTasksFrom(successor, loopTasks);

					final String label = "LOOPY_DUMMY_" + Utils.generateRandomIdentifier(15);
					final AbstractSyntaxNode dummyNode = new AbstractSyntaxNode(AbstractType.TASK, label);
					dummyNode.setLabel(label);

					for (AbstractSyntaxNode loopTask : loopTasks)
					{
						final AbstractSyntaxNode dummyCopy = dummyNode.copy();
						final AbstractSyntaxNode loopTaskCopy = loopTask.copy();

						final AbstractSyntaxTree abstractSyntaxTree1 = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));
						abstractSyntaxTree1.root().addSuccessor(dummyCopy);
						dummyCopy.setPredecessor(abstractSyntaxTree1.root());
						abstractSyntaxTree1.root().addSuccessor(loopTaskCopy);
						loopTaskCopy.setPredecessor(abstractSyntaxTree1.root());

						final AbstractSyntaxTree abstractSyntaxTree2 = new AbstractSyntaxTree(new AbstractSyntaxNode(AbstractType.SEQ));
						abstractSyntaxTree2.root().addSuccessor(loopTaskCopy);
						loopTaskCopy.setPredecessor(abstractSyntaxTree2.root());
						abstractSyntaxTree2.root().addSuccessor(dummyCopy);
						dummyCopy.setPredecessor(abstractSyntaxTree2.root());

						newTrees.add(abstractSyntaxTree1);
						newTrees.add(abstractSyntaxTree2);
					}
				}
			}
			else
			{
				throw new IllegalStateException();
			}
		}
	}
}
