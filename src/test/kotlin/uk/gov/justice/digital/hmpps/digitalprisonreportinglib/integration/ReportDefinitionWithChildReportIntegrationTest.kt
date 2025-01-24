package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.web.util.UriBuilder
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ReportDefinitionSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepositoryTest

class ReportDefinitionWithChildReportIntegrationTest : IntegrationTestBase() {

  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.add("dpr.lib.definition.locations") { "productDefinitionWithChild.json" }
    }
  }

  @Test
  fun `Definitions are returned when they match the filter`() {
    val result = webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions")
          .queryParam("renderMethod", "HTML")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBodyList<ReportDefinitionSummary>()
      .returnResult()

    assertThat(result.responseBody).isNotNull
    assertThat(result.responseBody).hasSize(1)
    assertThat(result.responseBody).first().isNotNull

    val definition = result.responseBody!!.first()

    assertThat(definition.variants).hasSize(1)
  }

  @Test
  fun `Definitions are not returned when they do not match the filter`() {
    val result = webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions")
          .queryParam("renderMethod", "SVG")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBodyList<ReportDefinitionSummary>()
      .returnResult()

    assertThat(result.responseBody).isNotNull
    assertThat(result.responseBody).hasSize(0)
  }

  @Test
  fun `Definitions do not contain null values`() {
    val result = webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions")
          .queryParam("renderMethod", "HTML")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(String::class.java)
      .returnResult()

    assertThat(result.responseBody).isNotNull
    assertThat(result.responseBody).doesNotContain(": null")
  }

  @Test
  fun `Single definition is returned in expected format`() {
    try {
      prisonerRepository.save(ConfiguredApiRepositoryTest.AllPrisoners.prisoner9848)
      externalMovementRepository.save(ConfiguredApiRepositoryTest.AllMovements.externalMovementDestinationCaseloadDirectionIn)

      webTestClient.get()
        .uri { uriBuilder: UriBuilder ->
          uriBuilder
            .path("/definitions/external-movements/prisoner-report")
            .queryParam("renderMethod", "HTML")
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
            "id": "external-movements",
            "name": "External Movements",
            "description": "Reports about prisoner external movements",
            "variant": {
              "id": "prisoner-report",
              "name": "Prisoners",
              "resourceName": "reports/external-movements/prisoner-report",
              "description": "Prisoners",
              "specification": {
                "template": "parent-child",
                "fields": [
                  {
                    "name": "prisonNumber",
                    "display": "Prison Number",
                    "wordWrap": null,
                    "sortable": true,
                    "defaultsort": true,
                    "type": "string",
                    "mandatory": false,
                    "visible": true
                  },
                  {
                    "name": "name",
                    "display": "Name",
                    "wordWrap": "none",
                    "sortable": true,
                    "defaultsort": false,
                    "type": "string",
                    "mandatory": false,
                    "visible": true
                  }
                ]
              },
              "classification": "report classification",
              "printable": false,
              "childVariants": [
                {
                  "id": "last-month",
                  "name": "Last month",
                  "resourceName": "reports/external-movements/last-month",
                  "specification": {
                    "template": "list",
                    "fields": [
                      {
                        "name": "prisonNumber",
                        "display": "Prison Number",
                        "wordWrap": null,
                        "filter": null,
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "name",
                        "display": "Name",
                        "wordWrap": "none",
                        "filter": null,
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "date",
                        "display": "Date",
                        "wordWrap": null,
                        "sortable": true,
                        "defaultsort": true,
                        "type": "date",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "origin",
                        "display": "From",
                        "wordWrap": "none",
                        "filter": {
                          "type": "text",
                          "mandatory": false,
                          "pattern": null,
                          "staticOptions": null,
                          "dynamicOptions": null,
                          "defaultValue": null,
                          "min": null,
                          "max": null,
                          "interactive": false,
                          "defaultGranularity": null,
                          "defaultQuickFilterValue": null
                        },
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "destination",
                        "display": "To",
                        "wordWrap": "none",
                        "filter": null,
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "direction",
                        "display": "Direction",
                        "wordWrap": "break-words",
                        "filter": {
                          "type": "Radio",
                          "mandatory": false,
                          "pattern": null,
                          "staticOptions": [
                            {
                              "name": "in",
                              "display": "In"
                            },
                            {
                              "name": "out",
                              "display": "Out"
                            }
                          ],
                          "dynamicOptions": null,
                          "defaultValue": null,
                          "min": null,
                          "max": null,
                          "interactive": true,
                          "defaultGranularity": null,
                          "defaultQuickFilterValue": null
                        },
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "type",
                        "display": "Type",
                        "wordWrap": "normal",
                        "filter": null,
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": false,
                        "visible": false,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "reason",
                        "display": "Reason",
                        "wordWrap": null,
                        "filter": {
                          "type": "autocomplete",
                          "mandatory": false,
                          "pattern": null,
                          "staticOptions": [
                            {
                              "name": "Transfer In from Other Establishment",
                              "display": "Transfer In from Other Establishment"
                            }
                          ],
                          "dynamicOptions": {
                            "minimumLength": 2
                          },
                          "defaultValue": null,
                          "min": null,
                          "max": null,
                          "interactive": false,
                          "defaultGranularity": null,
                          "defaultQuickFilterValue": null
                        },
                        "sortable": true,
                        "defaultsort": false,
                        "type": "string",
                        "mandatory": true,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      },
                      {
                        "name": "is_closed",
                        "display": "Closed",
                        "wordWrap": null,
                        "filter": null,
                        "sortable": true,
                        "defaultsort": false,
                        "type": "boolean",
                        "mandatory": false,
                        "visible": true,
                        "calculated": false,
                        "header": false
                      }
                    ],
                    "sections": []
                  },
                  "joinFields": [
                    "prisonNumber"
                  ]
                }
              ]
            }
          }

          """.trimIndent(),
        )
    } finally {
      externalMovementRepository.delete(ConfiguredApiRepositoryTest.AllMovements.externalMovementDestinationCaseloadDirectionIn)
      prisonerRepository.delete(ConfiguredApiRepositoryTest.AllPrisoners.prisoner9848)
    }
  }
}
