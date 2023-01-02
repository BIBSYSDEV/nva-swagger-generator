package no.sikt.generator;

import io.swagger.v3.oas.models.OpenAPI;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class ApiData implements Comparable<ApiData> {

    private final RestApi awsRestApi;
    private final OpenAPI openApi;
    private final String rawYaml;

    public ApiData(RestApi awsRestApi, OpenAPI openApi, String rawYaml) {
        this.awsRestApi = awsRestApi;
        this.openApi = openApi;
        this.rawYaml = rawYaml;
    }

    public RestApi getAwsRestApi() {
        return awsRestApi;
    }

    public OpenAPI getOpenApi() {
        return openApi;
    }

    public String getRawYaml() {
        return rawYaml;
    }

    @Override
    public int compareTo(@NotNull ApiData o) {
        return awsRestApi.id().compareTo(o.getAwsRestApi().id());
    }
}
