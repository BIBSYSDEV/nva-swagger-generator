package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXPORT_STAGE_PROD;
import static no.sikt.generator.ApplicationConstants.EXTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.EXTERNAL_CLOUD_FRONT_DISTRIBUTION;
import static no.sikt.generator.Utils.distinctByKey;
import static nva.commons.core.attempt.Try.attempt;
import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.sikt.generator.OpenApiCombiner;
import no.sikt.generator.OpenApiExtractor;
import no.sikt.generator.OpenApiValidator;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public class GenerateExternalDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateExternalDocsHandler.class);
    private final ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    private final CloudFrontHighLevelClient cloudFrontHighLevelClient;
    private final S3Client s3ClientOutput;
    private final S3Client s3ClientInput;
    private final OpenApiValidator openApiValidator = new OpenApiValidator();
    private final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @JacocoGenerated
    public GenerateExternalDocsHandler() {
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
        this.s3ClientInput = S3Driver.defaultS3Client().build();
        this.s3ClientOutput = S3Driver.defaultS3Client().build();
    }

    public GenerateExternalDocsHandler(ApiGatewayHighLevelClient apiGatewayHighLevelClient,
                                       CloudFrontHighLevelClient cloudFrontHighLevelClient,
                                       S3Client s3ClientOutput,
                                       S3Client s3ClientInput) {
        this.apiGatewayHighLevelClient = apiGatewayHighLevelClient;
        this.cloudFrontHighLevelClient = cloudFrontHighLevelClient;
        this.s3ClientOutput = s3ClientOutput;
        this.s3ClientInput = s3ClientInput;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = apiGatewayHighLevelClient.getRestApis();
        logger.info(apis.toString());

        var templateOpenapiDocs = getTemplateOpenApiDocs();

        var template = openApiParser
                           .readContents(Utils.readResource("external.yaml"))
                           .getOpenAPI();


        var swaggers = validateAndFilterApis(apis, templateOpenapiDocs)
            .map(ApiData::getOpenapi)
            .collect(Collectors.toList());


        var onlyExternals = new OpenApiExtractor(swaggers).extract();
        var combined = new OpenApiCombiner(template, onlyExternals).combine();

        String combinedYaml = attempt(() -> Yaml.pretty().writeValueAsString(combined)).orElseThrow();
        writeToS3(EXTERNAL_BUCKET_NAME, "docs/openapi.yaml", combinedYaml);
        cloudFrontHighLevelClient.invalidateAll(EXTERNAL_CLOUD_FRONT_DISTRIBUTION);
    }

    private List<Pair<S3Object, OpenAPI>> getTemplateOpenApiDocs() {
        var listing =
            s3ClientInput.listObjects(ListObjectsRequest.builder().bucket("openapidocs").maxKeys(1000).build())
                .contents().stream().sorted(Comparator.comparing(S3Object::lastModified)).toList();
        return listing.stream().map(s3Object -> Pair.of(s3Object, getOpenApiFromFilePath(s3Object))).toList();
    }

    private OpenAPI getOpenApiFromFilePath(S3Object s3Object) {
        var request = GetObjectRequest.builder().bucket("openapidocs").key(s3Object.key()).build();
        var fileContent =
            s3ClientInput.getObject(request, ResponseTransformer.toBytes()).asUtf8String();
        return openApiParser.readContents(fileContent).getOpenAPI();
    }

    private Stream<ApiData> validateAndFilterApis(GetRestApisResponse apis, List<Pair<S3Object, OpenAPI>> templateOpenapiDocs) {
        return apis.items().stream()
                   .map(this::fetchProdApiData)
                   .filter(Objects::nonNull)
                   .peek(apiData -> openApiValidator.validateOpenApi(apiData.getOpenapi()))
                   .peek(apiData -> apiData.setMatchingGithubOpenapi(templateOpenapiDocs))
                   .sorted(ApiData::sortByDate)
                   .sorted(ApiData::sortByDashes)
                   .filter(this::apiShouldBeIncluded)
                   .filter(distinctByKey(ApiData::getName))
                   .sorted(ApiData::sortByName);
    }

    private void writeToS3(String bucket, String filename, String content) {
        var s3Driver = new S3Driver(s3ClientOutput, bucket);
        attempt(() -> s3Driver.insertFile(UnixPath.of(filename), content)).orElseThrow();
    }


    private ApiData fetchProdApiData(RestApi restApi) {
        return apiGatewayHighLevelClient.fetchApiDataForStage(restApi, EXPORT_STAGE_PROD, openApiParser);
    }

    private boolean apiShouldBeIncluded(ApiData apiData) {
        return apiData.hasCorrectDomain() && !apiData.isOnExcludeList();
    }
}
