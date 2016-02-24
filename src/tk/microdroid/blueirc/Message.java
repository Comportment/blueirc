package tk.microdroid.blueirc;

import java.util.concurrent.atomic.AtomicInteger;

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
