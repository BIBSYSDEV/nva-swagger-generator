package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXTERNAL_BUCKET_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.s3.S3Client;

class GenerateExternalDocsHandlerTest {

    private CloudFrontHighLevelClient cloudFrontHighLevelClient;
    private GenerateExternalDocsHandler handler;
    private S3Driver s3Driver;
    private OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();
    private S3Client inputS3client;
    private ApiGatewayAsyncClient apiGatewayAsyncClient;
    private CloudFrontClient cloudFrontClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        Supplier<ApiGatewayAsyncClient> mockApiGatewaySupplier = mock(Supplier.class);
        apiGatewayAsyncClient = mock(ApiGatewayAsyncClient.class);
        when(mockApiGatewaySupplier.get()).thenReturn(apiGatewayAsyncClient);


        var fakeS3ClientOutput = new FakeS3Client();
        this.inputS3client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3ClientOutput, EXTERNAL_BUCKET_NAME);

        Supplier<CloudFrontClient> mockCloudFrontSupplier = mock(Supplier.class);
        cloudFrontClient = mock(CloudFrontClient.class);
        when(mockCloudFrontSupplier.get()).thenReturn(cloudFrontClient);

        this.cloudFrontHighLevelClient = new CloudFrontHighLevelClient(mockCloudFrontSupplier);
        handler = new GenerateExternalDocsHandler(mockApiGatewaySupplier, cloudFrontHighLevelClient,
                                                  fakeS3ClientOutput, inputS3client);
    }

    private void setupTestCasesFromFiles(String folder, List<String> filenames) {
        TestUtils.setupTestcasesFromFiles(inputS3client, apiGatewayAsyncClient, cloudFrontClient, folder,
                                          filenames.stream()
                                              .map(fn -> new ImmutablePair<>(fn, Optional.<String>empty()))
                                              .collect(
                                                  Collectors.toList()));
    }

    private void setupSingleFile() {
        setupTestCasesFromFiles(null, List.of("api-a.yaml"));
    }

    private void setupSimpleMocks() {
        setupTestCasesFromFiles(null, List.of("api-a.yaml", "api-b.yaml"));
    }

    @Test
    void shouldHaveConstructorWithNoArgument() {
        Executable action = () -> new GenerateExternalDocsHandler();
    }

    @Test
    void shouldLogAPIsWhenInvoked() {
        setupSimpleMocks();
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("API A"));
    }

    @Test
    void shouldLogSchemasWithNumbersInName() {
        setupSimpleMocks();
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("schema 'UniqueSchemaWithNumber1' contains numbers"));
    }

    @Test
    void shouldWriteFilesToS3() {
        setupSimpleMocks();
        handler.handleRequest(null, null, null);

        var combinedFile = s3Driver.getFile(UnixPath.of("docs/openapi.yaml"));
        assertThat(combinedFile, notNullValue());
    }

    @Test
    void shouldRemoveExternalTags() {
        var fileNames = List.of(
            "api-with-external.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getTags(), hasSize(1));
        assertThat(openApi.getTags().get(0).getName(), not(equalTo("external")));
    }

    @Test
    void shouldNotIncludedNonExternalPaths() {
        var fileNames = List.of(
            "api-a.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getPaths(), nullValue());
        assertThat(openApi.getTags(), nullValue());
    }

    @Test
    void shouldIncludeExternalPaths() {
        var fileNames = List.of(
            "api-with-external.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getPaths().values(), hasSize(1));
        assertThat(openApi.getPaths().values().stream().findFirst().get().readOperations(), hasSize(1));
    }

    @Test
    void shouldIncludeParameteredSchemas() {
        setupTestCasesFromFilesWithGithubOpenapi("parameters", List.of(Pair.of("api.yaml", Optional.of(
            "github"
            + ".yaml"))));

        handler.handleRequest(null, null, null);
        var openApi = readGeneratedOpenApi();
        var categoryEnum = openApi.getComponents().getSchemas().get("CategoryEnum");

        assertThat(categoryEnum,is(notNullValue()));
    }

    @Test
    void shouldIncludeExternalSchemaWhenItsDirectlyReferencedFromResponse() {
        var openapi = generateOpenApiFromExternalSpecs();

        assertThat(openapi.getComponents().getSchemas().containsKey("ExternalSchema"),equalTo(true));
        assertThat(openapi.getComponents().getSchemas().containsKey("NestedExternalSchema"),equalTo(true));
    }

    @Test
    void shouldIncludeExternalSchemaWhenItsReferencedFromRequest() {
        var openapi = generateOpenApiFromExternalSpecs();

        assertThat(openapi.getComponents().getSchemas().containsKey("ExternalRequestSchema"),equalTo(true));
    }

    @Test
    void shouldNotIncludeNonExternalSchemaWhenItsDirectlyReferenced() {
        var openapi = generateOpenApiFromExternalSpecs();

        assertThat(openapi.getComponents().getSchemas().containsKey("InternalSchema"),equalTo(false));
    }


    @Test
    void shouldIncludeFieldsThatAreNestedWithAllOf() {
        setupTestCasesFromFiles("all-of", List.of("api.yaml", "api.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getComponents().getSchemas().get("NestedResponse"), notNullValue());
        assertThat(openApi.getComponents().getSchemas().get("NestedNestedResponse"), notNullValue());
    }

    @Test
    void shouldIncludeFieldsThatAreNestedInAdditionalProperties() {
        setupTestCasesFromFiles("additional-properties", List.of("api.yaml", "api.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getComponents().getSchemas().get("NestedResponse"), notNullValue());
    }

    @Test
    void shouldOnlyWriteTheCombinedOpenApiFileToS3() {
        var fileNames = List.of(
            "api-with-external.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        var files = s3Driver.listAllFiles(UnixPath.of("docs/"));
        assertThat(files, hasSize(1));
        assertThat(files.get(0).toString(), equalTo("docs/openapi.yaml"));
    }

    @Test
    void shouldOnlyIncludeOneSecurityScheme() {
        var openapi = generateOpenApiFromExternalSpecs();
        var securitySchemas = openapi.getComponents().getSecuritySchemes();
        assertThat(securitySchemas.entrySet(), hasSize(1));
    }

    @Test
    void shouldCallCloudFrontInvalidation() {
        setupSingleFile();

        handler.handleRequest(null, null, null);

        verify(cloudFrontClient).createInvalidation(any(CreateInvalidationRequest.class));
    }

    private OpenAPI generateOpenApiFromExternalSpecs() {
        var fileNames = List.of(
            "api-with-external.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        return readGeneratedOpenApi();
    }

    private OpenAPI readGeneratedOpenApi() {
        var yaml = s3Driver.getFile(UnixPath.of("docs/openapi.yaml"));
        assertThat(yaml, notNullValue());

        return openApiParser
                          .readContents(yaml)
                          .getOpenAPI();
    }

    private void setupTestCasesFromFilesWithGithubOpenapi(String folder,
                                                          List<Pair<String, Optional<String>>> filenames) {
        TestUtils.setupTestcasesFromFiles(inputS3client, apiGatewayAsyncClient, cloudFrontClient, folder, filenames);
    }



}