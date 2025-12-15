package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.constants.IntegrityStatus;
import chat_gpt.exceptions.ContradictoryValuesException;
import exceptions.ExpectedException;
import other.Pair;
import other.Utils;

import java.util.*;

public class ASTSequenceReducerV2
{
	private static final String DUMMY_SEQUENCE_REDUCER = "DUMMY_SEQUENCE_REDUCER_";

	private ASTSequenceReducerV2()
	{
		
	}
	
	public static void releaseLocalConstraints(final AbstractSyntaxTree currentTree,
											   final List<AbstractSyntaxTree> dependencies,
											   final AbstractSyntaxTree treeToMerge,
											   final AbstractSyntaxNode nodeToVerify)
	{
		//System.out.println("Tree before local sequence reduction:\n\n" + currentTree.toString());

		final Pair<AbstractSyntaxNode, Boolean> alreadyExistingParentAndLocation = ASTSequenceReducerV2.getAlreadyExistingNodeInMainTree(currentTree, treeToMerge, nodeToVerify);
		final AbstractSyntaxNode alreadyExistingParent = alreadyExistingParentAndLocation.first();
		final boolean nodeToVerifyIsOnTheRightOfExistingNode = alreadyExistingParentAndLocation.second();
		final ArrayList<AbstractSyntaxTree> sequenceDependencies = ASTSequenceReducerV2.getSequences(dependencies);
		ASTSequenceReducerV2.releaseLocalConstraints(currentTree, nodeToVerify, alreadyExistingParent, sequenceDependencies, nodeToVerifyIsOnTheRightOfExistingNode);

		//System.out.println("Tree after local sequence reduction:\n\n" + currentTree.toString());
	}

	public static void releaseGlobalConstraintsV1(final AbstractSyntaxTree currentTree,
												  final List<AbstractSyntaxTree> constraints)
	{
		//System.out.println("Tree before global sequence reduction:\n\n" + currentTree.toString());

		final HashSet<AbstractSyntaxNode> reducedSequences = new HashSet<>();
		final HashSet<AbstractSyntaxNode> sequencesToReduce = new HashSet<>();
		ASTSequenceReducerV2.retrieveSequencesToReduce(currentTree.root(), reducedSequences, sequencesToReduce);

		while (!sequencesToReduce.isEmpty())
		{
			System.out.println("Sequences to reduce:\n\n" + sequencesToReduce);

			for (AbstractSyntaxNode sequence : sequencesToReduce)
			{
				ASTSequenceReducerV2.reduceSequence(sequence, constraints, currentTree);
			}

			sequencesToReduce.clear();
			ASTSequenceReducerV2.retrieveSequencesToReduce(currentTree.root(), reducedSequences, sequencesToReduce);
		}

		//System.out.println("Tree after global sequence reduction:\n\n" + currentTree.toString());
	}
	
	//Private methods

	private static void reduceSequence(final AbstractSyntaxNode sequence,
									   final List<AbstractSyntaxTree> constraints,
									   final AbstractSyntaxTree mainTree)
	{
		//System.out.println("Sequence before reduction:\n\n" + sequence.stringify(0));
		boolean changed = true;

		while (changed)
		{
			changed = false;

			if (mainTree.findNodeOfId(sequence.id()) == null) break;

			final ArrayList<AbstractSyntaxNode> tasks = new ArrayList<>();

			for (AbstractSyntaxNode successor : sequence.successors())
			{
				if (successor.type() == AbstractType.TASK
					&& !successor.id().startsWith("LOOPY_DUMMY"))
				{
					tasks.add(successor);
				}
			}

			//No free task found, sequence is already reduced (in our sense)
			if (tasks.isEmpty()) return;

			final ArrayList<AbstractSyntaxNode> nonInsertableTasks = new ArrayList<>();

			//Try to insert the free tasks in their near structures (if any)
			for (AbstractSyntaxNode task : tasks)
			{
				final ArrayList<AbstractSyntaxNode> nearStructures = ASTSequenceReducerV2.getNearStructuresOf(task);

				if (nearStructures.isEmpty())
				{
					nonInsertableTasks.add(task);
				}
				else
				{
					final AbstractSyntaxNode nearStructure1 = nearStructures.get(0);
					final AbstractSyntaxNode nearStructure2;

					if (nearStructures.size() != 1)
					{
						nearStructure2 = nearStructures.get(1);
					}
					else
					{
						nearStructure2 = null;
					}

					if (ASTSequenceReducerV2.taskIsInsertable(task, nearStructure1, constraints))
					{
						changed = true;

						if (!ASTSequenceReducerV2.insertTaskInStructure(mainTree, sequence, task, nearStructure1, constraints))
						{
							throw new IllegalStateException();
						}

						break;
					}
					else
					{
						if (nearStructure2 != null)
						{
							if (ASTSequenceReducerV2.taskIsInsertable(task, nearStructure2, constraints))
							{
								changed = true;

								if (!ASTSequenceReducerV2.insertTaskInStructure(mainTree, sequence, task, nearStructure2, constraints))
								{
									throw new IllegalStateException();
								}

								break;
							}
							else
							{
								//System.out.println("Main tree atm: \n\n" + mainTree);

								final AbstractSyntaxNode dummyNode = AbstractSyntaxNodeFactory.newTask(DUMMY_SEQUENCE_REDUCER + Utils.generateRandomIdentifier(15));
								//final int taskIndex = sequence.successors().indexOf(task);
								sequence.replaceSuccessor(task, dummyNode);
								dummyNode.setPredecessor(sequence);

								if (ASTSequenceReducerV2.mergeSubNodesV2(nearStructures, task, constraints, mainTree))
								{
									sequence.removeSuccessor(dummyNode);

									if (sequence.successors().size() == 1)
									{
										final AbstractSyntaxNode sequenceParent = sequence.predecessor();

										if (sequenceParent == null)
										{
											sequence.successors().get(0).resetPredecessor();
											mainTree.setRoot(sequence.successors().get(0));
										}
										else
										{
											sequenceParent.replaceSuccessor(sequence, sequence.successors().get(0));
											sequence.successors().get(0).setPredecessor(sequenceParent);
										}
									}

									changed = true;
									break;
								}
								else
								{
									nonInsertableTasks.add(task);
									sequence.replaceSuccessor(dummyNode, task);
									task.setPredecessor(sequence);
								}
							}
						}
						else
						{
							//System.out.println("Main tree atm: \n\n" + mainTree);

							final AbstractSyntaxNode dummyNode = AbstractSyntaxNodeFactory.newTask(DUMMY_SEQUENCE_REDUCER + Utils.generateRandomIdentifier(15));
							//final int taskIndex = sequence.successors().indexOf(task);
							sequence.replaceSuccessor(task, dummyNode);
							dummyNode.setPredecessor(sequence);

							if (ASTSequenceReducerV2.mergeSubNodesV2(nearStructures, task, constraints, mainTree))
							{
								sequence.removeSuccessor(dummyNode);

								if (sequence.successors().size() == 1)
								{
									final AbstractSyntaxNode sequenceParent = sequence.predecessor();

									if (sequenceParent == null)
									{
										sequence.successors().get(0).resetPredecessor();
										mainTree.setRoot(sequence.successors().get(0));
									}
									else
									{
										sequenceParent.replaceSuccessor(sequence, sequence.successors().get(0));
										sequence.successors().get(0).setPredecessor(sequenceParent);
									}
								}

								changed = true;
								break;
							}
							else
							{
								nonInsertableTasks.add(task);
								sequence.replaceSuccessor(dummyNode, task);
								task.setPredecessor(sequence);
							}
						}
					}
				}
			}

			if (mainTree.findNodeOfId(sequence.id()) == null) break;

			//Manage all the remaining tasks: if neighbours, try to merge them, otherwise, do nothing
			final ArrayList<Integer> tasksIndices = new ArrayList<>();

			for (AbstractSyntaxNode task : nonInsertableTasks)
			{
				tasksIndices.add(sequence.successors().indexOf(task));
			}

			final ArrayList<ArrayList<AbstractSyntaxNode>> consecutiveNodesList = new ArrayList<>();
			ArrayList<AbstractSyntaxNode> currentList = new ArrayList<>();
			consecutiveNodesList.add(currentList);

			for (int i = 0; i < sequence.successors().size(); i++)
			{
				if (tasksIndices.contains(i))
				{
					currentList.add(sequence.successors().get(i));
				}
				else
				{
					if (!currentList.isEmpty())
					{
						currentList = new ArrayList<>();
						consecutiveNodesList.add(currentList);
					}
				}
			}

			for (ArrayList<AbstractSyntaxNode> consecutiveNodes : consecutiveNodesList)
			{
				if (consecutiveNodes.size() < 2) continue;

				if (consecutiveNodes.size() == 2)
				{
					final String dummyHash = consecutiveNodes.get(0).label() + "<" + consecutiveNodes.get(1).label();
					boolean canBeMerged = true;

					for (AbstractSyntaxTree constraint : constraints)
					{
						if (constraint.hash().equals(dummyHash))
						{
							//The nodes can not be merged
							canBeMerged = false;
							break;
						}
					}

					if (canBeMerged)
					{
						changed = true;
						final int firstIndex = sequence.successors().indexOf(consecutiveNodes.get(0));
						sequence.successors().removeAll(consecutiveNodes);

						final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);

						for (AbstractSyntaxNode consecutiveNode : consecutiveNodes)
						{
							parallelNode.addSuccessor(consecutiveNode);
							consecutiveNode.setPredecessor(parallelNode);
						}

						sequence.addSuccessor(firstIndex, parallelNode);
						parallelNode.setPredecessor(sequence);
						break;
					}
				}
				else
				{
					for (int i = 0; i < consecutiveNodes.size() - 1; i++)
					{
						final AbstractSyntaxNode currentNode = consecutiveNodes.get(i);
						final AbstractSyntaxNode nextNode = consecutiveNodes.get(i+1);
						final String dummyHash = currentNode.label() + "<" + nextNode.label();
						boolean canBeMerged = true;

						for (AbstractSyntaxTree constraint : constraints)
						{
							if (constraint.hash().equals(dummyHash))
							{
								//The nodes can not be merged
								canBeMerged = false;
								break;
							}
						}

						if (canBeMerged)
						{
							sequence.removeSuccessor(nextNode);
							final AbstractSyntaxNode parallel = new AbstractSyntaxNode(AbstractType.PAR);
							sequence.replaceSuccessor(currentNode, parallel);
							parallel.setPredecessor(sequence);
							parallel.addSuccessor(currentNode);
							currentNode.setPredecessor(parallel);
							parallel.addSuccessor(nextNode);
							nextNode.setPredecessor(parallel);

							changed = true;
							break;
						}
					}
				}
			}
		}

