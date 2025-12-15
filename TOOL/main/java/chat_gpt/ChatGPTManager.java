package chat_gpt;

import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import other.MyOwnLogger;
import refactoring.legacy.exceptions.BadDependencyException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class ChatGPTManager
{
	private static final boolean TASKS_NAMED = true;
	private static final String FORMATTING_SYSTEM_BASE = "You are a helpful assistant which aims at analyzing a textual" +
			" description given by a user. From this analysis, you are expected to extract atomic tasks representing the" +
			" important actions of the description. These tasks should have labels containing only letters without spaces" +
			" and in which each word starts with a capital letter. They can also be single letters if the text provided" +
			" them as is. Once you found all the tasks, you should rephrase the original text by removing all the" +
			" unnecessary information that it may contain, and by replacing the information concerning the tasks that" +
			" you discovered by their labels.";
	private static final String FORMATTING_SYSTEM_BASE_V2 = "You are a helpful assistant which aims at analyzing a" +
			" textual description given by a user. From this analysis, you are expected to extract atomic tasks" +
			" representing the important actions of the description. These tasks should have labels containing only" +
			" letters without spaces and in which each word starts with a capital letter. Once you found all the tasks," +
			" you should rephrase the original text by removing all the unnecessary information that it may contain," +
			" and by replacing the information concerning the tasks that you discovered by their labels. Finally, you" +
			" should put everything else than the task labels that you found in lowercase.";
	private static final String FORMATTING_SYSTEM_BASE_V3 = "You are a helpful assistant which aims at analyzing a" +
			" textual description given by a user. From this analysis, you are expected to extract atomic tasks" +
			" representing the important actions of the description. These tasks should have labels containing only" +
			" letters without spaces and in which each word starts with a capital letter. Once you found all the tasks," +
			" you should rephrase the original text by removing all the unnecessary information that it may contain," +
			" and by replacing the information concerning the tasks that you discovered by their labels. Finally, you" +
			" should put everything else than the task labels that you found in lowercase. Your answer should contain" +
			" only the original text rephrased.";

	private static final String SYSTEM_BASE = "Dependencer is a chatbot that analyses and extracts the tasks contained" +
			" in a text, along with their constraints. Tasks can either be executed in parallel, in sequence, or in" +
			" a mutual exclusion fashion. In addition, tasks can also belong to loops. When tasks can execute in" +
			" parallel, they are separated using the '&' symbol. When tasks must execute one after the other, they are" +
			" separated using the '<' symbol. When tasks are mutually exclusive, they are separated using the '|' symbol." +
			" When tasks belong to a loop, they either belong to its mandatory part, or to its optional part, but not both." +
			" When tasks belong to the mandatory part of the loop, they are parenthesised, and put next to the '+' symbol." +
			" When tasks belong to the optional part of the loop, they are parenthesised, and put next to the '?' symbol." +
			" Then, the two parts are themselves parenthesised. In the case where no constraints are given for a set of" +
			" tasks, they are separated using the ',' symbol.";

	private static final String SYSTEM_BASE_V2 = "You are a helpful assistant which aims at analysing and extracting" +
			" the relationships that exist between the different tasks of the textual description that is given to you." +
			" Your answer will be composed of one or several lines starting with the '-' symbol containing each an expression " +
			" representing one or several relationships between the tasks. The tasks that you discover can be related to other tasks in five different ways. (1) The tasks can be" +
			" ordered sequentially, meaning that some of them have to be executed before some others. In such a case, they" +
			" are separated by the '<' symbol. (2) The tasks can be mutually exclusive, meaning that only one of them" +
			" can be executed. In such a case, they are separated by the '|' symbol. (3) The tasks can be parallelizable, " +
			" meaning that they can all execute simultaneously. In such a case, they are separated by the '&' symbol." +
			" (4) The tasks can be repeated, meaning that they can be executed several times during the lifecycle of the" +
			" process. In such a case, they are put between parenthesis in which they are separated by a coma, and the '*'" +
			" symbol is put next to the closing parenthesis. (5) Finally, when no information is given about tasks, when you" +
			" need to list tasks without giving their relationship, or when you do not know how some tasks are related, you" +
			" can separate them using the ',' symbol. For instance, from the description 'I want to do A and B in parallel," +
			" followed by C or D', you can return the expression (A & B) < (C | D). Lastly, you must no use any other symbol" +
			" than those listed before.";

	private static final String SYSTEM_BASE_V2_CLEAN = "You are a helpful assistant whose goal is to analyze and extract" +
			" the relationships that exist between the different tasks of the textual description that is given to you" +
			" as input. Your answer will be composed of one or more lines starting with the symbol '-', each containing" +
			" an expression representing one or more relationships between the tasks of the textual description. The" +
			" tasks that you discover can be ordered relative to each other in five different ways: 1) They can be" +
			" ordered sequentially, meaning that some of them must be executed before others. In this case, you must" +
			" separate them with the symbol '<'. 2) They can be mutually exclusive, meaning that only one of them can" +
			" be executed. In this case, you must separate them with the symbol '|'. 3) They can be parallelizable," +
			" meaning that they can all be executed simultaneously. In this case, you must separate them with the symbol" +
			" '&'. 4) They can be repeated, that is, they can be executed multiple times during the lifecycle of the" +
			" process. In this case, you must separate them with commas and put them between parentheses, and the symbol" +
			" '*' must be added right after the closing parenthesis. 5) When no information is given about the tasks," +
			" when you need to list tasks without giving their relationship, or when you do not know how some tasks are" +
			" ordered, you must separate them with commas. As an example, if you receive as input the description 'I want" +
			" to do A and B in parallel, followed by C or D', you must answer '- (A & B) < (C | D)', which means that" +
			" 'A' and 'B' can be executed in parallel, and that both must be executed before 'C' and 'D', which are" +
			" mutually exclusive.";

	private static final String SYSTEM_BASE_V3 = "You are a helpful assistant which aims at analysing and extracting" +
			" the relationships that exist between the different tasks of the textual description that is given to" +
			" you. Your answer will be composed of one or several lines starting with the '-' symbol, each containing" +
			" an expression representing one or several relationships between the tasks. The tasks that you discover" +
			" can be related to each others in five different ways. (1) The tasks can be ordered sequentially," +
			" meaning that some of them have to be executed before some others. In such a case, they are separated" +
			" using the '<' symbol. (2) The tasks can be mutually exclusive, meaning that only one of them can be" +
			" executed. In such a case, they are separated using the '|' symbol. (3) The tasks can be parallelizable," +
			" meaning that they can all execute at the same time. In such a case, they are separated using the '&'" +
			" symbol. (4) The tasks can be repeated, meaning that they can be executed several times during the" +
			" lifecycle of the process. In such a case, they are put between parenthesis in which they are separated" +
			" by a coma, and the '*' symbol is put next to the closing parenthesis. (5) When no information is given" +
			" about tasks, when you need to list tasks without giving their relationship, or when you do not know how" +
			" some tasks are related, you can separate them using the ',' symbol. Lastly, when the process is" +
			" explicitly asked to finish after some tasks, these tasks should be followed by the sequence symbol '<'," +
			" itself followed by the termination symbol '#'. For instance, from the description 'I want to do A and B" +
			" in parallel, followed by C after which the process terminates, or D and E', you can return the expression" +
			" (A & B) < ((C < #) | (D, E)).";

	private static final String BASE_MODEL = "gpt-3.5-turbo-16k";
	private static final String BASE_MODEL_FOR_FORMATTING = "gpt-4.5-preview";
	private static final int MAX_TOKENS_FOR_FORMATTING = 4095;
	private static final String FINE_TUNED_MODEL_V1 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8VIytbMD";
	private static final String FINE_TUNED_MODEL_V2 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8YYQQ7th";
	private static final String FINE_TUNED_MODEL_V3 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8crA6qTP";
	private static final String FINE_TUNED_MODEL_V4 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8cvwp7h9";
	private static final String FINE_TUNED_MODEL_V5 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8dHiqPGf";
	private static final String FINE_TUNED_MODEL_V6 = "ft:gpt-3.5-turbo-0613:personal:inria-nivon-salaun:8dI6ExcU";
	private static final String FINE_TUNED_MODEL_V7 = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8hIk5iR2";
	private static final String FINE_TUNED_MODEL_V8 = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8hkVUi5I";
	private static final String FINE_TUNED_MODEL_V9 = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8hx8ltjM";
	private static final String FINE_TUNED_MODEL_V10 = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8hzIUOgr";
	private static final String FINE_TUNED_MODEL_V11 = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8i0CgIp8";
	private static final String FINE_TUNED_MODEL_V12_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8iKMx3VG";
	private static final String FINE_TUNED_MODEL_V13_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ifl3Me1";
	private static final String FINE_TUNED_MODEL_V14_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8igQLcob";
	private static final String FINE_TUNED_MODEL_V15_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ih6dAHN";
	private static final String FINE_TUNED_MODEL_V16_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ijUfE99";
	private static final String FINE_TUNED_MODEL_V17_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ikNfpHO";
	private static final String FINE_TUNED_MODEL_V18_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8il02MWF";
	private static final String FINE_TUNED_MODEL_V19_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ilD2hct";
	private static final String FINE_TUNED_MODEL_V20_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8ilmD8e2";
	private static final String FINE_TUNED_MODEL_V21_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8im1JRXa";
	private static final String FINE_TUNED_MODEL_V22_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8imEHg7b";
	private static final String FINE_TUNED_MODEL_V23_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8jjtJE5k";
	private static final String FINE_TUNED_MODEL_V24_L_GOOD = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8jkrc0d5";
	private static final String FINE_TUNED_MODEL_V25_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8jl6VUHM";
	private static final String FINE_TUNED_MODEL_V26_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8kEn8CNK";
	private static final String FINE_TUNED_MODEL_V27_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8krVLw0B";
	private static final String FINE_TUNED_MODEL_V28_L = "ft:gpt-3.5-turbo-1106:personal:inria-nivon-salaun:8qHIjuKQ";
	private static final String FINE_TUNED_MODEL_V29_L = "ft:gpt-3.5-turbo-0125:personal:inria-nivon-salaun:9b8P5X5g";
	private static final String FINE_TUNED_MODEL_V30_L = "ft:gpt-4o-2024-08-06:personal:inria-nivon-salaun:AmcqjZ5b";
	private static final String FINE_TUNED_MODEL_V31_L_NO_FORCED_END_EVENTS = "ft:gpt-4o-2024-08-06:personal:inria-nivon-salaun:AmimwWhn";
	private static final String FINE_TUNED_MODEL_V32_L_NO_FORCED_END_EVENTS = "ft:gpt-4o-2024-08-06:personal:inria-nivon-salaun:AnoT9dYH";
	private static final String URL = "https://api.openai.com/v1/chat/completions";
	private static final String REQUEST_METHOD = "POST";
	private static final String USER_ROLE = "user";
	private static final String SYSTEM_ROLE = "system";
	private static final double TEMPERATURE = 0; //Set to 0 so that the model behaves deterministically
	private static final double TOP_P = 0;
	private static final double FREQUENCE_PENALTY = 0;
	private static final double PRESENCE_PENALTY = 0;
	private static final int MAX_TOKENS = 2048;

	private ChatGPTManager()
	{

	}

	public static String generateAnswer(final String question,
										final String apiKey,
										final boolean tasksNamed) throws ExpectedException
	{
		final String textToUse;

		if (tasksNamed)
		{
			textToUse = question;
		}
		else
		{
			final String formattedText = ChatGPTManager.getFormattedTextFromRawText(question, apiKey);
			textToUse = formattedText.contains("already rephrased") ? question : formattedText;
		}

		return ChatGPTManager.getConstraintsFromText(textToUse, apiKey);
	}

	//Private methods

	private static String getFormattedTextFromRawText(final String rawText,
													  final String apiKey)
	{
		try
		{
			final URL url = new URL(URL);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(REQUEST_METHOD);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Authorization", "Bearer " + apiKey);
			connection.setDoOutput(true);

			final String body =
			"{" +
				"\"model\": \"" + BASE_MODEL_FOR_FORMATTING + "\"," +
				"\"messages\":" +
					"[" +
						"{\"role\": \"" + SYSTEM_ROLE + "\", \"content\": \"" + FORMATTING_SYSTEM_BASE_V3 + "\"}" + ", " +
						"{\"role\": \"" + USER_ROLE + "\", \"content\": \"" + rawText + "\"}" +
					"]," +
				"\"temperature\": " + TEMPERATURE + ", " +
				"\"top_p\": " + TOP_P + ", " +
				"\"max_tokens\": " + MAX_TOKENS_FOR_FORMATTING + /* ", " +
				"\"frequence_penalty\": " + FREQUENCE_PENALTY + ", " +
				"\"presence_penalty\": " + PRESENCE_PENALTY + */
			"}";

			MyOwnLogger.append(body);

			final OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
			writer.write(body);
			writer.flush();
			writer.close();

			final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			final StringBuilder response = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null)
			{
				response.append(line);
			}

			reader.close();

			System.out.println("Real answer: " + response);
			MyOwnLogger.append("Real answer: " + response);

			final int startIndex = response.indexOf("content") + 11;
			final int endIndex = response.indexOf("\"", startIndex);

			final String parseableResponse = response.substring(startIndex, endIndex);
			return parseableResponse;
			//return parseableResponse.substring(parseableResponse.indexOf("Rephrased Text:") + 15);
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String getConstraintsFromText(final String text,
												 final String apiKey) throws ExpectedException
	{
		try
		{
			final URL url = new URL(URL);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(REQUEST_METHOD);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Authorization", "Bearer " + apiKey);
			connection.setDoOutput(true);

			final String body = "{\"model\": \"" + FINE_TUNED_MODEL_V32_L_NO_FORCED_END_EVENTS + "\"," +
					"\"messages\": [" +
					"{\"role\": \"" + SYSTEM_ROLE + "\", \"content\": \"" + SYSTEM_BASE_V2_CLEAN + "\"}" + ", " +
					"{\"role\": \"" + USER_ROLE + "\", \"content\": \"" + text + "\"}" +
					"]," +
					"\"temperature\": " + TEMPERATURE + ", " +
					"\"top_p\": " + TOP_P + ", " +
					"\"max_tokens\": " + MAX_TOKENS +
					"}";

			MyOwnLogger.append(body);

			final OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
			writer.write(body);
			writer.flush();
			writer.close();

			final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			final StringBuilder response = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null)
			{
				response.append(line);
			}

			reader.close();

			//System.out.println("Real answer: " + response);

			final int startIndex = response.indexOf("content") + 11;
			final int endIndex = response.indexOf("\"", startIndex);

			return response.substring(startIndex, endIndex);
		}
		catch (IOException e)
		{
			throw new ExpectedException(e, ExceptionStatus.CHATGPT_IO);
		}
	}
}
