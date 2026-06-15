package no.sikt.generator.handlers;

import static no.sikt.generator.handlers.TestUtils.setupTestcasesFromFiles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.ApplicationConstants;
import no.unit.nva.stubs.FakeS3Client;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.DocumentationVersion;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesResponse;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateStageRequest;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;

class PublishDocumentationsHandlerTest {

    private final CloudFrontClient cloudFrontClient = Mockito.mock(CloudFrontClient.class);
    private PublishDocumentationsHandler handler;
    private ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    private ApiGatewayAsyncClient apiGatewayAsyncClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        Supplier<ApiGatewayAsyncClient> mockSupplier = mock(Supplier.class);
        apiGatewayAsyncClient = mock(ApiGatewayAsyncClient.class);
        when(mockSupplier.get()).thenReturn(apiGatewayAsyncClient);
        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(mockSupplier);
        setupTestcasesFromFiles(new FakeS3Client(), apiGatewayAsyncClient, cloudFrontClient, null, List.of(
            ImmutablePair.of("api-a.yaml", Optional.empty()), ImmutablePair.of("api-a.yaml", Optional.empty()))
        );
        handler = new PublishDocumentationsHandler(mockSupplier);
    }

    @Test
    void shouldNotPerformCreateOrUpdateStageWhenDocVersionExistsAndItsAssociatedWithProdStage() {
        var expectedHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash("");
        var expectedVersion = ApplicationConstants.VERSION_NAME + "-" + expectedHash;

        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().items(
            DocumentationVersion.builder().version(expectedVersion).build()
        ).build();

        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));

        var getStagesResponse = GetStagesResponse.builder().item(
            List.of(
                Stage.builder().stageName("Prod").documentationVersion(expectedVersion).build()
            )
        ).build();

        when(apiGatewayAsyncClient.getStages(any(GetStagesRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getStagesResponse));

        handler.handleRequest(null, null, null);

        verify(apiGatewayAsyncClient, never()).createDocumentationVersion(any(CreateDocumentationVersionRequest.class));
        verify(apiGatewayAsyncClient, never()).updateStage(any(UpdateStageRequest.class));
    }

    @Test
    void shouldPerformCreateWhenDocVersionDoesNotExists() {
        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().build();

        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));

        handler.handleRequest(null, null, null);

        verify(apiGatewayAsyncClient).createDocumentationVersion(any(CreateDocumentationVersionRequest.class));
    }

    @Test
    void shouldPerformUpdateStageWhenDocVersionExistsButItsNotAssociated() {
        var expectedHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash("");
        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().items(
            DocumentationVersion.builder().version(ApplicationConstants.VERSION_NAME + "-" + expectedHash).build()
        ).build();

        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));

        handler.handleRequest(null, null, null);

        verify(apiGatewayAsyncClient).updateStage(any(UpdateStageRequest.class));
    }

}