		if (mainTree.findNodeOfId(sequence.id()) == null) return;

		/*if (sequence.successors().size() == 1)
		{
			final AbstractSyntaxNode predecessor = sequence.predecessor();
			final AbstractSyntaxNode successor = sequence.successors().get(0);

			if (predecessor == null)
			{
				successor.resetPredecessor();
				mainTree.setRoot(successor);
			}
			else
			{
				predecessor.replaceSuccessor(sequence, successor);
				successor.setPredecessor(predecessor);
			}
		}*/

		//System.out.println("Sequence after reduction:\n\n" + sequence.stringify(0));
	}

	private static boolean taskIsInsertable(final AbstractSyntaxNode task,
											final AbstractSyntaxNode nearStructure,
											final List<AbstractSyntaxTree> constraints)
	{
		final HashSet<AbstractSyntaxNode> structureTasks = new HashSet<>();
		ASTUtils.retrieveAllTasksFrom(nearStructure, structureTasks);

		final int taskIndex = task.predecessor().successors().indexOf(task);
		final int structureIndex = task.predecessor().successors().indexOf(nearStructure);

		if (taskIndex > structureIndex)
		{
			for (AbstractSyntaxNode structureTask : structureTasks)
			{
				final String dummyHash = structureTask.label() + "<" + task.label();

				for (AbstractSyntaxTree constraint : constraints)
				{
					if (constraint.hash().equals(dummyHash)) return false;
				}
			}
		}
		else if (taskIndex < structureIndex)
		{
			for (AbstractSyntaxNode structureTask : structureTasks)
			{
				final String dummyHash = task.label() + "<" + structureTask.label();

				for (AbstractSyntaxTree constraint : constraints)
				{
					if (constraint.hash().equals(dummyHash)) return false;
				}
			}
		}
		else
		{
			throw new IllegalStateException();
		}

		return true;
	}

	private static boolean insertTaskInStructure(final AbstractSyntaxTree mainTree,
												 final AbstractSyntaxNode sequence,
												 final AbstractSyntaxNode task,
												 final AbstractSyntaxNode nearStructure,
												 final List<AbstractSyntaxTree> constraints)
	{
		final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
		final AbstractSyntaxNode sequenceParent = sequence.predecessor();
		sequence.removeSuccessor(nearStructure);
		parallelNode.addSuccessor(task);
		task.setPredecessor(parallelNode);
		parallelNode.addSuccessor(nearStructure);
		nearStructure.setPredecessor(parallelNode);

		if (sequenceParent == null)
		{
			if (sequence.successors().size() == 1)
			{
				parallelNode.resetPredecessor();
				mainTree.setRoot(parallelNode);
			}
			else
			{
				sequence.replaceSuccessor(task, parallelNode);
				parallelNode.setPredecessor(sequence);
			}
		}
		else
		{
			if (sequence.successors().size() == 1)
			{
				//System.out.println(sequenceParent.successors().size());
				//System.out.println(sequenceParent.successors().get(0).id());
				//System.out.println(sequence.id());
				sequenceParent.replaceSuccessor(sequence, parallelNode);
				parallelNode.setPredecessor(sequenceParent);
			}
			else
			{
				sequence.replaceSuccessor(task, parallelNode);
				parallelNode.setPredecessor(sequence);
			}
		}

		return ASTUtils.getProblematicTrees(mainTree, constraints).isEmpty();
	}

	private static ArrayList<AbstractSyntaxNode> getNearStructuresOf(final AbstractSyntaxNode task)
	{
		final ArrayList<AbstractSyntaxNode> nearStructures = new ArrayList<>();
		final AbstractSyntaxNode parent = task.predecessor();
		final int taskIndex = parent.successors().indexOf(task);

		if (taskIndex > 0)
		{
			final AbstractSyntaxNode previousNode = parent.successors().get(taskIndex - 1);

			if (previousNode.type() != AbstractType.TASK)
			{
				nearStructures.add(previousNode);
			}
		}

		if (taskIndex < parent.successors().size() - 1)
		{
			final AbstractSyntaxNode nextNode = parent.successors().get(taskIndex + 1);

			if (nextNode.type() != AbstractType.TASK)
			{
				nearStructures.add(nextNode);
			}
		}

		return nearStructures;
	}

	private static void retrieveSequencesToReduce(final AbstractSyntaxNode currentNode,
												  final HashSet<AbstractSyntaxNode> alreadyReducedSequences,
												  final HashSet<AbstractSyntaxNode> currentChoicesToReduce)
	{
		if (currentNode.type() == AbstractType.SEQ)
		{
			if (alreadyReducedSequences.contains(currentNode)) return;

			final HashSet<AbstractSyntaxNode> nonReducedSubSequences = new HashSet<>();

			for (AbstractSyntaxNode successor : currentNode.successors())
			{
				ASTSequenceReducerV2.getAllNonReducedSubSequences(successor, alreadyReducedSequences, nonReducedSubSequences);
			}

			if (nonReducedSubSequences.isEmpty())
			{
				//The current sequence can be reduced
				currentChoicesToReduce.add(currentNode);
				alreadyReducedSequences.add(currentNode);
				return;
			}
		}
		/*else if (currentNode.type() == AbstractType.LOOP)
		{
			return;
		}*/

		for (AbstractSyntaxNode successor : currentNode.successors())
		{
			ASTSequenceReducerV2.retrieveSequencesToReduce(successor, alreadyReducedSequences, currentChoicesToReduce);
		}
	}

	private static void getAllNonReducedSubSequences(final AbstractSyntaxNode currentNode,
													 final HashSet<AbstractSyntaxNode> alreadyReducedSequences,
													 final HashSet<AbstractSyntaxNode> nonReducedSubSequences)
	{
		if (currentNode.type() == AbstractType.SEQ)
		{
			if (!alreadyReducedSequences.contains(currentNode))
			{
				nonReducedSubSequences.add(currentNode);
			}
		}
		else
		{
			for (AbstractSyntaxNode successor : currentNode.successors())
			{
				ASTSequenceReducerV2.getAllNonReducedSubSequences(successor, alreadyReducedSequences, nonReducedSubSequences);
			}
		}
	}

	private static Pair<AbstractSyntaxNode, Boolean> getAlreadyExistingNodeInMainTree(final AbstractSyntaxTree mainTree,
																					  final AbstractSyntaxTree treeToMerge,
																					  final AbstractSyntaxNode nodeToVerify)
	{
		final AbstractSyntaxNode alreadyExistingNode;
		final AbstractSyntaxNode leftNode = treeToMerge.root().successors().get(0);
		final boolean nodeToVerifyIsOnTheRightOfExistingNode;

		if (nodeToVerify.type() == AbstractType.TASK)
		{
			if (leftNode.equals(nodeToVerify))
			{
				alreadyExistingNode = treeToMerge.root().successors().get(1);
				nodeToVerifyIsOnTheRightOfExistingNode = false;
			}
			else
			{
				alreadyExistingNode = leftNode;
				nodeToVerifyIsOnTheRightOfExistingNode = true;
			}
		}
		else
		{
			if (nodeToVerify.findNodeOfLabel(leftNode.label()) == null)
			{
				alreadyExistingNode = leftNode;
				nodeToVerifyIsOnTheRightOfExistingNode = true;
			}
			else
			{
				alreadyExistingNode = treeToMerge.root().successors().get(1);
				nodeToVerifyIsOnTheRightOfExistingNode = false;
			}
		}

		final AbstractSyntaxNode nodeToVerifyInMainTree = mainTree.findNodeOfId(nodeToVerify.id());

		if (nodeToVerifyInMainTree == null) throw new IllegalStateException("Did not find node |" + nodeToVerify.id() + "| in tree \n\n" + mainTree.toString());

		for (AbstractSyntaxNode child : nodeToVerifyInMainTree.predecessor().successors())
		{
			if (child.findNodeOfLabel(alreadyExistingNode.label()) != null)
			{
				return new Pair<>(child, nodeToVerifyIsOnTheRightOfExistingNode);
			}
		}

		throw new IllegalStateException();
	}

	private static ArrayList<AbstractSyntaxTree> getSequences(final List<AbstractSyntaxTree> dependencies)
	{
		final ArrayList<AbstractSyntaxTree> sequences = new ArrayList<>();

		for (AbstractSyntaxTree tree : dependencies)
		{
			if (tree.root().type() == AbstractType.SEQ)
			{
				sequences.add(tree);
			}
		}

		return sequences;
	}

	private static void releaseLocalConstraints(final AbstractSyntaxTree tree,
												final AbstractSyntaxNode nodeToVerify,
												final AbstractSyntaxNode alreadyExistingParent,
												final ArrayList<AbstractSyntaxTree> dependencies,
												final boolean nodeToVerifyIsOnTheRightOfExistingNode)
	{
		final ArrayList<AbstractSyntaxNode> tasksInNodeToVerifySubtree = new ArrayList<>();
		final AbstractSyntaxNode nodeToVerifyInMainTree = tree.findNodeOfId(nodeToVerify.id());

		if (nodeToVerifyInMainTree == null) throw new IllegalStateException();

		if (nodeToVerify.type() == AbstractType.TASK)
		{
			tasksInNodeToVerifySubtree.add(nodeToVerifyInMainTree);
		}
		else
		{
			ASTUtils.retrieveAllTasksFrom(nodeToVerifyInMainTree, tasksInNodeToVerifySubtree);
		}

		final ArrayList<AbstractSyntaxNode> eligibleNodes = new ArrayList<>();
		final int indexOfExistingNode =  alreadyExistingParent.predecessor().successors().indexOf(alreadyExistingParent);

		if (indexOfExistingNode == -1) throw new IllegalStateException();

		if (nodeToVerifyIsOnTheRightOfExistingNode)
		{
			for (int i = indexOfExistingNode + 1; i < alreadyExistingParent.predecessor().successors().size(); i++)
			{
				final AbstractSyntaxNode currentNode = alreadyExistingParent.predecessor().successors().get(i);

				if (!currentNode.equals(nodeToVerify))
				{
					eligibleNodes.add(currentNode);
				}
			}
		}
		else
		{
			for (int i = 0; i < indexOfExistingNode; i++)
			{
				final AbstractSyntaxNode currentNode = alreadyExistingParent.predecessor().successors().get(i);

				if (!currentNode.equals(nodeToVerify))
				{
					eligibleNodes.add(currentNode);
				}
			}
		}

		if (eligibleNodes.isEmpty())
		{
			//Node to verify can not be moved to another position nor merged with other nodes, so we can not do anything.
			return;
		}

		final ArrayList<AbstractSyntaxTree> problematicTrees = ASTUtils.getProblematicTrees(tree, dependencies);

		//The node to verify was not properly placed, thus we need to move it to a correct location (if possible)
		int i = 0;

		//Try to put the node to merge before each eligible node
		while (i < eligibleNodes.size())
		{
			final AbstractSyntaxTree copiedMainTree = tree.copy();
			final AbstractSyntaxNode currentEligibleNode = eligibleNodes.get(i);
			final AbstractSyntaxNode nodeToVerifyInCopiedTree = copiedMainTree.findNodeOfId(nodeToVerify.id());

			if (nodeToVerifyInCopiedTree == null) throw new IllegalStateException();

			nodeToVerifyInCopiedTree.predecessor().removeSuccessor(nodeToVerifyInCopiedTree);
			final int indexOfCurrentEligibleNode = nodeToVerifyInCopiedTree.predecessor().successors().indexOf(currentEligibleNode);
			nodeToVerifyInCopiedTree.predecessor().addSuccessor(indexOfCurrentEligibleNode, nodeToVerifyInCopiedTree);
			//nodeToVerifyInCopiedTree.setPredecessor(nodeToVerifyInCopiedTree.predecessor());

			problematicTrees.clear();
			problematicTrees.addAll(ASTUtils.getProblematicTrees(copiedMainTree, dependencies));

			if (problematicTrees.isEmpty())
			{
				//We found a good position for our node, so we replace it in the main tree
				nodeToVerifyInMainTree.predecessor().removeSuccessor(nodeToVerifyInMainTree);
				final int indexOfCurrentEligibleNodeInMainTree = nodeToVerifyInMainTree.predecessor().successors().indexOf(currentEligibleNode);
				nodeToVerifyInMainTree.predecessor().addSuccessor(indexOfCurrentEligibleNodeInMainTree, nodeToVerifyInMainTree);
				break;
			}

			i++;
		}

		//Last possible position if after the last eligible node
		if (!problematicTrees.isEmpty())
		{
			if (i != eligibleNodes.size()) throw new IllegalStateException();

			final AbstractSyntaxTree copiedMainTree = tree.copy();
			final AbstractSyntaxNode nodeToVerifyInCopiedTree = copiedMainTree.findNodeOfId(nodeToVerify.id());

			if (nodeToVerifyInCopiedTree == null) throw new IllegalStateException();

			nodeToVerifyInCopiedTree.predecessor().removeSuccessor(nodeToVerifyInCopiedTree);
			final int indexOfCurrentEligibleNode = nodeToVerifyInCopiedTree.predecessor().successors().indexOf(eligibleNodes.get(eligibleNodes.size() - 1)) + 1;
			nodeToVerifyInCopiedTree.predecessor().addSuccessor(indexOfCurrentEligibleNode, nodeToVerifyInCopiedTree);
			//nodeToVerifyInCopiedTree.setPredecessor(nodeToVerifyInCopiedTree.predecessor());

			if (ASTUtils.getProblematicTrees(copiedMainTree, dependencies).isEmpty())
			{
				//We found a good position for our node, so we replace it in the main tree
				nodeToVerifyInMainTree.predecessor().removeSuccessor(nodeToVerifyInMainTree);
				final int indexOfCurrentEligibleNodeInMainTree = nodeToVerifyInMainTree.predecessor().successors().indexOf(eligibleNodes.get(eligibleNodes.size() - 1)) + 1;
				nodeToVerifyInMainTree.predecessor().addSuccessor(indexOfCurrentEligibleNodeInMainTree, nodeToVerifyInMainTree);
				//nodeToVerifyInCopiedTree.setPredecessor(nodeToVerifyInCopiedTree.predecessor());
			}
		}

		final boolean nodeIsProperlyPlacedWithoutMerge = ASTUtils.getProblematicTrees(tree, dependencies).isEmpty();
		//System.out.println("Node requires merge to be well placed: " + !nodeIsProperlyPlacedWithoutMerge);

		//Now that the node is properly placed, check whether it can be merged with some other nodes or not
		final ArrayList<AbstractSyntaxNode> mergeableNodes = new ArrayList<>();

		for (AbstractSyntaxNode eligibleNode : eligibleNodes)
		{
			final int indexOfEligibleNode = nodeToVerifyInMainTree.predecessor().successors().indexOf(eligibleNode);
			final int indexOfNodeToVerify = nodeToVerifyInMainTree.predecessor().successors().indexOf(nodeToVerifyInMainTree);

			if (indexOfEligibleNode == -1
				|| indexOfNodeToVerify == -1
				|| indexOfNodeToVerify == indexOfEligibleNode) throw new IllegalStateException();

			final ArrayList<AbstractSyntaxNode> leftTasks = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> rightTasks = new ArrayList<>();

			if (indexOfEligibleNode < indexOfNodeToVerify)
			{
				ASTUtils.retrieveAllTasksFrom(eligibleNode, leftTasks);
				ASTUtils.retrieveAllTasksFrom(nodeToVerifyInMainTree, rightTasks);
			}
			else
			{
				ASTUtils.retrieveAllTasksFrom(eligibleNode, rightTasks);
				ASTUtils.retrieveAllTasksFrom(nodeToVerifyInMainTree, leftTasks);
			}

			boolean nodeCanBeMerged = true;

			for (AbstractSyntaxNode leftTask : leftTasks)
			{
				for (AbstractSyntaxNode rightTask : rightTasks)
				{
					final String dummyHash = leftTask.label() + "<" + rightTask.label();

					for (AbstractSyntaxTree currentDependency : dependencies)
					{
						if (currentDependency.hash().equals(dummyHash))
						{
							//This dependency exists and must thus be preserved.
							//Consequently, the current eligible node can not be merged with the node to verify
							nodeCanBeMerged = false;
							break;
						}
					}

					if (!nodeCanBeMerged) break;
				}

				if (!nodeCanBeMerged) break;
			}

			if (nodeCanBeMerged)
			{
				mergeableNodes.add(eligibleNode);
			}
		}

		boolean mergeable = true;

		if (mergeableNodes.isEmpty())
		{
			mergeable = false;
		}
		else if (mergeableNodes.size() == 1)
		{
			final AbstractSyntaxNode mergeableNode = mergeableNodes.get(0);

			//1 mergeable node was found: it should be located next to the node to verify
			final int indexOfMergeableNode = nodeToVerifyInMainTree.predecessor().successors().indexOf(mergeableNode);
			final int indexOfNodeToVerify = nodeToVerifyInMainTree.predecessor().successors().indexOf(nodeToVerifyInMainTree);

			if ((indexOfMergeableNode != indexOfNodeToVerify + 1)
				&& indexOfMergeableNode != indexOfNodeToVerify - 1)
			{
				//We found a mergeable node, but it is not next to the node to verify, thus we try to merge and stop if we see that it was not possible
				final AbstractSyntaxTree treeCopy = tree.copy();
				final AbstractSyntaxNode nodeToVerifyInTreeCopy = treeCopy.findNodeOfId(nodeToVerifyInMainTree.id());
				nodeToVerifyInTreeCopy.predecessor().removeSuccessor(mergeableNode);

				final AbstractSyntaxNode parNode = new AbstractSyntaxNode(AbstractType.PAR);

				nodeToVerifyInTreeCopy.predecessor().replaceSuccessor(nodeToVerifyInTreeCopy, parNode);
				parNode.setPredecessor(nodeToVerifyInTreeCopy.predecessor());

				final AbstractSyntaxNode mergeableNodeCopy = mergeableNode.copy();

				parNode.addSuccessor(mergeableNodeCopy);
				mergeableNodeCopy.setPredecessor(parNode);
				parNode.addSuccessor(nodeToVerifyInTreeCopy);
				nodeToVerifyInTreeCopy.setPredecessor(parNode);

				//If merging with a non-neighbour node generated a problem, set merged to False
				if (!ASTUtils.getProblematicTrees(treeCopy, dependencies).isEmpty())
				{
					mergeable = false;
				}
			}

			if (mergeable)
			{
				nodeToVerifyInMainTree.predecessor().removeSuccessor(mergeableNode);

				final AbstractSyntaxNode parNode = new AbstractSyntaxNode(AbstractType.PAR);

				nodeToVerifyInMainTree.predecessor().replaceSuccessor(nodeToVerifyInMainTree, parNode);
				parNode.setPredecessor(nodeToVerifyInMainTree.predecessor());

				parNode.addSuccessor(mergeableNode);
				mergeableNode.setPredecessor(parNode);
				parNode.addSuccessor(nodeToVerifyInMainTree);
				nodeToVerifyInMainTree.setPredecessor(parNode);

				final IntegrityStatus integrityStatus = ASTIntegrityVerifier.verifyIntegrity(tree, null);

				if (integrityStatus != IntegrityStatus.VALID)
				{
					throw new IllegalStateException("INTEGRITY CHECK FAILED: " + integrityStatus.meaning());
				}

				//Normally, this should not have created issues in the tree
				if (!ASTUtils.getProblematicTrees(tree, dependencies).isEmpty()) throw new IllegalStateException();
			}
		}
		else
		{
			//Several mergeable nodes were found: they should all be located next to the node to verify, without "holes"
			final ArrayList<Integer> indices = new ArrayList<>();
			final int nodeToVerifyIndex = nodeToVerifyInMainTree.predecessor().successors().indexOf(nodeToVerifyInMainTree);
			indices.add(nodeToVerifyIndex);

			for (AbstractSyntaxNode mergeableNode : mergeableNodes)
			{
				indices.add(nodeToVerifyInMainTree.predecessor().successors().indexOf(mergeableNode));
			}

			if (!Utils.integersAreConsecutive(indices))
			{
				//Not all mergeable nodes are neighbours: try to merge anyway and see what happens
				indices.remove(Integer.valueOf(nodeToVerifyIndex)); //Tricky bug: as we manipulate list of Integers, indices.remove(nodeToVerifyIndex) removes the element at index nodeToVerifyIndex instead of the value nodeToVerifyIndex :D
				Collections.sort(indices);

				final AbstractSyntaxTree treeCopy = tree.copy();
				final ArrayList<AbstractSyntaxNode> nodesToParallelise = new ArrayList<>();
				final AbstractSyntaxNode nodeToVerifyInTreeCopy = treeCopy.findNodeOfId(nodeToVerifyInMainTree.id());

				for (int index : indices)
				{
					nodesToParallelise.add(nodeToVerifyInTreeCopy.predecessor().successors().get(index));
				}

				nodeToVerifyInTreeCopy.predecessor().successors().removeIf(mergeableNodes::contains);

				final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
				final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);
				nodeToVerifyInTreeCopy.predecessor().replaceSuccessor(nodeToVerifyInTreeCopy, parallelNode);
				parallelNode.setPredecessor(nodeToVerifyInTreeCopy.predecessor());
				parallelNode.addSuccessor(sequenceNode);
				sequenceNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(nodeToVerifyInTreeCopy);
				nodeToVerifyInTreeCopy.setPredecessor(parallelNode);

				for (AbstractSyntaxNode mergeableNode : nodesToParallelise)
				{
					final AbstractSyntaxNode mergeableNodeCopy = treeCopy.findNodeOfId(mergeableNode.id());
					sequenceNode.addSuccessor(mergeableNodeCopy);
					mergeableNodeCopy.setPredecessor(sequenceNode);
				}

				//If merging with some non-neighbour nodes generated a problem, set merged to False
				if (!ASTUtils.getProblematicTrees(treeCopy, dependencies).isEmpty())
				{
					mergeable = false;
				}
			}

			if (mergeable)
			{
				indices.remove(Integer.valueOf(nodeToVerifyIndex)); //Tricky bug: as we manipulate list of Integers, indices.remove(nodeToVerifyIndex) removes the element at index nodeToVerifyIndex instead of the value nodeToVerifyIndex :D
				Collections.sort(indices);

				final ArrayList<AbstractSyntaxNode> nodesToParallelise = new ArrayList<>();

				for (int index : indices)
				{
					nodesToParallelise.add(nodeToVerifyInMainTree.predecessor().successors().get(index));
				}

				if (nodesToParallelise.contains(nodeToVerifyInMainTree)) throw new IllegalStateException();

				nodeToVerifyInMainTree.predecessor().successors().removeIf(mergeableNodes::contains);

				final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
				final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);
				nodeToVerifyInMainTree.predecessor().replaceSuccessor(nodeToVerifyInMainTree, parallelNode);
				parallelNode.setPredecessor(nodeToVerifyInMainTree.predecessor());
				parallelNode.addSuccessor(sequenceNode);
				sequenceNode.setPredecessor(parallelNode);
				parallelNode.addSuccessor(nodeToVerifyInMainTree);
				nodeToVerifyInMainTree.setPredecessor(parallelNode);

				for (AbstractSyntaxNode mergeableNode : nodesToParallelise)
				{
					sequenceNode.addSuccessor(mergeableNode);
					mergeableNode.setPredecessor(sequenceNode);
				}

				//Normally, this should not have created issues in the tree
				if (!ASTUtils.getProblematicTrees(tree, dependencies).isEmpty()) throw new IllegalStateException();
			}
			else
			{
				//We tried to merge non-neighbour nodes, but it failed -> find the biggest set of neighbours and merge with them
				final ArrayList<Integer> leftNeighbourNodesIndices = new ArrayList<>();
				final ArrayList<Integer> rightNeighbourNodesIndices = new ArrayList<>();

				for (int j = indexOfExistingNode - 1; j >= 0; j--)
				{
					if (indices.contains(j))
					{
						leftNeighbourNodesIndices.add(j);
					}
					else
					{
						//The current index does not correspond to a neighbour of the existing node: break
						break;
					}
				}

				//Moche
				for (int j = indexOfExistingNode + 1; j < Integer.MAX_VALUE; j++)
				{
					if (indices.contains(j))
					{
						rightNeighbourNodesIndices.add(j);
					}
					else
					{
						//The current index does not correspond to a neighbour of the existing node: break
						break;
					}
				}

				final ArrayList<Integer> nodesToMerge;

				if (leftNeighbourNodesIndices.size() == rightNeighbourNodesIndices.size())
				{
					//Choose one: left
					nodesToMerge = leftNeighbourNodesIndices;
				}
				else if (leftNeighbourNodesIndices.size() > rightNeighbourNodesIndices.size())
				{
					nodesToMerge = leftNeighbourNodesIndices;
				}
				else
				{
					nodesToMerge = rightNeighbourNodesIndices;
				}

				if (!nodesToMerge.isEmpty())
				{
					mergeable = true;

					//If some nodes were found to be mergeable, merge them
					final ArrayList<AbstractSyntaxNode> nodesToParallelise = new ArrayList<>();

					for (int index : nodesToMerge)
					{
						nodesToParallelise.add(nodeToVerifyInMainTree.predecessor().successors().get(index));
					}

					nodeToVerifyInMainTree.predecessor().successors().removeIf(mergeableNodes::contains);

					final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
					final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);
					nodeToVerifyInMainTree.predecessor().replaceSuccessor(nodeToVerifyInMainTree, parallelNode);
					parallelNode.setPredecessor(nodeToVerifyInMainTree.predecessor());
					parallelNode.addSuccessor(sequenceNode);
					sequenceNode.setPredecessor(parallelNode);
					parallelNode.addSuccessor(nodeToVerifyInMainTree);
					nodeToVerifyInMainTree.setPredecessor(parallelNode);

					for (AbstractSyntaxNode mergeableNode : nodesToParallelise)
					{
						sequenceNode.addSuccessor(mergeableNode);
						mergeableNode.setPredecessor(sequenceNode);
					}

					//Normally, this should not have created issues in the tree
					if (!ASTUtils.getProblematicTrees(tree, dependencies).isEmpty()) throw new IllegalStateException();
				}
			}
		}

		if (!mergeable)
		{
			//We could not merge our node with the principal nodes --> try with subnodes
			ASTSequenceReducerV2.mergeSubNodesV2(eligibleNodes, nodeToVerifyInMainTree, dependencies, tree);
		}
	}

	private static boolean mergeSubNodes(final Collection<AbstractSyntaxNode> nodesToVerify,
										 final AbstractSyntaxNode nodeToVerify,
										 final Collection<AbstractSyntaxTree> constraints,
										 final AbstractSyntaxTree mainTree)
	{
		final ArrayList<AbstractSyntaxNode> eligibleParents = new ArrayList<>();

		for (AbstractSyntaxNode child : nodesToVerify)
		{
			if (!child.equals(nodeToVerify))
			{
				if (child.type() != AbstractType.XOR)
				{
					eligibleParents.add(child);
				}
			}
		}

		boolean merged = false;

		for (AbstractSyntaxNode eligibleParent : eligibleParents)
		{
			final ArrayList<AbstractSyntaxNode> mergeableNodes = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> nonMergeableNodes = new ArrayList<>();

			for (AbstractSyntaxNode child : eligibleParent.successors())
			{
				final ArrayList<AbstractSyntaxNode> leftTasks = new ArrayList<>();
				final ArrayList<AbstractSyntaxNode> rightTasks = new ArrayList<>();

				ASTUtils.retrieveAllTasksFrom(child, leftTasks);
				ASTUtils.retrieveAllTasksFrom(nodeToVerify, rightTasks);

				boolean nodeCanBeMerged = true;

				for (AbstractSyntaxNode leftTask : leftTasks)
				{
					for (AbstractSyntaxNode rightTask : rightTasks)
					{
						final String dummyHash1 = leftTask.label() + "<" + rightTask.label();
						final String dummyHash2 = rightTask.label() + "<" + leftTask.label();

						for (AbstractSyntaxTree currentDependency : constraints)
						{
							if (currentDependency.hash().equals(dummyHash1)
								|| currentDependency.hash().equals(dummyHash2))
							{
								//This dependency exists and must thus be preserved.
								//Consequently, the current eligible node can not be merged with the node to verify
								nonMergeableNodes.add(child);
								nodeCanBeMerged = false;
								break;
							}
						}

						if (!nodeCanBeMerged) break;
					}

					if (!nodeCanBeMerged) break;
				}

				if (nodeCanBeMerged)
				{
					mergeableNodes.add(child);
				}
			}

			if (!mergeableNodes.isEmpty())
			{
				if (eligibleParent.type() == AbstractType.PAR)
				{
					eligibleParent.removeSuccessors();
					final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);
					final AbstractSyntaxNode subParallelNode = new AbstractSyntaxNode(AbstractType.PAR);

					for (AbstractSyntaxNode mergeableNode : mergeableNodes)
					{
						eligibleParent.addSuccessor(mergeableNode);
						mergeableNode.setPredecessor(eligibleParent);
					}

					eligibleParent.addSuccessor(sequenceNode);
					sequenceNode.setPredecessor(eligibleParent);

					for (AbstractSyntaxNode nonMergeableNode : nonMergeableNodes)
					{
						subParallelNode.addSuccessor(nonMergeableNode);
						nonMergeableNode.setPredecessor(subParallelNode);
					}

					sequenceNode.addSuccessor(subParallelNode);
					subParallelNode.setPredecessor(sequenceNode);

					sequenceNode.addSuccessor(nodeToVerify);
					nodeToVerify.setPredecessor(sequenceNode);

					merged = true;
					break;
				}
				else if (eligibleParent.type() == AbstractType.SEQ)
				{
					if (mergeableNodes.size() == 1)
					{
						final AbstractSyntaxNode mergeableNode = mergeableNodes.get(0);
						final int mergeableNodeIndex = eligibleParent.successors().indexOf(mergeableNode);

						if (mergeableNodeIndex == -1) throw new IllegalStateException();

						if (mergeableNodeIndex == eligibleParent.successors().size() - 1)
						{
							//Mergeable node is the last node of the sequence --> we can merge directly
							eligibleParent.removeSuccessor(mergeableNode);

							final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
							parallelNode.addSuccessor(mergeableNode);
							mergeableNode.setPredecessor(parallelNode);
							parallelNode.addSuccessor(nodeToVerify);
							nodeToVerify.setPredecessor(parallelNode);

							eligibleParent.addSuccessor(parallelNode);
							parallelNode.setPredecessor(eligibleParent);

							merged = true;
							break;
						}
						else
						{
							//Mergeable node is not the last node of the sequence, thus we should not be able to merge
							//Just to be sure, we try to move the mergeable node to the end of the sequence and merge
							//If it does not create any issue, we leave the mergeable node here. Otherwise, we don't do anything
							final AbstractSyntaxTree copiedTree = mainTree.copy();
							final AbstractSyntaxNode nodeToVerifyCopy = nodeToVerify.deepCopy();
							final AbstractSyntaxNode mergeableNodeInCopiedTree = copiedTree.findNodeOfId(mergeableNode.id());
							final AbstractSyntaxNode eligibleParentInCopiedTree = copiedTree.findNodeOfId(eligibleParent.id());
							eligibleParentInCopiedTree.removeSuccessor(mergeableNode);

							final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
							parallelNode.addSuccessor(mergeableNodeInCopiedTree);
							mergeableNodeInCopiedTree.setPredecessor(parallelNode);
							parallelNode.addSuccessor(nodeToVerifyCopy);
							nodeToVerifyCopy.setPredecessor(parallelNode);

							eligibleParentInCopiedTree.addSuccessor(parallelNode);
							parallelNode.setPredecessor(eligibleParentInCopiedTree);

							if (ASTUtils.getProblematicTrees(copiedTree, constraints).isEmpty())
							{
								//Moving the mergeable node did not create any issue --> move it in the real tree
								eligibleParent.removeSuccessor(mergeableNode);

								final AbstractSyntaxNode realParallelNode = new AbstractSyntaxNode(AbstractType.PAR);
								realParallelNode.addSuccessor(mergeableNode);
								mergeableNode.setPredecessor(realParallelNode);
								realParallelNode.addSuccessor(nodeToVerify);
								nodeToVerify.setPredecessor(realParallelNode);

								eligibleParent.addSuccessor(realParallelNode);
								realParallelNode.setPredecessor(eligibleParent);

								merged = true;
								break;
							}
						}
					}
					else
					{
						//Several mergeable nodes from a sequence
						final ArrayList<AbstractSyntaxNode> eligibleParentLastChildren = new ArrayList<>();

						//Retrieve the mergeableNodes.size() last successors of the eligible parent
						for (int i = eligibleParent.successors().size() - mergeableNodes.size(); i < eligibleParent.successors().size(); i++)
						{
							eligibleParentLastChildren.add(eligibleParent.successors().get(i));
						}

						//Check whether they correspond to the mergeable nodes
						boolean mergeableNodesAreLastNodes = true;

						for (int i = 0; i < mergeableNodes.size(); i++)
						{
							final AbstractSyntaxNode mergeableNode = mergeableNodes.get(i);
							final AbstractSyntaxNode eligibleParentSuccessor = eligibleParentLastChildren.get(i);

							if (!mergeableNode.equals(eligibleParentSuccessor))
							{
								mergeableNodesAreLastNodes = false;
								break;
							}
						}

						if (mergeableNodesAreLastNodes)
						{
							//All the mergeable nodes are the last nodes of the sequence --> good case
							eligibleParent.removeSuccessors();

							for (AbstractSyntaxNode nonMergeableNode : nonMergeableNodes)
							{
								eligibleParent.addSuccessor(nonMergeableNode);
								nonMergeableNode.setPredecessor(eligibleParent);
							}

							final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
							final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);

							eligibleParent.addSuccessor(parallelNode);
							parallelNode.setPredecessor(eligibleParent);

							parallelNode.addSuccessor(sequenceNode);
							sequenceNode.setPredecessor(parallelNode);
							parallelNode.addSuccessor(nodeToVerify);
							nodeToVerify.setPredecessor(parallelNode);

							for (AbstractSyntaxNode mergeableNode : mergeableNodes)
							{
								sequenceNode.addSuccessor(mergeableNode);
								mergeableNode.setPredecessor(sequenceNode);
							}

							merged = true;
							break;
						}
						else
						{
							//Some mergeable nodes are not the last nodes of the sequence --> bad case
							//Just to be sure, we try to move the mergeable nodes to the end of the sequence and merge
							//If it does not create any issue, we leave the mergeable nodes here. Otherwise, we don't do anything
							final AbstractSyntaxTree copiedTree = mainTree.copy();
							final AbstractSyntaxNode eligibleParentInCopiedTree = copiedTree.findNodeOfId(eligibleParent.id());

							eligibleParentInCopiedTree.removeSuccessors();

							for (AbstractSyntaxNode nonMergeableNode : nonMergeableNodes)
							{
								final AbstractSyntaxNode nonMergeableNodeCopy = nonMergeableNode.deepCopy();
								eligibleParentInCopiedTree.addSuccessor(nonMergeableNodeCopy);
								nonMergeableNodeCopy.setPredecessor(eligibleParentInCopiedTree);
							}

							final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
							final AbstractSyntaxNode sequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);

							eligibleParentInCopiedTree.addSuccessor(parallelNode);
							parallelNode.setPredecessor(eligibleParentInCopiedTree);

							parallelNode.addSuccessor(sequenceNode);
							sequenceNode.setPredecessor(parallelNode);

							final AbstractSyntaxNode nodeToVerifyCopy = nodeToVerify.deepCopy();

							parallelNode.addSuccessor(nodeToVerifyCopy);
							nodeToVerifyCopy.setPredecessor(parallelNode);

							for (AbstractSyntaxNode mergeableNode : mergeableNodes)
							{
								final AbstractSyntaxNode mergeableNodeCopy = mergeableNode.deepCopy();
								sequenceNode.addSuccessor(mergeableNodeCopy);
								mergeableNodeCopy.setPredecessor(sequenceNode);
							}

							if (ASTUtils.getProblematicTrees(copiedTree, constraints).isEmpty())
							{
								//Moving the mergeable node did not create any issue --> move it in the real tree
								eligibleParent.removeSuccessors();

								for (AbstractSyntaxNode nonMergeableNode : nonMergeableNodes)
								{
									eligibleParent.addSuccessor(nonMergeableNode);
									nonMergeableNode.setPredecessor(eligibleParent);
								}

								final AbstractSyntaxNode realParallelNode = new AbstractSyntaxNode(AbstractType.PAR);
								final AbstractSyntaxNode realSequenceNode = new AbstractSyntaxNode(AbstractType.SEQ);

								eligibleParent.addSuccessor(realParallelNode);
								realParallelNode.setPredecessor(eligibleParent);

								realParallelNode.addSuccessor(realSequenceNode);
								realSequenceNode.setPredecessor(realParallelNode);
								realParallelNode.addSuccessor(nodeToVerify);
								nodeToVerify.setPredecessor(realParallelNode);

								for (AbstractSyntaxNode mergeableNode : mergeableNodes)
								{
									realSequenceNode.addSuccessor(mergeableNode);
									mergeableNode.setPredecessor(realSequenceNode);
								}

								merged = true;
								break;
							}
						}
					}
				}
				else
				{
					throw new IllegalStateException();
				}
			}
		}

		if (merged)
		{
			return true;
		}
		else
		{
			for (AbstractSyntaxNode eligibleParent : eligibleParents)
			{
				final boolean mergedInChild = ASTSequenceReducerV2.mergeSubNodes(eligibleParent.successors(), nodeToVerify, constraints, mainTree);

				if (mergedInChild)
				{
					return true;
				}
			}

			return false;
		}
	}

	private static boolean mergeSubNodesV2(final Collection<AbstractSyntaxNode> nodesToVerify,
										   final AbstractSyntaxNode nodeToVerify,
										   final Collection<AbstractSyntaxTree> constraints,
										   final AbstractSyntaxTree mainTree)
	{
		//System.out.println("Main tree before bug:\n\n" + mainTree);

		final ArrayList<AbstractSyntaxNode> eligibleParents = new ArrayList<>();

		for (AbstractSyntaxNode child : nodesToVerify)
		{
			if (!child.equals(nodeToVerify))
			{
				if (child.type() != AbstractType.XOR
					&& child.type() != AbstractType.LOOP)
				{
					eligibleParents.add(child);
				}
			}
		}

		boolean merged = false;

		for (AbstractSyntaxNode eligibleParent : eligibleParents)
		{
			final ArrayList<AbstractSyntaxNode> mergeableNodes = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> nonMergeableLeftNodes = new ArrayList<>();
			final ArrayList<AbstractSyntaxNode> nonMergeableRightNodes = new ArrayList<>();
			
			for (AbstractSyntaxNode child : eligibleParent.successors())
			{
				final ArrayList<AbstractSyntaxNode> childTasks = new ArrayList<>();
				final ArrayList<AbstractSyntaxNode> nodeToVerifyTasks = new ArrayList<>();

				ASTUtils.retrieveAllTasksFrom(child, childTasks);
				ASTUtils.retrieveAllTasksFrom(nodeToVerify, nodeToVerifyTasks);

				boolean nodeCanBeMerged = true;

				for (AbstractSyntaxNode childTask : childTasks)
				{
					for (AbstractSyntaxNode nodeToVerifyTask : nodeToVerifyTasks)
					{
						final String dummyHash1 = childTask.label() + "<" + nodeToVerifyTask.label();
						final String dummyHash2 = nodeToVerifyTask.label() + "<" + childTask.label();

						for (AbstractSyntaxTree currentDependency : constraints)
						{
							if (currentDependency.hash().equals(dummyHash1))
							{
								//This dependency exists and must thus be preserved.
								//Consequently, the current eligible node can not be merged with the node to verify
								nonMergeableLeftNodes.add(child);
								nodeCanBeMerged = false;
								break;
							}
							else if (currentDependency.hash().equals(dummyHash2))
							{
								//This dependency exists and must thus be preserved.
								//Consequently, the current eligible node can not be merged with the node to verify
								nonMergeableRightNodes.add(child);
								nodeCanBeMerged = false;
								break;
							}
						}

						if (!nodeCanBeMerged) break;
					}

					if (!nodeCanBeMerged) break;
				}

				if (nodeCanBeMerged)
				{
					mergeableNodes.add(child);
				}
			}

			if (!mergeableNodes.isEmpty())
			{
				if (eligibleParent.type() == AbstractType.PAR)
				{
					eligibleParent.successors().removeAll(nonMergeableRightNodes);
					eligibleParent.successors().removeAll(nonMergeableLeftNodes);
					eligibleParent.successors().remove(nodeToVerify); //Useless

					final AbstractSyntaxNode sequence = new AbstractSyntaxNode(AbstractType.SEQ);
					eligibleParent.addSuccessor(sequence);
					sequence.setPredecessor(eligibleParent);

					final AbstractSyntaxNode leftParent;
					final AbstractSyntaxNode rightParent;

					if (nonMergeableLeftNodes.size() < 2)
					{
						leftParent = sequence;
					}
					else
					{
						leftParent = new AbstractSyntaxNode(AbstractType.PAR);
						sequence.addSuccessor(leftParent);
						leftParent.setPredecessor(sequence);
					}

					for (AbstractSyntaxNode leftNode : nonMergeableLeftNodes)
					{
						leftParent.addSuccessor(leftNode);
						leftNode.setPredecessor(leftParent);
					}

					sequence.addSuccessor(nodeToVerify);
					nodeToVerify.setPredecessor(sequence);

					if (nonMergeableRightNodes.size() < 2)
					{
						rightParent = sequence;
					}
					else
					{
						rightParent = new AbstractSyntaxNode(AbstractType.PAR);
						sequence.addSuccessor(rightParent);
						rightParent.setPredecessor(sequence);
					}

					for (AbstractSyntaxNode rightNode : nonMergeableRightNodes)
					{
						rightParent.addSuccessor(rightNode);
						rightNode.setPredecessor(rightParent);
					}

					merged = true;
				}
				else if (eligibleParent.type() == AbstractType.SEQ)
				{
					if (mergeableNodes.size() == 1)
					{
						//System.out.println("Tree before bug:\n\n" + mainTree);

						final AbstractSyntaxTree mainTreeCopy = mainTree.copy();
						final AbstractSyntaxNode mergeableNodeCopy = mainTree.findNodeOfId(mergeableNodes.get(0).id());
						final AbstractSyntaxNode sequenceCopy = mainTreeCopy.findNodeOfId(eligibleParent.id());
						final AbstractSyntaxNode parallelNodeCopy = new AbstractSyntaxNode(AbstractType.PAR);
						final AbstractSyntaxNode nodeToVerifyCopy = mainTreeCopy.findNodeOfId(nodeToVerify.id()) == null ? nodeToVerify.deepCopy() : mainTreeCopy.findNodeOfId(nodeToVerify.id());

						parallelNodeCopy.addSuccessor(mergeableNodeCopy);
						mergeableNodeCopy.setPredecessor(parallelNodeCopy);
						parallelNodeCopy.addSuccessor(nodeToVerifyCopy);
						nodeToVerifyCopy.setPredecessor(parallelNodeCopy);
						sequenceCopy.replaceSuccessor(mergeableNodeCopy, parallelNodeCopy);
						parallelNodeCopy.setPredecessor(sequenceCopy);
						sequenceCopy.removeSuccessor(nodeToVerifyCopy);

						//System.out.println("Problematic tree copy:\n\n" + mainTreeCopy.toString());

						if (ASTUtils.getProblematicTrees(mainTreeCopy, constraints).isEmpty())
						{
							//Merge did not create issues --> apply it on the real tree
							merged = true;
							final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);

							parallelNode.addSuccessor(mergeableNodes.get(0));
							mergeableNodes.get(0).setPredecessor(parallelNode);
							parallelNode.addSuccessor(nodeToVerify);
							nodeToVerify.setPredecessor(parallelNode);
							eligibleParent.replaceSuccessor(mergeableNodes.get(0), parallelNode);
							parallelNode.setPredecessor(eligibleParent);
							eligibleParent.removeSuccessor(nodeToVerify);
						}
					}
					else
					{
						final ArrayList<Integer> mergeableNodesIndices = new ArrayList<>();

						for (AbstractSyntaxNode mergeableNode : mergeableNodes)
						{
							mergeableNodesIndices.add(eligibleParent.successors().indexOf(mergeableNode));
						}

						if (mergeableNodesIndices.contains(-1)) throw new IllegalStateException();

						if (Utils.integersAreConsecutive(mergeableNodesIndices))
						{
							//We try to merge everything
							final AbstractSyntaxNode subSequenceCopy = new AbstractSyntaxNode(AbstractType.SEQ);
							final AbstractSyntaxTree mainTreeCopy = mainTree.copy();
							final ArrayList<AbstractSyntaxNode> mergeableNodesCopy = new ArrayList<>();
							final AbstractSyntaxNode nodeToVerifyCopy = mainTreeCopy.findNodeOfId(nodeToVerify.id()) == null ? nodeToVerify.deepCopy() : mainTreeCopy.findNodeOfId(nodeToVerify.id());
							final AbstractSyntaxNode parentCopy = mainTreeCopy.findNodeOfId(eligibleParent.id());

							for (AbstractSyntaxNode mergeableNode : mergeableNodes)
							{
								mergeableNodesCopy.add(mainTree.findNodeOfId(mergeableNode.id()));
							}

							final AbstractSyntaxNode firstMergeableNodeCopy = mergeableNodesCopy.remove(0);

							parentCopy.removeSuccessors(mergeableNodesCopy);

							subSequenceCopy.addSuccessor(firstMergeableNodeCopy);
							firstMergeableNodeCopy.setPredecessor(subSequenceCopy);

							for (AbstractSyntaxNode mergeableNodeCopy : mergeableNodesCopy)
							{
								subSequenceCopy.addSuccessor(mergeableNodeCopy);
								mergeableNodeCopy.setPredecessor(subSequenceCopy);
							}

							final AbstractSyntaxNode parallelNodeCopy = new AbstractSyntaxNode(AbstractType.PAR);
							parallelNodeCopy.addSuccessor(subSequenceCopy);
							subSequenceCopy.setPredecessor(parallelNodeCopy);
							parallelNodeCopy.addSuccessor(nodeToVerifyCopy);
							nodeToVerifyCopy.setPredecessor(parallelNodeCopy);

							parentCopy.replaceSuccessor(firstMergeableNodeCopy, parallelNodeCopy);
							parallelNodeCopy.setPredecessor(parentCopy);

							if (ASTUtils.getProblematicTrees(mainTreeCopy, constraints).isEmpty())
							{
								//Merge did not create issues
								merged = true;

								final AbstractSyntaxNode subSequence = new AbstractSyntaxNode(AbstractType.SEQ);
								final AbstractSyntaxNode firstMergeableNode = mergeableNodes.remove(0);

								eligibleParent.removeSuccessors(mergeableNodes);

								subSequence.addSuccessor(firstMergeableNode);
								firstMergeableNode.setPredecessor(subSequence);

								for (AbstractSyntaxNode mergeableNode : mergeableNodes)
								{
									subSequence.addSuccessor(mergeableNode);
									mergeableNode.setPredecessor(subSequence);
								}

								final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
								parallelNode.addSuccessor(subSequence);
								subSequence.setPredecessor(parallelNode);
								parallelNode.addSuccessor(nodeToVerify);
								nodeToVerify.setPredecessor(parallelNode);

								eligibleParent.replaceSuccessor(firstMergeableNode, parallelNode);
								parallelNode.setPredecessor(eligibleParent);
							}
						}
						else
						{
							//Mergeable nodes are not consecutive --> we try to make them consecutive and merge
							//If the result is bad (it should be!), we try to merge with the biggest subgroups of nodes
							final AbstractSyntaxTree mainTreeCopy = mainTree.copy();
							final AbstractSyntaxNode eligibleParentCopy = mainTreeCopy.findNodeOfId(eligibleParent.id());
							final AbstractSyntaxNode nodeToVerifyCopy = mainTreeCopy.findNodeOfId(nodeToVerify.id()) == null ? nodeToVerify.deepCopy() : mainTreeCopy.findNodeOfId(nodeToVerify.id());
							final ArrayList<AbstractSyntaxNode> mergeableNodesCopy = new ArrayList<>();

							for (AbstractSyntaxNode mergeableNode : mergeableNodes)
							{
								mergeableNodesCopy.add(mainTreeCopy.findNodeOfId(mergeableNode.id()));
							}

							final AbstractSyntaxNode firstMergeableNodeCopy = mergeableNodesCopy.remove(0);
							eligibleParentCopy.removeSuccessors(mergeableNodesCopy);

							final AbstractSyntaxNode subSequenceCopy = new AbstractSyntaxNode(AbstractType.SEQ);
							subSequenceCopy.addSuccessor(firstMergeableNodeCopy);
							firstMergeableNodeCopy.setPredecessor(subSequenceCopy);

							for (AbstractSyntaxNode mergeableNodeCopy : mergeableNodesCopy)
							{
								subSequenceCopy.addSuccessor(mergeableNodeCopy);
								mergeableNodeCopy.setPredecessor(subSequenceCopy);
							}

							final AbstractSyntaxNode parallelNodeCopy = new AbstractSyntaxNode(AbstractType.PAR);
							parallelNodeCopy.addSuccessor(subSequenceCopy);
							subSequenceCopy.setPredecessor(parallelNodeCopy);
							parallelNodeCopy.addSuccessor(nodeToVerifyCopy);
							nodeToVerifyCopy.setPredecessor(parallelNodeCopy);

							eligibleParentCopy.replaceSuccessor(firstMergeableNodeCopy, parallelNodeCopy);
							parallelNodeCopy.setPredecessor(eligibleParentCopy);

							if (ASTUtils.getProblematicTrees(mainTreeCopy, constraints).isEmpty())
							{
								merged = true;

								//That's a miracle!!!!!! In this case, merge :-D
								final AbstractSyntaxNode firstMergeableNode = mergeableNodes.remove(0);
								eligibleParent.removeSuccessors(mergeableNodes);

								final AbstractSyntaxNode subSequence = new AbstractSyntaxNode(AbstractType.SEQ);
								subSequence.addSuccessor(firstMergeableNode);
								firstMergeableNode.setPredecessor(subSequence);

								for (AbstractSyntaxNode mergeableNode : mergeableNodes)
								{
									subSequence.addSuccessor(mergeableNode);
									mergeableNode.setPredecessor(subSequence);
								}

								final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
								parallelNode.addSuccessor(subSequence);
								subSequence.setPredecessor(parallelNode);
								parallelNode.addSuccessor(nodeToVerify);
								nodeToVerify.setPredecessor(parallelNode);

								eligibleParent.replaceSuccessor(firstMergeableNode, parallelNode);
								parallelNode.setPredecessor(eligibleParent);
							}
							else
							{
								//Case that should happen most of the time: try to merge with the biggest connected subsets of mergeable nodes
								final ArrayList<ArrayList<AbstractSyntaxNode>> consecutiveNodesList = new ArrayList<>();
								final int maxIndex = Utils.max(mergeableNodesIndices);
								ArrayList<AbstractSyntaxNode> currentList = new ArrayList<>();
								consecutiveNodesList.add(currentList);

								for (int i = 0; i <= maxIndex; i++)
								{
									if (mergeableNodesIndices.contains(i))
									{
										currentList.add(eligibleParent.successors().get(i));
									}
									else
									{
										if (!currentList.isEmpty())
										{
											currentList = new ArrayList<>();
											consecutiveNodesList.add(currentList);
										}
									}
								}

								consecutiveNodesList.sort((o1, o2) -> o2.size() - o1.size());

								for (ArrayList<AbstractSyntaxNode> consecutiveNodes : consecutiveNodesList)
								{
									final AbstractSyntaxTree mainTreeCopy2 = mainTree.copy();
									final AbstractSyntaxNode nodeToVerifyCopy2 = mainTreeCopy2.findNodeOfId(nodeToVerify.id()) == null ? nodeToVerify.deepCopy() : mainTreeCopy.findNodeOfId(nodeToVerify.id());
									final AbstractSyntaxNode eligibleParentCopy2 = mainTreeCopy2.findNodeOfId(eligibleParent.id());
									final ArrayList<AbstractSyntaxNode> consecutiveNodesCopy = new ArrayList<>();

									for (AbstractSyntaxNode consecutiveNode : consecutiveNodes)
									{
										consecutiveNodesCopy.add(mainTreeCopy2.findNodeOfId(consecutiveNode.id()));
									}

									final AbstractSyntaxNode firstConsecutiveNodeCopy = consecutiveNodesCopy.remove(0);

									if (consecutiveNodesCopy.isEmpty())
									{
										final AbstractSyntaxNode parallelNodeCopy2 = new AbstractSyntaxNode(AbstractType.PAR);
										parallelNodeCopy2.addSuccessor(firstConsecutiveNodeCopy);
										firstConsecutiveNodeCopy.setPredecessor(parallelNodeCopy2);
										parallelNodeCopy2.addSuccessor(nodeToVerifyCopy2);
										nodeToVerifyCopy2.setPredecessor(parallelNodeCopy2);
										eligibleParentCopy2.replaceSuccessor(firstConsecutiveNodeCopy, parallelNodeCopy2);
										parallelNodeCopy2.setPredecessor(eligibleParentCopy2);
									}
									else
									{
										eligibleParentCopy2.removeSuccessors(consecutiveNodesCopy);
										final AbstractSyntaxNode subSequenceCopy2 = new AbstractSyntaxNode(AbstractType.SEQ);
										subSequenceCopy2.addSuccessor(firstConsecutiveNodeCopy);
										firstConsecutiveNodeCopy.setPredecessor(subSequenceCopy2);

										for (AbstractSyntaxNode consecutiveNodeCopy : consecutiveNodesCopy)
										{
											subSequenceCopy2.addSuccessor(consecutiveNodeCopy);
											consecutiveNodeCopy.setPredecessor(subSequenceCopy2);
										}

										final AbstractSyntaxNode parallelNodeCopy2 = new AbstractSyntaxNode(AbstractType.PAR);
										parallelNodeCopy2.addSuccessor(subSequenceCopy2);
										subSequenceCopy2.setPredecessor(parallelNodeCopy2);
										parallelNodeCopy2.addSuccessor(nodeToVerifyCopy2);
										nodeToVerifyCopy2.setPredecessor(parallelNodeCopy2);
										eligibleParentCopy2.replaceSuccessor(firstConsecutiveNodeCopy, parallelNodeCopy2);
										parallelNodeCopy2.setPredecessor(eligibleParentCopy2);
									}

									if (ASTUtils.getProblematicTrees(mainTreeCopy2, constraints).isEmpty())
									{
										//We managed to merge with the biggest set of consecutive nodes possible, so we stop
										final AbstractSyntaxNode firstConsecutiveNode = consecutiveNodes.remove(0);

										if (consecutiveNodes.isEmpty())
										{
											final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
											parallelNode.addSuccessor(firstConsecutiveNode);
											firstConsecutiveNode.setPredecessor(parallelNode);
											parallelNode.addSuccessor(nodeToVerify);
											nodeToVerify.setPredecessor(parallelNode);
											eligibleParent.replaceSuccessor(firstConsecutiveNode, parallelNode);
											parallelNode.setPredecessor(eligibleParent);
										}
										else
										{
											eligibleParent.removeSuccessors(consecutiveNodes);
											final AbstractSyntaxNode subSequence = new AbstractSyntaxNode(AbstractType.SEQ);
											subSequence.addSuccessor(firstConsecutiveNode);
											firstConsecutiveNode.setPredecessor(subSequence);

											for (AbstractSyntaxNode consecutiveNode : consecutiveNodes)
											{
												subSequence.addSuccessor(consecutiveNode);
												consecutiveNode.setPredecessor(subSequence);
											}

											final AbstractSyntaxNode parallelNode = new AbstractSyntaxNode(AbstractType.PAR);
											parallelNode.addSuccessor(subSequence);
											subSequence.setPredecessor(parallelNode);
											parallelNode.addSuccessor(nodeToVerify);
											nodeToVerify.setPredecessor(parallelNode);
											eligibleParent.replaceSuccessor(firstConsecutiveNode, parallelNode);
											parallelNode.setPredecessor(eligibleParent);
										}

										merged = true;
										break;
									}
								}
							}
						}
					}
				}
				else
				{
					throw new IllegalStateException();
				}
			}
		}

		/*
		 * The recursive was false because it was removing some existing constraints of the specification.
		 * Indeed, putting a task in sub-constructs of a parallel node means that some child of the parallel
		 * nodes were constrained to the task, but these constraints were removed by adding them to the sub-construct,
		 * as the sub-construct is by definition not constrained to the other children of the parallel node.
		 */

		/*if (merged)
		{
			return true;
		}
		else
		{
			for (AbstractSyntaxNode eligibleParent : eligibleParents)
			{
				final boolean mergedInChild = ASTSequenceReducerV2.mergeSubNodesV2(eligibleParent.successors(), nodeToVerify, constraints, mainTree);

				if (mergedInChild)
				{
					return true;
				}
			}

			return false;
		}*/

		return merged;
	}
}
