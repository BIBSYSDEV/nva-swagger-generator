package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.EXPORT_STAGE_PROD;
import static no.sikt.generator.ApplicationConstants.VERSION_NAME;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import no.sikt.generator.ApiData;
import no.sikt.generator.ApiGatewayAsyncClientSupplier;
import no.sikt.generator.ApiGatewayHighLevelClient;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class PublishDocumentationsHandler implements RequestStreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(PublishDocumentationsHandler.class);
    private final ApiGatewayHighLevelClient apiGatewayHighLevelClient;
    private final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();

    @JacocoGenerated
    public PublishDocumentationsHandler() {
        this(ApiGatewayAsyncClientSupplier.getSupplier());
    }

    @JacocoGenerated
    public PublishDocumentationsHandler(Supplier<ApiGatewayAsyncClient> clientSupplier) {
        this.apiGatewayHighLevelClient = new ApiGatewayHighLevelClient(clientSupplier);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var apis = apiGatewayHighLevelClient.getRestApis();
        logger.info(apis.toString());

        publishDocumentations(apis);
    }

    private void publishDocumentations(GetRestApisResponse apis) {
        apis.items().stream()
            .map(this::fetchProdApiData)
            .filter(Objects::nonNull)
            .sorted(ApiData::sortByDate)
            .sorted(ApiData::sortByDashes)
            .filter(this::apiShouldBeIncluded)
            .filter(distinctByKey(ApiData::getName))
            .forEach(this::publishDocumentation);
    }

    private ApiData fetchProdApiData(RestApi restApi) {
        return apiGatewayHighLevelClient.fetchApiDataForStage(restApi, EXPORT_STAGE_PROD, openApiParser);
    }

    private void publishDocumentation(ApiData apiData) {
        var name = apiData.getOpenapi().getInfo().getTitle();
        var apiId = apiData.getAwsRestApi().id();
        logger.info("publishing {}", name);

        var existingVersions = apiGatewayHighLevelClient.fetchVersions(apiId);
        var partsHash = apiGatewayHighLevelClient.fetchDocumentationPartsHash(apiId);
        var wantedDocVersion = VERSION_NAME + "-" + partsHash;
        var docVersionExists
            = existingVersions.items().stream().anyMatch(item -> wantedDocVersion.equals(item.version()));
        logger.info("{} has parts-hash {}", name, partsHash);

        if (docVersionExists && apiData.getCurrentDocVersion().equals(wantedDocVersion)) {
            logger.info("{} has existing documentation and its set - ignoring", name);
        } else if (docVersionExists) {
            logger.info("{} has existing documentation but its currently associated with {} - patching",
                        name,
                        apiData.getCurrentDocVersion()
            );
            apiGatewayHighLevelClient.setStageDocVersion(apiId, EXPORT_STAGE_PROD, wantedDocVersion);
        } else {
            logger.info("{} has no existing documentation - creating", name);
            apiGatewayHighLevelClient.createDocumentation(apiId, wantedDocVersion, EXPORT_STAGE_PROD);
        }
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T,Object> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> Objects.isNull(seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE));
    }

    private boolean apiShouldBeIncluded(ApiData apiData) {
        return apiData.hasCorrectDomain() && !apiData.isOnExcludeList();
    }
}
