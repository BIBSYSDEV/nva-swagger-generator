package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.INTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.INTERNAL_CLOUD_FRONT_DISTRIBUTION;
import static no.sikt.generator.Utils.distinctByKey;
import static no.sikt.generator.Utils.toSnakeCase;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.generator.ApiData;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.sikt.generator.OpenApiCombiner;
import no.sikt.generator.Utils;
import nva.commons.core.JacocoGenerated;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

public class GenerateInternalDocsHandler extends GenerateDocsHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenerateInternalDocsHandler.class);

    @JacocoGenerated
    public GenerateInternalDocsHandler() {
        super();
    }

    public GenerateInternalDocsHandler(Supplier<ApiGatewayAsyncClient> apiGatewayAsyncClientSupplier,
                                       CloudFrontHighLevelClient cloudFrontHighLevelClient,
                                       S3Client s3ClientOutput,
                                       S3Client s3ClientInput) {
        super(apiGatewayAsyncClientSupplier, cloudFrontHighLevelClient, s3ClientOutput, s3ClientInput);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = apiGatewayHighLevelClient.getRestApis();
        logger.info(apis.toString());

        var templateOpenapiDocs = getTemplateOpenApiDocs();

        var template = openApiParser
                           .readContents(Utils.readResource("internal.yaml"))
                           .getOpenAPI();

        var swaggers = validateAndFilterApis(apis, templateOpenapiDocs)
            .peek(this::writeApiDocsToInternalS3)
            .map(ApiData::getOpenapi)
            .collect(Collectors.toList());

        var combined = new OpenApiCombiner(template, swaggers).combine();

        String combinedYaml = attempt(() -> Yaml.pretty().writeValueAsString(combined)).orElseThrow();
        writeToS3(INTERNAL_BUCKET_NAME, "docs/openapi.yaml", combinedYaml);
        cloudFrontHighLevelClient.invalidateAll(INTERNAL_CLOUD_FRONT_DISTRIBUTION);
    }

    private Stream<ApiData> validateAndFilterApis(GetRestApisResponse apis,
                                                  List<Pair<S3Object, OpenAPI>> templateOpenapiDocs) {
        return apis.items().stream()
                   .map(this::fetchProdApiData)
                   .filter(Objects::nonNull)
                   .filter(this::apiShouldBeIncluded)
                   .peek(apiData -> openApiValidator.validateOpenApi(apiData.getOpenapi()))
                   .peek(apiData -> apiData.setMatchingGithubOpenapi(templateOpenapiDocs))
                   .map(ApiData::applyEmptySchemasIfNull)
                   .map(ApiData::overridePropsFromGithub)
                   .sorted(ApiData::sortByDate)
                   .sorted(ApiData::sortByDashes)
                   .filter(distinctByKey(ApiData::getName))
                   .sorted(ApiData::sortByName);
    }



    private void writeApiDocsToInternalS3(ApiData apiData) {
        var yamlFilename = "docs/" + toSnakeCase(apiData.getAwsRestApi().name()) + ".yaml";
        writeToS3(INTERNAL_BUCKET_NAME, yamlFilename, apiData.getRawYaml());
    }

}
