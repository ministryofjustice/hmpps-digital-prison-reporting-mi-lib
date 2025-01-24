package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

abstract class Identified {
  companion object {
    const val REF_PREFIX = "\$ref:"
  }

  abstract fun getIdentifier(): String
}
