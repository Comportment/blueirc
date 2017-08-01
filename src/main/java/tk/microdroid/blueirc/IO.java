package tk.microdroid.blueirc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.net.ssl.SSLSocket;

/**
 * {@code IO} manages I/O for the IRC connection.
 * Contains helper methods for safe and easier IRC command sending
 * An instance of {@code IO} is created upon {@code Socket} creation
 * in {@code Worker}
 */
public class IO {
    private BufferedWriter writer;
    private BufferedReader reader;
    String line;

	public IO(Socket socket) throws IOException {
		writer = new BufferedWriter(new OutputStreamWriter(
				socket.getOutputStream(), Charset.forName("UTF-8")));
		reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
	}

	public IO(SSLSocket socket) throws IOException {
		writer = new BufferedWriter(new OutputStreamWriter(
				socket.getOutputStream(), Charset.forName("UTF-8")));
		reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
	}

	/**
	 * Reads a line from the socket.
	 * Updates the global {@code line} variable with the new line
	 * 
	 * @return The read line
	 * @throws IOException When unable to read from the stream
	 */
	public String read() throws IOException {
		line = reader.readLine();
		return line;
	}

	/**
	 * Flushes {@code data} to the socket stream.
	 * If {@code data} length is more than 510, the data is trimmed to 510 characters
	 * If {@code data} contains newlines, each line is flushed on its own
	 * 
	 * @param data The data to be sent
	 * @throws IOException When unable to write to the stream
	 */
    public void write(String data) throws IOException {
        if (data.length() > 510) data = data.substring(0, 510);
		Arrays.stream(data.split("\n")).forEach(line -> {
			try {
				writer.write(line + "\r\n");
			} catch (IOException e) {
				System.err.println("Error occurred when writing a line to the writer.\n\r" + line);
			}
		});
        writer.flush();
    }
    
    /**
     * Generates an IRC command from parameters.
     * 
     * @param cmd The IRC command
     * @param args IRC command arguments
     * @param msg IRC command's data that comes after the ' :'
     * @return The IRC command
     */
    public static String compile(String cmd, String[] args, String msg) {
        return cmd + " " + concat(args, " ") + (!msg.equals("") ? " :" + msg : "");
    }

    /**
     * Generates a PRIVMSG IRC command.
     * Just a safer way to send PRIVMSGs
     * If {@code msg} contains newlines, it generates multiple commands
     * 
     * @param target The receiver of {@code msg}
     * @param msg The message to be sent
     * @return The raw IRC command
     */
    public static String privmsg(String target, String msg) {
    	if (msg.contains("\n")) {
    		StringBuilder result = new StringBuilder();
			Arrays.stream(msg.split("\n")).forEach(message -> result.append(privmsg(target, message)).append("\n"));
    		return (result + "").trim();
    	} else if ((target + msg).length() > 498) {
            return compile("PRIVMSG", new String[]{target}, String.join("\n", msg.substring(0, (510 - target.length())), privmsg(target, msg.substring(510 - target.length()))));
        } else {
			return compile("PRIVMSG", new String[]{target}, msg);
		}
    }
    
    /**
     * Concatenates a {@code String[]} to {@code String}
     * Delimited by {@code delimiter}
     *  
     * @param array The array that contains the Strings
     * @param delimiter The delimiter to be used between joined Strings
     * @return A String contains the values of {@code array} delimited with {@code delimiter}
     */
    public static String concat(String[] array, String delimiter) {
    	StringBuilder sb = new StringBuilder();
		Arrays.stream(array).forEach(element -> sb.append(delimiter).append(element));
    	return (sb + "").substring(delimiter.length());
    }
}