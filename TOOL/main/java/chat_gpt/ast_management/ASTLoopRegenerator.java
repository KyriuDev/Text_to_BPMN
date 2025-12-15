package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.MyOwnLogger;
import other.Pair;
import other.Utils;

import java.util.*;

public class ASTLoopRegenerator
{
	private static final boolean SPLIT_LOOPS = false;
	private static final boolean MERGE_MANDATORY_AND_OPTIONAL_LOOPS = true;

	private ASTLoopRegenerator()
	{

	}

	public static void reintegrateLoops(final AbstractSyntaxTree mainTree,
										final ArrayList<Pair<HashSet<String>, HashSet<String>>> loops,
										final Collection<AbstractSyntaxTree> constraints) throws ExpectedException
	{
		MyOwnLogger.append("Tree before loop regeneration:\n\n" + mainTree.toString());

		for (Pair<HashSet<String>, HashSet<String>> loop : loops)
		{
			ASTLoopRegenerator.reintegrateLoopsV1(loop, mainTree, constraints);
		}

		MyOwnLogger.append("Tree after loop regeneration:\n\n" + mainTree.toString());
	}

	//Private methods

	private static void reintegrateLoopsV1(final Pair<HashSet<String>, HashSet<String>> loop,
										   final AbstractSyntaxTree mainTree,
										   final Collection<AbstractSyntaxTree> constraints) throws ExpectedException
	{
		final Pair<HashSet<AbstractSyntaxNode>, HashSet<AbstractSyntaxNode>> loopNodes = new Pair<>(new HashSet<>(), new HashSet<>());

		for (String label : loop.first())
		{
			final AbstractSyntaxNode correspondingNode = mainTree.findNodeOfLabel(label);

			if (correspondingNode == null) throw new IllegalStateException();

			loopNodes.first().add(correspondingNode);
		}

		for (String label : loop.second())
		{
			final AbstractSyntaxNode correspondingNode = mainTree.findNodeOfLabel(label);

			if (correspondingNode == null) throw new IllegalStateException();

			loopNodes.second().add(correspondingNode);
		}

		if (loopNodes.first().size() + loopNodes.second().size() == 1)
		{
			//One-task loop
			final AbstractSyntaxNode singleTask = loopNodes.first().isEmpty() ? loopNodes.second().iterator().next() : loopNodes.first().iterator().next();
			final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
			final AbstractSyntaxNode loopChildNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
			loopNode.addSuccessor(loopChildNode);
			loopChildNode.setPredecessor(loopNode);

			if (singleTask.predecessor() == null)
			{
				mainTree.setRoot(loopNode);
			}
			else
			{
				final AbstractSyntaxNode singleTaskParent = singleTask.predecessor();
				singleTaskParent.replaceSuccessor(singleTask, loopNode);
				loopNode.setPredecessor(singleTaskParent);
			}

			loopChildNode.addSuccessor(singleTask);
			singleTask.setPredecessor(loopChildNode);
		}
		else
		{
			//Get the least common ancestor of all the loop nodes
			final AbstractSyntaxNode leastCommonAncestor = ASTLoopRegenerator.getLeastCommonAncestor(loopNodes);
			//System.out.println("Least common ancestor of " + (loopNodes.first().size() + loopNodes.second().size()) + " loop nodes: " + leastCommonAncestor.id());

			//Get all the tasks belonging to the subtree starting with the common ancestor
			final HashSet<AbstractSyntaxNode> reachableTasks = new HashSet<>();
			ASTUtils.retrieveAllTasksFrom(leastCommonAncestor, reachableTasks);

			//System.out.println("Reachable tasks: " + reachableTasks.toString());
			//System.out.println("Loop nodes: " + loopNodes.toString());
			final HashSet<AbstractSyntaxNode> allLoopNodes = new HashSet<>(loopNodes.first());
			allLoopNodes.addAll(loopNodes.second());

			//Verify whether the common ancestor reaches only the loop nodes or not
			if (reachableTasks.equals(allLoopNodes))
			{
				//System.out.println("EQUALITY!");
				//If yes, loop can encompass all the least common ancestor reachable nodes
				if (loopNodes.first().isEmpty()
					|| loopNodes.second().isEmpty())
				{
					//All loop nodes are either mandatory or optional
					final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
					final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
					loopNode.addSuccessor(loopSideNode);
					loopSideNode.setPredecessor(loopNode);

					if (leastCommonAncestor.predecessor() == null)
					{
						mainTree.setRoot(loopNode);
					}
					else
					{
						final AbstractSyntaxNode leastCommonAncestorParent = leastCommonAncestor.predecessor();
						leastCommonAncestorParent.replaceSuccessor(leastCommonAncestor, loopNode);
						loopNode.setPredecessor(leastCommonAncestorParent);
					}

					loopSideNode.addSuccessor(leastCommonAncestor);
					leastCommonAncestor.setPredecessor(loopSideNode);
				}
				else
				{
					//Some loop nodes are mandatory and other are optional => check whether they can be properly separated or not
					boolean splitFailed = false;

					final ArrayList<AbstractSyntaxNode> mandatoryChildren = new ArrayList<>();
					final ArrayList<AbstractSyntaxNode> optionalChildren = new ArrayList<>();

					for (AbstractSyntaxNode commonAncestorChild : leastCommonAncestor.successors())
					{
						if (commonAncestorChild.type() == AbstractType.TASK)
						{
							if (loopNodes.first().contains(commonAncestorChild))
							{
								mandatoryChildren.add(commonAncestorChild);
							}
							if (loopNodes.second().contains(commonAncestorChild))
							{
								optionalChildren.add(commonAncestorChild);
							}
						}
						else
						{
							for (AbstractSyntaxNode mandatoryChild : loopNodes.first())
							{
								if (commonAncestorChild.hasDescendant(mandatoryChild))
								{
									mandatoryChildren.add(commonAncestorChild);
									break;
								}
							}

							for (AbstractSyntaxNode optionalChild : loopNodes.second())
							{
								if (commonAncestorChild.hasDescendant(optionalChild))
								{
									optionalChildren.add(commonAncestorChild);
									break;
								}
							}
						}
					}

					if (Utils.intersectionIsNotEmpty(mandatoryChildren, optionalChildren))
					{
						splitFailed = true;
					}
					else if (leastCommonAncestor.type() == AbstractType.SEQ)
					{
						//We need to verify whether the nodes are correctly ordered or not
						final AbstractSyntaxTree treeCopy = mainTree.copy();
						final AbstractSyntaxNode commonAncestorCopy = treeCopy.findNodeOfId(leastCommonAncestor.id());
						commonAncestorCopy.removeSuccessors();

						for (AbstractSyntaxNode child : mandatoryChildren)
						{
							final AbstractSyntaxNode childCopy = child.deepCopy();
							commonAncestorCopy.addSuccessor(childCopy);
							childCopy.setPredecessor(commonAncestorCopy);
						}

						for (AbstractSyntaxNode child : optionalChildren)
						{
							final AbstractSyntaxNode childCopy = child.deepCopy();
							commonAncestorCopy.addSuccessor(childCopy);
							childCopy.setPredecessor(commonAncestorCopy);
						}

						if (ASTUtils.getProblematicTrees(treeCopy, constraints).isEmpty())
						{
							leastCommonAncestor.removeSuccessors();

							for (AbstractSyntaxNode child : mandatoryChildren)
							{
								commonAncestorCopy.addSuccessor(child);
								child.setPredecessor(commonAncestorCopy);
							}

							for (AbstractSyntaxNode child : optionalChildren)
							{
								commonAncestorCopy.addSuccessor(child);
								child.setPredecessor(commonAncestorCopy);
							}
						}
						else
						{
							splitFailed = true;
						}
					}

					if (splitFailed)
					{
						if (MERGE_MANDATORY_AND_OPTIONAL_LOOPS)
						{
							//We cannot split the loop properly => put everything in the mandatory part
							final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
							final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
							loopNode.addSuccessor(loopSideNode);
							loopSideNode.setPredecessor(loopNode);

							if (leastCommonAncestor.predecessor() == null)
							{
								mainTree.setRoot(loopNode);
							}
							else
							{
								final AbstractSyntaxNode leastCommonAncestorParent = leastCommonAncestor.predecessor();
								leastCommonAncestorParent.replaceSuccessor(leastCommonAncestor, loopNode);
								loopNode.setPredecessor(leastCommonAncestorParent);
							}

							loopSideNode.addSuccessor(leastCommonAncestor);
							leastCommonAncestor.setPredecessor(loopSideNode);
						}
						else
						{
							throw new ExpectedException("Could not reintegrate loop values", ExceptionStatus.CONTRADICTORY_VALUES);
						}
					}
					else
					{
						//We can split the loop properly
						final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
						final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
						final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
						loopNode.addSuccessor(loopMandatoryNode);
						loopMandatoryNode.setPredecessor(loopNode);
						loopNode.addSuccessor(loopOptionalNode);
						loopOptionalNode.setPredecessor(loopNode);

						if (leastCommonAncestor.predecessor() == null)
						{
							mainTree.setRoot(loopNode);
						}
						else
						{
							final AbstractSyntaxNode leastCommonAncestorParent = leastCommonAncestor.predecessor();
							leastCommonAncestorParent.replaceSuccessor(leastCommonAncestor, loopNode);
							loopNode.setPredecessor(leastCommonAncestorParent);
						}

						if (leastCommonAncestor.type() == AbstractType.SEQ)
						{
							//We have possibly reordered the nodes => rebuild the sets
							mandatoryChildren.clear();
							optionalChildren.clear();

							for (AbstractSyntaxNode commonAncestorChild : leastCommonAncestor.successors())
							{
								if (commonAncestorChild.type() == AbstractType.TASK)
								{
									if (loopNodes.first().contains(commonAncestorChild))
									{
										mandatoryChildren.add(commonAncestorChild);
									}
									else if (loopNodes.second().contains(commonAncestorChild))
									{
										optionalChildren.add(commonAncestorChild);
									}
									else
									{
										throw new IllegalStateException();
									}
								}
								else
								{
									for (AbstractSyntaxNode mandatoryChild : loopNodes.first())
									{
										if (commonAncestorChild.hasDescendant(mandatoryChild))
										{
											mandatoryChildren.add(commonAncestorChild);
											break;
										}
									}

									for (AbstractSyntaxNode optionalChild : loopNodes.second())
									{
										if (commonAncestorChild.hasDescendant(optionalChild))
										{
											optionalChildren.add(commonAncestorChild);
											break;
										}
									}
								}
							}
						}

						final AbstractSyntaxNode mandatoryNodeToConnect;
						final AbstractSyntaxNode optionalNodeToConnect;

						if (mandatoryChildren.size() == 1)
						{
							if (leastCommonAncestor.type() != AbstractType.PAR) throw new IllegalStateException();

							mandatoryNodeToConnect = loopMandatoryNode;
						}
						else
						{
							final AbstractSyntaxNode leastCommonAncestorMandatoryNode = new AbstractSyntaxNode(leastCommonAncestor.type());
							loopMandatoryNode.addSuccessor(leastCommonAncestorMandatoryNode);
							leastCommonAncestorMandatoryNode.setPredecessor(loopMandatoryNode);
							mandatoryNodeToConnect = leastCommonAncestorMandatoryNode;
						}

						if (optionalChildren.size() == 1)
						{
							if (leastCommonAncestor.type() != AbstractType.PAR) throw new IllegalStateException();

							optionalNodeToConnect = loopOptionalNode;
						}
						else
						{
							final AbstractSyntaxNode leastCommonAncestorOptionalNode = new AbstractSyntaxNode(leastCommonAncestor.type());
							loopOptionalNode.addSuccessor(leastCommonAncestorOptionalNode);
							leastCommonAncestorOptionalNode.setPredecessor(loopOptionalNode);
							optionalNodeToConnect = leastCommonAncestorOptionalNode;
						}

						for (AbstractSyntaxNode mandatoryChild : mandatoryChildren)
						{
							mandatoryNodeToConnect.addSuccessor(mandatoryChild);
							mandatoryChild.setPredecessor(mandatoryNodeToConnect);
						}

						for (AbstractSyntaxNode optionalChild : optionalChildren)
						{
							optionalNodeToConnect.addSuccessor(optionalChild);
							optionalChild.setPredecessor(optionalNodeToConnect);
						}
					}
				}
			}
			else
			{
				//No, common ancestor encompasses nodes that should not belong to a loop.
				//Sometimes, the common ancestor can be decomposed without losing generality nor adding constraints
				if (ASTLoopRegenerator.verifyCommonAncestorSplittability(leastCommonAncestor, loopNodes, constraints, mainTree))
				{
					//Common ancestor can be decomposed
					ASTLoopRegenerator.decomposeCommonAncestorAndAddLoop(leastCommonAncestor, loopNodes, mainTree, constraints);
				}
				else
				{
					//Common ancestor can not be decomposed
					/*if (SPLIT_LOOPS)
					{
						for (AbstractSyntaxNode child : leastCommonAncestor.successors())
						{
							final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
							ASTUtils.retrieveAllTasksFrom(child, childTasks);

							final HashSet<String> subTasks = ASTLoopRegenerator.extractLoopTasks(childTasks, loop);
							final AbstractSyntaxTree subTree = new AbstractSyntaxTree();
							subTree.setRoot(child);

							ASTLoopRegenerator.reintegrateLoopsV1(subTasks, subTree, constraints);
						}
					}
					else*/
					{
						throw new ExpectedException("Could not reintegrate loop values", ExceptionStatus.CONTRADICTORY_VALUES);
					}
				}
			}
		}
	}

