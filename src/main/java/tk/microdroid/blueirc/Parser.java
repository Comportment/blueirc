package tk.microdroid.blueirc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code Parser} holds a received IRC command and parses it internally.
 * A {@code Parser} parses the IRC command in the constructor itself.
 * {@code Parser} instances are passed internally in this library, rather
 * Than the raw IRC command, User and Channel messages {@code ArrayList} stores
 * Instances of {@code Parser} hmm
 * 
 * @see User
 * @see Channel
 *
 */
public class Parser {
	private MessageType type;
	private boolean failed = false;

	// _cmdArgs is command arguments without splitting
	private boolean hasIdent = false;
	private String raw = "";
	private String server = "";
	private String numberAction = "";
	private String nick = "";
	private String username = "";
	private String host = "";
	private String action = "";
	private String msg = "";
	private String cmd = "";
	private List<String> actionArgs = new ArrayList<>();
	private List<String> cmdArgs = new ArrayList<>();

	/**
	 * Parses an IRC command received from server.
	 * 
	 * @param line the IRC command received from the server
	 */
	public Parser(String line) {
		try {
			raw = line;
			hasIdent = true;
			line = line.trim();
			String[] lineWords = line.split(" ");

			if (!line.startsWith(":")) {
				type = MessageType.OTHER;
				String[] splitter = line.split(" :", 2);
				String[] splitter2 = splitter[0].split(" ");
				action = splitter2[0];
				actionArgs.addAll(Arrays.asList(splitter2));
				msg = splitter[1];
				//if (splitter2.length == 2) {
					//_cmdArgs = splitter2[1];
				//}
			} else if (lineWords.length >= 3) {
				if (lineWords[1].matches("\\d\\d\\d")) {
					type = MessageType.NUMERIC;
					server = lineWords[0].substring(1);
					numberAction = lineWords[1];
					String afterNumber = line.split(Pattern.quote(numberAction), 2)[1].trim();

					if (afterNumber.contains(" :")) {
						String[] splitter = afterNumber.split(" :", 2);

						if (!splitter[0].isEmpty()) {
						    Util.addSplitArgs(actionArgs, splitter[0]);
						}
						msg = splitter[1];
						String[] splitter2 = msg.split(" ", 2);
						cmd = splitter2[0];
						Util.addSplitArgs(cmdArgs, splitter2[1]);

					} else {
					    Util.addSplitArgs(actionArgs, afterNumber);
					}
				} else {
					type = MessageType.ACTION;
					if (lineWords[0].matches(":.+!.+@.+")) {
						String[] splitter = lineWords[0].split("[!@]");
						nick = splitter[0].substring(1);
						username = splitter[1];
						host = splitter[2];
					} else {
						hasIdent = false;
					}
					action = lineWords[1];
					String afterAction = line.split(Pattern.quote(action), 2)[1];
					if (!afterAction.startsWith(" :")) {
						afterAction = afterAction.substring(1);
					}
					if (afterAction.contains(" :")) {
						String[] splitter = afterAction.split(" :", 2);
						if (!splitter[0].isEmpty()) {
						    Util.addSplitArgs(actionArgs, splitter[0]);
						}
						if (splitter.length == 2) {
							msg = splitter[1];
							String[] splitter2 = msg.split(" ", 2);
							cmd = splitter2[0];
							if (splitter2.length == 2) {
								Util.addSplitArgs(cmdArgs, splitter2[1]);
							}
						}
					} else {
					    Util.addSplitArgs(cmdArgs, afterAction);
					}
				}
			} else {
				type = MessageType.UNKNOWN;
				failed = true;
			}
			if (msg.equals("") && actionArgs.size() == 1) {
				msg = actionArgs.get(0);
			}
		} catch (Exception e) {
			type = MessageType.UNKNOWN;
			e.printStackTrace();
			failed = true;
		}
	}
}