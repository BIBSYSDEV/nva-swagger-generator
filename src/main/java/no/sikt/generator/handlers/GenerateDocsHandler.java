package no.sikt.generator.handlers;

import static java.util.Locale.ENGLISH;
import static no.sikt.generator.ApplicationConstants.OUTPUT_BUCKET_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.core.util.Yaml;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import no.sikt.generator.OpenApiCombiner;
import no.sikt.generator.OpenApiValidator;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetStagesRequest;
import software.amazon.awssdk.services.apigateway.model.Stage;
import software.amazon.awssdk.services.s3.S3Client;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;

public class GenerateDocsHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDocsHandler.class);
    public static final String EXPORT_TYPE_OA_3 = "oas30";
    public static final String EXPORT_STAGE_PROD = "Prod";
    public static final String APPLICATION_YAML = "application/yaml";
    public static final String APPLICATION_JSON = "application/json";
    private final ApiGatewayAsyncClient apiGatewayClient;
    private final S3Client s3Client;
    private final OpenApiValidator openApiValidator = new OpenApiValidator();

    @JacocoGenerated
    public GenerateDocsHandler() {
        this.apiGatewayClient = ApiGatewayAsyncClient.builder().build();
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

    private String toSnakeCase(String string) {
        return string.replaceAll("\\s+", "-").toLowerCase(ENGLISH);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var openApiParser = new OpenAPIV3Parser();
        var apis = attempt(() -> apiGatewayClient.getRestApis().get()).orElseThrow();
        logger.info(apis.toString());

        var template = openApiParser
                           .readContents(Utils.readResource("template.yaml"))
                           .getOpenAPI();


        List<OpenAPI> swaggers = new ArrayList<>();
        apis.items().forEach(api -> {
            var stages = fetchStages(api.id());
            var hasProdStage = stages.stream().anyMatch(stage -> EXPORT_STAGE_PROD.equals(stage));
            if (hasProdStage) {
                var yaml = fetchApiExport(api.id(), EXPORT_STAGE_PROD, APPLICATION_YAML, EXPORT_TYPE_OA_3);
                var yamlFilename = "docs/" + toSnakeCase(api.name()) + ".yaml";
                writeToS3(yamlFilename, yaml);

                var parseResult = openApiParser.readContents(yaml);
                var openApi = parseResult.getOpenAPI();

                openApiValidator.validateOpenApi(openApi);

                swaggers.add(openApi);
            } else {
                logger.warn("API {} ({}) does not have stage {}. Stages found: {}", api.name(), api.id(),
                            EXPORT_STAGE_PROD, stages);
            }
        });

        var combined = new OpenApiCombiner(template, swaggers).combine();

        String combinedYaml = attempt(() -> Yaml.pretty().writeValueAsString(combined)).orElseThrow();
        writeToS3("docs/combined.yaml", combinedYaml);
    }
}
