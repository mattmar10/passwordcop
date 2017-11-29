package com.mattmartin.example.impl

import java.time.{Instant, LocalDateTime}

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import com.mattmartin.example.api.PasswordHistoryItem
import play.api.libs.json.{Format, Json}

import scala.collection.immutable.Seq

/**
  * This is an event sourced entity. It has a state, [[PasswordcopState]], which
  * stores what the greeting should be (eg, "Hello").
  *
  * Event sourced entities are interacted with by sending them commands. This
  * entity supports two commands, a [[UseGreetingMessage]] command, which is
  * used to change the greeting, and a [[Hello]] command, which is a read
  * only command which returns a greeting to the name specified by the command.
  *
  * Commands get translated to events, and it's the events that get persisted by
  * the entity. Each event will have an event handler registered for it, and an
  * event handler simply applies an event to the current state. This will be done
  * when the event is first created, and it will also be done when the entity is
  * loaded from the database - each event will be replayed to recreate the state
  * of the entity.
  *
  * This entity defines one event, the [[GreetingMessageChanged]] event,
  * which is emitted when a [[UseGreetingMessage]] command is received.
  */
class PasswordcopEntity extends PersistentEntity {

  override type Command = PasswordcopCommand[_]
  override type Event = PasswordcopEvent
  override type State = Option[PasswordEntityState]


  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  override def initialState = None

  /**
    * An entity can define different behaviours for different states, so the behaviour
    * is a function of the current state to a set of actions.
    */
  /*override def behavior: Behavior = {
    case (state: PasswordEntityState) => Actions().onCommand[UseGreetingMessage, Done] {

      // Command handler for the UseGreetingMessage command
      case (UseGreetingMessage(newMessage), ctx, state) =>
        // In response to this command, we want to first persist it as a
        // GreetingMessageChanged event
        ctx.thenPersist(
          GreetingMessageChanged(newMessage)
        ) { _ =>
          // Then once the event is successfully persisted, we respond with done.
          ctx.reply(Done)
        }

    }.onCommand[ChangePasswordRequest, PasswordHistoryItem]{
      case (ChangePasswordRequest(email, pass), ctx, state) => {

          val evt = PasswordChangeSuccessulEvent(email, Instant.now.toEpochMilli, Instant.now.toEpochMilli + 25000)
          ctx.thenPersist(evt)
          {_ => ctx.reply(state.)}
      }
    }.onReadOnlyCommand[Hello, String] {

      // Command handler for the Hello command
      case (Hello(name), ctx, state) =>
        // Reply with a message built from the current message, and the name of
        // the person we're meant to say hello to.
        ctx.reply(s"$message, $name!")

    }.onEvent {

      // Event handler for the GreetingMessageChanged event
      case (GreetingMessageChanged(newMessage), state) =>
        // We simply update the current state to use the greeting message from
        // the event.
        PasswordcopState(newMessage, LocalDateTime.now().toString)

    }
  }*/
  def validateNewPassword(userIdEmail: String, newPass: String, history: Option[Seq[PasswordHistoryItem]]) : Option[PasswordHistoryItem] = {
    PasswordValidator.validatePasswordForUser(userIdEmail, newPass, history) match {
      case false => None
      case true => {
        val time = Instant.now.toEpochMilli
        val passwordHistoryItem = PasswordHistoryItem(userIdEmail, newPass, Some(time), Some(time + PasswordValidator.PASSWORD_EXPIRATION_MS) )
        Some(passwordHistoryItem)
      }
    }
  }


  override def behavior: Behavior = {
    case None => notCreated
    case _ => created
  }

  private val getLastPasswordCommand = Actions().onReadOnlyCommand[GetLastPasswordChanged, Option[PasswordHistoryItem]] {
    case (GetLastPasswordChanged(emailId), ctx, state) => ctx.reply( state match {
      case None => None
      case Some(passwordEntityState) => Some(passwordEntityState.passwordHistory.last)
    })
  }

  private val notCreated = {
    Actions().onCommand[ChangePasswordRequest, Option[PasswordHistoryItem]] {
      case ((cpr: ChangePasswordRequest), ctx, state) =>
        val passwordHistoryMaybe = validateNewPassword(cpr.emailId, cpr.encryptedPassword, None)

        passwordHistoryMaybe match{
          case None => {
            //ctx.invalidCommand("Unable to create password")
            ctx.reply(passwordHistoryMaybe)
            ctx.done
          }
          case Some(phi) => ctx.thenPersist(PasswordChangeSuccessulEvent(phi))(_ => ctx.reply(passwordHistoryMaybe))
        }
    }.onReadOnlyCommand[GetLastPasswordChanged, Option[PasswordHistoryItem]]{
      case ((glpc: GetLastPasswordChanged), ctx, state) => ctx.reply(None)
    }.onEvent {
      case (pcse: PasswordChangeSuccessulEvent, state) =>
        Some(PasswordEntityState(pcse.passwordHistoryItem.userIdEmail, List(pcse.passwordHistoryItem), LocalDateTime.now.toString ))
      // if this happens, just do nothing and return state
      case (_, state) => state
    }.orElse(getLastPasswordCommand)
  }

