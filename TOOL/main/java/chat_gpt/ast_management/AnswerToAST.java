package chat_gpt.ast_management;

import chat_gpt.ast_management.constants.AbstractType;
import chat_gpt.exceptions.BadAnswerException;
import chat_gpt.exceptions.UnparsableSequenceException;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.MyOwnLogger;
import other.Triple;
import other.Utils;

import java.util.ArrayList;
import java.util.HashMap;

public class AnswerToAST
{
	//Operators priority: '|' > '&' > '<' > ',' > '*'
	//Associativity: Right

	private AnswerToAST()
	{

	}

	public static ArrayList<AbstractSyntaxTree> convertAnswerToConstraints(final String answer) throws ExpectedException
	{
		int[] nbEndEvents =  {0};
		//System.out.println(answer);
		final HashMap<String, AbstractSyntaxNode> tasks = new HashMap<>();

		final ArrayList<AbstractSyntaxTree> trees = new ArrayList<>();
		final int startIndex;
		final int padding;

		if (answer.contains("\\n\\n"))
		{
			startIndex = answer.indexOf("\\n\\n");
			padding = 4;
		}
		else
		{
			startIndex = answer.indexOf("\\n");
			padding = 2;
		}

		if (startIndex == -1) throw new ExpectedException("No '\\n\\n' found in the answer.", ExceptionStatus.NON_PARSABLE_ANSWER);

		String removedPart = answer.substring(0, startIndex);
		String trimmedAnswer = answer.substring(startIndex + padding);

		if (removedPart.contains("<")
				|| removedPart.contains("|")
				|| removedPart.contains("&")
				|| removedPart.contains("+")
				|| removedPart.contains("?")
				|| removedPart.contains(","))
		{
			trimmedAnswer = answer;
		}

		final int endIndex = trimmedAnswer.indexOf("\\n\\n");

		if (endIndex != -1)
		{
			trimmedAnswer = trimmedAnswer.substring(0, endIndex);
		}

		int nextLineIndex = trimmedAnswer.indexOf("\\n");

		//if (nextLineIndex == -1) throw new BadAnswerException("No '\\n' found in the answer.");

		while (nextLineIndex != -1)
		{
			final String currentLine = AnswerToAST.correctLine(trimmedAnswer.substring(0, nextLineIndex));
			final AbstractSyntaxTree currentTree = AnswerToAST.convert(currentLine, tasks, nbEndEvents);

			if (currentTree != null) trees.add(currentTree);

			trimmedAnswer = trimmedAnswer.substring(nextLineIndex + 2);
			nextLineIndex = trimmedAnswer.indexOf("\\n");
		}

		final AbstractSyntaxTree lastTree = AnswerToAST.convert(AnswerToAST.correctLine(trimmedAnswer), tasks, nbEndEvents);
		if (lastTree != null) trees.add(lastTree);

		//System.out.println("--------- TREES GENERATED FROM CHATGPT ANSWER --------");

		int i = 1;

		for (AbstractSyntaxTree tree : trees)
		{
			//System.out.println("Tree n°" + i++ + ":\n\n" + tree.toString());
		}

		return trees;
	}

	//Private methods

