package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.ast_management.constants.MergeStatus;
import chat_gpt.exceptions.ContradictoryValuesException;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.MyOwnLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ASTMerger
{
	private final List<AbstractSyntaxTree> immutableTreesToMerge;
	private final ArrayList<AbstractSyntaxTree> treesToMerge;
	private final AbstractSyntaxTree mainTree;
	private final ArrayList<AbstractSyntaxTree> addedConstraints;
	private final boolean buildChoices;
	private final ArrayList<ASTLoop> loops;
	private AbstractSyntaxTree mergedTree;

	public ASTMerger(final AbstractSyntaxTree mainTree,
					 final List<AbstractSyntaxTree> constraints,
					 final ArrayList<AbstractSyntaxTree> treesToMerge,
					 final ArrayList<ASTLoop> loops,
					 final boolean buildChoices)
	{
		this.treesToMerge = new ArrayList<>(treesToMerge);
		this.immutableTreesToMerge = constraints;
		this.mainTree = mainTree;
		this.addedConstraints = new ArrayList<>();
		this.loops = loops == null ? new ArrayList<>() : new ArrayList<>(loops);
		this.buildChoices = buildChoices;
	}

	//Public methods

	public AbstractSyntaxTree merge() throws ExpectedException
	{
		//Merge
		this.mergeFromSets(this.treesToMerge);

		//Return the merged tree
		return this.mergedTree = this.mainTree;
	}

	public AbstractSyntaxTree addMissingLoops()
	{
		//Add missing loops
		this.loops.removeIf(ASTLoop::wasAddedToTree);

		if (!this.loops.isEmpty())
		{
			final AbstractSyntaxNode parNode = new AbstractSyntaxNode(AbstractType.PAR);
			parNode.addSuccessor(this.mergedTree.root());
			this.mergedTree.root().setPredecessor(parNode);
			this.mergedTree = new AbstractSyntaxTree(parNode);

			for (ASTLoop loop : this.loops)
			{
				parNode.addSuccessor(loop.loop().root());
				loop.loop().root().setPredecessor(parNode);
			}
		}

		return this.mergedTree;
	}

	public AbstractSyntaxTree mergedTree()
	{
		return this.mergedTree;
	}

	public ArrayList<AbstractSyntaxTree> addedConstraints()
	{
		return this.addedConstraints;
	}

	//Private methods

	private ArrayList<AbstractSyntaxTree> merge(final AbstractSyntaxTree mainTree,
												final AbstractSyntaxTree treeToMerge) throws ExpectedException
	{
		//System.out.println("--------------------------------------------------------------------------------");
		//System.out.println("Tree before merge:\n\n" + mainTree);
		//System.out.println("Tree to merge:\n\n" + treeToMerge);

		final ArrayList<AbstractSyntaxTree> nextTrees = new ArrayList<>();

		//MyOwnLogger.append("Tree to merge:\n\n" + treeToMerge.toString());
		//MyOwnLogger.append("Main tree:\n\n" + mainTree.toString());
		//MyOwnLogger.append("Main tree is valid: " + (mainTree.root().predecessor() == null));

		final AbstractType type = treeToMerge.root().type();
		final String leftNodeLabel = treeToMerge.root().successors().get(0).label();
		final String rightNodeLabel = treeToMerge.root().successors().get(1).label();

		final AbstractSyntaxTree treeToMergeCopy = treeToMerge.copy();
		AbstractSyntaxNode leftNodeInMainTree = mainTree.findNodeOfLabel(leftNodeLabel);
		AbstractSyntaxNode rightNodeInMainTree = mainTree.findNodeOfLabel(rightNodeLabel);
		AbstractSyntaxNode leftNodeInTreeToMerge = treeToMerge.root().successors().get(0).deepCopy();
		AbstractSyntaxNode rightNodeInTreeToMerge = treeToMerge.root().successors().get(1).deepCopy();

		final AbstractSyntaxNode nodeToAssertLocationOf;

		if (type == AbstractType.PAR)
		{
			if (leftNodeInMainTree == null
				|| rightNodeInMainTree == null)
			{
				final AbstractSyntaxNode newRoot = new AbstractSyntaxNode(AbstractType.PAR);
				newRoot.addSuccessor(mainTree.root());
				mainTree.root().setPredecessor(newRoot);
				mainTree.setRoot(newRoot);

				if (leftNodeInMainTree == null)
				{
					newRoot.addSuccessor(leftNodeInTreeToMerge);
					leftNodeInTreeToMerge.setPredecessor(newRoot);
				}

				if (rightNodeInMainTree == null)
				{
					newRoot.addSuccessor(rightNodeInTreeToMerge);
					rightNodeInTreeToMerge.setPredecessor(newRoot);
				}
			}
			//else {} : the two nodes are already in the tree, and they are not constrained (parallel)

			nodeToAssertLocationOf = null;
		}
		else
		{
			if (leftNodeInMainTree == null
				&& rightNodeInMainTree == null)
			{
				//None of the nodes are in the tree, thus we can just put the tree to merge in parallel of the main tree
				final AbstractSyntaxNode newRoot = new AbstractSyntaxNode(AbstractType.PAR);
				newRoot.addSuccessor(mainTree.root());
				mainTree.root().setPredecessor(newRoot);
				newRoot.addSuccessor(treeToMergeCopy.root());
				treeToMergeCopy.root().setPredecessor(newRoot);
				mainTree.setRoot(newRoot);
				nodeToAssertLocationOf = null;
			}
			else if (leftNodeInMainTree == null)
			{
				//The right node is already in the tree
				this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, rightNodeInMainTree, rightNodeInTreeToMerge, rightNodeLabel);
				nodeToAssertLocationOf = treeToMerge.root().successors().get(0);
			}
			else if (rightNodeInMainTree == null)
			{
				//The left node is already in the tree
				this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, leftNodeInMainTree, leftNodeInTreeToMerge, leftNodeLabel);
				nodeToAssertLocationOf = treeToMerge.root().successors().get(1);
			}
			else
			{
				//Both nodes are already in the tree
				final AbstractSyntaxNode lessRestrictiveNodeOfLeftNode = this.getLessRestrictiveNodeOf(leftNodeInMainTree);
				final AbstractSyntaxNode lessRestrictiveNodeOfRightNode = this.getLessRestrictiveNodeOf(rightNodeInMainTree);

				if (lessRestrictiveNodeOfLeftNode == null
					&& lessRestrictiveNodeOfRightNode == null)
				{
					//None of the two nodes are constrained, thus they should be successors of the root
					if (!leftNodeInMainTree.predecessor().equals(mainTree.root())
						|| !rightNodeInMainTree.predecessor().equals(mainTree.root()))
					{
						throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
					}

					//We remove them from the successors of the root, and we branch the tree to merge instead
					mainTree.root().removeSuccessor(leftNodeInMainTree);
					mainTree.root().removeSuccessor(rightNodeInMainTree);
					mainTree.root().addSuccessor(treeToMergeCopy.root());
					treeToMergeCopy.root().setPredecessor(mainTree.root());

					if (mainTree.root().type() == AbstractType.PAR
						&& mainTree.root().successors().size() == 1)
					{
						final AbstractSyntaxNode newRoot = mainTree.root().successors().get(0);
						newRoot.resetPredecessor();
						mainTree.root().removeSuccessors();
						mainTree.setRoot(newRoot);
					}

					nodeToAssertLocationOf = null;
				}
				else if (lessRestrictiveNodeOfLeftNode == null)
				{
					//Right node is constrained
					if (!leftNodeInMainTree.predecessor().equals(mainTree.root())) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
					mainTree.root().removeSuccessor(leftNodeInMainTree);

					if (mainTree.root().type() == AbstractType.PAR
							&& mainTree.root().successors().size() == 1)
					{
						final AbstractSyntaxNode newRoot = mainTree.root().successors().get(0);
						newRoot.resetPredecessor();
						mainTree.root().removeSuccessors();
						mainTree.setRoot(newRoot);
					}

					//Left node has been removed from the tree so only right node is in the tree
					this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, rightNodeInMainTree, rightNodeInTreeToMerge, rightNodeLabel);
					nodeToAssertLocationOf = treeToMerge.root().successors().get(0);
				}
				else if (lessRestrictiveNodeOfRightNode == null)
				{
					//Left node is constrained
					if (!rightNodeInMainTree.predecessor().equals(mainTree.root())) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
					mainTree.root().removeSuccessor(rightNodeInMainTree);

					if (mainTree.root().type() == AbstractType.PAR
							&& mainTree.root().successors().size() == 1)
					{
						final AbstractSyntaxNode newRoot = mainTree.root().successors().get(0);
						newRoot.resetPredecessor();
						mainTree.root().removeSuccessors();
						mainTree.setRoot(newRoot);
					}

					//Right node has been removed from the tree so only left node is in the tree
					this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, leftNodeInMainTree, leftNodeInTreeToMerge, leftNodeLabel);
					nodeToAssertLocationOf = treeToMerge.root().successors().get(1);
				}
				else
				{
					//Both nodes are constrained
					final AbstractSyntaxNode commonAncestor = this.getLeastCommonAncestor(leftNodeInMainTree, rightNodeInMainTree);

					//If it's not a PAR, the constraint is either already satisfied ((<,<), (|,|)) or unsatisfiable ((<,|), (|,<))
					if (commonAncestor.type() != AbstractType.PAR) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
					
					final AbstractSyntaxNode lessRestrictiveNodeOfLeftNodeBeforeCommonAncestor = this.getLessRestrictiveNodeBefore(leftNodeInMainTree, commonAncestor);
					final AbstractSyntaxNode lessRestrictiveNodeOfRightNodeBeforeCommonAncestor = this.getLessRestrictiveNodeBefore(rightNodeInMainTree, commonAncestor);

					if (lessRestrictiveNodeOfLeftNodeBeforeCommonAncestor == null
						&& lessRestrictiveNodeOfRightNodeBeforeCommonAncestor == null)
					{
						//We remove them from the successors of the root, and we branch the tree to merge instead
						commonAncestor.removeSuccessor(leftNodeInMainTree);
						commonAncestor.removeSuccessor(rightNodeInMainTree);

						if (commonAncestor.successors().isEmpty())
						{
							//Parallel ancestor is not needed anymore
							commonAncestor.predecessor().replaceSuccessor(commonAncestor, treeToMergeCopy.root());
							treeToMergeCopy.root().setPredecessor(commonAncestor.predecessor());
						}
						else
						{
							commonAncestor.addSuccessor(treeToMergeCopy.root());
							treeToMergeCopy.root().setPredecessor(commonAncestor);
						}

						nodeToAssertLocationOf = null;
					}
					else if (lessRestrictiveNodeOfLeftNodeBeforeCommonAncestor == null)
					{
						//Right node is constrained
						commonAncestor.removeSuccessor(leftNodeInMainTree);
						leftNodeInMainTree.resetPredecessor();

						if (commonAncestor.successors().size() == 1)
						{
							commonAncestor.predecessor().replaceSuccessor(commonAncestor, commonAncestor.successors().get(0));
							commonAncestor.successors().get(0).setPredecessor(commonAncestor.predecessor());
						}

						//Left node has been removed from the tree so only right node is in the tree
						this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, rightNodeInMainTree, rightNodeInTreeToMerge, rightNodeLabel);
						nodeToAssertLocationOf = treeToMerge.root().successors().get(0);
					}
					else if (lessRestrictiveNodeOfRightNodeBeforeCommonAncestor == null)
					{
						//Left node is constrained
						commonAncestor.removeSuccessor(rightNodeInMainTree);
						rightNodeInMainTree.resetPredecessor();

						if (commonAncestor.successors().size() == 1)
						{
							commonAncestor.predecessor().replaceSuccessor(commonAncestor, commonAncestor.successors().get(0));
							commonAncestor.successors().get(0).setPredecessor(commonAncestor.predecessor());
						}

						//Right node has been removed from the tree so only left node is in the tree
						this.manageOneNodeAlreadyInTreeCase(mainTree, treeToMerge, treeToMergeCopy, leftNodeInMainTree, leftNodeInTreeToMerge, leftNodeLabel);
						nodeToAssertLocationOf = treeToMerge.root().successors().get(1);
					}
					else
					{
						nodeToAssertLocationOf = this.manageBothNodesAlreadyInTheTreeCaseV2(commonAncestor, treeToMerge, mainTree);
					}

					//if (!this.getProblematicTrees(mainTree).isEmpty()) throw new ContradictoryValuesException();
				}
			}
		}

		if (nodeToAssertLocationOf != null)
		{
			final ArrayList<AbstractSyntaxNode> successors;
			final AbstractSyntaxNode nodeToTryToMerge;
			final AbstractSyntaxNode nodeToAssertLocationOfInMainTree = mainTree.findNodeOfId(nodeToAssertLocationOf.id());

			if (nodeToAssertLocationOf.type() == nodeToAssertLocationOfInMainTree.predecessor().type())
			{
				//The node will be merged
				successors = new ArrayList<>(nodeToAssertLocationOfInMainTree.successors());

				for (AbstractSyntaxNode successor : successors)
				{
					if (successor.type() == nodeToAssertLocationOf.type()) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
				}
			}
			else
			{
				successors = null;
			}

			ASTReductor.reduce(mainTree);

			if (successors != null)
			{
				final AbstractSyntaxNode parent = successors.get(0).predecessor();
				final AbstractSyntaxNode successorForIndex = successors.remove(0);

				parent.removeSuccessors(successors);

				final AbstractSyntaxNode replacementNode = new AbstractSyntaxNode(parent.type());
				replacementNode.addSuccessor(successorForIndex);
				successorForIndex.setPredecessor(replacementNode);

				for (AbstractSyntaxNode successor : successors)
				{
					replacementNode.addSuccessor(successor);
					successor.setPredecessor(replacementNode);
				}

				parent.replaceSuccessor(successorForIndex, replacementNode);
				replacementNode.setPredecessor(parent);
				nodeToTryToMerge = replacementNode;
			}
			else
			{
				nodeToTryToMerge = nodeToAssertLocationOf;
			}

			if (this.buildChoices)
			{
				ASTChoiceReducerV2.releaseConstraints(mainTree, this.immutableTreesToMerge, treeToMerge, nodeToTryToMerge);
			}
			else
			{
				ASTSequenceReducerV2.releaseLocalConstraints(mainTree, this.immutableTreesToMerge, treeToMerge, nodeToTryToMerge);
			}
		}

		ASTReductor.reduce(mainTree);
		ASTSequenceReducerV2.releaseGlobalConstraintsV1(mainTree, this.immutableTreesToMerge);
		ASTReductor.reduce(mainTree);

		final ArrayList<AbstractSyntaxTree> remainingProblematicTrees = ASTUtils.getProblematicTrees(mainTree, this.immutableTreesToMerge);

		if (!remainingProblematicTrees.isEmpty())
		{
			MyOwnLogger.append(remainingProblematicTrees.size() + " trees could not be merged to the main tree:\n");
			MyOwnLogger.append(remainingProblematicTrees.get(0).toString());
			MyOwnLogger.append(mainTree.toString() );

			throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
		}

		//MyOwnLogger.append("Merged tree:\n\n" + mainTree.toString());
		//MyOwnLogger.append("Main tree is valid: " + (mainTree.root().predecessor() == null));

		if (mainTree.root().predecessor() != null)
		{
			while (mainTree.root().predecessor() != null)
			{
				mainTree.setRoot(mainTree.root().predecessor());
			}

			MyOwnLogger.append("Bad main tree:\n\n" + mainTree.toString());
			//MyOwnLogger.append(mainTree.root().predecessor().predecessor().predecessor() == null);
			throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
		}

		//System.out.println("Tree after merge:\n\n" + mainTree);

		return nextTrees;
	}

	private void manageBothNodesAlreadyInTheTreeCase(final AbstractSyntaxNode commonAncestor,
													 final AbstractSyntaxTree treeToMerge,
													 final String leftNodeLabel,
													 final String rightNodeLabel) throws ExpectedException
	{
		if (commonAncestor.successors().size() == 2)
		{
			//The parallel ancestor becomes of the needed type
			commonAncestor.switchType(treeToMerge.root().type());
		}
		else
		{
			AbstractSyntaxNode leftNodeAncestorChild = null;
			AbstractSyntaxNode rightNodeAncestorChild = null;
			final ArrayList<AbstractSyntaxNode> remainingSuccessors = new ArrayList<>();

			for (AbstractSyntaxNode child : commonAncestor.successors())
			{
				final AbstractSyntaxTree newTree = new AbstractSyntaxTree();
				newTree.setRoot(child);

				if (newTree.findNodeOfLabel(leftNodeLabel) != null)
				{
					leftNodeAncestorChild = child;
				}
				else if (newTree.findNodeOfLabel(rightNodeLabel) != null)
				{
					rightNodeAncestorChild = child;
				}
				else
				{
					remainingSuccessors.add(child);
				}
			}

			if (leftNodeAncestorChild == null
					|| rightNodeAncestorChild == null
					|| remainingSuccessors.isEmpty()) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

			commonAncestor.removeSuccessors();

			final AbstractSyntaxNode constrainingNode = new AbstractSyntaxNode(treeToMerge.root().type());
			commonAncestor.addSuccessor(constrainingNode);
			constrainingNode.setPredecessor(commonAncestor);

			constrainingNode.addSuccessor(leftNodeAncestorChild);
			leftNodeAncestorChild.setPredecessor(constrainingNode);

			constrainingNode.addSuccessor(rightNodeAncestorChild);
			rightNodeAncestorChild.setPredecessor(constrainingNode);

			for (AbstractSyntaxNode remainingSuccessor : remainingSuccessors)
			{
				commonAncestor.addSuccessor(remainingSuccessor);
				remainingSuccessor.setPredecessor(commonAncestor);
			}
		}
	}

	private AbstractSyntaxNode manageBothNodesAlreadyInTheTreeCaseV2(final AbstractSyntaxNode commonAncestor,
																	 final AbstractSyntaxTree treeToMerge,
																	 final AbstractSyntaxTree mainTree) throws ExpectedException
	{
		final AbstractSyntaxTree fullTreeToMerge = new AbstractSyntaxTree(new AbstractSyntaxNode(treeToMerge.root().type()));

		if (commonAncestor.successors().size() == 2)
		{
			final AbstractSyntaxNode leftSuccessor = commonAncestor.successors().get(0);
			final AbstractSyntaxNode rightSuccessor = commonAncestor.successors().get(1);
			final AbstractSyntaxNode successorLeft;
			final AbstractSyntaxNode successorRemoved;

			if (leftSuccessor.findNodeOfLabel(treeToMerge.root().successors().get(0).label()) != null)
			{
				successorLeft = leftSuccessor;
				successorRemoved = rightSuccessor;
			}
			else if (leftSuccessor.findNodeOfLabel(treeToMerge.root().successors().get(1).label()) != null)
			{
				successorLeft = rightSuccessor;
				successorRemoved = leftSuccessor;
			}
			else
			{
				throw new IllegalStateException();
			}

			final AbstractSyntaxNode leftNodeCopy = treeToMerge.root().successors().get(0).deepCopy();
			final AbstractSyntaxNode rightNodeCopy = successorRemoved.deepCopy();
			fullTreeToMerge.root().addSuccessor(leftNodeCopy);
			leftNodeCopy.setPredecessor(fullTreeToMerge.root());
			fullTreeToMerge.root().addSuccessor(rightNodeCopy);
			rightNodeCopy.setPredecessor(fullTreeToMerge.root());

			final AbstractSyntaxNode commonAncestorParent = commonAncestor.predecessor();

			commonAncestor.removeSuccessors();
			commonAncestor.resetPredecessor();

			if (commonAncestorParent == null)
			{
				mainTree.setRoot(successorLeft);
				successorLeft.resetPredecessor();
			}
			else
			{
				commonAncestorParent.replaceSuccessor(commonAncestor, successorLeft);
				successorLeft.setPredecessor(commonAncestorParent);
			}
		}
		else
		{
			AbstractSyntaxNode successorToRemove = null;

			for (AbstractSyntaxNode successor : commonAncestor.successors())
			{
				if (successor.findNodeOfLabel(treeToMerge.root().successors().get(1).label()) != null)
				{
					successorToRemove = successor;
					break;
				}
			}

			if (successorToRemove == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
			commonAncestor.removeSuccessor(successorToRemove);

			final AbstractSyntaxNode leftNodeCopy = treeToMerge.root().successors().get(0).deepCopy();
			final AbstractSyntaxNode rightNodeCopy = successorToRemove.deepCopy();
			fullTreeToMerge.root().addSuccessor(leftNodeCopy);
			leftNodeCopy.setPredecessor(fullTreeToMerge.root());
			fullTreeToMerge.root().addSuccessor(rightNodeCopy);
			rightNodeCopy.setPredecessor(fullTreeToMerge.root());
		}


		//System.out.println("Tree without sub-tree:\n\n" + mainTree);
		//System.out.println("Full tree to merge:\n\n" + fullTreeToMerge);
		ASTSequenceReducerV2.releaseGlobalConstraintsV1(fullTreeToMerge, this.immutableTreesToMerge);

		final AbstractSyntaxNode leftNodeInMainTree = mainTree.findNodeOfLabel(treeToMerge.root().successors().get(0).label());
		this.manageOneNodeAlreadyInTreeCase(mainTree, fullTreeToMerge, fullTreeToMerge.copy(), leftNodeInMainTree, fullTreeToMerge.root().successors().get(0).deepCopy(), leftNodeInMainTree.label());

		return fullTreeToMerge.root().successors().get(1);
	}

	private ArrayList<AbstractSyntaxTree> manageBothNodesAlreadyInTheTreeCaseV3(final AbstractSyntaxNode commonAncestor,
																				final AbstractSyntaxTree treeToMerge,
																				final AbstractSyntaxTree mainTree) throws ExpectedException
	{
		final ArrayList<AbstractSyntaxTree> nextTrees = new ArrayList<>();
		final AbstractSyntaxTree fullTreeToMerge = new AbstractSyntaxTree();
		final AbstractSyntaxNode root = new AbstractSyntaxNode(treeToMerge.root().type());
		fullTreeToMerge.setRoot(root);

		if (commonAncestor.successors().size() == 2)
		{
			final AbstractSyntaxNode leftSuccessor = commonAncestor.successors().get(0);
			final AbstractSyntaxNode rightSuccessor = commonAncestor.successors().get(1);
			final AbstractSyntaxNode successorLeft;
			final AbstractSyntaxNode successorRemoved;

			if (leftSuccessor.findNodeOfLabel(treeToMerge.root().successors().get(0).label()) == null)
			{
				successorLeft = rightSuccessor;
				successorRemoved = leftSuccessor;
			}
			else
			{
				successorLeft = leftSuccessor;
				successorRemoved = rightSuccessor;
			}

			final AbstractSyntaxNode leftNodeCopy = treeToMerge.root().successors().get(0).deepCopy();
			final AbstractSyntaxNode rightNodeCopy = successorRemoved.deepCopy();
			root.addSuccessor(leftNodeCopy);
			leftNodeCopy.setPredecessor(root);
			root.addSuccessor(rightNodeCopy);
			rightNodeCopy.setPredecessor(root);

			final AbstractSyntaxNode commonAncestorParent = commonAncestor.predecessor();

			commonAncestor.removeSuccessors();
			commonAncestor.resetPredecessor();

			if (commonAncestorParent == null)
			{
				mainTree.setRoot(successorLeft);
				successorLeft.resetPredecessor();
			}
			else
			{
				commonAncestorParent.replaceSuccessor(commonAncestor, successorLeft);
				successorLeft.setPredecessor(commonAncestorParent);
			}
		}
		else
		{
			AbstractSyntaxNode successorToRemove = null;

			for (AbstractSyntaxNode successor : commonAncestor.successors())
			{
				if (successor.findNodeOfLabel(treeToMerge.root().successors().get(1).label()) != null)
				{
					successorToRemove = successor;
					break;
				}
			}

			if (successorToRemove == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
			commonAncestor.removeSuccessor(successorToRemove);

			final AbstractSyntaxNode leftNodeCopy = treeToMerge.root().successors().get(0).copy();
			final AbstractSyntaxNode rightNodeCopy = successorToRemove.deepCopy();
			root.addSuccessor(leftNodeCopy);
			leftNodeCopy.setPredecessor(root);
			root.addSuccessor(rightNodeCopy);
			rightNodeCopy.setPredecessor(root);
		}

		nextTrees.add(treeToMerge.copy());
		nextTrees.addAll(ASTSplitter.split(fullTreeToMerge.copy()));
		//MyOwnLogger.append("next trees size: " + nextTrees.size());
		return nextTrees;
	}

	private void manageOneNodeAlreadyInTreeCase(final AbstractSyntaxTree mainTree,
												final AbstractSyntaxTree treeToMerge,
												final AbstractSyntaxTree treeToMergeCopy,
												final AbstractSyntaxNode existingNodeInMainTree,
												final AbstractSyntaxNode existingNodeInTreeToMerge,
												final String nodeLabel) throws ExpectedException
	{
		//We make a copy of the main tree, and we try to add the missing task next to the existing one
		final AbstractSyntaxTree copiedTree = mainTree.copy();

		//System.out.println("-------------------------------------------------------------");
		//System.out.println("Tree to merge:\n\n" + treeToMerge);
		//System.out.println("Tree to merge copy:\n\n" + treeToMergeCopy);
		//System.out.println("Original main tree:\n\n" + mainTree);
		//System.out.println("Corresponding copied tree:\n\n" + copiedTree);

		final AbstractSyntaxNode nodeInCopiedTree = copiedTree.findNodeOfLabel(nodeLabel);
		final AbstractSyntaxNode parentOfNodeInCopiedTree = nodeInCopiedTree.predecessor();

		nodeInCopiedTree.resetPredecessor();
		final AbstractSyntaxTree treeToMergeCopy2 = treeToMerge.copy();
		parentOfNodeInCopiedTree.replaceSuccessor(nodeInCopiedTree, treeToMergeCopy2.root());
		treeToMergeCopy2.root().setPredecessor(parentOfNodeInCopiedTree);

		//We check whether the position of the task is good with regard to the remaining dependencies
		final ArrayList<AbstractSyntaxTree> problematicTrees = ASTUtils.getProblematicTrees(copiedTree, this.immutableTreesToMerge);

		if (problematicTrees.isEmpty())
		{
			//If the position does not generate issues, we transform the original tree to add this new information
			final AbstractSyntaxNode parentOfRightNode = existingNodeInMainTree.predecessor();

			existingNodeInMainTree.resetPredecessor();
			parentOfRightNode.replaceSuccessor(existingNodeInMainTree, treeToMergeCopy.root());
			treeToMergeCopy.root().setPredecessor(parentOfRightNode);
		}
		else
		{
			//System.out.println("Copied tree:\n\n" + copiedTree);

			for (AbstractSyntaxTree problematicTree : problematicTrees)
			{
				//System.out.println("Problematic tree:\n\n" + problematicTree);
			}

			//Some dependencies are in conflict with the new tree --> we must change the position of the tree that we have added
			final AbstractSyntaxNode earliestProblematicNodeInCopiedTree = this.findEarliestProblematicNode(problematicTrees, copiedTree);

			//System.out.println("Earliest problematic node in copied tree: " + earliestProblematicNodeInCopiedTree.id());

			final AbstractSyntaxNode earliestProblematicNodeInMainTree = mainTree.findNodeOfId(earliestProblematicNodeInCopiedTree.id());

			if (earliestProblematicNodeInMainTree == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

			//System.out.println("Main tree:\n\n" + mainTree);

			//System.out.println("Earliest problematic node in main tree: " + earliestProblematicNodeInMainTree);

			final AbstractSyntaxNode earliestProblematicNodeInMainTreeParent = earliestProblematicNodeInMainTree.predecessor();

			treeToMergeCopy.root().replaceSuccessor(existingNodeInTreeToMerge, earliestProblematicNodeInMainTree);
			earliestProblematicNodeInMainTree.setPredecessor(treeToMergeCopy.root());

			if (earliestProblematicNodeInMainTreeParent == null)
			{
				mainTree.setRoot(treeToMergeCopy.root());
			}
			else
			{
				//MyOwnLogger.append("Earliest problematic node in main tree: " + earliestProblematicNodeInMainTree.toString());
				earliestProblematicNodeInMainTreeParent.replaceSuccessor(earliestProblematicNodeInMainTree, treeToMergeCopy.root());
				treeToMergeCopy.root().setPredecessor(earliestProblematicNodeInMainTreeParent);
			}
		}

		//System.out.println("Final main tree:\n\n" + mainTree);

		/*final ArrayList<AbstractSyntaxTree> remainingProblematicTrees = this.getProblematicTrees(mainTree);

		if (!remainingProblematicTrees.isEmpty())
		{
			MyOwnLogger.append(remainingProblematicTrees.size() + " trees could not be merged to the main tree:\n");
			MyOwnLogger.append(remainingProblematicTrees.get(0).toString());
			MyOwnLogger.append(mainTree.toString() );

			throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES));
		}*/
	}

	private AbstractSyntaxNode getLessRestrictiveNodeOf(final AbstractSyntaxNode node)
	{
		AbstractSyntaxNode lessRestrictiveNode = null;
		AbstractSyntaxNode currentNode = node;

		while ((currentNode = currentNode.predecessor()) != null)
		{
			if (currentNode.type() == AbstractType.SEQ
				|| currentNode.type() == AbstractType.XOR)
			{
				lessRestrictiveNode = currentNode;
				break;
			}
		}

		return lessRestrictiveNode;
	}

	private AbstractSyntaxNode getLessRestrictiveNodeBefore(final AbstractSyntaxNode node,
															final AbstractSyntaxNode bound)
	{
		AbstractSyntaxNode lessRestrictiveNode = null;
		AbstractSyntaxNode currentNode = node;

		while (!(currentNode = currentNode.predecessor()).equals(bound))
		{
			if (currentNode.type() == AbstractType.SEQ
				|| currentNode.type() == AbstractType.XOR)
			{
				lessRestrictiveNode = currentNode;
				break;
			}
		}

		return lessRestrictiveNode;
	}

	private AbstractSyntaxNode findEarliestProblematicNode(final ArrayList<AbstractSyntaxTree> problematicTrees,
														   final AbstractSyntaxTree copiedTree) throws ExpectedException
	{
		final HashSet<AbstractSyntaxNode> leastCommonAncestors = new HashSet<>();

		for (AbstractSyntaxTree problematicTree : problematicTrees)
		{
			final AbstractSyntaxNode leftNodeInCopiedTree = copiedTree.findNodeOfLabel(problematicTree.root().successors().get(0).label());
			final AbstractSyntaxNode rightNodeInCopiedTree = copiedTree.findNodeOfLabel(problematicTree.root().successors().get(1).label());

			leastCommonAncestors.add(this.getLeastCommonAncestor(leftNodeInCopiedTree, rightNodeInCopiedTree));
		}

		AbstractSyntaxNode earliestProblematicNode = null;

		for (AbstractSyntaxNode node1 : leastCommonAncestors)
		{
			boolean isEarliestAncestor = true;

			for (AbstractSyntaxNode node2 : leastCommonAncestors)
			{
				if (!node1.equals(node2))
				{
					if (node1.hasAncestor(node2))
					{
						isEarliestAncestor = false;
						break;
					}
				}
			}

			if (isEarliestAncestor)
			{
				if (earliestProblematicNode != null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

				earliestProblematicNode = node1;
				//break;
			}
		}

		if (earliestProblematicNode == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

		return earliestProblematicNode;
	}

	private AbstractSyntaxNode getLeastCommonAncestor(final AbstractSyntaxNode node1,
													  final AbstractSyntaxNode node2) throws ExpectedException
	{
		final AbstractSyntaxTree tree = new AbstractSyntaxTree();
		tree.setRoot(node1.predecessor());

		//MyOwnLogger.append(node1.label());
		//MyOwnLogger.append(node2.label());
		//MyOwnLogger.append(tree.toString());

		while (tree.root() != null
			&& tree.findNodeOfLabel(node2.label()) == null)
		{
			tree.setRoot(tree.root().predecessor());
		}

		if (tree.root() == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

		return tree.root();
	}

	private AbstractSyntaxNode findPathOfNode(final AbstractSyntaxNode mostRestrictiveNode,
											  final AbstractSyntaxNode nodeToFindPath) throws ExpectedException
	{
		for (AbstractSyntaxNode child : mostRestrictiveNode.successors())
		{
			if (child.equals(nodeToFindPath)) return child;

			final AbstractSyntaxTree tree = new AbstractSyntaxTree();
			tree.setRoot(child);
			if (tree.findNodeOfLabel(nodeToFindPath.label()) != null) return child;
		}

		throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);
	}

	private AbstractSyntaxNode findMostRestrictiveNodeOf(final AbstractSyntaxNode node)
	{
		AbstractSyntaxNode currentNode = node.predecessor();
		AbstractSyntaxNode mostRestrictiveNode = null;

		while (currentNode != null)
		{
			if (currentNode.type() == AbstractType.SEQ
				|| currentNode.type() == AbstractType.XOR)
			{
				mostRestrictiveNode = currentNode;
			}

			currentNode = currentNode.predecessor();
		}

		return mostRestrictiveNode;
	}

	private void mergeFromSets(final ArrayList<AbstractSyntaxTree> treesToConsider) throws ExpectedException
	{
		final ArrayList<AbstractSyntaxTree> rawTrees = new ArrayList<>(treesToConsider);

		for (int i = 0; i < treesToConsider.size(); i++)
		{
			final AbstractSyntaxTree treeToMerge = ASTElector.nextTree(rawTrees, this.mainTree);
			rawTrees.remove(treeToMerge);

			//MyOwnLogger.append(treeToMerge.toString());
			boolean hasBeenMerged = false;
			//AbstractSyntaxTree possibleWithoutCorrespondence = null;

			final MergeStatus status = this.getMergeStatus(this.mainTree, treeToMerge);

			if (status == MergeStatus.POSSIBLE
				|| status == MergeStatus.POSSIBLE_WITHOUT_CORRESPONDENCES)
			{
				this.merge(this.mainTree, treeToMerge);
				//System.out.println("Current version of the tree:\n\n" + this.mainTree);
				//possibleWithoutCorrespondence = null;
				hasBeenMerged = true;
				//break;
			}
			else if (status == MergeStatus.ALREADY_SATISFIED)
			{
				//possibleWithoutCorrespondence = null;
				hasBeenMerged = true;
				//break;
			}

			if (!hasBeenMerged)
			{
				throw new ExpectedException("The following tree could not be merged to the current trees:\n\n" + treeToMerge.toString(), ExceptionStatus.CONTRADICTORY_VALUES);
			}
		}
	}

	private ArrayList<HashSet<String>> extractLoops()
	{
		final ArrayList<HashSet<String>> loops = new ArrayList<>();

		for (AbstractSyntaxTree tree : this.treesToMerge)
		{
			this.extractLoop(tree.root(), false, loops, null, tree);
		}

		return loops;
	}

	private void extractLoop(final AbstractSyntaxNode currentNode,
							 final boolean inLoop,
							 final ArrayList<HashSet<String>> loops,
							 final HashSet<String> currentLoop,
							 final AbstractSyntaxTree tree)
	{
		if (inLoop)
		{
			if (currentNode.type() == AbstractType.TASK)
			{
				currentLoop.add(currentNode.label());
			}
			else if (currentNode.type() == AbstractType.LOOP)
			{
				this.removeLoop(currentNode, tree);
			}
		}
		else
		{
			if (currentNode.type() == AbstractType.LOOP)
			{
				final ArrayList<AbstractSyntaxNode> childNodes = currentNode.successors();
				this.removeLoop(currentNode, tree);

				for (AbstractSyntaxNode child : childNodes)
				{
					final HashSet<String> loop = new HashSet<>();
					loops.add(loop);
					this.extractLoop(child, true, loops, loop, tree);
				}
			}
			else
			{
				for (AbstractSyntaxNode child : currentNode.successors())
				{
					this.extractLoop(child, false, loops, null, tree);
				}
			}
		}
	}

	private void removeLoop(final AbstractSyntaxNode currentNode,
							final AbstractSyntaxTree tree)
	{
		final AbstractSyntaxNode parent = currentNode.predecessor();
		final ArrayList<AbstractSyntaxNode> newChildren = currentNode.successors();

		if (parent == null)
		{
			if (currentNode.successors().size() == 1)
			{
				final AbstractSyntaxNode newRoot = currentNode.successors().iterator().next();
				newRoot.resetPredecessor();
				currentNode.removeSuccessors();
				tree.setRoot(newRoot);
			}
			else
			{
				currentNode.switchType(AbstractType.PAR);
			}
		}
		else if (parent.successors().size() == 1)
		{
			parent.removeSuccessors();
			currentNode.resetPredecessor();
			currentNode.removeSuccessors();

			for (AbstractSyntaxNode child : newChildren)
			{
				child.resetPredecessor();
				parent.addSuccessor(child);
				child.setPredecessor(parent);
			}
		}
		else
		{
			if (currentNode.successors().size() == 1)
			{
				final AbstractSyntaxNode successor = currentNode.successors().iterator().next();
				parent.replaceSuccessor(currentNode, successor);
				successor.setPredecessor(parent);
			}
			else
			{
				currentNode.switchType(AbstractType.PAR);
			}
		}
	}

	private void mergeLoopSets(final ArrayList<HashSet<String>> loops)
	{
		final ArrayList<HashSet<String>> previousLoops = new ArrayList<>(loops);
		final ArrayList<HashSet<String>> newLoops = new ArrayList<>();
		boolean changed = true;

		while (changed)
		{
			changed = false;
			int index1 = -1;
			int index2 = -1;

			for (int i = 0; i < previousLoops.size(); i++)
			{
				final HashSet<String> loop1 = previousLoops.get(i);

				for (int j = 0; j < previousLoops.size(); j++)
				{
					final HashSet<String> loop2 = previousLoops.get(j);

					if (loop1 != loop2)
					{
						for (String s1 : loop1)
						{
							if (loop2.contains(s1))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}
						}

						if (changed) break;

						for (String s2 : loop2)
						{
							if (loop1.contains(s2))
							{
								index1 = i;
								index2 = j;
								changed = true;
								break;
							}
						}

						if (changed) break;
					}
				}

				if (changed) break;
			}

			if (changed)
			{
				final int firstIndex = Math.max(index1, index2);
				final int secondIndex = index1 == firstIndex ? index2 : index1;

				final HashSet<String> hashset1 = previousLoops.remove(firstIndex);
				final HashSet<String> hashset2 = previousLoops.remove(secondIndex);

				newLoops.addAll(previousLoops);
				hashset1.addAll(hashset2);
				newLoops.add(hashset1);
				previousLoops.clear();
				previousLoops.addAll(newLoops);
				newLoops.clear();
			}
		}

		loops.clear();
		loops.addAll(previousLoops);
	}

	private MergeStatus getMergeStatus(final AbstractSyntaxTree fullTree,
									   final AbstractSyntaxTree sizeTwoTree) throws ExpectedException
	{
		final AbstractType type = sizeTwoTree.root().type();
		final String leftNodeLabel = sizeTwoTree.root().successors().get(0).label();
		final String rightNodeLabel = sizeTwoTree.root().successors().get(1).label();

		final AbstractSyntaxNode leftNode = fullTree.findNodeOfLabel(leftNodeLabel);
		final AbstractSyntaxNode rightNode = fullTree.findNodeOfLabel(rightNodeLabel);

		if (leftNode == null && rightNode == null)
		{
			return MergeStatus.POSSIBLE_WITHOUT_CORRESPONDENCES;
		}

		if (leftNode == null
			|| rightNode == null)
		{
			return MergeStatus.POSSIBLE;
		}

		final AbstractSyntaxNode commonAncestor = this.getLeastCommonAncestor(leftNode, rightNode);

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

				if (leftNodeIndex == -1 || rightNodeIndex == -1) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

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
			if (this.typesAreCompatible(commonAncestor.type(), type))
			{
				return MergeStatus.POSSIBLE;
			}
			else
			{
				return MergeStatus.NOT_POSSIBLE;
			}
		}
	}

	private void findParentsOf(final AbstractSyntaxNode currentNode,
							   final HashSet<AbstractSyntaxNode> parents)
	{
		if (currentNode.type() != AbstractType.TASK) parents.add(currentNode);

		if (currentNode.predecessor() != null) this.findParentsOf(currentNode.predecessor(), parents);
	}

	private AbstractSyntaxNode findIntersectionOf(final HashSet<AbstractSyntaxNode> leftNodes,
												  final HashSet<AbstractSyntaxNode> rightNodes) throws ExpectedException
	{
		AbstractSyntaxNode commonAncestor = null;

		for (AbstractSyntaxNode leftNode : leftNodes)
		{
			for (AbstractSyntaxNode rightNode : rightNodes)
			{
				if (leftNode.equals(rightNode))
				{
					if (commonAncestor != null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

					commonAncestor = leftNode;
				}
			}
		}

		if (commonAncestor == null) throw new ExpectedException(ExceptionStatus.CONTRADICTORY_VALUES);

		return commonAncestor;
	}

	private boolean typesAreCompatible(final AbstractType type1,
									   final AbstractType type2)
	{
		return (type1 != AbstractType.SEQ || type2 != AbstractType.XOR)
				&& (type1 != AbstractType.XOR || type2 != AbstractType.SEQ);
	}

	//Override
}
