package no.sikt.generator;

import static no.sikt.generator.ApplicationConstants.OUTPUT_BUCKET_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.s3.S3Client;

public class GenerateDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    private final ApiGatewayAsyncClient apiGatewayClient;
    private final S3Client s3Client;

    @JacocoGenerated
    public GenerateDocsHandler() {
        this.apiGatewayClient = ApiGatewayAsyncClient.builder().build();
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public GenerateDocsHandler(ApiGatewayAsyncClient apiGatewayClient, S3Client s3Client) {
        this.apiGatewayClient = apiGatewayClient;
        this.s3Client = s3Client;
    }

    private void writeToS3(String content) {
        var s3Driver = new S3Driver(s3Client, OUTPUT_BUCKET_NAME);
        attempt(() -> s3Driver.insertFile(UnixPath.of("docs/swagger.yaml"), content)).orElseThrow();
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = attempt(() -> apiGatewayClient.getRestApis().get()).orElseThrow();
        logger.info(apis.toString());
        var firstApi = apis.items().get(0);

        var getExportRequest = GetExportRequest.builder()
                                   .restApiId(firstApi.id())
                                   .stageName("Prod")
                                   .accepts("application/yaml")
                                   .exportType("swagger")
                                   .build();

        var export = attempt(() -> apiGatewayClient.getExport(getExportRequest).get())
                         .orElseThrow();

        logger.info(export.toString());
        writeToS3(export.toString());
    }
}
