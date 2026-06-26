window.onload = function () {
  // Each entry in apis.json is one service's source OpenAPI doc, served verbatim.
  // Swagger UI renders the selected spec with all its examples and descriptions intact.
  // The landing page (index.html) links here with ?api=<name> to preselect a spec;
  // the dropdown still lets the user switch between all services.
  fetch('./apis.json')
    .then(function (response) {
      return response.json();
    })
    .then(function (urls) {
      var requestedName = new URLSearchParams(window.location.search).get('api');
      var requestedMatch = urls.filter(function (entry) {
        return entry.name === requestedName;
      });
      var primaryName = requestedMatch.length
        ? requestedMatch[0].name
        : urls.length
          ? urls[0].name
          : undefined;
      window.ui = SwaggerUIBundle({
        urls: urls,
        'urls.primaryName': primaryName,
        dom_id: '#swagger-ui',
        deepLinking: true,
        presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
        plugins: [SwaggerUIBundle.plugins.DownloadUrl],
        layout: 'StandaloneLayout',
      });
    });
};
