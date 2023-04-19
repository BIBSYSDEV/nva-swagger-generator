package no.sikt.generator;

import static no.sikt.generator.ApplicationConstants.DOMAIN;
import static no.sikt.generator.OpenApiUtils.addTag;
import static no.sikt.generator.OpenApiUtils.getResourcePath;
import static no.sikt.generator.OpenApiUtils.hasPath;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiCombiner {

    public static final String COMPONENTS_SCHEMAS = "#/components/schemas/";
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

        if (allServers.isEmpty()) {
            return null;
        }

        var mainServer =
            allServers.stream().filter(server -> server.getUrl().contains(DOMAIN)).findFirst().get();
        return new Server().url(mainServer.getUrl().replace("/{basePath}",""));
    }

    public OpenAPI combine() {

        var mainServer = findMainServer();

        if (mainServer != null) {
            this.baseTemplate.setServers(List.of(mainServer));
        }


        if (this.baseTemplate.getComponents() == null) {
            this.baseTemplate.setComponents(new Components());
        }


        removeOptions();
        renameDuplicateSchemas();

        this.others.stream().forEach(api -> {
            this.baseTemplate.addTagsItem(OpenApiUtils.convertInfoToTag(api.getInfo()));
            removeTags(api);
            mergeComponents(api);
            mergePaths(api);
            mergeSecurity(api);
        });

        return baseTemplate;
    }

    private void removeTag(Operation op) {
        op.setTags(null);
    }

    private void removeTags(OpenAPI api) {
        api.getPaths()
            .values()
            .stream()
            .map(PathItem::readOperations)
            .flatMap(Collection::stream)
            .forEach(this::removeTag);
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

        for (var entry : api.getPaths().entrySet()) {

            entry.getValue().readOperations().forEach(op -> addTag(op, title));

            var newPathKey = "/" + resourcePath + entry.getKey();
            if (hasPath(baseTemplate, newPathKey)) {
                throw new IllegalStateException("Path " + newPathKey + " already exists");
            }

            this.baseTemplate.path(newPathKey, entry.getValue());
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

    private void removeOptions() {
        this.others.stream().forEach(api -> {
            api.getPaths().entrySet().forEach(path -> {
                if (path.getValue().getOptions() != null) {
                    logger.info("Removing options for {} {}", api.getInfo().getTitle(), path.getKey());
                    path.getValue().setOptions(null);
                }
            });
        });
    }

    private void renameDuplicateSchemas() {
        var duplicateNames = findDuplicateSchemaNames();
        renameSchemas(duplicateNames);
        renameNestedSchemaRefs(duplicateNames);
    }

    private void renameNestedSchemaRefs(Set<String> duplicateNames) {
        this.others.stream().forEach(api -> {
            if (api.getComponents().getSchemas() != null) {
                for (var schemaEntry : api.getComponents().getSchemas().entrySet()) {
                    var nestedSchemas = OpenApiUtils.getNestedSchemas(schemaEntry.getValue());
                    nestedSchemas.filter(Objects::nonNull).forEach(s -> {
                        var refName = StringUtils.stripStart(s.get$ref(), COMPONENTS_SCHEMAS);
                        var newName = CaseUtils.toCamelCase(api.getInfo().getTitle(), true)
                                      + refName;
                        if (duplicateNames.contains(refName)) {
                            s.set$ref(COMPONENTS_SCHEMAS + newName);
                        }
                    });
                }
            }
        });
    }

    private void renameSchemas(Set<String> duplicateNames) {
        this.others.stream().forEach(api -> {
            if (api.getComponents().getSchemas() != null) {
                Map<String, Schema> newSchemas = new HashMap<>();

                for (var schemaEntry : api.getComponents().getSchemas().entrySet()) {

                    var oldName = schemaEntry.getKey();
                    var newName = CaseUtils.toCamelCase(api.getInfo().getTitle(), true) + oldName;

                    if (duplicateNames.contains(oldName)) {
                        logger.info("API {}: Replacing {} with {}", api.getInfo().getTitle(), "/" + oldName,
                                    "/" + newName);
                        renameSchemaRef(api, oldName, newName);
                        newSchemas.put(newName, schemaEntry.getValue());
                    } else {
                        newSchemas.put(oldName, schemaEntry.getValue());
                    }
                    api.getComponents().setSchemas(newSchemas);
                }
            }
        });
    }

    private void renameSchemaRef(OpenAPI target, String oldName, String newName) {
        target.getPaths().values().forEach(pathItem -> {
            pathItem.readOperations().forEach(pathOperation -> {
                pathOperation.getResponses().entrySet().forEach(response -> {
                    response.getValue().getContent().entrySet().forEach(content -> {
                        var oldRef = content.getValue().getSchema().get$ref();
                        if (oldRef.equals(COMPONENTS_SCHEMAS + oldName)) {
                            content.getValue().getSchema().set$ref(COMPONENTS_SCHEMAS + newName);
                        }
                    });
                });
                var requestBody = pathOperation.getRequestBody();
                if (requestBody != null) {
                    requestBody.getContent().entrySet().forEach(content -> {
                        var oldRef = content.getValue().getSchema().get$ref();
                        if (oldRef.equals(COMPONENTS_SCHEMAS + oldName)) {
                            content.getValue().getSchema().set$ref(COMPONENTS_SCHEMAS + newName);
                        }
                    });
                }
            });
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
                logger.warn("Security schema " + newKey + " already exists and they are not equal");
            }
        }
        target.getComponents().addSecuritySchemes(newKey, source.getValue());
    }


}