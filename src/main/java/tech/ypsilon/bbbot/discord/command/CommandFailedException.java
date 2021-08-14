package tech.ypsilon.bbbot.discord.command;

public class CommandFailedException extends RuntimeException {

    public CommandFailedException() {
        this("Es ist ein Fehler aufgetreten");
    }

    public CommandFailedException(String message) {
        super(message);
    }

}
