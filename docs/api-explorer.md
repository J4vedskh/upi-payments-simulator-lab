# API Explorer

Browse the versioned UPI Payments Simulator contract as an interactive, read-only
reference. The explorer renders the same OpenAPI YAML that is validated by the
repository's contract tests and published with this documentation site.
Its pinned RapiDoc browser bundle is self-hosted with the site, so rendering does
not depend on a runtime CDN request.

!!! info "Read-only portfolio explorer"

    Request execution is disabled. The documented service URLs use local demo
    ports, and this page does not send payment or ledger requests.

[Download the OpenAPI YAML](api/openapi.yaml)

<div class="api-explorer-shell">
  <rapi-doc
    spec-url="../api/openapi.yaml"
    render-style="read"
    allow-try="false"
    show-header="false"
  ></rapi-doc>
</div>

<noscript>
  JavaScript is required for the interactive renderer. Use the OpenAPI YAML link
  above when scripting is unavailable.
</noscript>
