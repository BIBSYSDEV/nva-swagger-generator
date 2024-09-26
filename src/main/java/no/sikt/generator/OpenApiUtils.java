package no.sikt.generator;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nva.commons.core.JacocoGenerated;
import org.apache.commons.lang3.StringUtils;

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

    public static Stream<Schema> getNestedOneOfSchemas(Schema schema) {
        return nonNull(schema.getOneOf()) ? schema.getOneOf().stream() : Stream.of();
    }

    public static Stream<Schema> getNestedPropertiesSchemas(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        return nonNull(properties) ? properties.values().stream() : Stream.of();
    }

    public static Stream<Schema> getAdditionalPropertiesSchemas(Schema schema) {
        if (nonNull(schema.getAdditionalProperties()) && schema.getAdditionalProperties() instanceof Schema) {
            return Stream.of((Schema) schema.getAdditionalProperties());
        } else {
            return Stream.of();
        }
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

    public static List<Schema> getNestedSchemas(OpenAPI openAPI, Schema schema) {
        return getNestedSchemas(openAPI, new ArrayList<>(), schema);
    }

    public static List<Schema> getNestedSchemas(OpenAPI openAPI, List<Schema> visited, Schema schema) {
        var nestedSchemas = Stream.of(
            Stream.of(schema.getItems()),
            getReffedSchema(openAPI, schema),       
            getNestedAllOfSchemas(schema),
            getNestedAnyOfSchemas(schema),
            getNestedOneOfSchemas(schema),
            getNestedPropertiesSchemas(schema),
            getAdditionalPropertiesSchemas(schema)
        ).flatMap(stream -> stream).filter(Objects::nonNull).toList();

        var newSchemas = nestedSchemas.stream().filter(ns -> !visited.contains(ns)).collect(toSet());

        visited.addAll(nestedSchemas);
        newSchemas.stream()
            .forEach(s -> getNestedSchemas(openAPI, visited, s));

        return visited;

    }

    private static Stream<Schema> getReffedSchema(OpenAPI openAPI, Schema schema) {
        if (nonNull(schema.get$ref()) && schema.get$ref().startsWith(COMPONENTS_SCHEMAS)) {
            return Stream.of(openAPI.getComponents()
                                 .getSchemas().get(StringUtils.stripStart(schema.get$ref(), COMPONENTS_SCHEMAS)));
        } else {
            return Stream.of();
        }
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

    public static Stream<String> getRefsFromParameters(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths())
                   .orElseGet(Paths::new)
                   .values()
                   .stream()
                   .map(PathItem::readOperations)
                   .flatMap(Collection::stream)
                   .map(Operation::getParameters)
                   .filter(Objects::nonNull)
                   .flatMap(Collection::stream)
                   .map(Parameter::get$ref)
                   .filter(Objects::nonNull)
                   .distinct();
    }

    public static Stream<String> getRefsFromParametersSchemas(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths())
                   .orElseGet(Paths::new)
                   .values()
                   .stream()
                   .map(PathItem::readOperations)
                   .flatMap(Collection::stream)
                   .map(Operation::getParameters)
                   .filter(Objects::nonNull)
                   .flatMap(Collection::stream)
                   .map(Parameter::getSchema)
                   .filter(Objects::nonNull)
                   .map(Schema::get$ref)
                   .filter(Objects::nonNull)
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

    public static Set<String> getAllRefsFromPaths(OpenAPI openAPI) {
        return Stream.of(
                getResponseRefsFromPaths(openAPI),
                getRefsFromRequestBodies(openAPI),
                getRefsFromParametersSchemas(openAPI),
                getRefsFromParameters(openAPI)
            ).flatMap(stream -> stream)
                   .collect(toSet());
    }

    public static Set<String> getAllRefs(OpenAPI openAPI) {
        return Stream.of(
                getResponseRefsFromPaths(openAPI),
                getRefsFromRequestBodies(openAPI),
                getRefsFromParameters(openAPI),
                getRefsFromParametersSchemas(openAPI),
                getSchemaItemsRefs(openAPI),
                getSchemaPropertyRefs(openAPI),
                getSchemaPropertyItemRefs(openAPI)
            ).flatMap(stream -> stream)
               .collect(toSet());
    }

    public static Set<String> getAllUsedSchemas(OpenAPI openAPI) {
        var reffed = getAllRefsFromPaths(openAPI);

        var directUsedSchemas = openAPI.getComponents().getSchemas()
                                    .entrySet()
                                    .stream()
                                    .filter(entry -> reffed.contains(COMPONENTS_SCHEMAS + entry.getKey()))
                                    .map(Entry::getValue)
                                    .collect(toSet());
        var nestedSchemas = directUsedSchemas
                                .stream()
                                .flatMap(s -> getNestedSchemas(openAPI, s).stream())
                                .map(Schema::get$ref)
                                .collect(toSet());

        var combined = Stream.of(
                reffed,
                nestedSchemas
            ).flatMap(Set::stream).collect(toSet());

        return combined;
    }
}
