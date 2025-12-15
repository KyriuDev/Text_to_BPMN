package chat_gpt.ast_management.constants;

public enum IntegrityStatus
{
	NODE_AND_SUCCESSOR_EQUALITY("A node is equal to its successor"),
	NODE_AND_PREDECESSOR_EQUALITY("A node is equal to its predecessor"),
	FULL_NODES_INEQUALITY("The nodes retrieved from the top are not equal to the nodes retrieved from the bottom"),
	INVALID_OPERATORS("Some operators do not have a correct number of children"),
	DUPLICATED_NODES("Some tasks are duplicated in the tree."),
	MODIFIED_LOOPS("At least one loop includes an element that was not originally there."),
	VALID("");
	private final String label;

	IntegrityStatus(final String label)
	{
		this.label = label;
	}

	public String meaning()
	{
		return this.label;
	}
}
