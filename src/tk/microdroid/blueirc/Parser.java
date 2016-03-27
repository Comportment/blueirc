package tk.microdroid.blueirc;

import java.util.ArrayList;
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
 * @see MessageType
 * 
 */
public class Parser {
	public MessageType type;
	public boolean failed = false;

	// _cmdArgs is command arguments without splitting
	public boolean hasIdent;
	public String raw, server, numberAction, nick, username, host;
	public String action;
	public String msg;
	public String cmd;
	public String _cmdArgs;
	public List<String> actionArgs = new ArrayList<>();
	public List<String> cmdArgs = new ArrayList<>();

	/**
	 * Parses an IRC command received from server.
	 * 
	 * @param line the IRC command received from the server
	 */

	public Parser(String line) {
		try {
			raw = line;
			failed = false;
			server = numberAction = nick = username = host = action = msg = cmd = _cmdArgs = "";
			hasIdent = true;
			line = line.trim();
			String[] lineWords = line.split(" ");
			if (!line.startsWith(":")) {
				hasIdent = false;
				type = MessageType.OTHER;
				if (line.contains(" :")) {
					String[] splitter = line.split(" :", 2);
					String[] splitter2 = splitter[0].split(" ");
					action = splitter2[0];
					for (int i=1; i < splitter2.length; i++)
						actionArgs.add(splitter2[i]);
					msg = splitter[1];
					if (splitter2.length == 2)
						_cmdArgs = splitter2[1];
				} else if (lineWords.length == 2) {
					String[] splitter = line.split(" ", 2);
					action = lineWords[0];
					cmd = lineWords[1];
					msg = splitter[0];
				}
			} else if (lineWords.length >= 3) {
				if (lineWords[1].matches("\\d\\d\\d")) { // Must be 3 digits, as in rfc1459#section-2.4
					type = MessageType.NUMERIC;
					server = lineWords[0].substring(1);
					numberAction = lineWords[1];
					String afterNumber = line.split(Pattern.quote(numberAction), 2)[1].trim();
					if (afterNumber.contains(" :")) {
						String[] splitter = afterNumber.split(" :", 2);
						if (!splitter[0].isEmpty())
							for (String actionArg : splitter[0].split(" "))
								actionArgs.add(actionArg);
						msg = splitter[1];
						String[] splitter2 = msg.split(" ", 2);
						cmd = splitter2[0];
						if (splitter2.length == 2)
							_cmdArgs = splitter2[1];
						for (String cmdArg : _cmdArgs.split(" "))
							cmdArgs.add(cmdArg);
					} else {
						for (String actionArg : afterNumber.split(" "))
							actionArgs.add(actionArg);
					}
				} else {
					type = MessageType.ACTION;
					if (lineWords[0].matches(":.+!.+@.+")) {
						String[] splitter = lineWords[0].split("[!@]");
						nick = splitter[0].substring(1);
						username = splitter[1];
						host = splitter[2];
					} else hasIdent = false;
					action = lineWords[1];
					String afterAction = line.split(Pattern.quote(action), 2)[1];
					if (!afterAction.startsWith(" :"))
						afterAction = afterAction.substring(1);
					if (afterAction.contains(" :")) {
						String[] splitter = afterAction.split(" :", 2);
						if (!splitter[0].isEmpty())
							for (String actionArg : splitter[0].split(" "))
								actionArgs.add(actionArg);
						if (splitter.length == 2) {
							msg = splitter[1];
							String[] splitter2 = msg.split(" ", 2);
							cmd = splitter2[0];
							if (splitter2.length == 2) {
								_cmdArgs = splitter2[1];
								for (String cmdArg : _cmdArgs.split(" "))
									cmdArgs.add(cmdArg);
							}
						}
					} else {
						for (String actionArg : afterAction.split(" "))
							actionArgs.add(actionArg);
					}
				}
			} else {
				type = MessageType.UNKNOWN;
				failed = true;
			}
			if (msg.equals("") && actionArgs.size() == 1)
				msg = actionArgs.get(0);
		} catch (Exception e) {
			type = MessageType.UNKNOWN;
			e.printStackTrace();
			failed = true;
		}
	}
}
