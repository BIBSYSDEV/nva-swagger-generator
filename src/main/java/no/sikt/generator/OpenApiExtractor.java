package no.sikt.generator;

import static no.sikt.generator.OpenApiUtils.COMPONENTS_SCHEMAS;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiExtractor {
    private final List<OpenAPI> apis;
    private static final Logger logger = LoggerFactory.getLogger(OpenApiExtractor.class);

    public OpenApiExtractor(List<OpenAPI> apis) {
        this.apis = apis;
    }


    public List<OpenAPI> extract() {
        apis.forEach(this::removeNonExternalPaths);
        apis.forEach(this::removeUnusedSchemas);
        apis.removeIf(this::apiHasNoPaths);
        return apis;
    }

    private void removeUnusedSchemas(OpenAPI openAPI) {
        var schemas = openAPI.getComponents().getSchemas();
        var usedSchemas = getUsedSchemas(openAPI);
        var allSchemas = schemas.keySet();

        Map<String, Schema> newSchemas = new HashMap();

        allSchemas.forEach(key -> {
            var value = schemas.get(key);
            if (usedSchemas.contains(COMPONENTS_SCHEMAS + key)) {
                newSchemas.put(key, value);
            }
        });
        openAPI.getComponents().setSchemas(newSchemas);
    }

    private Set<String> getUsedSchemas(OpenAPI openAPI) {
        return OpenApiUtils.getAllUsedSchemas(openAPI);
    }

    private boolean apiHasNoPaths(OpenAPI openAPI) {
        return openAPI.getPaths().isEmpty();
    }

    private void removeNonExternalPaths(OpenAPI openAPI) {
        openAPI.getPaths().entrySet().forEach(this::removeExternalOps);
        openAPI.getPaths().entrySet().removeIf(this::pathHasNoOperations);
    }

    private void removeExternalOps(Entry<String, PathItem> pathEntry) {
        var path = pathEntry.getValue();
        path.readOperationsMap().forEach((httpMethod, operation) -> {
            if (operation.getTags() == null || !operation.getTags().stream().anyMatch(this::anyTagIsExternal)) {
                logger.info("Removing operation {} {} because its not tagged as external",
                            httpMethod,
                            pathEntry.getKey());
                path.operation(httpMethod, null);
            }
        });
    }

    private boolean anyTagIsExternal(String tag) {
        return "external".equals(tag);
    }

    private boolean pathHasNoOperations(Entry<String, PathItem> entries) {
        return entries.getValue().readOperations().isEmpty();
    }
}