package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

import com.google.gson.annotations.SerializedName

enum class Template {
  @SerializedName("list")
  List,

  @SerializedName("list-section")
  ListSection,

  @SerializedName("list-tab")
  ListTab,

  @SerializedName("summary")
  Summary,

  @SerializedName("summary-section")
  SectionedSummary,

  @SerializedName("parent-child")
  ParentChild,
}
