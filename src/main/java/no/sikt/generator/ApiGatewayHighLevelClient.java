package no.sikt.generator;

import static nva.commons.core.attempt.Try.attempt;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsResponse;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.PatchOperation;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionRequest;

public class ApiGatewayHighLevelClient {

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

    public List<String> fetchStages(String apiId) {
        var request = GetStagesRequest.builder().restApiId(apiId).build();
        var stages = attempt(() -> apiGatewayClient.getStages(request).get()).orElseThrow();

        return stages.item().stream().map(Stage::stageName).collect(Collectors.toList());
    }

    public GetRestApisResponse getRestApis() {
        return attempt(() -> apiGatewayClient.getRestApis().get()).orElseThrow();
    }

    public GetDocumentationVersionsResponse fetchVersions(String id) {
        var listRequest = GetDocumentationVersionsRequest.builder().restApiId(id).build();

        var existingVersions
            = attempt(() -> apiGatewayClient.getDocumentationVersions(listRequest).get()).orElseThrow();
        return existingVersions;
    }

    public void updateDocumentation(String id, String version) {
        var updateRequest = UpdateDocumentationVersionRequest.builder()
                                .restApiId(id)
                                .documentationVersion(version)
                                .patchOperations(
                                    PatchOperation.builder().op("replace").path("/description").build()
                                ).build();

        attempt(() -> apiGatewayClient.updateDocumentationVersion(updateRequest).get()).orElseThrow();
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