	private static AbstractSyntaxNode getLeastCommonAncestor(final Pair<HashSet<AbstractSyntaxNode>, HashSet<AbstractSyntaxNode>> nodes)
	{
		if (nodes.first().size() + nodes.second().size() < 2) throw new IllegalStateException();
		final HashSet<AbstractSyntaxNode> mergedSet = new HashSet<>(nodes.first());
		mergedSet.addAll(nodes.second());

		AbstractSyntaxNode leastCommonAncestor = null;

		final Iterator<AbstractSyntaxNode> iterator = mergedSet.iterator();
		final AbstractSyntaxNode firstNode = iterator.next();
		iterator.remove();

		while (iterator.hasNext())
		{
			final AbstractSyntaxNode node = iterator.next();

			if (leastCommonAncestor == null)
			{
				leastCommonAncestor = ASTUtils.getLeastCommonAncestor(firstNode, node);
			}
			else
			{
				final AbstractSyntaxNode currentCommonAncestor = ASTUtils.getLeastCommonAncestor(firstNode, node);
				leastCommonAncestor = getEldestBetween(currentCommonAncestor, leastCommonAncestor);
			}
		}

		if (leastCommonAncestor == null) throw new IllegalStateException();

		return leastCommonAncestor;
	}

	private static AbstractSyntaxNode getEldestBetween(final AbstractSyntaxNode node1,
													   final AbstractSyntaxNode node2)
	{
		if (node1.hasAncestor(node2))
		{
			return node2;
		}
		else
		{
			return node1;
		}
	}

