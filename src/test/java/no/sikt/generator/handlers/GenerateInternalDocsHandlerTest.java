package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.INTERNAL_BUCKET_NAME;
import static no.sikt.generator.Utils.readResource;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.sikt.generator.OpenApiUtils;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.s3.S3Client;

class GenerateInternalDocsHandlerTest {

    private CloudFrontHighLevelClient cloudFrontHighLevelClient;
    private GenerateInternalDocsHandler handler;
    private S3Driver outputS3Driver;
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

        var outputS3client = new FakeS3Client();
        this.inputS3client = new FakeS3Client();
        this.outputS3Driver = new S3Driver(outputS3client, INTERNAL_BUCKET_NAME);

        Supplier<CloudFrontClient> mockCloudFrontSupplier = mock(Supplier.class);
        cloudFrontClient = mock(CloudFrontClient.class);
        when(mockCloudFrontSupplier.get()).thenReturn(cloudFrontClient);

        this.cloudFrontHighLevelClient = new CloudFrontHighLevelClient(mockCloudFrontSupplier);
        handler = new GenerateInternalDocsHandler(mockApiGatewaySupplier, cloudFrontHighLevelClient,
                                                  outputS3client, inputS3client);
    }

    private void setupTestCasesFromFiles(String folder, List<String> filenames) {
        TestUtils.setupTestcasesFromFiles(inputS3client, apiGatewayAsyncClient, cloudFrontClient, folder,
                                          filenames.stream()
                                              .map(fn -> new ImmutablePair<String, Optional<String>>(fn,
                                                                                                     Optional.empty()))
                                              .collect(
                                                  Collectors.toList()));
    }

    private void setupTestCasesFromFilesWithGithubOpenapi(String folder,
                                                          List<Pair<String, Optional<String>>> filenames) {
        TestUtils.setupTestcasesFromFiles(inputS3client, apiGatewayAsyncClient, cloudFrontClient, folder, filenames);
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
    void shouldHaveConstructorWithNoArgument() {
        Executable action = () -> new GenerateInternalDocsHandler();
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

        var singleFile = outputS3Driver.getFile(UnixPath.of("docs/api-a.yaml"));
        assertThat(singleFile, notNullValue());
        assertThat(singleFile, is(equalTo(readResource("openapi_docs/api-a.yaml"))));

        var combinedFile = outputS3Driver.getFile(UnixPath.of("docs/openapi.yaml"));
        assertThat(combinedFile, notNullValue());
    }

    @Test
    void shouldRemoveOptionOperations() {
        var fileNames = List.of(
            "api-with-options.yaml"
        );
        setupTestCasesFromFiles(null, fileNames);

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        for (Entry<String, PathItem> path : openApi.getPaths().entrySet()) {
            assertThat(path.getValue().getOptions(), nullValue());
        }
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
    void shouldMergeFiles() {
        setupSimpleMocks();
        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

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
    void shouldHandleRealNvaFiles() {
        setupNvaMocks();
        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

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
    void shouldExcludeFilesWhenEnviormentVariableIsSet() {
        setupTestCasesFromFiles(null, List.of("api-exluded.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getPaths(), nullValue());
        assertThat(openApi.getComponents().getSchemas(), nullValue());
    }

    @Test
    void shouldRenameRefsWhenTheyHaveSameNameAndAreNested() {
        setupTestCasesFromFiles("same-schema-name", List.of("api-a.yaml", "api-b.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();

        assertThat(openApi.getComponents().getSchemas().get("DuplicateSchema"), nullValue());
        var responseSchema = openApi.getComponents().getSchemas().get("ApiAResponse");
        var firstSchema = OpenApiUtils.getNestedPropertiesSchemas(responseSchema).findFirst().get();
        var nestedSchemaRef = firstSchema.get$ref();
        assertThat(nestedSchemaRef,not(equalTo("#/components/schemas/DuplicateSchema")));
    }

    @Test
    void shouldNotThrowWhenApiSpecsMissServer() {
        setupTestCasesFromFiles("server", List.of("api-without-server.yaml", "api-without-server.yaml"));

        handler.handleRequest(null, null, null);
    }

    @Test
    void shouldOnlyIncludeSpecsForApiWithSingleDashInServerBasePathIfMultipleApisWithSameNamePresent() {
        setupTestCasesFromFiles("server", List.of("api-with-basepath-with-1-dashes.yaml",
                                                  "api-with-basepath-with-2-dashes.yaml"));

        handler.handleRequest(null, null, null);
        var openApi = readGeneratedOpenApi();
        assertThat(openApi.getPaths().keySet().size(), equalTo(1));
        assertThat(openApi.getPaths().keySet().stream().findFirst().get(), equalTo("/pathA-something/path"));
    }

    @Test
    void shouldSortApisAlphabetically() {
        setupNvaMocks();

        handler.handleRequest(null, null, null);
        var openApi = readGeneratedOpenApi();
        var tags = openApi.getTags().stream().map(Tag::getName).toList();
        ArrayList<String> sortedTags = new ArrayList<>(tags);
        Collections.sort(sortedTags);
        assertThat(tags, is(equalTo(sortedTags)));
    }

    @Test
    void shouldOverrideStyleIfSetInGithubOpenapi() {
        setupTestCasesFromFilesWithGithubOpenapi("nva/publication-api", List.of(Pair.of("apigateway.yaml", Optional.of(
            "github"
            + ".yaml"))));

        handler.handleRequest(null, null, null);
        var openApi = readGeneratedOpenApi();
        var parameter =
            openApi.getPaths().get("/publication/{publicationIdentifier}").getGet().getParameters().stream()
                .filter(param -> param.getName().equals("doNotRedirect")).findFirst().get();

        assertThat(parameter.getStyle(), is(equalTo(StyleEnum.FORM)));
        assertThat(parameter.getExplode(), is(equalTo(false)));
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
    void shouldSortSchemasAlphabetically() {
        setupNvaMocks();

        handler.handleRequest(null, null, null);
        var openApi = readGeneratedOpenApi();
        var schemas = openApi.getComponents().getSchemas().keySet();
        ArrayList<String> sortedSchemas = new ArrayList<>(schemas);
        Collections.sort(sortedSchemas);
        assertThat(new ArrayList<>(schemas), is(equalTo(sortedSchemas)));
    }

    @Test
    void shouldParseFile() {
        setupNvaMocks();

        handler.handleRequest(null, null, null);

        var s3FileContent = outputS3Driver.getFile(UnixPath.of("docs/nva-publication-api.yaml"));
        assertThat(s3FileContent, notNullValue());
    }

    @Test
    void shouldCallCloudFrontInvalidation() {
        setupSingleFile();

        handler.handleRequest(null, null, null);

        verify(cloudFrontClient).createInvalidation(any(CreateInvalidationRequest.class));
    }

    @Test
    public void shouldNotAddExplodeAndStyle() {
        setupTestCasesFromFiles("",List.of("api-without-style.yaml"));

        handler.handleRequest(null, null, null);

        var openApi = readGeneratedOpenApi();
        var allParameters = openApi.getPaths().values()
                                .stream().map(p -> p.getGet().getParameters()).flatMap(Collection::stream);

        allParameters.forEach(p -> assertThat(p.getStyle(),is(equalTo(null))));
        allParameters.forEach(p -> assertThat(p.getExplode(),is(equalTo(null))));
    }

    private OpenAPI readGeneratedOpenApi() {
        var yaml = outputS3Driver.getFile(UnixPath.of("docs/openapi.yaml"));
        assertThat(yaml, notNullValue());

        return openApiParser
                          .readContents(yaml)
                          .getOpenAPI();
    }



}