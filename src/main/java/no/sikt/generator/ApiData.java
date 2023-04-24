package no.sikt.generator;

import static no.sikt.generator.ApplicationConstants.DOMAIN;
import static no.sikt.generator.ApplicationConstants.EXCLUDED_APIS;
import io.swagger.v3.oas.models.OpenAPI;
import nva.commons.core.attempt.Failure;
import nva.commons.core.attempt.Try;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;

public class ApiData implements Comparable<ApiData> {

    private final RestApi awsRestApi;
    private final OpenAPI openApi;
    private final String rawYaml;
    private final Stage stage;
    private static final Logger logger = LoggerFactory.getLogger(ApiData.class);

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

    public int getDashesInPath() {
        return Try.attempt(this::getNumberOfDashesInBasePath).orElse(this::handleGetDashesFailure);
    }

    private int handleGetDashesFailure(Failure failure) {
        logger.info("Using default dashes 0");
        logger.info(failure.getException().toString());
        return 0;
    }

    private int getNumberOfDashesInBasePath() {
        return (int) openApi.getServers()
                         .get(0)
                         .getVariables()
                         .get("basePath")
                         .getDefault()
                         .chars()
                         .filter(c -> c == '-')
                         .count();
    }

    public boolean isOnExcludeList() {
        return EXCLUDED_APIS.stream().anyMatch(e -> e.equals(awsRestApi.name()));
    }

    public static int sortByDate(ApiData apiData, ApiData otherApiData) {
        return otherApiData.getAwsRestApi().createdDate().compareTo(apiData.getAwsRestApi().createdDate());
    }

    public static int sortByDashes(ApiData apiData, ApiData otherApiData) {
        return Integer.compare(apiData.getDashesInPath(),otherApiData.getDashesInPath());
    }

    public static int sortByName(ApiData apiData, ApiData otherApiData) {
        return otherApiData.getAwsRestApi().name().compareTo(apiData.getAwsRestApi().name());
    }

    @Override
    public int compareTo(@NotNull ApiData o) {
        return awsRestApi.id().compareTo(o.getAwsRestApi().id());
    }
}
