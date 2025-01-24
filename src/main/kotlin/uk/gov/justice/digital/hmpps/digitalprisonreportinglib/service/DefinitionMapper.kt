package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DynamicFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.GranularityDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.QuickFilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ParameterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.StaticFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.ChronoUnit

abstract class DefinitionMapper(
  private val syncDataApiService: SyncDataApiService,
  val identifiedHelper: IdentifiedHelper,
) {

  companion object {
    const val DEFAULT_MAX_STATIC_OPTIONS: Long = 30
  }

  private val todayRegex: Regex = Regex("today\\(\\)")
  private val dateRegex: Regex = Regex("today\\((-?\\d+), ?([a-z]+)\\)", RegexOption.IGNORE_CASE)

  protected fun convertParameterTypeToFieldType(parameterType: ParameterType): FieldType {
    return when (parameterType) {
      ParameterType.Boolean -> FieldType.Boolean
      ParameterType.Date -> FieldType.Date
      ParameterType.DateTime -> FieldType.Date
      ParameterType.Timestamp -> FieldType.Date
      ParameterType.Time -> FieldType.Time
      ParameterType.Double -> FieldType.Double
      ParameterType.Float -> FieldType.Double
      ParameterType.Integer -> FieldType.Long
      ParameterType.Long -> FieldType.Long
      ParameterType.String -> FieldType.String
    }
  }

  protected fun map(
    filterDefinition: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterDefinition,
    staticOptions: List<FilterOption>?,
  ): FilterDefinition {
    return FilterDefinition(
      type = FilterType.valueOf(filterDefinition.type.toString()),
      staticOptions = staticOptions,
      dynamicOptions = filterDefinition.dynamicOptions?.let {
        DynamicFilterOption(
          minimumLength = filterDefinition.dynamicOptions.minimumLength,
        )
      },
      defaultValue = replaceTokens(filterDefinition.default),
      min = replaceTokens(filterDefinition.min),
      max = replaceTokens(filterDefinition.max),
      mandatory = filterDefinition.mandatory,
      pattern = filterDefinition.pattern,
      interactive = filterDefinition.interactive ?: false,
      defaultGranularity = filterDefinition.defaultGranularity?.let { GranularityDefinition.valueOf(it.toString()) },
      defaultQuickFilterValue = filterDefinition.defaultQuickFilterValue?.let { QuickFilterDefinition.valueOf(it.toString()) },
    )
  }

  protected fun populateStaticOptions(
    filterDefinition: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterDefinition,
    productDefinitionId: String,
    reportVariantId: String,
    schemaFieldName: String,
    maxStaticOptions: Long?,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String?,
    allDatasets: List<Dataset>,
  ): List<FilterOption>? {
    return filterDefinition.dynamicOptions?.takeIf { it.returnAsStaticOptions }?.let { dynamicFilterOption ->
      dynamicFilterOption.dataset?.let { dynamicFilterDatasetId ->
        return populateStaticOptionsForFilterWithDataset(
          dynamicFilterOption,
          allDatasets,
          dynamicFilterDatasetId,
          maxStaticOptions,
        )
      }
        ?: populateStandardStaticOptionsForReportDefinition(
          productDefinitionId,
          reportVariantId,
          maxStaticOptions,
          schemaFieldName,
          userToken,
          dataProductDefinitionsPath,
        )
    } ?: filterDefinition.staticOptions?.map(this::map)
  }

  private fun populateStandardStaticOptionsForReportDefinition(
    productDefinitionId: String,
    reportVariantId: String,
    maxStaticOptions: Long?,
    schemaFieldName: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String?,
  ) = syncDataApiService.validateAndFetchData(
    reportId = productDefinitionId,
    reportVariantId = reportVariantId,
    filters = emptyMap(),
    selectedPage = 1,
    pageSize = maxStaticOptions ?: DEFAULT_MAX_STATIC_OPTIONS,
    sortColumn = schemaFieldName,
    sortedAsc = true,
    userToken = userToken,
    reportFieldId = setOf(schemaFieldName),
    dataProductDefinitionsPath = dataProductDefinitionsPath,
  )
    .flatMap { it.entries }
    .map { FilterOption(it.value.toString(), it.value.toString()) }

  protected fun populateStaticOptionsForFilterWithDataset(
    dynamicFilterOption: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.DynamicFilterOption,
    allDatasets: List<Dataset>,
    dynamicFilterDatasetId: String,
    maxStaticOptions: Long?,
  ): List<FilterOption> {
    val matchingFilterDataset = identifiedHelper.findOrFail(allDatasets, dynamicFilterDatasetId)
    val matchingSchemaFieldsForFilterDataset = matchingFilterDataset.schema.field
    val nameSchemaField = identifiedHelper.findOrFail(matchingSchemaFieldsForFilterDataset, dynamicFilterOption.name)
    val displaySchemaField = identifiedHelper.findOrFail(matchingSchemaFieldsForFilterDataset, dynamicFilterOption.display)
    return syncDataApiService.validateAndFetchDataForFilterWithDataset(
      pageSize = maxStaticOptions ?: DEFAULT_MAX_STATIC_OPTIONS,
      sortColumn = nameSchemaField.name,
      dataset = matchingFilterDataset,
    )
      .map { FilterOption(it[nameSchemaField.name].toString(), it[displaySchemaField.name].toString()) }
  }

  private fun replaceTokens(defaultValue: String?): String? {
    if (defaultValue == null) {
      return null
    }

    val today = LocalDate.now().format(ISO_LOCAL_DATE)
    var result = defaultValue.replace(todayRegex, today)

    dateRegex.findAll(result)
      .forEach {
        val offset = it.groupValues[1].toLong()
        val resultDate = LocalDate.now().plus(offset, ChronoUnit.valueOf(it.groupValues[2].uppercase()))

        result = result.replace(it.value, resultDate.format(ISO_LOCAL_DATE))
      }

    return result
  }

  protected fun map(definition: StaticFilterOption): FilterOption = FilterOption(
    name = definition.name,
    display = definition.display,
  )
}
