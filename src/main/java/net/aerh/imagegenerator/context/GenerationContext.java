package net.aerh.imagegenerator.context;

import java.util.Objects;

public record GenerationContext(String channelId, GenerationFeedback feedback, boolean aprilFools) {

    public GenerationContext {
        Objects.requireNonNull(feedback, "feedback");
    }

}
