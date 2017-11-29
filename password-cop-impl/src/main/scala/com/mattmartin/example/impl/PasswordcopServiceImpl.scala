package com.mattmartin.example.impl

import java.time.Instant

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.mattmartin.example.api.{ChangePasswordResponseMessage, PasswordChanged, PasswordCopService}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Implementation of the PasswordcopService.
  */
class PasswordcopServiceImpl(persistentEntityRegistry: PersistentEntityRegistry) (implicit ec: ExecutionContext) extends PasswordCopService {

  override def hello(id: String) = ServiceCall { _ =>
    // Look up the password-cop entity for the given ID.
    val ref = persistentEntityRegistry.refFor[PasswordcopEntity](id)

    // Ask the entity the Hello command.
    ref.ask(Hello(id))
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the password-cop entity for the given ID.
    val ref = persistentEntityRegistry.refFor[PasswordcopEntity](id)

    // Tell the entity to use the greeting message specified.
    ref.ask(UseGreetingMessage(request.message))
  }

  override def changePassword = ServiceCall { request =>
    val ref = persistentEntityRegistry.refFor[PasswordcopEntity](request.userId)
    val req = ChangePasswordRequest(request.userId, request.password)
    ref.ask(req).map{
      case Some(passwordHistoryItem) => ChangePasswordResponseMessage(passwordHistoryItem.userIdEmail, true)
      case _ => ChangePasswordResponseMessage(request.userId, false, Some("Unable to change password"))
    }

  }

  override def checkPasswordExpired(userIdEmail: String) = ServiceCall { _ =>
    val ref = persistentEntityRegistry.refFor[PasswordcopEntity](userIdEmail)
    val now = Instant.now.toEpochMilli

    ref.ask(GetLastPasswordChanged(userIdEmail)).map {
      case None => {
        Console.println("No password")
        true
      }
      case Some(pi) => {
        val expires = pi.expiresTimeStamp.get
        expires < now
      }
    }
  }

  override def passwordChangedTopic(): Topic[PasswordChanged] =
    TopicProducer.singleStreamWithOffset {
      fromOffset =>
        persistentEntityRegistry.eventStream(PasswordcopEvent.Tag, fromOffset)
          .map(ev => (convertEvent(ev), ev.offset))
    }

  private def convertEvent(passwordEvent: EventStreamElement[PasswordcopEvent]): PasswordChanged = {
    passwordEvent.event match {
      case passChange: PasswordChangeSuccessulEvent =>
        PasswordChanged(passChange.passwordHistoryItem.userIdEmail, passChange.passwordHistoryItem.expiresTimeStamp)
    }
  }


}