  private val created = {
    Actions().onCommand[ChangePasswordRequest, Option[PasswordHistoryItem]] {
      case ((cpr: ChangePasswordRequest), ctx, state) =>
        val time = Instant.now.toEpochMilli
        val passwordHistoryItem:PasswordHistoryItem = PasswordHistoryItem(cpr.emailId, cpr.encryptedPassword, Some(time), Some(time + 25000) )
        ctx.thenPersist(PasswordChangeSuccessulEvent(passwordHistoryItem))(_ => ctx.reply(Some(passwordHistoryItem)))
    }.onReadOnlyCommand[GetLastPasswordChanged, Option[PasswordHistoryItem]]{
      case ((glpc: GetLastPasswordChanged), ctx, state) => {
        state match {
          case None => ctx.reply(None)
          case Some(passwordEntityState) => ctx.reply(Some(passwordEntityState.passwordHistory.last))
        }
      }
    }.onEvent {
      case (pcse: PasswordChangeSuccessulEvent, state) =>

        state match {
          case None => Some(PasswordEntityState(pcse.passwordHistoryItem.userIdEmail, List(pcse.passwordHistoryItem), LocalDateTime.now.toString ))
          case Some(pEntity) => Some(PasswordEntityState(pEntity.emailId, pEntity.passwordHistory.:+(pcse.passwordHistoryItem), LocalDateTime.now.toString))
        }
        // if this happens, just do nothing and return state
      case (_, state) => state
    }.orElse(getLastPasswordCommand)
  }


}



/**
  * The current state held by the persistent entity.
  */
case class PasswordcopState(message: String,  timestamp: String)

case class PasswordEntityState(emailId: String, passwordHistory: List[PasswordHistoryItem], timestamp: String)

object PasswordEntityState{
  implicit val format: Format[PasswordEntityState] = Json.format
}

object PasswordcopState {
  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the entity gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[PasswordcopState] = Json.format
}

/**
  * This interface defines all the events that the PasswordcopEntity supports.
  */
sealed trait PasswordcopEvent extends AggregateEvent[PasswordcopEvent] {
  def aggregateTag = PasswordcopEvent.Tag
}

object PasswordcopEvent {
  val Tag = AggregateEventTag[PasswordcopEvent]
}


/**
  * Indicateds a password successfully changed for user given by emailId.
  */
case class PasswordChangeSuccessulEvent(passwordHistoryItem: PasswordHistoryItem) extends PasswordcopEvent

object PasswordChangeSuccessulEvent{
  implicit val format: Format[PasswordChangeSuccessulEvent] = Json.format
}


/**
  * An event that represents a change in greeting message.
  */
case class GreetingMessageChanged(message: String) extends PasswordcopEvent

object GreetingMessageChanged {

  /**
    * Format for the greeting message changed event.
    *
    * Events get stored and loaded from the database, hence a JSON format
    * needs to be declared so that they can be serialized and deserialized.
    */
  implicit val format: Format[GreetingMessageChanged] = Json.format
}

/**
  * This interface defines all the commands that the HelloWorld entity supports.
  */
sealed trait PasswordcopCommand[R] extends ReplyType[R]

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[Done]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class UseGreetingMessage(message: String) extends PasswordcopCommand[Done]

object UseGreetingMessage {

  /**
    * Format for the use greeting message command.
    *
    * Persistent entities get sharded across the cluster. This means commands
    * may be sent over the network to the node where the entity lives if the
    * entity is not on the same node that the command was issued from. To do
    * that, a JSON format needs to be declared so the command can be serialized
    * and deserialized.
    */
  implicit val format: Format[UseGreetingMessage] = Json.format
}

/**
  * A command to say hello to someone using the current greeting message.
  *
  * The reply type is String, and will contain the message to say to that
  * person.
  */
case class Hello(name: String) extends PasswordcopCommand[String]

object Hello {

  /**
    * Format for the hello command.
    *
    * Persistent entities get sharded across the cluster. This means commands
    * may be sent over the network to the node where the entity lives if the
    * entity is not on the same node that the command was issued from. To do
    * that, a JSON format needs to be declared so the command can be serialized
    * and deserialized.
    */
  implicit val format: Format[Hello] = Json.format
}

case class GetLastPasswordChanged(emailId: String) extends PasswordcopCommand[Option[PasswordHistoryItem]]

object GetLastPasswordChanged{
  implicit val format: Format[GetLastPasswordChanged] = Json.format
}

case class ChangePasswordRequest(emailId: String, encryptedPassword: String) extends PasswordcopCommand[Option[PasswordHistoryItem]]

object ChangePasswordRequest{
  implicit val format: Format[ChangePasswordRequest] = Json.format
}

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object PasswordcopSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[UseGreetingMessage],
    JsonSerializer[GetLastPasswordChanged],
    JsonSerializer[GreetingMessageChanged],
    JsonSerializer[ChangePasswordRequest],
    JsonSerializer[PasswordEntityState]
  )
}