	private static boolean verifyCommonAncestorSplittability(final AbstractSyntaxNode commonAncestor,
															 final Pair<HashSet<AbstractSyntaxNode>, HashSet<AbstractSyntaxNode>> loopNodes,
															 final Collection<AbstractSyntaxTree> constraints,
															 final AbstractSyntaxTree mainTree)
	{
		//System.out.println("Common ancestor: " + commonAncestor.id());
		final HashSet<AbstractSyntaxNode> loopChildren = new HashSet<>();

		for (AbstractSyntaxNode child : commonAncestor.successors())
		{
			for (AbstractSyntaxNode loopNode : loopNodes.first())
			{
				if (child.findNodeOfId(loopNode.id()) != null)
				{
					loopChildren.add(child);
					break;
				}
			}

			for (AbstractSyntaxNode loopNode : loopNodes.second())
			{
				if (child.findNodeOfId(loopNode.id()) != null)
				{
					loopChildren.add(child);
					break;
				}
			}
		}

		final HashSet<AbstractSyntaxNode> reachableTasks = new HashSet<>();
		final HashSet<AbstractSyntaxNode> mergedNodes = new HashSet<>(loopNodes.first());
		mergedNodes.addAll(loopNodes.second());

		for (AbstractSyntaxNode loopChild : loopChildren)
		{
			ASTUtils.retrieveAllTasksFrom(loopChild, reachableTasks);
		}

		//System.out.println("Found " + mergedNodes.size() + " merged nodes:");
		for (AbstractSyntaxNode mergedNode : mergedNodes)
		{
			//System.out.println(mergedNode.id());
		}

		//System.out.println("Found " + reachableTasks.size() + " reachable tasks:");
		for (AbstractSyntaxNode reachableTask : reachableTasks)
		{
			//System.out.println(reachableTask.id());
		}

		if (!mergedNodes.equals(reachableTasks))
		{
			//System.out.println("ici");
			//Not all the loop tasks are at the same level, thus they can not be merged
			return false;
		}

		if (commonAncestor.type() == AbstractType.SEQ)
		{
			//If the common ancestor is a sequence, the loop children must be consecutive
			//to prevent any reordering issue.

			final HashSet<Integer> childrenIndices = new HashSet<>();

			for (AbstractSyntaxNode child : loopChildren)
			{
				childrenIndices.add(commonAncestor.successors().indexOf(child));
			}

			if (!Utils.integersAreConsecutive(childrenIndices))
			{
				return ASTLoopRegenerator.switchPositionsAndVerifyConstraints(commonAncestor, constraints, mainTree, loopChildren);
			}
		}

		return true;
	}

