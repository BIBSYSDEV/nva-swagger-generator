package no.sikt.generator;

import static java.util.Objects.*;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import io.swagger.models.parameters.PathParameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nva.commons.core.JacocoGenerated;

public final class OpenApiUtils {

    public static final String COMPONENTS_SCHEMAS = "#/components/schemas/";

    @JacocoGenerated
    private OpenApiUtils() {

    }

    public static void addTag(Operation operation, String tag) {
        operation.addTagsItem(tag);
    }

    public static String getResourcePath(OpenAPI api) {
        return api.getServers().get(0).getVariables().get("basePath").getDefault().replace("/","");
    }

    public static boolean hasPath(OpenAPI api, String pathKey) {
        return nonNull(api.getPaths()) && nonNull(api.getPaths().get(pathKey));
    }

    public static Tag convertInfoToTag(Info info) {
        return new Tag().name(info.getTitle()).description(info.getDescription());
    }

    public static Stream<Schema> getNestedAllOfSchemas(Schema schema) {
        return nonNull(schema.getAllOf()) ? schema.getAllOf().stream() : Stream.of();
    }

    public static Stream<Schema> getNestedAnyOfSchemas(Schema schema) {
        return nonNull(schema.getAnyOf()) ? schema.getAnyOf().stream() : Stream.of();
    }

    public static Stream<Schema> getNestedPropertiesSchemas(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        return nonNull(properties) ? properties.values().stream() : Stream.of();
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

    public static Stream<Schema> getNestedSchemas(Schema schema) {
        var nestedSchemas = Stream.of(
            Stream.of(schema.getItems()),
            OpenApiUtils.getNestedAllOfSchemas(schema),
            OpenApiUtils.getNestedAnyOfSchemas(schema),
            OpenApiUtils.getNestedPropertiesSchemas(schema),
            OpenApiUtils.getNestedPropertiesSchemas(schema).map(Schema::getItems)
        ).flatMap(stream -> stream).filter(Objects::nonNull).collect(Collectors.toList());

        return Stream.of(
            nestedSchemas.stream(),
            nestedSchemas.stream().flatMap(OpenApiUtils::getNestedSchemas)
        ).flatMap(stream -> stream);
    }

    public static Stream<String> getRefsFromRequestBodies(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths())
                   .orElseGet(Paths::new)
                   .values()
                   .stream()
                   .map(PathItem::readOperations)
                   .flatMap(Collection::stream)
                   .map(Operation::getRequestBody)
                   .filter(Objects::nonNull)
                   .map(RequestBody::getContent)
                   .map(Content::values)
                   .flatMap(Collection::stream)
                   .map(MediaType::getSchema)
                   .map(Schema::get$ref)
                   .distinct();
    }

    public static Stream<String> getResponseRefsFromPaths(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths())
                    .orElseGet(Paths::new)
                    .values()
                    .stream()
                    .map(PathItem::readOperations)
                    .flatMap(Collection::stream)
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
                   .flatMap(OpenApiUtils::getNestedPropertiesSchemas)
                   .map(Schema::get$ref)
                   .filter(Objects::nonNull)
                   .distinct();
    }

    public static Stream<String> getSchemaPropertyItemRefs(OpenAPI openAPI) {
        return openAPI
                   .getComponents()
                   .getSchemas()
                   .values()
                   .stream()
                   .flatMap(OpenApiUtils::getNestedPropertiesSchemas)
                   .map(Schema::getItems)
                   .filter(Objects::nonNull)
                   .map(Schema::get$ref)
                   .filter(Objects::nonNull)
                   .distinct();
    }

    public static Set<String> getAllRefs(OpenAPI openAPI) {
        return Stream.of(
                getResponseRefsFromPaths(openAPI),
                getRefsFromRequestBodies(openAPI),
                getSchemaItemsRefs(openAPI),
                getSchemaPropertyRefs(openAPI),
                getSchemaPropertyItemRefs(openAPI)
            ).flatMap(stream -> stream)
               .collect(toSet());
    }

    public static Set<String> getAllUsedSchemas(OpenAPI openAPI) {
        var responseRefs = OpenApiUtils.getResponseRefsFromPaths(openAPI).collect(toSet());
        var requestRefs = OpenApiUtils.getRefsFromRequestBodies(openAPI).collect(toSet());
        var reffed = concat(responseRefs.stream(), requestRefs.stream())
                                   .collect(toSet());

        var directUsedSchemas = openAPI.getComponents().getSchemas()
                                    .entrySet()
                                    .stream()
                                    .filter(entry -> reffed.contains(COMPONENTS_SCHEMAS + entry.getKey()))
                                    .map(entry -> entry.getValue())
                                    .collect(toSet());

        var nestedSchemas = directUsedSchemas
                                .stream()
                                .flatMap(OpenApiUtils::getNestedSchemas)
                                .map(Schema::get$ref)
                                .collect(toSet());

        var combined = Stream.of(
                reffed,
                nestedSchemas
            ).flatMap(Set::stream).collect(toSet());

        return combined;
    }
}
