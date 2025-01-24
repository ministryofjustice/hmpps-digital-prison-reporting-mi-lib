package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.given
import org.mockito.kotlin.then
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.AwsProperties
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.DefinitionGsonConfig
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.DynamoDbProductDefinitionRepository.Companion.DEFAULT_PATH
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.DynamoDbProductDefinitionRepository.Companion.getQueryRequest

class DynamoDbProductDefinitionRepositoryTest {

  private val dynamoDbClient = mock<DynamoDbClient>()
  private val properties = AwsProperties(
    dynamoDb = AwsProperties.DynamoDb(),
    sts = AwsProperties.Sts(),
  )

  private val repo = DynamoDbProductDefinitionRepository(
    dynamoDbClient = dynamoDbClient,
    gson = DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    properties = properties,
    identifiedHelper = IdentifiedHelper(),
  )

  @BeforeEach
  fun setup() {
    val response = mock<QueryResponse>()

    given(dynamoDbClient.query(any(QueryRequest::class.java))).willReturn(response)
    given(response.items()).willReturn(
      listOf(
        mapOf("definition" to AttributeValue.fromS("{\"id\": \"test1\"}")),
        mapOf("definition" to AttributeValue.fromS("{\"id\": \"test2\"}")),
      ),
    )
  }

  @Test
  fun `returns the correct product definitions`() {
    val productDefinitions = repo.getProductDefinitions()

    assertThat(productDefinitions).isNotNull
    assertThat(productDefinitions.count()).isEqualTo(2)

    then(dynamoDbClient).should().query(getQueryRequest(properties, DEFAULT_PATH))
  }

  @Test
  fun `returns the correct product definition`() {
    val productDefinition = repo.getProductDefinition("test2")

    assertThat(productDefinition).isNotNull
    assertThat(productDefinition.id).isEqualTo("test2")
    then(dynamoDbClient).should().query(getQueryRequest(properties, DEFAULT_PATH))
  }

  @Test
  fun `returns the correct product definitions using a path`() {
    val path = "dpd/path"

    val productDefinitions = repo.getProductDefinitions(path)

    assertThat(productDefinitions).isNotNull
    assertThat(productDefinitions.count()).isEqualTo(2)
    then(dynamoDbClient).should().query(getQueryRequest(properties, path))
  }

  @Test
  fun `returns the correct product definition using a path`() {
    val path = "dpd/path"

    val productDefinition = repo.getProductDefinition("test2", path)

    assertThat(productDefinition).isNotNull
    assertThat(productDefinition.id).isEqualTo("test2")
    then(dynamoDbClient).should().query(getQueryRequest(properties, path))
  }
}
