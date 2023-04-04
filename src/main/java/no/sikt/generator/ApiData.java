package no.sikt.generator;

import static no.sikt.generator.ApplicationConstants.DOMAIN;
import static no.sikt.generator.ApplicationConstants.EXCLUDED_APIS;
import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;

public class ApiData implements Comparable<ApiData> {

    private final RestApi awsRestApi;
    private final OpenAPI openApi;
    private final String rawYaml;
    private final Stage stage;

    public ApiData(RestApi awsRestApi, OpenAPI openApi, String rawYaml, Stage stage) {
        this.awsRestApi = awsRestApi;
        this.openApi = openApi;
        this.rawYaml = rawYaml;
        this.stage = stage;
    }

    public RestApi getAwsRestApi() {
        return awsRestApi;
    }

    public OpenAPI getOpenApi() {
        return openApi;
    }

    public String getName() {
        return awsRestApi.name();
    }

    public String getRawYaml() {
        return rawYaml;
    }

    public String getCurrentDocVersion() {
        return stage.documentationVersion();
    }

    public boolean hasCorrectDomain() {
        return openApi.getServers().stream().anyMatch(server -> server.getUrl().contains(DOMAIN));
    }

    public boolean isOnExcludeList() {
        return EXCLUDED_APIS.stream().anyMatch(e -> e.equals(awsRestApi.name()));
    }

    @Override
    public int compareTo(@NotNull ApiData o) {
        return awsRestApi.id().compareTo(o.getAwsRestApi().id());
    }
}
