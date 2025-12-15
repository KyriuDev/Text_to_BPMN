package other;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;

public class MyOwnLogger
{
	private static final StringBuilder builder = new StringBuilder();

	private MyOwnLogger()
	{

	}

	public static void appendNextTo(String msg)
	{
		//System.out.println(msg);
		MyOwnLogger.builder.append(msg);
	}

	public static void append(String msg)
	{
		//System.out.println(msg);
		MyOwnLogger.builder.append("\n\n").append(msg);
	}

	public static void writeStdOut(final File workingDirectory)
	{
		final String stdOutPath = Path.of(workingDirectory.getPath(), "std.out").toString();
		final File stdOutFile = new File(stdOutPath);

		if (stdOutFile.exists()) stdOutFile.delete();

		final PrintWriter printWriter;

		try
		{
			printWriter = new PrintWriter(stdOutFile);
		}
		catch (FileNotFoundException e)
		{
			throw new RuntimeException(e);
		}

		printWriter.print(builder.toString());
		printWriter.flush();
		printWriter.close();
	}

	public static void writeStdErr(final File workingDirectory,
								   final String msg)
	{
		final String stdErrPath = Path.of(workingDirectory.getPath(), "std.err").toString();
		final File stdErrFile = new File(stdErrPath);

		if (stdErrFile.exists()) stdErrFile.delete();

		final PrintWriter printWriter;

		try
		{
			printWriter = new PrintWriter(stdErrFile);
		}
		catch (FileNotFoundException e)
		{
			throw new RuntimeException(e);
		}

		printWriter.print(msg);
		printWriter.flush();
		printWriter.close();
	}
}
