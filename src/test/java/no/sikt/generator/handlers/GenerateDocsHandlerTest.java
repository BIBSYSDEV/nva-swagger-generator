package no.sikt.generator.handlers;

import static no.sikt.generator.Utils.readResource;
import static no.sikt.generator.handlers.GenerateDocsHandler.VERSION_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.ApplicationConstants;
import no.sikt.generator.OpenApiUtils;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

class GenerateDocsHandlerTest {

    private final ApiGatewayAsyncClient apiGatewayAsyncClient = Mockito.mock(ApiGatewayAsyncClient.class);
    private ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    private GenerateDocsHandler handler;
    private S3Driver s3Driver;
    private OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @BeforeEach
    public void setup() {
        var fakeS3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3Client, ApplicationConstants.OUTPUT_BUCKET_NAME);
        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(apiGatewayAsyncClient);
        handler = new GenerateDocsHandler(apiGatewayHighLevelClient, fakeS3Client);
    }

    private void setupTestCasesFromFiles(String folder, List<String> filenames) {
        TestUtils.setupTestcasesFromFiles(apiGatewayAsyncClient, folder, filenames);
    }

    private void setupSingleFile() {
        setupTestCasesFromFiles(null, List.of("api-a.yaml"));
    }

    private void setupSimpleMocks() {
        setupTestCasesFromFiles(null, List.of("api-a.yaml", "api-b.yaml"));
    }

    private void setupNvaMocks() {
        setupTestCasesFromFiles("nva", List.of(
            "dlr-launchcanvas-api.yaml",
            "nva-courses-api.yaml",
            "nva-cristin-proxy-api.yaml",
            "nva-customer-api.yaml",
            "nva-download-publication-file-api.yaml",
            "nva-fetch-doi.yaml",
            "nva-ontology-service.yaml",
            "nva-public-search-api.yaml",
            "nva-publication-api.yaml",
            "nva-publication-channels.yaml",
            "nva-roles-and-users-catalogue.yaml",
            "nva-verified-funding-sources-api.yaml",
            "nva-s3-multipart-upload.yaml"
        ));
    }

    @Test
    public void shouldHaveConstructorWithNoArgument() {
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
    public void shouldLogSchemasWithNumbersInName() {
        setupSimpleMocks();
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("schema 'UniqueSchemaWithNumber1' contains numbers"));
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
    public void shouldRemoveOptionOperations() {
        var fileNames = List.of(
            "api-with-options.yaml"
        );
        TestUtils.setupTestcasesFromFiles(apiGatewayAsyncClient, null, fileNames);

        handler.handleRequest(null, null, null);

        var yaml = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));

        var openApi = openApiParser
                          .readContents(yaml)
                          .getOpenAPI();

        for (Entry<String, PathItem> path : openApi.getPaths().entrySet()) {
            assertThat(path.getValue().getOptions(), nullValue());
        }
    }

    @Test
    public void shouldNotPerformCreateOrUpdateStageWhenDocVersionExistsAndItsAssociatedWithProdStage() {
        setupSingleFile();

        var expectedHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash("");
        var expectedVersion = VERSION_NAME + "-" + expectedHash;

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
    public void shouldPerformCreateWhenDocVersionDoesNotExists() {
        setupSingleFile();

        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().build();

        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));

        handler.handleRequest(null, null, null);

        verify(apiGatewayAsyncClient).createDocumentationVersion(any(CreateDocumentationVersionRequest.class));
    }

    @Test
    public void shouldPerformUpdateStageWhenDocVersionExistsButItsNotAssociated() {
        setupSingleFile();

        var expectedHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash("");
        var listDocumentationVersionsResponse = GetDocumentationVersionsResponse.builder().items(
            DocumentationVersion.builder().version(VERSION_NAME + "-" + expectedHash).build()
        ).build();

        when(apiGatewayAsyncClient.getDocumentationVersions(any(GetDocumentationVersionsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listDocumentationVersionsResponse));

        handler.handleRequest(null, null, null);

        verify(apiGatewayAsyncClient).updateStage(any(UpdateStageRequest.class));
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
        assertThat(openApi.getComponents().getSchemas().entrySet(), hasSize(4));
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
    public void shouldExcludeFilesWhenEnviormentVariableIsSet() {
        setupTestCasesFromFiles(null, List.of("api-exluded.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getPaths(), nullValue());
        assertThat(openApi.getComponents().getSchemas(), nullValue());
    }

    @Test
    public void shouldParseFile() {
        setupNvaMocks();

        handler.handleRequest(null, null, null);

        var s3FileContent = s3Driver.getFile(UnixPath.of("docs/nva-publication-api.yaml"));
        assertThat(s3FileContent, notNullValue());

    }

    private OpenAPI readGeneratedOpenApi() {
        var yaml = s3Driver.getFile(UnixPath.of("docs/combined.yaml"));
        assertThat(yaml, notNullValue());

        return openApiParser
                          .readContents(yaml)
                          .getOpenAPI();
    }



}