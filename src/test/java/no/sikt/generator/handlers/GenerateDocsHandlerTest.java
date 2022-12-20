package no.sikt.generator.handlers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.generator.Utils.readResource;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import no.sikt.generator.ApplicationConstants;
import no.sikt.generator.OpenApiUtils;
import no.sikt.generator.handlers.GenerateDocsHandler;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.apache.commons.lang3.StringUtils;
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

    private final ApiGatewayAsyncClient apiGatewayAsyncClient = Mockito.mock(ApiGatewayAsyncClient.class);
    private GenerateDocsHandler handler;
    private S3Driver s3Driver;
    private OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @BeforeEach
    public void setup() {

        var fakeS3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3Client, ApplicationConstants.OUTPUT_BUCKET_NAME);

        handler = new GenerateDocsHandler(apiGatewayAsyncClient, fakeS3Client);
    }

    private void setupSimpleMocks() {
        var getRestApisResponse = GetRestApisResponse.builder().items(
            RestApi.builder().name("API A").id("api-a").build(),
            RestApi.builder().name("API B").id("api-b").build()
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

    private void setupNvaMocks() {
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
                var fileContent = readResource("openapi_docs/nva/" + requestedId + ".yaml");
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
        setupSimpleMocks();
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("API A"));
    }

    @Test
    public void shouldWriteFilesToS3() {
        setupSimpleMocks();
        handler.handleRequest(null, null, null);

        var singleFile = s3Driver.getFile(UnixPath.of("docs/api-a.yaml"));
        assertThat(singleFile, notNullValue());
        assertThat(singleFile, is(equalTo(readResource("openapi_docs/api-a.yaml"))));

        var combinedFile = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));
        assertThat(combinedFile, notNullValue());
    }

    @Test
    public void shouldMergeFiles() {
        setupSimpleMocks();
        handler.handleRequest(null, null, null);

        var yaml = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));

        var openApi = openApiParser
                           .readContents(yaml)
                           .getOpenAPI();

        assertThat(openApi.getInfo(), notNullValue());
        assertThat(openApi.getInfo().getVersion(), notNullValue());
        assertThat(openApi.getInfo().getTitle(), is(not(emptyString())));
        assertThat(openApi.getInfo().getDescription(), is(not(emptyString())));
        assertThat(openApi.getComponents().getSecuritySchemes().entrySet(), hasSize(1));
        assertThat(openApi.getComponents().getSchemas().entrySet(), hasSize(3));
        assertThat(openApi.getComponents().getSchemas().get("Error"), notNullValue());
        assertThat(openApi.getComponents().getSchemas().get("ApiAResponse"), notNullValue());
        assertThat(openApi.getComponents().getSchemas().get("ApiBResponse"), notNullValue());
        assertThat(openApi.getServers(), notNullValue());
        assertThat(openApi.getServers(), hasSize(1));
    }

    @Test
    public void shouldHandleRealNvaFiles() {
        setupNvaMocks();
        handler.handleRequest(null, null, null);

        var yaml = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));
        assertThat(yaml, notNullValue());

        var openApi = openApiParser
                          .readContents(yaml)
                          .getOpenAPI();
        
        
        assertThatOpenApiIsValid(openApi);
    }

    private void assertThatOpenApiIsValid(OpenAPI openApi) {

        var allRefs = OpenApiUtils.getAllRefs(openApi);
        var schemas = openApi
                          .getComponents()
                          .getSchemas()
                          .keySet();

        for (String ref : allRefs) {
            var expected = StringUtils.removeStart(ref, "#/components/schemas/");
            assertThat(schemas, hasItem(expected));
        }
    }

    @Test
    public void shouldParseFile() {
        setupNvaMocks();

        handler.handleRequest(null, null, null);

        var s3FileContent = s3Driver.getFile(UnixPath.of("docs/nva-publication-api.yaml"));
        assertThat(s3FileContent, notNullValue());

    }



}