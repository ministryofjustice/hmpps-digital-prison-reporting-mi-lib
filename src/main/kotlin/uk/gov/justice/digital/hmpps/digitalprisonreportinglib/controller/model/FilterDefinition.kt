package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model

import com.google.gson.annotations.SerializedName
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.DynamicFilterOption

data class FilterDefinition(
  val type: FilterType,
  val staticOptions: List<FilterOption>? = null,
  @SerializedName("dynamicoptions")
  val dynamicOptions: DynamicFilterOption? = null,
  val defaultValue: String?,
  val min: String? = null,
  val max: String? = null,
)