	public static AbstractSyntaxTree convert(final String line,
											 final HashMap<String, AbstractSyntaxNode> tasks,
											 final int[] nbEndEvents)
	{
		String replacedString = line;
		final HashMap<Integer, String> correspondences = new HashMap<>();

		if (line.contains("("))
		{
			int currentReplacementValue = 1;
			final ArrayList<Integer> leftParenthesisIndices = new ArrayList<>();
			final HashMap<Integer, HashMap<Integer, Triple<String, Integer, Integer>>> replacementWithDepth = new HashMap<>();
			final char[] charArray = line.toCharArray();

			//Find all the parenthesised elements
			for (int i = 0; i < charArray.length; i++)
			{
				final char c = charArray[i];

				if (c == '(')
				{
					leftParenthesisIndices.add(i);
				}
				else if (c == ')')
				{
					if (leftParenthesisIndices.isEmpty())
					{
						//There are too many parenthesis. Can happen because ChatGPT does not really understand how to parenthesize.
						MyOwnLogger.append("Line |" + line + "| is badly parenthesised!");
						continue;
						//throw new UnparsableSequenceException("Line |" + line + "| contains right parenthesis that were not previously opened.");
					}

					final int depth = leftParenthesisIndices.size();
					final int leftParenthesisIndex = leftParenthesisIndices.remove(leftParenthesisIndices.size() - 1);
					final int rightParenthesisIndex = i + 1;
					final String parenthesisedString = line.substring(leftParenthesisIndex, rightParenthesisIndex);

					final HashMap<Integer, Triple<String, Integer, Integer>> replacementValuesCorrespondences = replacementWithDepth.computeIfAbsent(depth, h -> new HashMap<>());
					replacementValuesCorrespondences.put(currentReplacementValue++, new Triple<>(parenthesisedString, leftParenthesisIndex, rightParenthesisIndex));
				}
			}

			//Replace them by an integer
			final int maxDepth = Utils.max(replacementWithDepth.keySet());

			for (int i = maxDepth; i > 0; i--)
			{
				final HashMap<Integer, Triple<String, Integer, Integer>> replacementValues = replacementWithDepth.get(i);

				if (replacementValues == null) throw new IllegalStateException();

				for (int j = 0; j < currentReplacementValue; j++)
				{
					final Triple<String, Integer, Integer> replacementValue = replacementValues.get(j);

					if (replacementValue == null) continue;

					replacedString = replacedString.replace(replacementValue.first(), String.valueOf(j));

					int indice = i - 1;
					HashMap<Integer, Triple<String, Integer, Integer>> nextReplacementValues = replacementWithDepth.get(indice);

					while (nextReplacementValues != null)
					{
						for (Triple<String, Integer, Integer> value  : nextReplacementValues.values())
						{
							if (value.first().contains(replacementValue.first())
									&& value.second() < replacementValue.second()
									&& replacementValue.third() < value.third())
							{
								value.setFirst(value.first().replace(replacementValue.first(), String.valueOf(j)));
							}
						}

						nextReplacementValues = replacementWithDepth.get(--indice);
					}
				}
			}

			for (Integer key : replacementWithDepth.keySet())
			{
				final HashMap<Integer, Triple<String, Integer, Integer>> value = replacementWithDepth.get(key);

				for (Integer key2 : value.keySet())
				{
					final Triple<String, Integer, Integer> value2 = value.get(key2);
					correspondences.put(key2, value2.first());
				}
			}
		}

		if (replacedString.trim().isEmpty()) return null;

		final AbstractSyntaxTree tree = new AbstractSyntaxTree();

		//System.out.println(replacedString);

		while (Utils.isAnInt(replacedString.trim()))
		{
			//On unwrappe les strings qui sont entièrement parenthésées
			replacedString = correspondences.get(Integer.parseInt(replacedString.trim()));

			if (replacedString.charAt(0) == '('
					&& replacedString.charAt(replacedString.length() - 1) == ')')
			{
				replacedString = replacedString.substring(1, replacedString.length() - 1);
			}
		}

		//System.out.println(replacedString);

		final int index;
		final AbstractSyntaxNode root;

		if (replacedString.contains("|"))
		{
			index = replacedString.indexOf("|");
			root = new AbstractSyntaxNode(AbstractType.XOR);
		}
		else if (replacedString.contains("&"))
		{
			index = replacedString.indexOf("&");
			root = new AbstractSyntaxNode(AbstractType.PAR);
		}
		else if (replacedString.contains("<"))
		{
			index = replacedString.indexOf("<");
			root = new AbstractSyntaxNode(AbstractType.SEQ);
		}
		else if (replacedString.contains(","))
		{
			index = replacedString.indexOf(",");
			root = new AbstractSyntaxNode(AbstractType.PAR);
		}
		else if (replacedString.contains("*"))
		{
			index = replacedString.indexOf("*");
			root = new AbstractSyntaxNode(AbstractType.LOOP);
		}
		else if (replacedString.contains("+"))
		{
			index = replacedString.indexOf("+");
			root = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
		}
		else
		{
			index = -1;
			final String taskName = replacedString.trim();
			final String finalTaskName;
			if (taskName.equals("#"))
			{
				finalTaskName = "EndEvent_" + nbEndEvents[0];
				nbEndEvents[0] = nbEndEvents[0] + 1;
			}
			else
			{
				finalTaskName = taskName;
			}
			root = tasks.computeIfAbsent(finalTaskName, a -> new AbstractSyntaxNode(AbstractType.TASK)).copy();
			root.setLabel(finalTaskName);
		}

		if (index != -1)
		{
			final String leftPart = replacedString.substring(0, index).trim().replace("(", "").replace(")", "");
			final String rightPart = replacedString.substring(index + 1).trim().replace("(", "").replace(")", "");
			//System.out.println("left part: |" + leftPart + "|");
			//System.out.println("right part: |" + rightPart + "|");
			AnswerToAST.buildTree(correspondences, leftPart, root, tasks, nbEndEvents);
			AnswerToAST.buildTree(correspondences, rightPart, root, tasks, nbEndEvents);
		}

		tree.setRoot(root);
		return tree;
	}

