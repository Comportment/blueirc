package tk.microdroid.blueirc;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An IRC message container.
 * The purpose of this is tracking messages, so when you send
 * A message, a new UID is generated and returned from {@link Worker#send(String)}.
 * When an event such as DATA_SEND_FAILED or DATA_SENT are fired, arguments are
 * An instance of {@code Message}, this way you get the ID back and match it with
 *
 */
public class Message {
	private final static AtomicInteger idGenerator = new AtomicInteger(0);
	private String msg = "";
	private int id = 0;
	
	Message(String line) {
		id = idGenerator.incrementAndGet();
		msg = line;
	}
	
	Message(String line, int id) {
		this.id = id;
		msg = line;
	}
	
	/**
	 * Get the contained message.
	 * 
	 * @return The message
	 */
	public String getMsg() {
		return msg;
	}
	
	/**
	 * Get the contained message id.
	 * 
	 * @return The message id
	 */
	public int getId() {
		return id;
	}
}