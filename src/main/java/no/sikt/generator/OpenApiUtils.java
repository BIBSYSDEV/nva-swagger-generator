package no.sikt.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Objects;
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

    static List<Operation> getAllOperationsFromPathItem(PathItem pathItem) {
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
}
