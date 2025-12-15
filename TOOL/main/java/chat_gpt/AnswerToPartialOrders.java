package chat_gpt;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Task;
import chat_gpt.exceptions.BadAnswerException;
import other.Pair;
import other.Utils;
import refactoring.legacy.dependencies.Choice;
import refactoring.legacy.dependencies.Dependency;

import java.util.HashMap;
import java.util.HashSet;

public class AnswerToPartialOrders
{
	private AnswerToPartialOrders()
	{

	}

	public static Pair<HashSet<Dependency>, HashSet<Choice>> convertAnswerToDependencies(final String answer) throws BadAnswerException
	{
		final HashMap<String, Node> nodes = new HashMap<>();
		final HashSet<Dependency> dependencies = new HashSet<>();
		final HashSet<Choice> choices = new HashSet<>();
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

		if (startIndex == -1) throw new BadAnswerException("No '\\n\\n' found in the answer.");

		String removedPart = answer.substring(0, startIndex);
		String trimmedAnswer = answer.substring(startIndex + padding);

		if (removedPart.contains("<")
			|| removedPart.contains("|"))
		{
			trimmedAnswer = answer;
		}

		final int endIndex = trimmedAnswer.indexOf("\\n\\n");

		if (endIndex != -1)
		{
			trimmedAnswer = trimmedAnswer.substring(0, endIndex);
		}

		int nextLineIndex = trimmedAnswer.indexOf("\\n");

		if (nextLineIndex == -1) throw new BadAnswerException("No '\\n' found in the answer.");

		while (nextLineIndex != -1)
		{
			String currentLine = trimmedAnswer.substring(0, nextLineIndex);
			final Pair<HashSet<Dependency>, HashSet<Choice>> choicesAndDependencies = AnswerToPartialOrders.parseCurrentLine(currentLine, nodes);
			dependencies.addAll(choicesAndDependencies.first());
			choices.addAll(choicesAndDependencies.second());
			trimmedAnswer = trimmedAnswer.substring(nextLineIndex + 2);
			nextLineIndex = trimmedAnswer.indexOf("\\n");
		}

		final Pair<HashSet<Dependency>, HashSet<Choice>> choicesAndDependencies = AnswerToPartialOrders.parseCurrentLine(trimmedAnswer, nodes);
		dependencies.addAll(choicesAndDependencies.first());
		choices.addAll(choicesAndDependencies.second());

		for (Dependency dependency : dependencies)
		{
			System.out.println("- " + dependency.stringify(0));
		}

		for (Choice choice : choices)
		{
			System.out.println("- " + choice.toString());
		}

		return new Pair<>(dependencies, choices);
	}

	private static Pair<HashSet<Dependency>, HashSet<Choice>> parseCurrentLine(final String currentLine,
																			   final HashMap<String, Node> nodes) throws BadAnswerException
	{
		System.out.println("Current line is: " + currentLine);
		final HashSet<Dependency> dependencies = new HashSet<>();
		final HashSet<Choice> choices = new HashSet<>();
		int dependencySeparatorIndex = currentLine.indexOf('<');
		int choiceSeparatorIndex = currentLine.indexOf('|');

		if (dependencySeparatorIndex == -1
			&& choiceSeparatorIndex == -1) throw new BadAnswerException("Line |" + currentLine + "| does no contain dependency nor choice.");

		/*if (dependencySeparatorIndex != -1
				&& choiceSeparatorIndex != -1) throw new BadAnswerException("Line |" + currentLine + "| contains both dependency and choice.");*/

		char separator = '!';

		StringBuilder currentLeft = new StringBuilder();
		StringBuilder currentRight = new StringBuilder();
		boolean useRight = false;

		for (char c : currentLine.toCharArray())
		{
			if (useRight)
			{
				if (currentRight.toString().isEmpty())
				{
					if (Utils.isALetter(c))
					{
						//Wait for a letter to start
						currentRight.append(c);
					}
				}
				else
				{
					if (c == '<' || c == '|')
					{
						separator = c;
						final HashSet<Node> leftTasks = AnswerToPartialOrders.parseTasks(currentLeft.toString().trim(), nodes);
						final HashSet<Node> rightTasks = AnswerToPartialOrders.parseTasks(currentRight.toString().trim(), nodes);

						if (separator == '<')
						{
							for (Node leftTask : leftTasks)
							{
								for (Node rightTask : rightTasks)
								{
									dependencies.add(new Dependency(leftTask, rightTask));
								}
							}
						}
						else
						{
							for (Node leftTask : leftTasks)
							{
								for (Node rightTask : rightTasks)
								{
									choices.add(new Choice(leftTask, rightTask));
								}
							}
						}

						currentLeft = new StringBuilder(currentRight);
						currentRight = new StringBuilder();
					}
					else
					{
						currentRight.append(c);
					}
				}
			}
			else
			{
				if (currentLeft.toString().isEmpty())
				{
					if (Utils.isALetter(c))
					{
						currentLeft.append(c);
					}
				}
				else
				{
					if (c == '<' || c == '|')
					{
						separator = c;
						useRight = true;
					}
					else
					{
						currentLeft.append(c);
					}
				}
			}
		}

		if (separator == '!') throw new IllegalStateException();

		final HashSet<Node> leftTasks = AnswerToPartialOrders.parseTasks(currentLeft.toString().trim(), nodes);
		final HashSet<Node> rightTasks = AnswerToPartialOrders.parseTasks(currentRight.toString().trim(), nodes);

		if (separator == '<')
		{
			for (Node leftTask : leftTasks)
			{
				for (Node rightTask : rightTasks)
				{
					dependencies.add(new Dependency(leftTask, rightTask));
				}
			}
		}
		else
		{
			for (Node leftTask : leftTasks)
			{
				for (Node rightTask : rightTasks)
				{
					choices.add(new Choice(leftTask, rightTask));
				}
			}
		}

		return new Pair<>(dependencies, choices);
	}

	private static HashSet<Node> parseTasks(final String string,
											final HashMap<String, Node> nodes) throws BadAnswerException
	{
		final HashSet<Node> parsedNodes = new HashSet<>();
		String modifiableString = string.trim();
		int comaIndex = modifiableString.indexOf(',');

		while (comaIndex != -1)
		{
			final String tempString = modifiableString.substring(0, comaIndex).trim();
			final String finalModifiableString = tempString.indexOf(' ') == -1 ? tempString : tempString.substring(tempString.indexOf(' ') + 1).trim();
			final Node currentNode = nodes.computeIfAbsent(finalModifiableString, n -> new Node(new Task(finalModifiableString, BpmnProcessType.TASK, -1)));
			currentNode.bpmnObject().setName(finalModifiableString);
			parsedNodes.add(currentNode);
			modifiableString = modifiableString.substring(comaIndex + 1);
			comaIndex = modifiableString.indexOf(',');

			if (currentNode.bpmnObject().id().isEmpty()
				|| currentNode.bpmnObject().id().isEmpty()) throw new BadAnswerException();
		}

		final String finalModifiableString1 = modifiableString.indexOf(' ') == -1 ? modifiableString : modifiableString.substring(modifiableString.indexOf(' ') + 1);
		final Node currentNode = nodes.computeIfAbsent(modifiableString.trim(), n -> new Node(new Task(finalModifiableString1.trim(), BpmnProcessType.TASK, -1)));
		currentNode.bpmnObject().setName(finalModifiableString1);
		parsedNodes.add(currentNode);

		if (currentNode.bpmnObject().id().isEmpty()
				|| currentNode.bpmnObject().id().isEmpty()) throw new BadAnswerException();

		return parsedNodes;
	}
}
