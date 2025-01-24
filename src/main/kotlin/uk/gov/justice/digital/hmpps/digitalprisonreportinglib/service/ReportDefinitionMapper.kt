package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ChildVariantDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ReportSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SingleVariantReportDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.Specification
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SummaryField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SummaryTemplate
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.Template
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.VariantDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.WordWrap
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.establishmentsAndWings.EstablishmentToWing.Companion.ALL_WINGS
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FeatureType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Identified.Companion.REF_PREFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Parameter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReferenceType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportChild
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportMetadataHint
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SchemaField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Visible
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.FormulaEngine.Companion.MAKE_URL_FORMULA_PREFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.estcodesandwings.EstablishmentCodesToWingsCacheService

@Component
class ReportDefinitionMapper(
  syncDataApiService: SyncDataApiService,
  identifiedHelper: IdentifiedHelper,
  val establishmentCodesToWingsCacheService: EstablishmentCodesToWingsCacheService,
) : DefinitionMapper(syncDataApiService, identifiedHelper) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun mapReport(definition: SingleReportProductDefinition, userToken: DprAuthAwareAuthenticationToken?, dataProductDefinitionsPath: String? = null): SingleVariantReportDefinition {
    return SingleVariantReportDefinition(
      id = definition.id,
      name = definition.name,
      description = definition.description,
      variant = mapVariant(
        report = definition.report,
        dataSet = definition.reportDataset,
        productDefinitionId = definition.id,
        userToken = userToken,
        dataProductDefinitionsPath = dataProductDefinitionsPath,
        allDatasets = definition.allDatasets,
        allReports = definition.allReports,
      ),
    )
  }

  private fun mapVariant(
    report: Report,
    dataSet: Dataset,
    productDefinitionId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String? = null,
    allDatasets: List<Dataset>,
    allReports: List<Report>,
  ): VariantDefinition {
    return VariantDefinition(
      id = report.id,
      name = report.name,
      description = report.description,
      specification = mapSpecification(
        specification = report.specification,
        schemaFields = dataSet.schema.field,
        productDefinitionId = productDefinitionId,
        reportVariantId = report.id,
        userToken = userToken,
        dataProductDefinitionsPath = dataProductDefinitionsPath,
        allDatasets = allDatasets,
        parameters = dataSet.parameters,
      ),
      classification = report.classification,
      printable = report.feature?.any { it.type == FeatureType.PRINT } ?: false,
      resourceName = "reports/$productDefinitionId/${report.id}",
      summaries = report.summary?.map { mapReportSummary(it, allDatasets) },
      interactive = report.metadata?.hints?.contains(ReportMetadataHint.INTERACTIVE),
      childVariants = report.child?.map { c ->
        mapChildVariant(
          child = c,
          productDefinitionId = productDefinitionId,
          userToken = userToken,
          dataProductDefinitionsPath = dataProductDefinitionsPath,
          allDatasets = allDatasets,
          allReports = allReports,
        )
      },
    )
  }

  private fun mapChildVariant(
    child: ReportChild,
    productDefinitionId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String? = null,
    allDatasets: List<Dataset>,
    allReports: List<Report>,
  ): ChildVariantDefinition {
    val report = identifiedHelper.findOrFail(allReports, child.reportId)

    val variant = mapVariant(
      report = report,
      dataSet = identifiedHelper.findOrFail(allDatasets, report.dataset),
      productDefinitionId = productDefinitionId,
      userToken = userToken,
      dataProductDefinitionsPath = dataProductDefinitionsPath,
      allDatasets = allDatasets,
      allReports = allReports,
    )

    return ChildVariantDefinition(
      id = variant.id,
      name = variant.name,
      resourceName = variant.resourceName,
      specification = variant.specification,
      joinFields = child.joinField,
    )
  }

  private fun mapSpecification(
    specification: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Specification?,
    schemaFields: List<SchemaField>,
    productDefinitionId: String,
    reportVariantId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String?,
    allDatasets: List<Dataset> = emptyList(),
    parameters: List<Parameter>? = null,
  ): Specification? {
    if (specification == null) {
      return null
    }
    return Specification(
      template = Template.valueOf(specification.template.toString()),
      sections = specification.section?.map { it.removePrefix(REF_PREFIX) } ?: emptyList(),
      fields = mapToReportFieldDefinitions(
        specification,
        schemaFields,
        productDefinitionId,
        reportVariantId,
        userToken,
        dataProductDefinitionsPath,
        allDatasets,
      ) + maybeConvertToReportFields(parameters),
    )
  }

  private fun mapReportSummary(summary: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportSummary, allDatasets: List<Dataset>): ReportSummary =
    ReportSummary(
      id = summary.id,
      template = SummaryTemplate.valueOf(summary.template.toString()),
      fields = identifiedHelper.findOrFail(allDatasets, summary.dataset).schema.field.map { mapSummaryField(it, summary.field) },
    )

  private fun mapSummaryField(field: SchemaField, summaryFields: List<uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SummaryField>?): SummaryField {
    val summaryField = identifiedHelper.findOrNull(summaryFields, field.name)
    return SummaryField(
      name = field.name,
      display = field.display,
      type = convertParameterTypeToFieldType(field.type),
      header = summaryField?.header,
      mergeRows = summaryField?.mergeRows,
    )
  }

  private fun maybeConvertToReportFields(parameters: List<Parameter>?) =
    parameters?.map { mapParameterToField(it) } ?: emptyList()

  private fun mapToReportFieldDefinitions(
    specification: uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Specification,
    schemaFields: List<SchemaField>,
    productDefinitionId: String,
    reportVariantId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String?,
    allDatasets: List<Dataset>,
  ) = specification.field.map {
    mapField(
      field = it,
      schemaFields = schemaFields,
      productDefinitionId = productDefinitionId,
      reportVariantId = reportVariantId,
      userToken = userToken,
      dataProductDefinitionsPath = dataProductDefinitionsPath,
      allDatasets = allDatasets,
    )
  }

  private fun mapParameterToField(parameter: Parameter): FieldDefinition {
    return FieldDefinition(
      name = parameter.name,
      display = parameter.display,
      sortable = false,
      defaultsort = false,
      type = convertParameterTypeToFieldType(parameter.reportFieldType),
      mandatory = false,
      visible = false,
      filter = FilterDefinition(
        type = FilterType.valueOf(parameter.filterType.toString()),
        mandatory = parameter.mandatory,
        interactive = false,
        staticOptions = populateStaticOptionsForParameter(parameter),
      ),
    )
  }

  private fun populateStaticOptionsForParameter(parameter: Parameter): List<FilterOption>? {
    return parameter.referenceType
      ?.let {
        when (it) {
          ReferenceType.ESTABLISHMENT -> mapEstablishmentsToFilterOptions()
          ReferenceType.WING -> mapWingsToFilterOptions()
        }
      }?.takeIf { it.isNotEmpty() }
  }

  private fun mapWingsToFilterOptions(): List<FilterOption> {
    val wingsFlattened = establishmentCodesToWingsCacheService
      .getEstablishmentsAndPopulateCacheIfNeeded()
      .takeIf { it.isNotEmpty() }
      ?.flatMap { it.value }
    log.debug("All wings count: ${wingsFlattened?.count() ?: 0}")
    return wingsFlattened
      ?.map { FilterOption(it.wing, it.wing) }
      ?.plus(FilterOption(ALL_WINGS, ALL_WINGS))
      ?: emptyList()
  }

  private fun mapEstablishmentsToFilterOptions(): List<FilterOption> {
    return establishmentCodesToWingsCacheService
      .getEstablishmentsAndPopulateCacheIfNeeded()
      .map { FilterOption(it.key, it.value.first().description) }
  }

  private fun mapField(
    field: ReportField,
    schemaFields: List<SchemaField>,
    productDefinitionId: String,
    reportVariantId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String?,
    allDatasets: List<Dataset>,
  ): FieldDefinition {
    val schemaField = identifiedHelper.findOrFail(schemaFields, field.name)

    return FieldDefinition(
      name = schemaField.name,
      display = populateDisplay(field.display, schemaField.display),
      wordWrap = field.wordWrap?.toString()?.let(WordWrap::valueOf),
      filter = (schemaField.filter ?: field.filter)?.let {
        map(
          filterDefinition = it,
          staticOptions = populateStaticOptions(
            filterDefinition = it,
            productDefinitionId = productDefinitionId,
            reportVariantId = reportVariantId,
            schemaFieldName = schemaField.name,
            maxStaticOptions = it.dynamicOptions?.maximumOptions,
            userToken = userToken,
            dataProductDefinitionsPath = dataProductDefinitionsPath,
            allDatasets = allDatasets,
          ),
        )
      },
      sortable = field.sortable,
      defaultsort = field.defaultSort,
      type = populateType(schemaField, field),
      mandatory = populateMandatory(field.visible),
      visible = populateVisible(field.visible),
      calculated = field.formula?.isNotBlank() ?: false,
    )
  }

  private fun populateDisplay(reportFieldDisplay: String?, schemaFieldDisplay: String): String {
    return reportFieldDisplay?.ifBlank { schemaFieldDisplay } ?: schemaFieldDisplay
  }

  private fun populateVisible(visible: Visible?): Boolean {
    return visible?.let {
      when (visible) {
        Visible.TRUE -> true
        Visible.FALSE -> false
        Visible.MANDATORY -> true
      }
    } ?: true
  }

  private fun populateMandatory(visible: Visible?): Boolean {
    return visible?.let {
      when (visible) {
        Visible.TRUE -> false
        Visible.FALSE -> false
        Visible.MANDATORY -> true
      }
    } ?: false
  }

  private fun populateType(schemaField: SchemaField, reportField: ReportField): FieldType {
    if (reportField.formula?.startsWith(MAKE_URL_FORMULA_PREFIX) == true) {
      return FieldType.HTML
    }

    return convertParameterTypeToFieldType(schemaField.type)
  }
}
