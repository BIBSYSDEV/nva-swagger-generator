package no.sikt.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OpenApiUtils {

    private OpenApiUtils() {

    }

    public static void addTag(Operation operation, String tag) {
        operation.addTagsItem(tag);
    }

    public static String getResourcePath(OpenAPI api) {
        return api.getServers().get(0).getVariables().get("basePath").getDefault().replace("/","");
    }

    public static boolean hasPath(OpenAPI api, String pathKey) {
        return api.getPaths() != null && api.getPaths().get(pathKey) != null;
    }

    public static Tag convertInfoToTag(Info info) {
        return new Tag().name(info.getTitle()).description(info.getDescription());
    }

    public static Stream<Operation> getAllOperationsFromPathItem(PathItem pathItem) {
        return Stream.of(
            pathItem.getGet(),
            pathItem.getDelete(),
            pathItem.getPut(),
            pathItem.getPost(),
            pathItem.getOptions(),
            pathItem.getHead(),
            pathItem.getTrace(),
            pathItem.getPatch()
        )
           .filter(Objects::nonNull);
    }

    public static Stream<Schema> getChildSchema(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        return properties != null ? properties.values().stream() : Stream.of();
    }

    public static Stream<ApiResponse> getApiResponsesFromOperation(Operation operation) {
        return operation.getResponses().values().stream().filter(Objects::nonNull);
    }

    public static Stream<MediaType> getMediaTypesFromContent(ApiResponse apiResponse) {
        return apiResponse.getContent().values().stream();
    }

    public static String getRefFromMediaType(MediaType mediaType) {
        return mediaType.getSchema().get$ref();
    }

    public static Stream<String> getRefsFromPaths(OpenAPI openAPI) {
        return openAPI
                    .getPaths()
                    .values()
                    .stream()
                    .flatMap(OpenApiUtils::getAllOperationsFromPathItem)
                    .flatMap(OpenApiUtils::getApiResponsesFromOperation)
                    .flatMap(OpenApiUtils::getMediaTypesFromContent)
                    .map(OpenApiUtils::getRefFromMediaType)
                    .distinct();
    }

    public static Stream<String> getSchemaItemsRefs(OpenAPI openAPI) {
        return openAPI
                     .getComponents()
                     .getSchemas()
                     .values()
                     .stream()
                     .map(Schema::getItems)
                     .filter(Objects::nonNull)
                     .map(Schema::get$ref)
                     .distinct();
    }

    public static Stream<String> getSchemaPropertyRefs(OpenAPI openAPI) {
        return openAPI
                   .getComponents()
                   .getSchemas()
                   .values()
                   .stream()
                   .flatMap(OpenApiUtils::getChildSchema)
                   .map(Schema::get$ref)
                   .filter(Objects::nonNull)
                   .distinct();
    }

    public static Set<String> getAllRefs(OpenAPI openAPI) {
        return Stream.of(
                getRefsFromPaths(openAPI),
                getSchemaItemsRefs(openAPI),
                getSchemaPropertyRefs(openAPI)
            ).flatMap(stream -> stream)
               .collect(Collectors.toSet());
    }
}
