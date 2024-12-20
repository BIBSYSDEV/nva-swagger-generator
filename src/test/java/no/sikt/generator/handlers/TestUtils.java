package no.sikt.generator.handlers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static no.sikt.generator.ApplicationConstants.readOpenApiBucketName;
import static no.sikt.generator.Utils.readResource;
import static no.sikt.generator.Utils.readResourceOptional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import no.sikt.generator.ApplicationConstants;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.attempt.Try;
import nva.commons.core.paths.UnixPath;
import org.apache.commons.lang3.tuple.Pair;
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
import software.amazon.awssdk.services.apigateway.model.GetRestApisRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionResponse;
import software.amazon.awssdk.services.apigateway.model.UpdateStageRequest;
import software.amazon.awssdk.services.apigateway.model.UpdateStageResponse;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.s3.S3Client;

public class TestUtils {

    private static OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    private static TestCase loadTestCase(String filename, Optional<String> filenameGithub) {
        var id = filename.substring(filename.lastIndexOf('/') + 1).split(".yaml")[0];
        var fileContentApiGateway = readResource(filename);
        var fileContentGithub = filenameGithub.map(Utils::readResource);
        var openApi = openApiParser
                          .readContents(fileContentApiGateway)
                          .getOpenAPI();
        return new TestCase(id, openApi.getInfo().getTitle(), fileContentApiGateway, fileContentGithub);
    }

    private static RestApi buildRestApiFromTestCase(TestCase testCase) {
        var id = testCase.getId();
        var created = Instant.now();
        return RestApi.builder().name(testCase.getName()).id(id).createdDate(created).build();
    }

    public static void loadGithubOpenapiFile(S3Driver s3Driver, TestCase testCase) {
        if (testCase.getContentGithub().isPresent()) {
            Try.attempt(() ->
                            s3Driver.insertFile(UnixPath.of(String.valueOf(Path.of(testCase.getId() + ".yaml"))),
                                                testCase.getContentGithub().get())
            );
        }
    }

    public static void setupTestcasesFromFiles(S3Client s3Client,
                                               ApiGatewayAsyncClient apiGatewayAsyncClient,
                                               CloudFrontClient cloudFrontClient,
                                               String folder,
                                               List<Pair<String, Optional<String>>> fileNames) {
        var filePrefix = "openapi_docs/" + ((isNull(folder)) ? "" : folder + "/");
        var testCases = fileNames.stream().map(fileName -> loadTestCase(filePrefix + fileName.getLeft(),
                                                                        fileName.getRight()
                                                                            .map(fn -> filePrefix + fn))).toList();

        var s3driver = new S3Driver(s3Client, readOpenApiBucketName());
        testCases.forEach(tc -> loadGithubOpenapiFile(s3driver, tc));

        var getRestApisResponse = GetRestApisResponse.builder().items(
            testCases.stream().map(TestUtils::buildRestApiFromTestCase).collect(Collectors.toList())
        ).build();



        var getStagesResponse = GetStagesResponse.builder().item(
            List.of(
                Stage.builder().stageName("Prod").documentationVersion("doc1").build(),
                Stage.builder().stageName("Stage").documentationVersion("doc1").build()
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
        var updateStageResponse = UpdateStageResponse.builder().build();

        when(apiGatewayAsyncClient.getRestApis()).thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(apiGatewayAsyncClient.getRestApis(any(GetRestApisRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(apiGatewayAsyncClient.getDocumentationParts(any(GetDocumentationPartsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getDocPartsResponse));
        when(apiGatewayAsyncClient.getStages(any(GetStagesRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getStagesResponse));
        when(apiGatewayAsyncClient.updateStage(any(UpdateStageRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(updateStageResponse));
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

        var createInvalidationResponse = CreateInvalidationResponse.builder().build();
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
            .thenReturn(createInvalidationResponse);
    }
}
