package tk.microdroid.blueirc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;

import javax.net.ssl.SSLSocket;

/**
 * {@code IO} manages I/O for the IRC connection.
 * Contains helper methods for safe and easier IRC command sending
 * An instance of {@code IO} is created upon {@code Socket} creation
 * in {@code Worker}
 * 
 * @see Worker#send(String)
 * @see Worker.socket
 * @see Worker.sslSocket
 * 
 */
public class IO {
    BufferedWriter writer;
    BufferedReader reader;
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
        if (data.length() > 508)
            data = data.substring(0, 508);
        for (String line : data.split("\n"))
            writer.write(line + "\r\n");
        writer.flush();
    }
    
    /**
     * Generates an IRC command from parameters.
     * 
     * @param cmd The IRC command
     * @param args IRC command arguments
     * @param msg IRC command's data that comes after the ' :'
     * @return
     */
    public static String compile(String cmd, String[] args, String msg) {
        return cmd + " " + concat(args, " ") + (!msg.isEmpty() ? " :" + msg : "");
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
    		for (String message : msg.split("\n"))
    			result.append(privmsg(target, message) + "\n");
    		return result.toString().trim();
    	} else if ((target + msg).length() > 498) {
            return compile("PRIVMSG", new String[] {target}, msg.substring(0, (510 - target.length())))
                    + "\n" + privmsg(target, msg.substring(510 - target.length()));
        } else
            return compile("PRIVMSG", new String[] {target}, msg);
    }
    
    /**
     * Concats a {@code String[]} to {@code String}
     * Delimited by {@code delimiter}
     *  
     * @param array The array that contains the Strings
     * @param delimiter The delimiter to be used between joined Strings
     * @return A String contains the values of {@code array} delimited with {@code delimiter}
     */
    public static String concat(String[] array, String delimiter) {
    	StringBuilder sb = new StringBuilder();
    	for (String element : array)
    		sb.append(delimiter + element);
    	return sb.toString().substring(delimiter.length());
    }
}
