package no.sikt.generator;

import static no.sikt.generator.ApplicationConstants.APPLICATION_YAML;
import static no.sikt.generator.ApplicationConstants.EXPORT_TYPE_OA_3;
import static nva.commons.core.attempt.Try.attempt;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationPartsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsResponse;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.Op;
import software.amazon.awssdk.services.apigateway.model.PatchOperation;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateStageRequest;

public class ApiGatewayHighLevelClient {
    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayHighLevelClient.class);
    public static final String DOCUMENTATION_VERSION_PATH = "/documentationVersion";
    public static final int MAX_API_LIMIT = 500;
    public static final int LIMIT = 500;
    private final ApiGatewayAsyncClient apiGatewayClient;

    public ApiGatewayHighLevelClient(ApiGatewayAsyncClient apiGatewayClient) {
        this.apiGatewayClient = apiGatewayClient;
    }

    public String fetchApiExport(String apiId, String stage, String contentType, String exportType) {
        var getExportRequest = GetExportRequest.builder()
                                   .restApiId(apiId)
                                   .stageName(stage)
                                   .accepts(contentType)
                                   .exportType(exportType)
                                   .build();

        var export = attempt(() -> apiGatewayClient.getExport(getExportRequest).get())
                         .orElseThrow();

        return export.body().asString(StandardCharsets.UTF_8);
    }

    public List<Stage> fetchStages(String apiId) {
        var request = GetStagesRequest.builder().restApiId(apiId).build();
        var stages = attempt(() -> apiGatewayClient.getStages(request).get()).orElseThrow();
        return stages.item();
    }

    public void setStageDocVersion(String apiId, String stage, String docVersion) {
        var request = UpdateStageRequest.builder().restApiId(apiId).stageName(stage).patchOperations(
            PatchOperation.builder().op(Op.REPLACE).path(DOCUMENTATION_VERSION_PATH).value(docVersion).build()
        ).build();
        attempt(() -> apiGatewayClient.updateStage(request).get()).orElseThrow();
    }

    public ApiData fetchApiDataForStage(RestApi api, String stageName, OpenAPIV3Parser openApiParser) {
        var stages = fetchStages(api.id());
        var productionStage =
            stages.stream().filter(s -> stageName.equals(s.stageName())).findFirst().orElse(null);

        if (productionStage != null) {
            var yaml = fetchApiExport(
                api.id(),
                stageName,
                APPLICATION_YAML,
                EXPORT_TYPE_OA_3
            );
            var parseResult = openApiParser.readContents(yaml);
            return new ApiData(api, parseResult.getOpenAPI(), yaml, productionStage);
        } else {
            logger.warn("API {} ({}) does not have stage {}. Stages found: {}", api.name(), api.id(),
                        stageName, stages);
            return null;
        }
    }

    public GetRestApisResponse getRestApis() {
        var request = GetRestApisRequest.builder()
                          .limit(MAX_API_LIMIT)
                          .build();
        return attempt(() -> apiGatewayClient.getRestApis(request).get()).orElseThrow();
    }

    public GetDocumentationVersionsResponse fetchVersions(String id) {
        var listRequest = GetDocumentationVersionsRequest.builder().restApiId(id).limit(LIMIT).build();

        var existingVersions
            = attempt(() -> apiGatewayClient.getDocumentationVersions(listRequest).get()).orElseThrow();
        return existingVersions;
    }

    public int fetchDocumentationPartsHash(String apiId) {

        var getDocumentationVersionRequest = GetDocumentationPartsRequest.builder()
                                                 .restApiId(apiId)
                                                 .limit(LIMIT)
                                                 .build();
        var documentParts =
            attempt(() -> apiGatewayClient.getDocumentationParts(getDocumentationVersionRequest).get()).orElseThrow();

        return documentParts.items().hashCode();
    }

    public void createDocumentation(String id, String version, String stage) {
        var createRequest = CreateDocumentationVersionRequest.builder()
                                .restApiId(id)
                                .stageName(stage)
                                .documentationVersion(version)
                                .build();
        attempt(() -> apiGatewayClient.createDocumentationVersion(createRequest).get()).orElseThrow();
    }
}