	private static void buildTree(final HashMap<Integer, String> correspondences,
								  final String string,
								  final AbstractSyntaxNode currentNode,
								  final HashMap<String, AbstractSyntaxNode> tasks,
								  final int[] nbEndEvents)
	{
		final int index;
		final AbstractSyntaxNode node;

		if (string.contains("|"))
		{
			index = string.indexOf("|");
			node = new AbstractSyntaxNode(AbstractType.XOR);
			node.setPredecessor(currentNode);
		}
		else if (string.contains("&"))
		{
			index = string.indexOf("&");
			node = new AbstractSyntaxNode(AbstractType.PAR);
			node.setPredecessor(currentNode);
		}
		else if (string.contains("<"))
		{
			index = string.indexOf("<");
			node = new AbstractSyntaxNode(AbstractType.SEQ);
			node.setPredecessor(currentNode);
		}
		else if (string.contains(","))
		{
			index = string.indexOf(",");
			node = new AbstractSyntaxNode(AbstractType.PAR);
			node.setPredecessor(currentNode);
		}
		else if (string.contains("*"))
		{
			index = string.indexOf("*");
			node = new AbstractSyntaxNode(AbstractType.LOOP);
			node.setPredecessor(currentNode);
		}
		else if (string.contains("+"))
		{
			index = string.indexOf("+");
			node = new AbstractSyntaxNode(AbstractType.LOOP_MANDATORY);
			node.setPredecessor(currentNode);
		}
		else if (string.contains("?"))
		{
			index = string.indexOf("?");
			node = new AbstractSyntaxNode(AbstractType.LOOP_OPTIONAL);
			node.setPredecessor(currentNode);
		}
		else
		{
			index = -1;
			final String trimmedString = string.trim();

			if (Utils.isAnInt(trimmedString))
			{
				final String replacementString = correspondences.get(Integer.parseInt(trimmedString))
						.replace("(", "")
						.replace(")", "");
				AnswerToAST.buildTree(correspondences, replacementString, currentNode, tasks, nbEndEvents);
				return;
			}
			else
			{
				if (trimmedString.isEmpty()) return;

				final String finalTaskName;
				if (trimmedString.equals("#"))
				{
					finalTaskName = "EndEvent_" + nbEndEvents[0];
					nbEndEvents[0] = nbEndEvents[0] + 1;
				}
				else
				{
					finalTaskName = trimmedString;
				}

				node = tasks.computeIfAbsent(finalTaskName, a -> new AbstractSyntaxNode(AbstractType.TASK)).copy();
				node.setPredecessor(currentNode);
				node.setLabel(finalTaskName);
			}
		}

		currentNode.addSuccessor(node);

		if (index != -1)
		{
			final String leftPart = string.substring(0, index).trim();
			final String rightPart = string.substring(index + 1).trim();
			AnswerToAST.buildTree(correspondences, leftPart, node, tasks, nbEndEvents);
			AnswerToAST.buildTree(correspondences, rightPart, node, tasks, nbEndEvents);
		}
	}

	private static String correctLine(final String line)
	{
		final StringBuilder builder = new StringBuilder();

		boolean hasStarted = false;

		for (char c : line.toCharArray())
		{
			if (!hasStarted)
			{
				if ((c >= 65 && c <= 90)
						|| (c >= 97 && c <= 122)
						|| c == '(')
				{
					hasStarted = true;
					builder.append(c);
				}
			}
			else
			{
				builder.append(c);
			}
		}

		return builder.toString();
	}
}
