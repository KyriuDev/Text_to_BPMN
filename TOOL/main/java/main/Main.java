package main;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.writing.generation.GraphicalGenerationWriter;
import chat_gpt.ChatGPTManager;
import chat_gpt.ast_management.*;
import chat_gpt.ast_management.constants.AnswerType;
import chat_gpt.exceptions.BadAnswerException;
import chat_gpt.exceptions.ContradictoryValuesException;
import chat_gpt.exceptions.UnparsableSequenceException;
import chat_gpt.no_ast.*;
import chat_gpt.tests.Expressions;
import constants.CommandLineOption;
import constants.PrintLevel;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import other.MyOwnLogger;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class Main
{
	public static final boolean LOCAL_EXAMPLES_TESTING = false;
	public static final boolean TASKS_ALREADY_NAMED = true;
	public static final boolean LOCAL_TESTING = false;
    public static final int PRINT_LEVEL = PrintLevel.PRINT_SOME_IMPORTANT;
	private static final AnswerType ANSWER_TYPE = AnswerType.CHAT_GPT;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException, IOException, BadDependencyException, UnparsableSequenceException, ContradictoryValuesException, BadAnswerException, ExpectedException
	{
		final long time = System.nanoTime();

		if (LOCAL_EXAMPLES_TESTING)
		{
			BpmnProcessFactory.setObjectIDs(new ArrayList<>());
			System.out.println("Converting answer to abstract syntax trees...");
			final ArrayList<AbstractSyntaxTree> treesToMerge = new ArrayList<>(AnswerToAST.convertAnswerToConstraints(ConstraintsExamples.EXAMPLE_6));

			System.out.println(treesToMerge.size() + " ASTs were generated: ");

			if (treesToMerge.isEmpty())
				throw new ExpectedException("AST generation did not fail but generated 0 AST!!!", ExceptionStatus.NO_AST_GENERATED);

			//Reduce the generated ASTs
			for (AbstractSyntaxTree tree : treesToMerge)
			{
				ASTReductor.reduce(tree);
				System.out.println(tree.toString());
			}

			//Structure the ASTs' information (dependency graph + choices + parallels + explicit loops)
			final Ast2StructuredInformation ast2StructuredInformation = new Ast2StructuredInformation(treesToMerge);
			final StructuredInformation structuredInformation = ast2StructuredInformation.structureInformation();
			System.out.println("Structured information generated.");

			//Generated the corresponding BPMN process
			final DependencyGraph2BPMN dependencyGraph2BPMN = new DependencyGraph2BPMN(structuredInformation);
			final Graph graph = dependencyGraph2BPMN.translateToBpmn();

			System.out.println("Generated BPMN process:\n\n" + graph.toString());

			//Converting BPMN process to list
			final GraphToList graphToList = new GraphToList(graph);
			graphToList.convert();

			System.out.println("Writing BPMN process to file...");

			//Writing BPMN process file
			try
			{
				final GraphicalGenerationWriter generationWriter = new GraphicalGenerationWriter(
					new File("/home/quentin"),
					graphToList.objectsList(),
					""
				);
				generationWriter.write();
			}
			catch (Exception e)
			{
				throw new ExpectedException(e, ExceptionStatus.GRAPHICAL_GENERATION_FAILED);
			}

			return;
		}

		final CommandLineParser commandLineParser;
		BpmnProcessFactory.setObjectIDs(new ArrayList<>());

		try
		{
			commandLineParser = new CommandLineParser(args);
		}
		catch (FileNotFoundException e)
		{
			throw new IllegalStateException("Some necessary files have not be found or are not valid.");
		}

        try
		{
			final String question = ((String) commandLineParser.get(CommandLineOption.QUESTION)).trim();
			final String apiKey = ((String) commandLineParser.get(CommandLineOption.API_KEY)).trim();
			final boolean tasksNamed = (Boolean) commandLineParser.get(CommandLineOption.TASKS_ALREADY_NAMED);

			MyOwnLogger.append("Question: " + question);
			MyOwnLogger.append("API key: " + apiKey);
			MyOwnLogger.append("Tasks already named: " + tasksNamed);

			//Prompt GPT
			MyOwnLogger.append("Waiting for ChatGPT answer...");
			final String answer = Main.produceAnswer(question, apiKey, tasksNamed);
			MyOwnLogger.append("Answer received.");

			MyOwnLogger.append("Answer: " + answer);
			Main.writeAnswerToFile(commandLineParser, answer);
			Main.writeQuestionToFile(commandLineParser, question);

			//Convert GPT's answer to ASTs
			MyOwnLogger.append("Converting answer to abstract syntax trees...");
			final ArrayList<AbstractSyntaxTree> treesToMerge = new ArrayList<>(AnswerToAST.convertAnswerToConstraints(answer));

			MyOwnLogger.append(treesToMerge.size() + " ASTs were generated: ");

			if (treesToMerge.isEmpty()) throw new ExpectedException("AST generation did not fail but generated 0 AST!!!", ExceptionStatus.NO_AST_GENERATED);

			//Reduce the generated ASTs
			for (AbstractSyntaxTree tree : treesToMerge)
			{
				ASTReductor.reduce(tree);
				MyOwnLogger.append(tree.toString());
			}

			//Structure the ASTs' information (dependency graph + choices + parallels + explicit loops)
			final Ast2StructuredInformation ast2StructuredInformation = new Ast2StructuredInformation(treesToMerge);
			final StructuredInformation structuredInformation = ast2StructuredInformation.structureInformation();

			//Generated the corresponding BPMN process
			final DependencyGraph2BPMN dependencyGraph2BPMN = new DependencyGraph2BPMN(structuredInformation);
			final Graph graph = dependencyGraph2BPMN.translateToBpmn();

			MyOwnLogger.append("Generated BPMN process:\n\n" + graph.toString());

			//Converting BPMN process to list
			final GraphToList graphToList = new GraphToList(graph);
			graphToList.convert();

			MyOwnLogger.append("Writing BPMN process to file...");

			//Writing BPMN process file
			try
			{
				final GraphicalGenerationWriter generationWriter = new GraphicalGenerationWriter(
					commandLineParser,
					graphToList.objectsList(),
					""
				);
				generationWriter.write();
			}
			catch (Exception e)
			{
				throw new ExpectedException(e, ExceptionStatus.GRAPHICAL_GENERATION_FAILED);
			}

			MyOwnLogger.append("BPMN process written.");

			final long endTime = System.nanoTime();
			final long totalTime = endTime - time;

			MyOwnLogger.append("Generating the BPMN process took " + Utils.nanoSecToReadable(totalTime) + ".\n");
			MyOwnLogger.writeStdOut((File) commandLineParser.get(CommandLineOption.WORKING_DIRECTORY));
			MyOwnLogger.writeStdErr((File) commandLineParser.get(CommandLineOption.WORKING_DIRECTORY), "");
			Main.writeTimeFile(commandLineParser, Utils.nanoSecToReadable(totalTime));
		}
		catch (Exception e)
		{
			final long endTime = System.nanoTime();
			final long totalTime = endTime - time;
			
			try
			{
				Main.writeTimeFile(commandLineParser, Utils.nanoSecToReadable(totalTime));
			}
			catch (ExpectedException ex)
			{
				throw new RuntimeException(ex);
			}
			
			e.printStackTrace();

			final int exitCode;

			if (e instanceof ExpectedException)
			{
				final ExpectedException expectedException = (ExpectedException) e;
				exitCode = expectedException.code();
			}
			else
			{
				exitCode = ExceptionStatus.NO_CODE;
			}

			MyOwnLogger.append("Generating the BPMN process took " + Utils.nanoSecToReadable(totalTime) + ".\n");
			MyOwnLogger.writeStdOut((File) commandLineParser.get(CommandLineOption.WORKING_DIRECTORY));
			MyOwnLogger.writeStdErr((File) commandLineParser.get(CommandLineOption.WORKING_DIRECTORY), Utils.getStackTrace(e));

			System.exit(exitCode);
		}
    }

	public static String produceAnswer(final String question,
									   final String apiKey,
									   final boolean tasksNamed) throws ExpectedException
	{
		if (ANSWER_TYPE == AnswerType.HANDWRITTEN)
		{
			return Expressions.getExpressionToUse();
		}
		else
		{
			return ChatGPTManager.generateAnswer(question, apiKey, tasksNamed);
		}
	}

	public static void writeTimeFile(final CommandLineParser parser,
									 final String time) throws ExpectedException
	{
		final String timeFileName = "time.txt";
		final String workingDirectory = ((File) parser.get(CommandLineOption.WORKING_DIRECTORY)).getPath();
		final File timeFile = new File(Path.of(workingDirectory, timeFileName).toString());
		final PrintWriter writer;

		try
		{
			writer = new PrintWriter(timeFile);
		}
		catch (FileNotFoundException e)
		{
			throw new ExpectedException(e, ExceptionStatus.FILE_GENERATION_FAILED);
		}

		writer.print(time);
		writer.flush();
		writer.close();
	}

	public static void writeQuestionToFile(final CommandLineParser parser,
										   final String question) throws ExpectedException
	{
		final String answerFileName = "question.txt";
		final String workingDirectory = ((File) parser.get(CommandLineOption.WORKING_DIRECTORY)).getPath();
		final File answerFile = new File(Path.of(workingDirectory, answerFileName).toString());
		final PrintWriter writer;

		try
		{
			writer = new PrintWriter(answerFile);
		}
		catch (FileNotFoundException e)
		{
			throw new ExpectedException(e, ExceptionStatus.FILE_GENERATION_FAILED);
		}

		writer.print(question.trim());
		writer.flush();
		writer.close();
	}

	public static void writeAnswerToFile(final CommandLineParser parser,
										 final String answer) throws ExpectedException
	{
		final String answerFileName = "chatgpt.answ";
		final String workingDirectory = ((File) parser.get(CommandLineOption.WORKING_DIRECTORY)).getPath();
		final File answerFile = new File(Path.of(workingDirectory, answerFileName).toString());
		final PrintWriter writer;

		try
		{
			writer = new PrintWriter(answerFile);
		}
		catch (FileNotFoundException e)
		{
			throw new ExpectedException(e, ExceptionStatus.FILE_GENERATION_FAILED);
		}

		writer.print(answer.replace("\\n", "\n").trim());
		writer.flush();
		writer.close();
	}
}