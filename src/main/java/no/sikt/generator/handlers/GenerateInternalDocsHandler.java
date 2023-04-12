package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXPORT_STAGE_PROD;
import static no.sikt.generator.ApplicationConstants.INTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.INTERNAL_CLOUD_FRONT_DISTRIBUTION;
import static no.sikt.generator.Utils.distinctByKey;
import static no.sikt.generator.Utils.toSnakeCase;
import static nva.commons.core.attempt.Try.attempt;
import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.CloudFrontHighLevelClient;
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
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;

public class GenerateInternalDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateInternalDocsHandler.class);
    private final ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    private final CloudFrontHighLevelClient cloudFrontHighLevelClient;
    private final S3Client s3Client;
    private final OpenApiValidator openApiValidator = new OpenApiValidator();
    private final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @JacocoGenerated
    public GenerateInternalDocsHandler() {
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
        var cloudFrontClient = CloudFrontClient.builder()
                                   .httpClient(UrlConnectionHttpClient.builder().build())
                                   .region(AWS_GLOBAL)
                                   .build();

        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(apiGatewayClient);
        this.cloudFrontHighLevelClient = new CloudFrontHighLevelClient(cloudFrontClient);
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public GenerateInternalDocsHandler(ApiGatewayHighLevelClient apiGatewayHighLevelClient,
                                       CloudFrontHighLevelClient cloudFrontHighLevelClient,
                                       S3Client s3Client) {
        this.apiGatewayHighLevelClient = apiGatewayHighLevelClient;
        this.cloudFrontHighLevelClient = cloudFrontHighLevelClient;
        this.s3Client = s3Client;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = apiGatewayHighLevelClient.getRestApis();
        logger.info(apis.toString());

        var template = openApiParser
                           .readContents(Utils.readResource("internal.yaml"))
                           .getOpenAPI();

        var swaggers = validateAndFilterApis(apis)
            .peek(this::writeApiDocsToInternalS3)
            .sorted()
            .map(ApiData::getOpenApi)
            .collect(Collectors.toList());

        var combined = new OpenApiCombiner(template, swaggers).combine();

        String combinedYaml = attempt(() -> Yaml.pretty().writeValueAsString(combined)).orElseThrow();
        writeToS3(INTERNAL_BUCKET_NAME, "docs/openapi.yaml", combinedYaml);
        cloudFrontHighLevelClient.invalidateAll(INTERNAL_CLOUD_FRONT_DISTRIBUTION);
    }

    private Stream<ApiData> validateAndFilterApis(GetRestApisResponse apis) {
        return apis.items().stream()
                   .map(this::fetchProdApiData)
                   .filter(Objects::nonNull)
                   .peek(apiData -> openApiValidator.validateOpenApi(apiData.getOpenApi()))
                   .sorted(this::sortApisByDate)
                   .filter(this::apiShouldBeIncluded)
                   .filter(distinctByKey(ApiData::getName));
    }

    private void writeToS3(String bucket, String filename, String content) {
        var s3Driver = new S3Driver(s3Client, bucket);
        attempt(() -> s3Driver.insertFile(UnixPath.of(filename), content)).orElseThrow();
    }



    private int sortApisByDate(ApiData apiData, ApiData otherApiData) {
        return otherApiData.getAwsRestApi().createdDate().compareTo(apiData.getAwsRestApi().createdDate());
    }

    private void writeApiDocsToInternalS3(ApiData apiData) {
        var yamlFilename = "docs/" + toSnakeCase(apiData.getAwsRestApi().name()) + ".yaml";
        writeToS3(INTERNAL_BUCKET_NAME, yamlFilename, apiData.getRawYaml());
    }

    private ApiData fetchProdApiData(RestApi restApi) {
        return apiGatewayHighLevelClient.fetchApiDataForStage(restApi, EXPORT_STAGE_PROD, openApiParser);
    }

    private boolean apiShouldBeIncluded(ApiData apiData) {
        return apiData.hasCorrectDomain() && !apiData.isOnExcludeList();
    }
}
