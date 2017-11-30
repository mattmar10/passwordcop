package com.mattmartin.example.impl

import java.time.{Instant, LocalDateTime}

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import com.mattmartin.example.api.PasswordHistoryItem
import play.api.libs.json.{Format, Json}
import com.github.t3hnar.bcrypt._
import com.mattmartin.example.impl

import scala.collection.immutable.Seq

/**
  * This is an event sourced entity. It has a state, [[PasswordEntityState]], which
  * stores what the greeting should be (eg, "Hello").
  *
  * Event sourced entities are interacted with by sending them commands. This
  * entity supports two commands, a [[ChangePasswordRequest]] command, which is
  * used to change the password, and a [[GetLastPasswordChanged]] command, which is a read
  * only command which returns an optional with the last password change.
  *
  * Commands get translated to events, and it's the events that get persisted by
  * the entity. Each event will have an event handler registered for it, and an
  * event handler simply applies an event to the current state. This will be done
  * when the event is first created, and it will also be done when the entity is
  * loaded from the database - each event will be replayed to recreate the state
  * of the entity.
  *
  * This entity defines one event, the [[PasswordChangeSuccessulEvent]] event,
  * which is emitted on a successful password change -
  * when a [[ChangePasswordRequest]] command is received and the password meets requirements.
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
        val passwordHistoryMaybe = PasswordValidator.validatePasswordForUser(cpr.emailId, cpr.password, None)

        passwordHistoryMaybe match{
          case None => {
            ctx.invalidCommand("Unable to create password. Validation failed.")
            ctx.done
          }
          case Some(phi) => ctx.thenPersist(PasswordChangeSuccessulEvent(phi))(_ => ctx.reply(passwordHistoryMaybe))
        }
    }.onReadOnlyCommand[GetLastPasswordChanged, Option[PasswordHistoryItem]]{
      case ((glpc: GetLastPasswordChanged), ctx, state) => ctx.reply(None)
    }.onEvent {
      case (pcse: PasswordChangeSuccessulEvent, state) =>
        Some(PasswordEntityState(pcse.passwordHistoryItem.userIdEmail,
          List(pcse.passwordHistoryItem),
          LocalDateTime.now.toString ))
      // if this happens, just do nothing and return state
      case (_, state) => state
    }.orElse(getLastPasswordCommand)
  }

  private val created = {
    Actions().onCommand[ChangePasswordRequest, Option[PasswordHistoryItem]] {
      case ((cpr: ChangePasswordRequest), ctx, state) =>
        val passwordHistoryMaybe =
          PasswordValidator.validatePasswordForUser(cpr.emailId, cpr.password, Some(state.get.passwordHistory))

        passwordHistoryMaybe match{
          case None => {
            ctx.invalidCommand("Unable to create password. Validation failed.")
            ctx.done
          }
          case Some(phi) => ctx.thenPersist(PasswordChangeSuccessulEvent(phi))(_ => ctx.reply(passwordHistoryMaybe))
        }
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

case class PasswordEntityState(emailId: String, passwordHistory: List[PasswordHistoryItem], timestamp: String)

object PasswordEntityState{

  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the entity gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[PasswordEntityState] = Json.format
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

  /**
    * Format for the password change successful event.
    *
    * Events get stored and loaded from the database, hence a JSON format
    * needs to be declared so that they can be serialized and deserialized.
    */
  implicit val format: Format[PasswordChangeSuccessulEvent] = Json.format
}

/**
  * This interface defines all the commands that the HelloWorld entity supports.
  */
sealed trait PasswordcopCommand[R] extends ReplyType[R]

/**
  * A command to get the last password history item.
  *
  * The reply type is [[Option[PasswordHistoryItem]], and will an option of the last password history item
  * for the given emailId.
  */

case class GetLastPasswordChanged(emailId: String) extends PasswordcopCommand[Option[PasswordHistoryItem]]

object GetLastPasswordChanged{

  /**
    * Format for the GetLastPasswordChanged command.
    *
    * Persistent entities get sharded across the cluster. This means commands
    * may be sent over the network to the node where the entity lives if the
    * entity is not on the same node that the command was issued from. To do
    * that, a JSON format needs to be declared so the command can be serialized
    * and deserialized.
    */
  implicit val format: Format[GetLastPasswordChanged] = Json.format
}

case class ChangePasswordRequest(emailId: String, password: String)
  extends PasswordcopCommand[Option[PasswordHistoryItem]]

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
    JsonSerializer[GetLastPasswordChanged],
    JsonSerializer[ChangePasswordRequest],
    JsonSerializer[PasswordEntityState]
  )
}
