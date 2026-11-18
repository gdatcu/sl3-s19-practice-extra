package ro.qatester.api.v1.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)    // nu trimitem câmpurile null (ex: postare fără sondaj)
public class CreatePostRequest {
    private final String body;
    private final String image;
    private final Poll poll;

    public CreatePostRequest(String body, String image, Poll poll) {
        this.body = body;
        this.image = image;
        this.poll = poll;
    }

    public String getBody() { return body; }
    public String getImage() { return image; }
    public Poll getPoll() { return poll; }

    public static class Poll {
        private final String question;
        private final List<String> options;

        public Poll(String question, List<String> options) {
            this.question = question;
            this.options = options;
        }

        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
    }
}
