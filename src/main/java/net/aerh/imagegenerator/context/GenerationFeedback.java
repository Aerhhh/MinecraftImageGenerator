package net.aerh.imagegenerator.context;

@FunctionalInterface
public interface GenerationFeedback {

    void sendMessage(String message, boolean ephemeral);

}
