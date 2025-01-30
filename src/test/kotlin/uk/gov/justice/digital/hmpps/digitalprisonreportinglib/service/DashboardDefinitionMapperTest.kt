package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.DefinitionGsonConfig
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ChartDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ChartTypeDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ColumnAggregateTypeDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ColumnDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DashboardDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DynamicFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.LabelDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.MetricDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IsoLocalDateTimeTypeAdaptor
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.JsonFileProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository

class DashboardDefinitionMapperTest {

  private val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
    listOf("productDefinitionWithMetrics.json"),
    DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    identifiedHelper = IdentifiedHelper(),
  )
  private val syncDataApiService: SyncDataApiService = Mockito.mock()

  private val dashboardDefinitionMapper = DashboardDefinitionMapper(syncDataApiService, IdentifiedHelper())

  @Test
  fun `getDashboardDefinition returns the dashboard definition`() {
    whenever(syncDataApiService.validateAndFetchDataForFilterWithDataset(any(), any(), any())).then {
      listOf(
        mapOf("establishment_id" to "AAA", "establishment_name" to "Aardvark"),
        mapOf("establishment_id" to "BBB", "establishment_name" to "Bumblebee"),
      )
    }

    val productDefinition = productDefinitionRepository.getSingleDashboardProductDefinition("missing-ethnicity-metrics", "test-dashboard-1")

    val actual = dashboardDefinitionMapper.toDashboardDefinition(
      dashboard = productDefinition.dashboard,
      allDatasets = productDefinition.allDatasets,
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
            columns = listOf(
              ColumnDefinition(name = "has_ethnicity", display = "No. of Prisoners with ethnicity", unit = "number", aggregate = ColumnAggregateTypeDefinition.SUM),
              ColumnDefinition(name = "ethnicity_is_missing", display = "No. of Prisoners without ethnicity", unit = "number", aggregate = ColumnAggregateTypeDefinition.SUM),
            ),
            charts = listOf(
              ChartDefinition(
                type = ChartTypeDefinition.BAR,
                label = LabelDefinition(name = "establishment_id", display = "Establishment ID"),
                columns = listOf(
                  "has_ethnicity",
                  "ethnicity_is_missing",
                ),
              ),
              ChartDefinition(
                type = ChartTypeDefinition.DOUGHNUT,
                label = LabelDefinition(name = "establishment_id", display = "Establishment ID"),
                columns = listOf(
                  "has_ethnicity",
                  "ethnicity_is_missing",
                ),
              ),
            ),
          ),
        ),
        filterFields = listOf(
          FieldDefinition(
            name = "establishment_id",
            display = "Establishment ID",
            filter = FilterDefinition(
              type = FilterType.Select,
              mandatory = false,
              staticOptions = listOf(
                FilterOption(name = "AAA", display = "Aardvark"),
                FilterOption(name = "BBB", display = "Bumblebee"),
              ),
              dynamicOptions = DynamicFilterOption(minimumLength = null),
              interactive = true,
            ),
            sortable = true,
            defaultsort = false,
            type = FieldType.String,
            mandatory = false,
            visible = true,
            calculated = false,
            header = false,
          ),
        ),
      ),
      actual,
    )

    verify(syncDataApiService).validateAndFetchDataForFilterWithDataset(
      pageSize = eq(123L),
      sortColumn = eq("establishment_id"),
      dataset = any(),
    )
  }
}
