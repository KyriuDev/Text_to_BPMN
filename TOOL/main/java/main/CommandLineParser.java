package main;

import other.Utils;
import constants.CommandLineOption;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static main.Main.LOCAL_TESTING;

public class CommandLineParser
{
    private static final File LIG_SERVER_WORKING_DIRECTORY = new File("/home/convecs/nivonq/nl_to_mc");
    private final Map<CommandLineOption, Object> commands;

    public CommandLineParser(final String[] args) throws FileNotFoundException
    {
        this.commands = new HashMap<>();
        if (args == null) return;

        this.parse(args);

        if (this.commands.containsKey(CommandLineOption.HELP))
        {
            System.out.println(this.helpMessage());
            this.commands.clear();
        }

       // this.retrieveArgsFromWorkingDirectory();

        if (!this.verifyArgs())
        {
            throw new IllegalStateException("Necessary arguments are missing. Please make sure you have specified the" +
                    " following elements: question, API key, or that the working directory that you have" +
                    " specified contains all of these elements.");
        }
    }

    public void put(CommandLineOption commandLineOption,
                    Object o)
    {
        this.commands.put(commandLineOption, o);
    }

    public Object get(CommandLineOption commandLineOption)
    {
        return this.commands.get(commandLineOption);
    }

    public boolean containsKey(final CommandLineOption key)
    {
        return this.commands.containsKey(key);
    }

    //Private methods

    private String helpMessage()
    {
        //TODO
        return "";
    }

