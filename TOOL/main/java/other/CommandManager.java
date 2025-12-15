package other;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager
{
	private final String command;
	private final ArrayList<String> args;
	private final StringBuilder stdOut;
	private final StringBuilder stdErr;
	private final File workingDirectory;
	private int returnValue;

	public CommandManager(String command,
						  File workingDirectory,
						  String... args)
	{
		this.command = command;
		this.args = new ArrayList<>();
		this.args.addAll(Arrays.asList(args));
		this.workingDirectory = workingDirectory;
		this.stdOut = new StringBuilder();
		this.stdErr = new StringBuilder();
	}

	public CommandManager(String command,
						  File workingDirectory,
						  List<String> args)
	{
		this.command = command;
		this.args = new ArrayList<>(args);
		this.workingDirectory = workingDirectory;
		this.stdOut = new StringBuilder();
		this.stdErr = new StringBuilder();
	}

	public void execute() throws IOException, InterruptedException
	{
		//Build the command
		final ArrayList<String> command = new ArrayList<>();
		command.add(this.command);
		command.addAll(this.args);
		System.out.println("Command to execute: \"" + Utils.join(command, " ") + "\".");
		MyOwnLogger.append("Command to execute: \"" + Utils.join(command, " ") + "\".");

		//Create the process builder
		final ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(this.workingDirectory);

		//Start the process
		final Process process = processBuilder.start();

		//Read stdout
		final InputStreamReader stdOutInputStreamReader = new InputStreamReader(process.getInputStream());
		final BufferedReader stdoutBufferedReader = new BufferedReader(stdOutInputStreamReader);
		boolean forceQuit = false;

		String line;
		while ((line = stdoutBufferedReader.readLine()) != null)
		{
			this.stdOut.append(line).append("\n");

			if (line.contains("impossible rendez-vous detected")) //Use for lnt.open that blocks for some reason
			{
				forceQuit = true;
				break;
			}
		}

		stdOutInputStreamReader.close();
		stdoutBufferedReader.close();

		if (forceQuit)
		{
			process.destroy();
			this.returnValue = 0;

			while (process.isAlive())
			{
			}
		}
		else
		{
			//Read stderr
			final InputStreamReader stdErrInputStreamReader = new InputStreamReader(process.getErrorStream());
			final BufferedReader stdErrBufferedReader = new BufferedReader(stdErrInputStreamReader);

			while ((line = stdErrBufferedReader.readLine()) != null)
			{
				this.stdErr.append(line).append("\n");
			}

			stdErrInputStreamReader.close();
			stdErrBufferedReader.close();

			this.returnValue = process.waitFor();
		}
	}

	public String stdOut()
	{
		return this.stdOut.toString();
	}

	public String stdErr()
	{
		return this.stdErr.toString();
	}

	public int returnValue()
	{
		return this.returnValue;
	}
}
