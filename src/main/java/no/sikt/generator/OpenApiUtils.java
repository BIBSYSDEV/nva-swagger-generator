package no.sikt.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    public static List<Operation> getAllOperationsFromPathItem(PathItem pathItem) {
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
           .filter(Objects::nonNull)
           .collect(Collectors.toList());
    }

    public static Collection<Schema> getChildSchema(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        return properties != null ? properties.values() : null;

    }

    public static Set<String> getRefsFromPaths(OpenAPI openAPI) {
        return openAPI
                    .getPaths()
                    .values()
                    .stream()
                    .map(OpenApiUtils::getAllOperationsFromPathItem)
                    .flatMap(List::stream)
                    .map(Operation::getResponses)
                    .filter(Objects::nonNull)
                    .map(ApiResponses::values)
                    .flatMap(Collection::stream)
                    .map(ApiResponse::getContent)
                    .map(Content::values)
                    .flatMap(Collection::stream)
                    .map(MediaType::getSchema)
                    .map(Schema::get$ref)
                    .collect(Collectors.toSet());
    }

    public static Set<String> getSchemaItemsRefs(OpenAPI openAPI) {
        return openAPI
                     .getComponents()
                     .getSchemas()
                     .values()
                     .stream()
                     .map(Schema::getItems)
                     .filter(Objects::nonNull)
                     .map(Schema::get$ref)
                     .collect(Collectors.toSet());
    }

    public static Set<String> getSchemaPropertyRefs(OpenAPI openAPI) {
        return openAPI
                   .getComponents()
                   .getSchemas()
                   .values()
                   .stream()
                   .map(OpenApiUtils::getChildSchema)
                   .filter(Objects::nonNull)
                   .flatMap(Collection::stream)
                   .map(Schema::get$ref)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toSet());
    }

    public static Set<String> getAllRefs(OpenAPI openAPI) {

        Set<String> allRefs = new HashSet<>();
        allRefs.addAll(getRefsFromPaths(openAPI));
        allRefs.addAll(getSchemaItemsRefs(openAPI));
        allRefs.addAll(getSchemaPropertyRefs(openAPI));

        return allRefs;
    }
}