	private static boolean switchPositionsAndVerifyConstraints(final AbstractSyntaxNode commonAncestor,
															   final Collection<AbstractSyntaxTree> constraints,
															   final AbstractSyntaxTree mainTree,
															   final Collection<AbstractSyntaxNode> loopChildren)
	{
		final AbstractSyntaxTree mainTreeCopy = mainTree.copy();
		final AbstractSyntaxNode commonAncestorInCopiedTree = mainTreeCopy.findNodeOfId(commonAncestor.id());

		final ArrayList<Integer> childrenIndices = new ArrayList<>();

		for (AbstractSyntaxNode child : loopChildren)
		{
			childrenIndices.add(commonAncestorInCopiedTree.successors().indexOf(child));
		}

		final int smallestIndex = Utils.min(childrenIndices);
		childrenIndices.remove(Integer.valueOf(smallestIndex));
		childrenIndices.sort(Collections.reverseOrder());

		final ArrayList<AbstractSyntaxNode> nodes = new ArrayList<>();

		for (int index : childrenIndices)
		{
			nodes.add(commonAncestorInCopiedTree.successors().remove(index));
		}

		Collections.reverse(nodes);

		final AbstractSyntaxNode sequence = new AbstractSyntaxNode(AbstractType.SEQ);
		sequence.addSuccessor(commonAncestorInCopiedTree.successors().get(smallestIndex));
		commonAncestorInCopiedTree.successors().get(smallestIndex).setPredecessor(sequence);

		for (AbstractSyntaxNode node : nodes)
		{
			sequence.addSuccessor(node);
			node.setPredecessor(sequence);
		}

		commonAncestorInCopiedTree.setSuccessor(smallestIndex, sequence);
		sequence.setPredecessor(commonAncestorInCopiedTree);

		return ASTUtils.getProblematicTrees(mainTreeCopy, constraints).isEmpty();
	}

