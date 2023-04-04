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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayHighLevelClient;
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
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.s3.S3Client;

public class GenerateDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    public static final String EXPORT_TYPE_OA_3 = "oas30";
    public static final String EXPORT_STAGE_PROD = "Prod";
    public static final String APPLICATION_YAML = "application/yaml";
    public static final String VERSION_NAME = "swagger-generator";
    private final ApiGatewayHighLevelClient apiGatewayHighLevelClient;
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

        var apiGatewayClient =
            ApiGatewayAsyncClient.builder().overrideConfiguration(clientOverrideConfiguration).build();

        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(apiGatewayClient);
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public GenerateDocsHandler(ApiGatewayHighLevelClient apiGatewayHighLevelClient, S3Client s3Client) {
        this.apiGatewayHighLevelClient = apiGatewayHighLevelClient;
        this.s3Client = s3Client;
    }

    private void writeToS3(String filename, String content) {
        var s3Driver = new S3Driver(s3Client, OUTPUT_BUCKET_NAME);
        attempt(() -> s3Driver.insertFile(UnixPath.of(filename), content)).orElseThrow();
    }


    private void publishDocumentation(ApiData apiData) {
        var name = apiData.getOpenApi().getInfo().getTitle();
        var id = apiData.getAwsRestApi().id();
        logger.info("publishing {}", name);

        var existingVersions = apiGatewayHighLevelClient.fetchVersions(id);
        var partsHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash(id);
        var versionName = VERSION_NAME + "-" + partsHash;
        logger.info("{} has parts-hash {}", id, partsHash);

        if (existingVersions.items().stream().anyMatch(item -> versionName.equals(item.version()))) {
            logger.info("{} has existing documentation - ignoring", name);
        } else {
            logger.info("{} has no existing documentation - creating", name);
            apiGatewayHighLevelClient.createDocumentation(id, versionName, EXPORT_STAGE_PROD);
        }
    }

    private String toSnakeCase(String string) {
        return string.replaceAll("\\s+", "-").toLowerCase(ENGLISH);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = apiGatewayHighLevelClient.getRestApis();
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
        var stages = apiGatewayHighLevelClient.fetchStages(api.id());
        var hasProdStage = stages.stream().anyMatch(stage -> EXPORT_STAGE_PROD.equals(stage));
        if (hasProdStage) {
            var yaml = apiGatewayHighLevelClient.fetchApiExport(
                api.id(),
                EXPORT_STAGE_PROD,
                APPLICATION_YAML,
                EXPORT_TYPE_OA_3
            );
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
