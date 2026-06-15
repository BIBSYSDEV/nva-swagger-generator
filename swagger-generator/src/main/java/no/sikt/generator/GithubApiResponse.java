package no.sikt.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubApiResponse {

    @JsonProperty("zipball_url")
    public String zipUrl;

    public GithubApiResponse() {
    }

    public GithubApiResponse(String zipUrl) {
        this.zipUrl = zipUrl;
    }

    @Override
    public String toString() {
        return "{"
                + "\"zipball_url\": \"" + zipUrl + "\""
                + "}";
    }
}
