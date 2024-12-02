package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXPORT_STAGE_PROD;
import static no.sikt.generator.ApplicationConstants.readOpenApiBucketName;
import static nva.commons.core.attempt.Try.attempt;
import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Comparator;
import java.util.List;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.sikt.generator.OpenApiValidator;
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
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public abstract class GenerateDocsHandler implements RequestStreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    CloudFrontHighLevelClient cloudFrontHighLevelClient;
    S3Client s3ClientOutput;
    S3Client s3ClientInput;
    final OpenApiValidator openApiValidator = new OpenApiValidator();
    final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();
    final String openApiBucketName = readOpenApiBucketName();

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
        var cloudFrontClient = CloudFrontClient.builder()
                                   .httpClient(UrlConnectionHttpClient.builder().build())
                                   .region(AWS_GLOBAL)
                                   .build();

        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(apiGatewayClient);
        this.cloudFrontHighLevelClient = new CloudFrontHighLevelClient(cloudFrontClient);
        this.s3ClientInput = S3Driver.defaultS3Client().build();
        this.s3ClientOutput = S3Driver.defaultS3Client().build();
    }

    public GenerateDocsHandler(ApiGatewayHighLevelClient apiGatewayHighLevelClient,
                                       CloudFrontHighLevelClient cloudFrontHighLevelClient,
                                       S3Client s3ClientOutput,
                                       S3Client s3ClientInput) {
        this.apiGatewayHighLevelClient = apiGatewayHighLevelClient;
        this.cloudFrontHighLevelClient = cloudFrontHighLevelClient;
        this.s3ClientOutput = s3ClientOutput;
        this.s3ClientInput = s3ClientInput;
    }

    List<Pair<S3Object, OpenAPI>> getTemplateOpenApiDocs() {
        var listing =
            s3ClientInput.listObjects(ListObjectsRequest.builder().bucket(openApiBucketName).maxKeys(1000).build())
                .contents().stream()
                .filter(s3Object -> s3Object.key().endsWith(".yaml"))
                .sorted(Comparator.comparing(S3Object::lastModified)).toList();
        logger.info("Found " + listing.size() + " .yaml files");
        return listing.stream().map(s3Object -> Pair.of(s3Object, getOpenApiFromFilePath(s3Object))).toList();
    }

    private OpenAPI getOpenApiFromFilePath(S3Object s3Object) {
        var request = GetObjectRequest.builder().bucket(openApiBucketName).key(s3Object.key()).build();
        var fileContent =
            s3ClientInput.getObject(request, ResponseTransformer.toBytes()).asUtf8String();
        return openApiParser.readContents(fileContent).getOpenAPI();
    }

    void writeToS3(String bucket, String filename, String content) {
        var s3Driver = new S3Driver(s3ClientOutput, bucket);
        attempt(() -> s3Driver.insertFile(UnixPath.of(filename), content)).orElseThrow();
    }


    ApiData fetchProdApiData(RestApi restApi) {
        return apiGatewayHighLevelClient.fetchApiDataForStage(restApi, EXPORT_STAGE_PROD, openApiParser);
    }

    boolean apiShouldBeIncluded(ApiData apiData) {
        return apiData.hasCorrectDomain() && !apiData.isOnExcludeList();
    }
}
