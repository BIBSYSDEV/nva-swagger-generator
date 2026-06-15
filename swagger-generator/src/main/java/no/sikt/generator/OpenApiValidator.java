package no.sikt.generator;

import static java.util.Objects.nonNull;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiValidator.class);

  public OpenApiValidator() {}

  public void validateOpenApi(OpenAPI openAPI) {
    if (nonNull(openAPI.getComponents()) && nonNull(openAPI.getComponents().getSchemas())) {
      openAPI
          .getComponents()
          .getSchemas()
          .keySet()
          .forEach(schemaName -> validateSchemaName(openAPI.getInfo().getTitle(), schemaName));
    }
  }

  public void validateSchemaName(String apiName, String schemaName) {
    if (schemaName.matches(".*\\d.*")) {
      LOGGER.warn("API {} schema '{}' contains numbers", apiName, schemaName);
    }
    if (schemaName.matches(".*\\s.*")) {
      LOGGER.warn("API {} schema '{}' contains whitespace", apiName, schemaName);
    }
  }
}
