package no.sikt.generator.handlers;

import java.util.Optional;

public class TestCase {

  private String id;
  private String name;
  private String contentApiGateway;
  private Optional<String> contentGithub;

  public TestCase(
      String id, String name, String contentApiGateway, Optional<String> contentGithub) {
    this.id = id;
    this.name = name;
    this.contentApiGateway = contentApiGateway;
    this.contentGithub = contentGithub;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getContentApiGateway() {
    return contentApiGateway;
  }

  public Optional<String> getContentGithub() {
    return contentGithub;
  }
}
