package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.integration

import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.util.UriBuilder

class DashboardDefinitionIntegrationTest : IntegrationTestBase() {
  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.add("dpr.lib.definition.locations") { "productDefinitionWithMetrics.json" }
    }
  }

  @Test
  fun `Dashboard definition is returned as expected`() {
    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/missing-ethnicity-metrics/dashboards/test-dashboard-1")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .json(
        """
            {
            "id": "test-dashboard-1",
            "name": "Test Dashboard 1",
            "description": "Test Dashboard 1 Description",
            "metrics": [
              {
                "id": "missing-ethnicity-metric",
                "name": "Missing Ethnicity By Establishment Metric",
                "display": "Missing Ethnicity By Establishment Metric",
                "description": "Missing Ethnicity By Establishment Metric",
                "columns": [
                      {
                        "name": "has_ethnicity",
                        "display": "No. of Prisoners with ethnicity",
                        "unit": "number",
                        "aggregate": "sum"
                      },
                      {
                        "name": "ethnicity_is_missing",
                        "display": "No. of Prisoners without ethnicity",
                        "unit": "number",
                        "aggregate": "sum"
                      }
                ],
                "charts": [
                  {
                    "type": "bar",
                    "label": {
                      "name": "establishment_id",
                      "display": "Establishment ID"
                    },
                    "columns": ["has_ethnicity","ethnicity_is_missing"]
                  },
                  {
                    "type": "doughnut",
                    "label": {
                      "name": "establishment_id",
                      "display": "Establishment ID"
                    },
                    "columns": ["has_ethnicity","ethnicity_is_missing"]
                  }
                ]
              }
            ],
            "filterFields": [
              {
                "name":"establishment_id",
                "display":"Establishment ID",
                "filter": {
                  "type":"Select",
                  "mandatory":false,
                  "staticOptions": [
                    {
                      "name":"AAA",
                      "display":"Aardvark"
                    },
                    {
                      "name":"BBB",
                      "display":"Bumblebee"
                    }
                  ],
                  "dynamicOptions": {
                    "minimumLength":null
                  },
                  "interactive":true
                },
                "sortable":true,
                "defaultsort":false,
                "type":"string",
                "mandatory":false,
                "visible":true,
                "calculated":false,
                "header":false
              }
            ] 
          }
        """.trimIndent(),
      )
  }
}
