package no.sikt.generator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.nio.charset.StandardCharsets;
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
import software.amazon.awssdk.services.apigateway.model.RestApi;

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

        var restApi1 = RestApi.builder().name("First API").build();
        var restApi2 = RestApi.builder().name("Second API").build();

        var getRestApisResponse = GetRestApisResponse.builder().items(
            restApi1, restApi2
        ).build();

        var getExportResponse = GetExportResponse.builder()
                                    .body(SdkBytes.fromString(OPEN_API_DATA, UTF_8))
                                    .build();

        when(apiGatewayAsyncClient.getRestApis()).thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(apiGatewayAsyncClient.getExport(any(GetExportRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getExportResponse));
    }

    @Test
    public void shouldHaveConstrcutorWithNoArgument() {
        Executable action = () -> new GenerateDocsHandler();
    }

    @Test
    public void shouldLogAPIsWhenInvoked() {
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("First API"));
    }

    @Test
    public void shouldWriteFileToS3() {
        handler.handleRequest(null, null, null);

        var fileContent = s3Driver.getFile(UnixPath.of("docs/first-api.yaml"));
        assertThat(fileContent, notNullValue());
        assertThat(fileContent, is(equalTo(OPEN_API_DATA)));
    }

}