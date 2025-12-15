package chat_gpt.ast_management;

import java.util.ArrayList;

public class DummyStartManager
{
	private static final AbstractSyntaxNode DUMMY_START = AbstractSyntaxNodeFactory.newTask("START_DUMMY_NODE");

	private DummyStartManager()
	{

	}

	public static void addDummyStart(final ArrayList<AbstractSyntaxTree> abstractSyntaxTrees)
	{
		/*
			Small hack: we consider that the first found expression and thus the first tree correspond to the
			beginning of the process.
			Then, we add a dummy node before the first expression which improves the management of processes
			directly starting with a loop.
		 */
		final AbstractSyntaxTree firstTree = abstractSyntaxTrees.get(0);

		final AbstractSyntaxNode dummyCopy = DUMMY_START.copy();
		final AbstractSyntaxNode sequenceNode = AbstractSyntaxNodeFactory.newSequence();
		sequenceNode.addSuccessor(dummyCopy);
		dummyCopy.setPredecessor(sequenceNode);
		sequenceNode.addSuccessor(firstTree.root());
		firstTree.root().setPredecessor(sequenceNode);
		firstTree.setRoot(sequenceNode);
		ASTReductor.reduce(firstTree);
	}

	public static void removeDummyStart(final AbstractSyntaxTree finalTree)
	{
		final AbstractSyntaxNode dummyStart = finalTree.findNodeOfId(DUMMY_START.id());

		if (dummyStart != null)
		{
			final AbstractSyntaxNode predecessor = dummyStart.predecessor();
			predecessor.removeSuccessor(dummyStart);
			dummyStart.resetPredecessor();
		}

		ASTCleaner.findCleanableNodes(finalTree);
		ASTReductor.reduce(finalTree);
	}
}