	private static void decomposeCommonAncestorAndAddLoop(final AbstractSyntaxNode commonAncestor,
														  final Pair<HashSet<AbstractSyntaxNode>, HashSet<AbstractSyntaxNode>> loopNodes,
														  final AbstractSyntaxTree mainTree,
														  final Collection<AbstractSyntaxTree> constraints) throws ExpectedException
	{
		if (loopNodes.first().isEmpty()
			|| loopNodes.second().isEmpty())
		{
			final HashSet<AbstractSyntaxNode> loopChildren = new HashSet<>();

			for (AbstractSyntaxNode child : commonAncestor.successors())
			{
				for (AbstractSyntaxNode loopNode : loopNodes.first().isEmpty() ? loopNodes.second() : loopNodes.first())
				{
					if (child.findNodeOfId(loopNode.id()) != null)
					{
						loopChildren.add(child);
					}
				}
			}

			if (commonAncestor.type() == AbstractType.SEQ)
			{
				//We need to take some precautions
				final ArrayList<Integer> childrenIndices = new ArrayList<>();

				for (AbstractSyntaxNode child : loopChildren)
				{
					childrenIndices.add(commonAncestor.successors().indexOf(child));
				}

				final int smallestIndex = Utils.min(childrenIndices);
				childrenIndices.remove(Integer.valueOf(smallestIndex));
				childrenIndices.sort(Collections.reverseOrder());

				final ArrayList<AbstractSyntaxNode> nodes = new ArrayList<>();

				for (int index : childrenIndices)
				{
					nodes.add(commonAncestor.successors().remove(index));
				}

				Collections.reverse(nodes);

				final AbstractSyntaxNode sequence = new AbstractSyntaxNode(AbstractType.SEQ);
				sequence.addSuccessor(commonAncestor.successors().get(smallestIndex));
				commonAncestor.successors().get(smallestIndex).setPredecessor(sequence);

				for (AbstractSyntaxNode node : nodes)
				{
					sequence.addSuccessor(node);
					node.setPredecessor(sequence);
				}

				final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
				final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
				loopNode.addSuccessor(loopSideNode);
				loopSideNode.setPredecessor(loopNode);
				commonAncestor.setSuccessor(smallestIndex, loopNode);
				loopNode.setPredecessor(commonAncestor);
				loopSideNode.addSuccessor(sequence);
				sequence.setPredecessor(loopSideNode);
			}
			else
			{
				//Order is not meaningful thus we can do it without much care
				commonAncestor.removeSuccessors(loopChildren);

				final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
				final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
				loopNode.addSuccessor(loopSideNode);
				loopSideNode.setPredecessor(loopNode);
				commonAncestor.addSuccessor(loopNode);
				loopNode.setPredecessor(commonAncestor);

				final AbstractSyntaxNode typeNode = new AbstractSyntaxNode(commonAncestor.type());
				loopSideNode.addSuccessor(typeNode);
				typeNode.setPredecessor(loopSideNode);

				for (AbstractSyntaxNode loopChild : loopChildren)
				{
					typeNode.addSuccessor(loopChild);
					loopChild.setPredecessor(typeNode);
				}
			}
		}
		else
		{
			//Some loop nodes are mandatory and other are optional => check whether they can be properly separated or not
			boolean splitFailed = false;

			final ArrayList<AbstractSyntaxNode> mandatoryChildren = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> optionalChildren = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> remainingChildren = new ArrayList<>();

			for (AbstractSyntaxNode commonAncestorChild : commonAncestor.successors())
			{
				if (commonAncestorChild.type() == AbstractType.TASK)
				{
					boolean added = false;

					if (loopNodes.first().contains(commonAncestorChild))
					{
						mandatoryChildren.add(commonAncestorChild);
						added = true;
					}
					if (loopNodes.second().contains(commonAncestorChild))
					{
						optionalChildren.add(commonAncestorChild);
						added = true;
					}

					if (!added)
					{
						remainingChildren.add(commonAncestorChild);
					}
				}
				else
				{
					boolean added = false;

					for (AbstractSyntaxNode mandatoryChild : loopNodes.first())
					{
						if (commonAncestorChild.hasDescendant(mandatoryChild))
						{
							mandatoryChildren.add(commonAncestorChild);
							added = true;
							break;
						}
					}

					for (AbstractSyntaxNode optionalChild : loopNodes.second())
					{
						if (commonAncestorChild.hasDescendant(optionalChild))
						{
							optionalChildren.add(commonAncestorChild);
							added = true;
							break;
						}
					}

					if (!added)
					{
						remainingChildren.add(commonAncestorChild);
					}
				}
			}

			if (Utils.intersectionIsNotEmpty(mandatoryChildren, optionalChildren))
			{
				splitFailed = true;
			}
			else if (commonAncestor.type() == AbstractType.SEQ)
			{
				//We need to verify whether the nodes are correctly ordered or not
				if (!ASTLoopRegenerator.tryLeft(mainTree, commonAncestor, mandatoryChildren, optionalChildren, remainingChildren, constraints)
					//&& !ASTLoopRegenerator.tryMiddle(mainTree, commonAncestor, mandatoryChildren, optionalChildren, remainingChildren, constraints)
					&& !ASTLoopRegenerator.tryRight(mainTree, commonAncestor, mandatoryChildren, optionalChildren, remainingChildren, constraints))
				{
					splitFailed = true;
				}
			}

			if (splitFailed)
			{
				if (MERGE_MANDATORY_AND_OPTIONAL_LOOPS)
				{
					final HashSet<AbstractSyntaxNode> loopChildren = new HashSet<>();

					for (AbstractSyntaxNode child : commonAncestor.successors())
					{
						for (AbstractSyntaxNode loopNode : loopNodes.first().isEmpty() ? loopNodes.second() : loopNodes.first())
						{
							if (child.findNodeOfId(loopNode.id()) != null)
							{
								loopChildren.add(child);
							}
						}
					}

					if (commonAncestor.type() == AbstractType.SEQ)
					{
						//We need to take some precautions
						final ArrayList<Integer> childrenIndices = new ArrayList<>();

						for (AbstractSyntaxNode child : loopChildren)
						{
							childrenIndices.add(commonAncestor.successors().indexOf(child));
						}

						final int smallestIndex = Utils.min(childrenIndices);
						childrenIndices.remove(Integer.valueOf(smallestIndex));
						childrenIndices.sort(Collections.reverseOrder());

						final ArrayList<AbstractSyntaxNode> nodes = new ArrayList<>();

						for (int index : childrenIndices)
						{
							nodes.add(commonAncestor.successors().remove(index));
						}

						Collections.reverse(nodes);

						final AbstractSyntaxNode sequence = new AbstractSyntaxNode(AbstractType.SEQ);
						sequence.addSuccessor(commonAncestor.successors().get(smallestIndex));
						commonAncestor.successors().get(smallestIndex).setPredecessor(sequence);

						for (AbstractSyntaxNode node : nodes)
						{
							sequence.addSuccessor(node);
							node.setPredecessor(sequence);
						}

						final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
						final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
						loopNode.addSuccessor(loopSideNode);
						loopSideNode.setPredecessor(loopNode);
						commonAncestor.setSuccessor(smallestIndex, loopNode);
						loopNode.setPredecessor(commonAncestor);
						loopSideNode.addSuccessor(sequence);
						sequence.setPredecessor(loopSideNode);
					}
					else
					{
						//Order is not meaningful thus we can do it without much care
						commonAncestor.removeSuccessors(loopChildren);

						final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
						final AbstractSyntaxNode loopSideNode = new AbstractSyntaxNode(loopNodes.first().isEmpty() ? AbstractType.LOOP_OPTIONAL : AbstractType.LOOP_MANDATORY);
						loopNode.addSuccessor(loopSideNode);
						loopSideNode.setPredecessor(loopNode);
						commonAncestor.addSuccessor(loopNode);
						loopNode.setPredecessor(commonAncestor);

						final AbstractSyntaxNode typeNode = new AbstractSyntaxNode(commonAncestor.type());
						loopSideNode.addSuccessor(typeNode);
						typeNode.setPredecessor(loopSideNode);

						for (AbstractSyntaxNode loopChild : loopChildren)
						{
							typeNode.addSuccessor(loopChild);
							loopChild.setPredecessor(typeNode);
						}
					}
				}
				else
				{
					throw new ExpectedException("Could not reintegrate loop values", ExceptionStatus.CONTRADICTORY_VALUES);
				}
			}
			else
			{
				//We can split the loop properly
				final AbstractSyntaxNode loopNode = new AbstractSyntaxNode(AbstractType.LOOP);
				final AbstractSyntaxNode loopMandatoryNode = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
				final AbstractSyntaxNode loopOptionalNode = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
				loopNode.addSuccessor(loopMandatoryNode);
				loopMandatoryNode.setPredecessor(loopNode);
				loopNode.addSuccessor(loopOptionalNode);
				loopOptionalNode.setPredecessor(loopNode);

				if (remainingChildren.isEmpty())
				{
					if (commonAncestor.predecessor() == null)
					{
						mainTree.setRoot(loopNode);
					}
					else
					{
						final AbstractSyntaxNode leastCommonAncestorParent = commonAncestor.predecessor();
						leastCommonAncestorParent.replaceSuccessor(commonAncestor, loopNode);
						loopNode.setPredecessor(leastCommonAncestorParent);
					}
				}


				if (commonAncestor.type() == AbstractType.SEQ)
				{
					//Nodes have possibly been reordered => rebuild the sets
					mandatoryChildren.clear();
					optionalChildren.clear();
					remainingChildren.clear();

					for (AbstractSyntaxNode commonAncestorChild : commonAncestor.successors())
					{
						if (commonAncestorChild.type() == AbstractType.TASK)
						{
							if (loopNodes.first().contains(commonAncestorChild))
							{
								mandatoryChildren.add(commonAncestorChild);
							}
							else if (loopNodes.second().contains(commonAncestorChild))
							{
								optionalChildren.add(commonAncestorChild);
							}
							else
							{
								remainingChildren.add(commonAncestorChild);
							}
						}
						else
						{
							boolean added = false;

							for (AbstractSyntaxNode mandatoryChild : loopNodes.first())
							{
								if (commonAncestorChild.hasDescendant(mandatoryChild))
								{
									mandatoryChildren.add(commonAncestorChild);
									added = true;
									break;
								}
							}

							for (AbstractSyntaxNode optionalChild : loopNodes.second())
							{
								if (commonAncestorChild.hasDescendant(optionalChild))
								{
									optionalChildren.add(commonAncestorChild);
									added = true;
									break;
								}
							}

							if (!added)
							{
								remainingChildren.add(commonAncestorChild);
							}
						}
					}
				}

				if (!remainingChildren.isEmpty())
				{
					if (remainingChildren.contains(commonAncestor.successors().get(0)))
					{
						commonAncestor.successors().clear();

						//Remaining children first
						for (AbstractSyntaxNode remainingChild : remainingChildren)
						{
							commonAncestor.addSuccessor(remainingChild);
							remainingChild.setPredecessor(commonAncestor);
						}

						commonAncestor.addSuccessor(loopNode);
						loopNode.setPredecessor(commonAncestor);
					}
					else
					{
						//Remaining children last
						commonAncestor.successors().clear();

						commonAncestor.addSuccessor(loopNode);
						loopNode.setPredecessor(commonAncestor);

						//Remaining children first
						for (AbstractSyntaxNode remainingChild : remainingChildren)
						{
							commonAncestor.addSuccessor(remainingChild);
							remainingChild.setPredecessor(commonAncestor);
						}
					}
				}

				final AbstractSyntaxNode mandatoryNodeToConnect;
				final AbstractSyntaxNode optionalNodeToConnect;

				if (mandatoryChildren.size() == 1)
				{
					if (commonAncestor.type() != AbstractType.PAR) throw new IllegalStateException();

					mandatoryNodeToConnect = loopMandatoryNode;
				}
				else
				{
					final AbstractSyntaxNode leastCommonAncestorMandatoryNode = new AbstractSyntaxNode(commonAncestor.type());
					loopMandatoryNode.addSuccessor(leastCommonAncestorMandatoryNode);
					leastCommonAncestorMandatoryNode.setPredecessor(loopMandatoryNode);
					mandatoryNodeToConnect = leastCommonAncestorMandatoryNode;
				}

				if (optionalChildren.size() == 1)
				{
					if (commonAncestor.type() != AbstractType.PAR) throw new IllegalStateException();

					optionalNodeToConnect = loopOptionalNode;
				}
				else
				{
					final AbstractSyntaxNode leastCommonAncestorOptionalNode = new AbstractSyntaxNode(commonAncestor.type());
					loopOptionalNode.addSuccessor(leastCommonAncestorOptionalNode);
					leastCommonAncestorOptionalNode.setPredecessor(loopOptionalNode);
					optionalNodeToConnect = leastCommonAncestorOptionalNode;
				}

				for (AbstractSyntaxNode mandatoryChild : mandatoryChildren)
				{
					mandatoryNodeToConnect.addSuccessor(mandatoryChild);
					mandatoryChild.setPredecessor(mandatoryNodeToConnect);
				}

				for (AbstractSyntaxNode optionalChild : optionalChildren)
				{
					optionalNodeToConnect.addSuccessor(optionalChild);
					optionalChild.setPredecessor(optionalNodeToConnect);
				}
			}
		}
	}

