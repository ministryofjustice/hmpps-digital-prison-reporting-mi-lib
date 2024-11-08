package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.DefinitionGsonConfig
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ChartDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ChartTypeDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DashboardDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DataDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.MetricDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IsoLocalDateTimeTypeAdaptor
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.JsonFileProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository

class DashboardDefinitionServiceTest {

  private val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
    listOf("productDefinitionWithMetrics.json"),
    DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
  )

  private val dashboardDefinitionService = DashboardDefinitionService(productDefinitionRepository)

  @Test
  fun `getDashboardDefinition returns the dashboard definition`() {
    val actual = dashboardDefinitionService.getDashboardDefinition(
      dataProductDefinitionId = "missing-ethnicity-metrics",
      dashboardId = "test-dashboard-1",
    )
    assertEquals(
      DashboardDefinition(
        id = "test-dashboard-1",
        name = "Test Dashboard 1",
        description = "Test Dashboard 1 Description",
        metrics = listOf(
          MetricDefinition(
            id = "missing-ethnicity-metric",
            name = "Missing Ethnicity By Establishment Metric",
            display = "Missing Ethnicity By Establishment Metric",
            description = "Missing Ethnicity By Establishment Metric",
            charts = listOf(
              ChartDefinition(
                ChartTypeDefinition.BAR,
                dimension = "establishment_id",
              ),
            ),
            data = listOf(
              listOf(
                DataDefinition(name = "ethnicity_is_missing", display = "No. of Prisoners without ethnicity", unit = "number"),
                DataDefinition(name = "has_ethnicity", display = "No. of Prisoners with ethnicity", unit = "number"),
              ),
            ),
          ),
        ),
      ),
      actual,
    )
  }
}