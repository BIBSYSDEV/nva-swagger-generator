package no.sikt.generator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.generator.Utils.readResource;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetExportResponse;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;

class GenerateDocsHandlerTest {

    public static final String OPEN_API_DATA = "no-body";
    private final ApiGatewayAsyncClient apiGatewayAsyncClient = Mockito.mock(ApiGatewayAsyncClient.class);

    private GenerateDocsHandler handler;
    private S3Driver s3Driver;

    @BeforeEach
    public void setup() {

        var fakeS3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3Client, ApplicationConstants.OUTPUT_BUCKET_NAME);

        handler = new GenerateDocsHandler(apiGatewayAsyncClient, fakeS3Client);

        setupMocks();
    }

    private void setupMocks() {
        var getRestApisResponse = GetRestApisResponse.builder().items(
            RestApi.builder().name("DLR LaunchCanvas API").id("dlr-launchcanvas-api").build(),
            RestApi.builder().name("NVA Courses API").id("nva-courses-api").build(),
            RestApi.builder().name("NVA Cristin Proxy API").id("nva-cristin-proxy-api").build(),
            RestApi.builder().name("NVA Customer API").id("nva-customer-api").build(),
            RestApi.builder().name("NVA Download Publication File API").id("nva-download-publication-file-api").build(),
            RestApi.builder().name("NVA Fetch DOI").id("nva-fetch-doi").build(),
            RestApi.builder().name("NVA Ontology Service").id("nva-ontology-service").build(),
            RestApi.builder().name("NVA Public Search API").id("nva-public-search-api").build(),
            RestApi.builder().name("NVA Publication API").id("nva-publication-api").build(),
            RestApi.builder().name("NVA Publication Channels").id("nva-publication-channels").build(),
            RestApi.builder().name("NVA Roles and Users catalogue").id("nva-roles-and-users-catalogue").build(),
            RestApi.builder().name("NVA S3 Multipart Upload").id("nva-s3-multipart-upload").build()
        ).build();

        var getStagesResponse = GetStagesResponse.builder().item(
            List.of(
                Stage.builder().stageName("Prod").build(),
                Stage.builder().stageName("Stage").build()
            )
        ).build();

        when(apiGatewayAsyncClient.getRestApis()).thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(apiGatewayAsyncClient.getStages(any(GetStagesRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getStagesResponse));

        when(apiGatewayAsyncClient.getExport(any(GetExportRequest.class)))
            .thenAnswer(invocation -> {
                var requestedId = invocation.getArgument(0, GetExportRequest.class).restApiId();
                var fileContent = readResource("openapi_docs/" + requestedId + ".yaml");
                var sdkBody = SdkBytes.fromString(fileContent, UTF_8);
                var response =  GetExportResponse.builder()
                                    .body(sdkBody)
                                    .build();
                return CompletableFuture.completedFuture(response);
            });
    }

    @Test
    public void shouldHaveConstrcutorWithNoArgument() {
        Executable action = () -> new GenerateDocsHandler();
    }

    @Test
    public void shouldLogAPIsWhenInvoked() {
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("NVA Publication API"));
    }

    @Test
    public void shouldWriteFileToS3() {
        handler.handleRequest(null, null, null);

        var singleFile = s3Driver.getFile(UnixPath.of("docs/nva-publication-api.yaml"));
        assertThat(singleFile, notNullValue());
        assertThat(singleFile, is(equalTo(readResource("openapi_docs/nva-publication-api.yaml"))));

        var combinedFile = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));
        assertThat(combinedFile, notNullValue());
    }

    @Test
    public void shouldParseFile() {

        handler.handleRequest(null, null, null);

        var s3FileContent = s3Driver.getFile(UnixPath.of("docs/nva-publication-api.yaml"));
        assertThat(s3FileContent, notNullValue());

    }



}