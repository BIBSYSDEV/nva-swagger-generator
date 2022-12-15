package no.sikt.generator;

import static no.sikt.generator.OpenApiUtils.addTag;
import static no.sikt.generator.OpenApiUtils.getAllOperationsFromPathItem;
import static no.sikt.generator.OpenApiUtils.getResourcePath;
import static no.sikt.generator.OpenApiUtils.hasPath;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.text.CaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiCombiner {

    private final OpenAPI baseTemplate;
    private final List<OpenAPI> others;

    private static final Logger logger = LoggerFactory.getLogger(OpenApiCombiner.class);

    public OpenApiCombiner(OpenAPI baseTemplate, List<OpenAPI> others) {
        this.baseTemplate = baseTemplate;
        this.others = others;
    }

    private Server findMainServer() {
        var allServers = this.others.stream()
                             .map(OpenAPI::getServers)
                             .flatMap(Collection::stream)
                             .collect(Collectors.toList());
        var mainServer = allServers.stream().filter(it -> it.getUrl().contains("unit.no")).findFirst().get();
        return new Server().url(mainServer.getUrl().replace("/{basePath}",""));
    }

    public OpenAPI combine() {

        this.baseTemplate.setServers(List.of(findMainServer()));

        if (this.baseTemplate.getComponents() == null) {
            this.baseTemplate.setComponents(new Components());
        }

        renameDuplicateSchemas();

        this.others.stream().forEach(api -> {
            this.baseTemplate.addTagsItem(OpenApiUtils.convertInfoToTag(api.getInfo()));
            mergeComponents(api);
            mergePaths(api);
            mergeSecurity(api);
        });
        
        detectIssues();

        return baseTemplate;
    }

    private void detectIssues() {

    }

    private void mergeSecurity(OpenAPI api) {
        if (api.getSecurity() != null) {
            for (SecurityRequirement securityRequirement : api.getSecurity()) {
                logger.info("adding security req {}", securityRequirement.toString());
                this.baseTemplate.addSecurityItem(securityRequirement);
            }
        }
    }

    private void mergePaths(OpenAPI api) {
        var title = api.getInfo().getTitle();
        var resourcePath = getResourcePath(api);

        for (Entry<String, PathItem> path : api.getPaths().entrySet()) {

            getAllOperationsFromPathItem(path.getValue()).forEach(op -> addTag(op, title));

            var newPathKey = "/" + resourcePath + path.getKey();
            if (hasPath(baseTemplate, newPathKey)) {
                throw new IllegalStateException("Path " + newPathKey + " already exists");
            }

            this.baseTemplate.path(newPathKey, path.getValue());
        }
    }

    private void mergeComponents(OpenAPI api) {
        if (api.getComponents() == null) {
            return;
        }

        if (api.getComponents().getParameters() != null) {
            for (Entry<String, Parameter> parameter : api.getComponents().getParameters().entrySet()) {
                this.baseTemplate.getComponents().addParameters(parameter.getKey(), parameter.getValue());
            }
        }

        if (api.getComponents().getSchemas() != null) {
            for (Entry<String, Schema> schema : api.getComponents().getSchemas().entrySet()) {
                mergeScheme(baseTemplate, schema);
            }
        }

        if (api.getComponents().getSecuritySchemes() != null) {
            var entrySet = api.getComponents().getSecuritySchemes().entrySet();
            for (Entry<String, SecurityScheme> securitySchemeEntry : entrySet) {
                mergeSecuritySchemes(baseTemplate, securitySchemeEntry);
            }
        }
    }

    private Set<String> findDuplicateSchemaNames() {
        Map<String, Schema> schemas = new HashMap<>();
        Set<String> collidingSchemaNames = new HashSet<>();

        this.others.stream().forEach(api -> {
            if (api.getComponents().getSchemas() != null) {
                for (Entry<String, Schema> schema : api.getComponents().getSchemas().entrySet()) {
                    if (schemas.get(schema.getKey()) == null) {
                        schemas.put(schema.getKey(), schema.getValue());
                    } else if (!schemas.get(schema.getKey()).equals(schema.getValue())) {
                        collidingSchemaNames.add(schema.getKey());
                    }
                }
            }
        });

        return collidingSchemaNames;
    }

    private void renameDuplicateSchemas() {
        var duplicateNames = findDuplicateSchemaNames();

        this.others.stream().forEach(api -> {
            if (api.getComponents().getSchemas() != null) {
                Map<String, Schema> newSchemas = new HashMap<>();

                for (var schemaEntry : api.getComponents().getSchemas().entrySet()) {

                    var newName = CaseUtils.toCamelCase(api.getInfo().getTitle(), true)
                                  + schemaEntry.getKey();

                    if (duplicateNames.contains(schemaEntry.getKey())) {
                        renameSchemaRef(api, schemaEntry.getKey(), newName);
                        newSchemas.put(newName, schemaEntry.getValue());
                    } else {
                        newSchemas.put(schemaEntry.getKey(), schemaEntry.getValue());
                    }
                    api.getComponents().setSchemas(newSchemas);
                }
            }
        });
    }

    private void renameSchemaRef(OpenAPI target, String oldName, String newName) {
        target.getPaths().forEach((key, value) -> {
            getAllOperationsFromPathItem(value).forEach(pathOperation -> {
                pathOperation.getResponses().entrySet().forEach(response -> {
                    response.getValue().getContent().entrySet().forEach(content -> {
                        var oldRef = content.getValue().getSchema().get$ref();
                        if (oldRef.equals("#/components/schemas/" + oldName)) {
                            logger.info("Replacing {} with {}", "/" + oldName, "/" + newName);
                            content.getValue().getSchema().set$ref("#/components/schemas/" + newName);
                        }
                    });
                });
            });

            //throw new IllegalStateException("Schema " + newKey + " already exists and they are not equal");
        });

    }

    private void mergeScheme(OpenAPI target, Entry<String, Schema> source) {
        var newKey = source.getKey();
        if (this.baseTemplate.getComponents().getSchemas() != null
            && this.baseTemplate.getComponents().getSchemas().get(newKey) != null) {

            var targetValue = this.baseTemplate.getComponents().getSchemas().get(newKey);
            var sourceValue = source.getValue();
            if (targetValue.equals(sourceValue)) {
                logger.info("Ignoring equal schema for {}", newKey);
            } else {
                throw new IllegalStateException("Schema " + newKey + " already exists and they are not equal");
            }
        } else {
            target.getComponents().addSchemas(newKey, source.getValue());
        }

    }

    private void mergeSecuritySchemes(OpenAPI target, Entry<String, SecurityScheme> source) {
        var newKey = source.getKey();
        if (this.baseTemplate.getComponents().getSecuritySchemes() != null
            && this.baseTemplate.getComponents().getSecuritySchemes().get(newKey) != null) {

            var targetValue = this.baseTemplate.getComponents().getSecuritySchemes().get(newKey);
            var sourceValue = source.getValue();
            if (targetValue.equals(sourceValue)) {
                logger.info("Ignoring equal securityScheme");
                return;
            } else {
                throw new IllegalStateException("Security schema " + newKey + " already exists and they are not equal");
            }

        }
        target.getComponents().addSecuritySchemes(newKey, source.getValue());
    }


}