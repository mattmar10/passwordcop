package com.mattmartin.example.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

object PasswordCopService  {
  val TOPIC_NAME = "passwordchanges"
}

/**
  * The password-cop service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the PasswordcopService.
  */
trait PasswordCopService extends Service {

  /**
    * Example: curl http://localhost:9000/api/hello/Alice
    */
  //def hello(id: String): ServiceCall[NotUsed, String]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
    * "Hi"}' http://localhost:9000/api/hello/Alice
    */
  //def useGreeting(id: String): ServiceCall[GreetingMessage, Done]


  /**
    * Changes the password for a given userId. Will add a user if one is not found by the given
    * userId.
    *
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"userId":
    * "bob@bobsemail.com", "password":"thisIs@StrongPAssworD!"}' http://localhost:9000/api/changePassword
    */
  def changePassword: ServiceCall[ChangePasswordMessage, ChangePasswordResponseMessage]

  /**
    * Checks if the given userId has a password that is expired. If no user found, returns
    *
    */
  def checkPasswordExpired(userIdEmail: String): ServiceCall[NotUsed, Boolean]




  /**
    * This gets published to Kafka.
    */
  def passwordChangedTopic(): Topic[PasswordChanged]

  override final def descriptor = {
    import Service._
    // @formatter:off
    named("password-cop")
      .withCalls(
        pathCall("/api/checkPasswordExpiration/:userIdEmail", checkPasswordExpired _),
        pathCall("/api/changePassword", changePassword )
      )
      .withTopics(
        topic(PasswordCopService.TOPIC_NAME, passwordChangedTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[PasswordChanged](_.emailId)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
  * The greeting message class.
  */
case class GreetingMessage(message: String)

object GreetingMessage {
  /**
    * Format for converting greeting messages to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}

case class ChangePasswordMessage(userId: String, password: String)

object ChangePasswordMessage{
  implicit val format: Format[ChangePasswordMessage] = Json.format[ChangePasswordMessage]
}

case class ChangePasswordResponseMessage(emailId: String, result: Boolean, message: Option[String] = None)

object ChangePasswordResponseMessage{
  implicit val format: Format[ChangePasswordResponseMessage] = Json.format[ChangePasswordResponseMessage]
}


/**
  * The greeting message class used by the topic stream.
  * Different than [[GreetingMessage]], this message includes the name (id).
  */
case class GreetingMessageChanged(name: String, message: String)

object GreetingMessageChanged {
  /**
    * Format for converting greeting messages to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[GreetingMessageChanged] = Json.format[GreetingMessageChanged]
}

/**
  * Used by the topic stream.
  * @param emailId
  * @param expirationDate
  */
case class PasswordChanged(emailId: String, expirationDate: Option[Long])

object PasswordChanged{
  implicit val format: Format[PasswordChanged] = Json.format[PasswordChanged]
}