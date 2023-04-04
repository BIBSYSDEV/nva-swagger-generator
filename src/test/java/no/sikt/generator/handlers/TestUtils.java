package no.sikt.generator.handlers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.generator.Utils.readResource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionResponse;
import software.amazon.awssdk.services.apigateway.model.DeleteDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.DeleteDocumentationVersionResponse;
import software.amazon.awssdk.services.apigateway.model.DocumentationPart;
import software.amazon.awssdk.services.apigateway.model.DocumentationPartLocation;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationPartsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationPartsResponse;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsResponse;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetExportResponse;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionResponse;

public class TestUtils {

    private static OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    private static TestCase loadTestCase(String filename) {
        var id = filename.substring(filename.lastIndexOf('/') + 1).split(".yaml")[0];
        var fileContent = readResource(filename);
        var openApi = openApiParser
                          .readContents(fileContent)
                          .getOpenAPI();
        return new TestCase(id, openApi.getInfo().getTitle());
    }

    private static RestApi buildRestApiFromTestCase(TestCase testCase) {
        var id = testCase.getId();
        var created = Instant.now();
        return RestApi.builder().name(testCase.getName()).id(id).createdDate(created).build();
    }

    public static void setupTestcasesFromFiles(ApiGatewayAsyncClient apiGatewayAsyncClient, String folder,
                                               List<String> fileNames) {
        var filePrefix = "openapi_docs/" + ((folder == null) ? "" : folder + "/");
        var testCases = fileNames.stream().map(f -> filePrefix + f).map(TestUtils::loadTestCase);

        var getRestApisResponse = GetRestApisResponse.builder().items(
            testCases.map(TestUtils::buildRestApiFromTestCase).collect(Collectors.toList())
        ).build();

        var getStagesResponse = GetStagesResponse.builder().item(
            List.of(
                Stage.builder().stageName("Prod").build(),
                Stage.builder().stageName("Stage").build()
            )
        ).build();

        var docPartLoc1 = DocumentationPartLocation.builder().name("loc1").path("/1").build();
        var docPartLoc2 = DocumentationPartLocation.builder().name("loc2").path("/2").build();
        var getDocPartsResponse = GetDocumentationPartsResponse.builder().items(
            DocumentationPart.builder().id("1").location(docPartLoc1).properties("prop1").build(),
            DocumentationPart.builder().id("2").location(docPartLoc1).properties("prop2").build(),
            DocumentationPart.builder().id("3").location(docPartLoc2).properties("prop3").build()
        ).build();

        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().build();
        var createDocumentationVersionResponse = CreateDocumentationVersionResponse.builder().build();
        var deleteDocumentationVersionResponse = DeleteDocumentationVersionResponse.builder().build();
        var updateDocumentationVersionResponse = UpdateDocumentationVersionResponse.builder().build();

        when(apiGatewayAsyncClient.getRestApis()).thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(apiGatewayAsyncClient.getDocumentationParts(any(GetDocumentationPartsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getDocPartsResponse));
        when(apiGatewayAsyncClient.getStages(any(GetStagesRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getStagesResponse));
        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));
        when(apiGatewayAsyncClient.createDocumentationVersion(any(CreateDocumentationVersionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(createDocumentationVersionResponse));
        when(apiGatewayAsyncClient.deleteDocumentationVersion(any(DeleteDocumentationVersionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(deleteDocumentationVersionResponse));
        when(apiGatewayAsyncClient.updateDocumentationVersion(any(UpdateDocumentationVersionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(updateDocumentationVersionResponse));

        when(apiGatewayAsyncClient.getExport(any(GetExportRequest.class)))
            .thenAnswer(invocation -> {
                var requestedId = invocation.getArgument(0, GetExportRequest.class).restApiId();
                var fileContent = readResource(filePrefix + requestedId + ".yaml");
                var sdkBody = SdkBytes.fromString(fileContent, UTF_8);
                var response =  GetExportResponse.builder()
                                    .body(sdkBody)
                                    .build();
                return CompletableFuture.completedFuture(response);
            });
    }
}