    private void parse(String[] commandLineArgs)
    {
        if (LOCAL_TESTING)
        {
            this.put(CommandLineOption.API_KEY, "");
            this.put(CommandLineOption.QUESTION, "");
            this.put(CommandLineOption.TASKS_ALREADY_NAMED, Main.TASKS_ALREADY_NAMED);
            this.put(CommandLineOption.WORKING_DIRECTORY, new File("/home/quentin/Documents/nl_to_bpmn_local_tests"));
        }
        else
        {
            if (commandLineArgs.length < 4) return;

            this.put(CommandLineOption.API_KEY, commandLineArgs[0]);
            this.put(CommandLineOption.QUESTION, commandLineArgs[1]);
            this.put(CommandLineOption.TASKS_ALREADY_NAMED, commandLineArgs[2].equals("0"));
            final String leafDirectoryName = commandLineArgs[3];

            try
            {
                final File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                final File finalDirectory = this.buildArborescence(file, leafDirectoryName);
                this.put(CommandLineOption.WORKING_DIRECTORY, finalDirectory);
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        }
	}

    private boolean isIAT(final String arg)
    {
        return Utils.isAnInt(arg);
    }

    private boolean isResourcePool(final String arg)
    {
        return (arg.endsWith(".rsp") || arg.endsWith(".rp"))
                && new File(arg).isFile();
    }

    private boolean isGlobalInfo(final String arg)
    {
        return arg.endsWith(".inf")
                && new File(arg).isFile();
    }

    private boolean isBpmnProcess(final String arg)
    {
        return arg.endsWith(".bpmn")
                && new File(arg).isFile();
    }

    private boolean isWorkingDirectory(final String arg)
    {
        return new File(arg).isDirectory();
    }

    private boolean isDependencies(final String arg)
    {
        return arg.endsWith(".dep")
                && new File(arg).isFile();
    }

    private boolean isQuestion(final String arg)
    {
        return arg.endsWith(".txt")
                && new File(arg).isFile();
    }

    private boolean isApiKey(final String arg)
    {
        return arg.endsWith(".key")
                && new File(arg).isFile();
    }

    private boolean isOverwrite(final String arg)
    {
        final String lowerArg = arg.toLowerCase();

        return lowerArg.equals("-f")
                || lowerArg.equals("--f")
                || lowerArg.equals("-overwrite")
                || lowerArg.equals("--overwrite");
    }

    private void retrieveArgsFromWorkingDirectory()
    {
        if (this.commands.get(CommandLineOption.WORKING_DIRECTORY) == null) return;

        //Working directory was specified --> check whether it contains a BPMN process and/or a resource file
        final File workingDirectory = (File) this.commands.get(CommandLineOption.WORKING_DIRECTORY);

        for (File file : Objects.requireNonNull(workingDirectory.listFiles()))
        {
            if (isQuestion(file.getPath()))
            {
                this.commands.put(CommandLineOption.QUESTION, file);
            }
            else if (isApiKey(file.getPath()))
            {
                this.commands.put(CommandLineOption.API_KEY, file);
            }
        }
    }

    private boolean verifyArgs()
    {
        return this.commands.get(CommandLineOption.WORKING_DIRECTORY) != null
                && this.commands.get(CommandLineOption.QUESTION) != null
                && this.commands.get(CommandLineOption.API_KEY) != null;
    }

    /**
     * This method generates the arborescence by building the necessary directories
     * if they do not exist.
     * To classify the user tries, the following arborescence is adopted:
     * - generated:
     *     - current_year:
     *         - current_month:
     *             - current_day:
     *                 - current_time: (+ eventual extension if conflict)
     *
     * @param jarFile the jar file being executed
     * @return the directory in which the computations must be done
     */
    private File buildArborescence(final File jarFile,
                                   final String leafDirectoryName)
    {
        //Parent file is the working directory
        final File parent = jarFile.getParentFile();

        //Generate and/or verify the "generated" directory
        final File generatedDirectory = this.buildAndVerifyDir(parent, "generated");
        if (generatedDirectory == null) throw new IllegalStateException("An error occurred during the generation of the \"generated\" directory.");

        //Generate and/or verify the "current_year" directory
        final String currentYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        final File currentYearDirectory = this.buildAndVerifyDir(generatedDirectory, currentYear);
        if (currentYearDirectory == null) throw new IllegalStateException("An error occurred during the generation of the \"" + currentYear + "\" directory.");

        //Generate and/or verify the "current_month" directory
        final String currentMonth = Utils.convertMonth(Calendar.getInstance().get(Calendar.MONTH));
        final File currentMonthDirectory = this.buildAndVerifyDir(currentYearDirectory, currentMonth);
        if (currentMonthDirectory == null) throw new IllegalStateException("An error occurred during the generation of the \"" + currentMonth + "\" directory.");

        //Generate and/or verify the "current_month" directory
        final String currentDay = String.valueOf(Calendar.getInstance().get(Calendar.DATE));
        final File currentDayDirectory = this.buildAndVerifyDir(currentMonthDirectory, currentDay);
        if (currentDayDirectory == null) throw new IllegalStateException("An error occurred during the generation of the \"" + currentDay + "\" directory.");

        //Generate and/or verify the "current_month" directory
        final File currentTimeDirectory = this.buildAndVerifyDir(currentDayDirectory, leafDirectoryName);
        if (currentTimeDirectory == null) throw new IllegalStateException("An error occurred during the generation of the \"" + leafDirectoryName + "\" directory.");

        return currentTimeDirectory;
    }

    private File buildAndVerifyDir(final File parent,
                                   final String suffix)
    {
        final String generatedDirectoryPath = Path.of(parent.getPath(), suffix).toString();
        final File generatedDirectory = new File(generatedDirectoryPath);

		if (!generatedDirectory.exists())
		{
			if (!generatedDirectory.mkdir())
			{
				return null;
			}
		}

		if (!generatedDirectory.isDirectory())
		{
			return null;
		}

		return generatedDirectory;
    }

    private File buildUntilAndVerifyDir(final File parent,
                                        final String suffix)
    {
        String generatedDirectoryPath = Path.of(parent.getPath(), suffix).toString();
        File generatedDirectory = new File(generatedDirectoryPath);
        int index = 1;

        while (generatedDirectory.exists())
        {
            generatedDirectoryPath = Path.of(parent.getPath(), suffix + "-" + index++).toString();
            generatedDirectory = new File(generatedDirectoryPath);
        }

        if (!generatedDirectory.mkdir())
        {
            return null;
        }

        if (!generatedDirectory.isDirectory())
        {
            return null;
        }

        return generatedDirectory;
    }
}
