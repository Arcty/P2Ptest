package tbs.p2p;

/**
 * Created by Michael on 5/12/2015.
 */
public class Message {
    public static final String MESSAGE_SEPARATOR = "///,///,///";
    public Message(String message, MessageType messageType) {
        this.message = message;
        this.messageType = messageType;
    }

    public String message;
    public MessageType messageType;

    public enum MessageType {
        SEND_MESSAGE, SEND_FILE, SEND_COMMAND
    }
}
