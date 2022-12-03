package no.sikt.generator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;

public class GenerateDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    private final ApiGatewayAsyncClient apiGatewayClient;

    @JacocoGenerated
    public GenerateDocsHandler() {
        this.apiGatewayClient = ApiGatewayAsyncClient.builder().build();
    }

    public GenerateDocsHandler(ApiGatewayAsyncClient client) {
        this.apiGatewayClient = client;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        try {
            var apis = apiGatewayClient.getRestApis().get();
            logger.info(apis.toString());
            var firstApi = apis.items().get(0);

            var getExportRequest = GetExportRequest.builder()
                                       .restApiId(firstApi.id())
                                       .stageName("Prod")
                                       .accepts("application/yaml")
                                       .exportType("swagger")
                                       .build();

            var export = apiGatewayClient.getExport(getExportRequest).get();
            logger.info(export.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
