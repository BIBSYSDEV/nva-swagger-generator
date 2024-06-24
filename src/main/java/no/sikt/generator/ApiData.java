package no.sikt.generator;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.generator.ApplicationConstants.DOMAIN;
import static no.sikt.generator.ApplicationConstants.EXCLUDED_APIS;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import nva.commons.core.attempt.Try;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.s3.model.S3Object;

public class ApiData {

    private final RestApi awsRestApi;
    private final OpenAPI openapiApiGateway;
    private Optional<OpenAPI> openapiApiGithub;
    private final String rawYaml;
    private final Stage stage;
    private static final Logger logger = LoggerFactory.getLogger(ApiData.class);

    public ApiData(RestApi awsRestApi, OpenAPI openapiApiGateway, String rawYaml, Stage stage) {
        this.awsRestApi = awsRestApi;
        this.openapiApiGateway = openapiApiGateway;
        this.rawYaml = rawYaml;
        this.stage = stage;
    }

    public RestApi getAwsRestApi() {
        return awsRestApi;
    }

    public OpenAPI getOpenapi() {
        return openapiApiGateway;
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
        return openapiApiGateway.getServers().stream().anyMatch(server -> server.getUrl().contains(DOMAIN));
    }

    public int getDashesInPath() {
        return Try.attempt(this::getNumberOfDashesInBasePath).orElse(this::handleGetDashesFailure);
    }

    public void setMatchingGithubOpenapi(List<Pair<S3Object, OpenAPI>> templateOpenapiDocs) {
        var title = this.openapiApiGateway.getInfo().getTitle();
        var matchingGithubOpenApi = templateOpenapiDocs.stream()
                                        .filter(templateOpenapiDoc -> templateOpenapiDoc.getRight()
                                                                          .getInfo()
                                                                          .getTitle()
                                                                          .equals(title))
                                        .findFirst();
        if (matchingGithubOpenApi.isPresent()) {
            logger.info("Using matching github openapi at " + matchingGithubOpenApi.get().getLeft().key()+ " for " + title);
            this.openapiApiGithub = Optional.of(matchingGithubOpenApi.get().getRight());
        } else {
            logger.warn("No matching github openapi found for " + title);
            this.openapiApiGithub = Optional.empty();
        }
;
    }

    public ApiData setEmptySchemasIfNull() {
        if (isNull(this.openapiApiGateway.getComponents())) {
            this.openapiApiGateway.setComponents(new Components());
        }
        if (isNull(this.openapiApiGateway.getComponents().getSchemas())) {
            this.openapiApiGateway.getComponents().setSchemas(new HashMap<>());
        }
        return this;
    }

    public ApiData overridePropsFromGithub() {
        if (this.openapiApiGithub.isPresent()) {
            this.openapiApiGateway.getPaths().forEach((pathKey, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    if (nonNull(operation.getParameters())) {
                        operation.getParameters().forEach(parameter -> {
                            var operationInGithub = Optional.ofNullable(openapiApiGithub.get().getPaths().get(pathKey).readOperationsMap()
                                                                             .get(httpMethod));
                            operationInGithub.ifPresent(value -> operation.setParameters(value.getParameters()));
                        });
                    }
                });
            });
            if (nonNull(this.openapiApiGithub.get().getComponents())
                && nonNull(this.openapiApiGithub.get().getComponents().getSchemas())
            ) {
                this.openapiApiGithub.get().getComponents().getSchemas().forEach(((key,value) -> {
                    if (isNull(this.openapiApiGateway.getComponents().getSchemas().get(key))) {
                        logger.info("Adding schema " + key + " from github");
                        this.openapiApiGateway.getComponents().getSchemas().put(key, value);
                    }
                }));
            }

        }
        return this;
    }

    @JacocoGenerated
    private int handleGetDashesFailure(Failure failure) {
        logger.info("Using default dashes 0");
        logger.info(failure.getException().toString());
        return 0;
    }

    private int getNumberOfDashesInBasePath() {
        return (int) openapiApiGateway.getServers()
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
        return apiData.getName().compareTo(otherApiData.getName());
    }
}
