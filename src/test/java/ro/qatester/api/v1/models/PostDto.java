package ro.qatester.api.v1.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostDto {
    private int id;
    private String body;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("likes_count")
    private int likesCount;

    @JsonProperty("is_pinned")
    private boolean pinned;

    @JsonProperty("is_edited")
    private boolean edited;

    private PollDto poll;

    public int getId() { return id; }
    public String getBody() { return body; }
    public String getImageUrl() { return imageUrl; }
    public int getLikesCount() { return likesCount; }
    public boolean isPinned() { return pinned; }
    public boolean isEdited() { return edited; }
    public PollDto getPoll() { return poll; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PollDto {
        private int id;
        private String question;

        @JsonProperty("total_votes")
        private int totalVotes;

        private List<OptionDto> options;

        public int getId() { return id; }
        public String getQuestion() { return question; }
        public int getTotalVotes() { return totalVotes; }
        public List<OptionDto> getOptions() { return options; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionDto {
        private int id;

        @JsonProperty("option_text")
        private String text;

        private int votes;

        public int getId() { return id; }
        public String getText() { return text; }
        public int getVotes() { return votes; }
    }
}
