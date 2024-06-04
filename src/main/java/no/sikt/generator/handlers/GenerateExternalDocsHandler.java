package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.EXTERNAL_CLOUD_FRONT_DISTRIBUTION;
import static no.sikt.generator.Utils.distinctByKey;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayHighLevelClient;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.sikt.generator.OpenApiCombiner;
import no.sikt.generator.OpenApiExtractor;
import no.sikt.generator.Utils;
import nva.commons.core.JacocoGenerated;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

public class GenerateExternalDocsHandler extends GenerateDocsHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateExternalDocsHandler.class);

    @JacocoGenerated
    public GenerateExternalDocsHandler() {
        super();
    }

    public GenerateExternalDocsHandler(ApiGatewayHighLevelClient apiGatewayHighLevelClient,
                                       CloudFrontHighLevelClient cloudFrontHighLevelClient,
                                       S3Client s3ClientOutput,
                                       S3Client s3ClientInput) {
        super(apiGatewayHighLevelClient, cloudFrontHighLevelClient, s3ClientOutput, s3ClientInput);
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
}
