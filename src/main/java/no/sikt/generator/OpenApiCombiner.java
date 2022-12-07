package no.sikt.generator;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by erik on 2017-01-05.
 */
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

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
    public OpenAPI combine() {

        this.baseTemplate.setServers(List.of(findMainServer()));

        this.others.stream().forEach(api -> {

            var resource = api.getServers().get(0).getVariables().get("basePath").getDefault().replace("/","");

            this.baseTemplate.addTagsItem(
                new Tag()
                    .name(api.getInfo().getTitle())
                    .description(api.getInfo().getDescription())
            );


            //api.getServers().stream().forEach( apiServer -> this.baseTemplate.addServersItem(apiServer));

            for (Entry<String, PathItem> path : api.getPaths().entrySet()) {
                if (path.getValue().getGet() != null) {
                    path.getValue().getGet().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getPost() != null) {
                    path.getValue().getPost().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getHead() != null) {
                    path.getValue().getHead().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getDelete() != null) {
                    path.getValue().getDelete().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getPatch() != null) {
                    path.getValue().getPatch().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getPut() != null) {
                    path.getValue().getPut().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getTrace() != null) {
                    path.getValue().getTrace().addTagsItem(api.getInfo().getTitle());
                }
                if (path.getValue().getOptions() != null) {
                    path.getValue().getOptions().addTagsItem(api.getInfo().getTitle());
                }

                var newPathKey = "/" + resource + path.getKey();
                if (this.baseTemplate.getPaths() != null && this.baseTemplate.getPaths().get(newPathKey) != null) {
                    throw new IllegalStateException("Path " + newPathKey + " already exists");
                }

                this.baseTemplate.path(newPathKey, path.getValue());
            }

            if (api.getSecurity() != null) {
                for (SecurityRequirement securityRequirement : api.getSecurity()) {
                    logger.info("adding security req {}", securityRequirement.toString());
                    this.baseTemplate.addSecurityItem(securityRequirement);
                }
            }

            if (api.getComponents() != null) {
                if (this.baseTemplate.getComponents() == null) {
                    this.baseTemplate.setComponents(new Components());
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

        });

        return baseTemplate;
    }

    private void mergeScheme(OpenAPI target, Entry<String, Schema> source) {
        var newKey = source.getKey();
        if (this.baseTemplate.getComponents().getSchemas() != null
            && this.baseTemplate.getComponents().getSchemas().get(newKey) != null) {

            var targetValue = this.baseTemplate.getComponents().getSchemas().get(newKey);
            var sourceValue = source.getValue();
            if (targetValue.equals(sourceValue)) {
                logger.info("Ignoring equal schema");
                return;
            } else {
                //throw new IllegalStateException("Schema " + newKey + " already exists and they are not equal");
                logger.warn("Schema " + newKey + " already exists and they are not equal");
                return;
            }

        }
        target.getComponents().addSchemas(newKey, source.getValue());

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