	private static boolean tryLeft(final AbstractSyntaxTree mainTree,
								   final AbstractSyntaxNode commonAncestor,
								   final ArrayList<AbstractSyntaxNode> mandatoryChildren,
								   final ArrayList<AbstractSyntaxNode> optionalChildren,
								   final ArrayList<AbstractSyntaxNode> remainingChildren,
								   final Collection<AbstractSyntaxTree> constraints)
	{
		final AbstractSyntaxTree treeCopy = mainTree.copy();
		final AbstractSyntaxNode commonAncestorCopy = treeCopy.findNodeOfId(commonAncestor.id());
		commonAncestorCopy.removeSuccessors();

		for (AbstractSyntaxNode child : remainingChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : mandatoryChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : optionalChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		if (ASTUtils.getProblematicTrees(treeCopy, constraints).isEmpty())
		{
			commonAncestor.removeSuccessors();

			for (AbstractSyntaxNode child : remainingChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : mandatoryChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : optionalChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			return true;
		}
		else
		{
			return false;
		}
	}

	private static boolean tryMiddle(final AbstractSyntaxTree mainTree,
									 final AbstractSyntaxNode commonAncestor,
									 final ArrayList<AbstractSyntaxNode> mandatoryChildren,
									 final ArrayList<AbstractSyntaxNode> optionalChildren,
									 final ArrayList<AbstractSyntaxNode> remainingChildren,
									 final Collection<AbstractSyntaxTree> constraints)
	{
		final AbstractSyntaxTree treeCopy = mainTree.copy();
		final AbstractSyntaxNode commonAncestorCopy = treeCopy.findNodeOfId(commonAncestor.id());
		commonAncestorCopy.removeSuccessors();

		for (AbstractSyntaxNode child : mandatoryChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : remainingChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : optionalChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		if (ASTUtils.getProblematicTrees(treeCopy, constraints).isEmpty())
		{
			commonAncestor.removeSuccessors();

			for (AbstractSyntaxNode child : mandatoryChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : remainingChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : optionalChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			return true;
		}
		else
		{
			return false;
		}
	}

	private static boolean tryRight(final AbstractSyntaxTree mainTree,
									final AbstractSyntaxNode commonAncestor,
									final ArrayList<AbstractSyntaxNode> mandatoryChildren,
									final ArrayList<AbstractSyntaxNode> optionalChildren,
									final ArrayList<AbstractSyntaxNode> remainingChildren,
									final Collection<AbstractSyntaxTree> constraints)
	{
		final AbstractSyntaxTree treeCopy = mainTree.copy();
		final AbstractSyntaxNode commonAncestorCopy = treeCopy.findNodeOfId(commonAncestor.id());
		commonAncestorCopy.removeSuccessors();

		for (AbstractSyntaxNode child : mandatoryChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : optionalChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		for (AbstractSyntaxNode child : remainingChildren)
		{
			final AbstractSyntaxNode childCopy = child.deepCopy();
			commonAncestorCopy.addSuccessor(childCopy);
			childCopy.setPredecessor(commonAncestorCopy);
		}

		if (ASTUtils.getProblematicTrees(treeCopy, constraints).isEmpty())
		{
			commonAncestor.removeSuccessors();

			for (AbstractSyntaxNode child : mandatoryChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : optionalChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			for (AbstractSyntaxNode child : remainingChildren)
			{
				commonAncestorCopy.addSuccessor(child);
				child.setPredecessor(commonAncestorCopy);
			}

			return true;
		}
		else
		{
			return false;
		}
	}
}
