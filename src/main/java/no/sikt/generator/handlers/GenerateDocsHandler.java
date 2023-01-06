package no.sikt.generator.handlers;

import static java.util.Locale.ENGLISH;
import static no.sikt.generator.ApplicationConstants.OUTPUT_BUCKET_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.generator.ApiData;
import no.sikt.generator.OpenApiCombiner;
import no.sikt.generator.OpenApiValidator;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.CreateDocumentationVersionRequest;
import software.amazon.awssdk.services.apigateway.model.GetDocumentationVersionsRequest;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.PatchOperation;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.apigateway.model.UpdateDocumentationVersionRequest;
import software.amazon.awssdk.services.s3.S3Client;

public class GenerateDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    public static final String EXPORT_TYPE_OA_3 = "oas30";
    public static final String EXPORT_STAGE_PROD = "Prod";
    public static final String APPLICATION_YAML = "application/yaml";
    public static final String VERSION_NAME = "swagger-generator";
    private final ApiGatewayAsyncClient apiGatewayClient;
    private final S3Client s3Client;
    private final OpenApiValidator openApiValidator = new OpenApiValidator();
    private final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @JacocoGenerated
    public GenerateDocsHandler() {
        var retryPolicy = RetryPolicy.builder()
                               .backoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                               .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                               .numRetries(10)
                               .build();


        var clientOverrideConfiguration = ClientOverrideConfiguration.builder()
                         .retryPolicy(retryPolicy)
                         .build();

        this.apiGatewayClient =
            ApiGatewayAsyncClient.builder().overrideConfiguration(clientOverrideConfiguration).build();
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public GenerateDocsHandler(ApiGatewayAsyncClient apiGatewayClient, S3Client s3Client) {
        this.apiGatewayClient = apiGatewayClient;
        this.s3Client = s3Client;
    }

    private void writeToS3(String filename, String content) {
        var s3Driver = new S3Driver(s3Client, OUTPUT_BUCKET_NAME);
        attempt(() -> s3Driver.insertFile(UnixPath.of(filename), content)).orElseThrow();
    }



    private String fetchApiExport(String apiId, String stage, String contentType, String exportType) {
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

    private List<String> fetchStages(String apiId) {
        var request = GetStagesRequest.builder().restApiId(apiId).build();
        var stages = attempt(() -> apiGatewayClient.getStages(request).get()).orElseThrow();

        return stages.item().stream().map(Stage::stageName).collect(Collectors.toList());
    }

    private void publishDocumentation(ApiData apiData) {
        var name = apiData.getOpenApi().getInfo().getTitle();
        var id = apiData.getAwsRestApi().id();
        logger.info("publishing {}", name);

        var listRequest = GetDocumentationVersionsRequest.builder().restApiId(id).build();

        var existingVersions
            = attempt(() -> apiGatewayClient.getDocumentationVersions(listRequest).get()).orElseThrow();


        if (existingVersions.items().stream().anyMatch(item -> VERSION_NAME.equals(item.version()))) {
            logger.info("{} has existing documentation - updating", name);
            var updateRequest = UpdateDocumentationVersionRequest.builder()
                                    .restApiId(id)
                                    .documentationVersion(VERSION_NAME)
                                    .patchOperations(
                                        PatchOperation.builder().op("replace").path("/description").build()
                                    ).build();

            attempt(() -> apiGatewayClient.updateDocumentationVersion(updateRequest).get()).orElseThrow();
        } else {
            logger.info("{} has no existing documentation - creating", name);
            var createRequest = CreateDocumentationVersionRequest.builder()
                                    .restApiId(id)
                                    .stageName(EXPORT_STAGE_PROD)
                                    .documentationVersion(VERSION_NAME)
                                    .build();
            attempt(() -> apiGatewayClient.createDocumentationVersion(createRequest).get()).orElseThrow();
        }
    }

    private String toSnakeCase(String string) {
        return string.replaceAll("\\s+", "-").toLowerCase(ENGLISH);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = attempt(() -> apiGatewayClient.getRestApis().get()).orElseThrow();
        logger.info(apis.toString());

        var template = openApiParser
                           .readContents(Utils.readResource("template.yaml"))
                           .getOpenAPI();

        var swaggers = apis.items().stream()
                           .map(this::fetchApiData)
                           .filter(Objects::nonNull)
                           .peek(apiData -> openApiValidator.validateOpenApi(apiData.getOpenApi()))
                           .sorted(this::sortApisByDate)
                           .filter(this::apiShouldBeIncluded)
                           .filter(distinctByKey(ApiData::getName))
                           .peek(this::publishDocumentation)
                           .map(ApiData::getAwsRestApi)
                           .map(this::fetchApiData)
                           .peek(this::writeApiDataToS3)
                           .sorted()
                           .map(ApiData::getOpenApi)
                           .collect(Collectors.toList());

        var combined = new OpenApiCombiner(template, swaggers).combine();

        String combinedYaml = attempt(() -> Yaml.pretty().writeValueAsString(combined)).orElseThrow();
        writeToS3("docs/combined.yaml", combinedYaml);
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T,Object> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private int sortApisByDate(ApiData apiData, ApiData otherApiData) {
        return otherApiData.getAwsRestApi().createdDate().compareTo(apiData.getAwsRestApi().createdDate());
    }

    private void writeApiDataToS3(ApiData apiData) {
        var yamlFilename = "docs/" + toSnakeCase(apiData.getAwsRestApi().name()) + ".yaml";
        writeToS3(yamlFilename, apiData.getRawYaml());
    }

    private ApiData fetchApiData(RestApi api) {
        var stages = fetchStages(api.id());
        var hasProdStage = stages.stream().anyMatch(stage -> EXPORT_STAGE_PROD.equals(stage));
        if (hasProdStage) {
            var yaml = fetchApiExport(api.id(), EXPORT_STAGE_PROD, APPLICATION_YAML, EXPORT_TYPE_OA_3);
            var parseResult = openApiParser.readContents(yaml);
            return new ApiData(api, parseResult.getOpenAPI(), yaml);
        } else {
            logger.warn("API {} ({}) does not have stage {}. Stages found: {}", api.name(), api.id(),
                        EXPORT_STAGE_PROD, stages);
            return null;
        }
    }

    private boolean apiShouldBeIncluded(ApiData apiData) {
        return apiData.hasCorrectDomain() && !apiData.isOnExcludeList();
    }
}
