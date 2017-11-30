package com.mattmartin.example.impl

import com.lightbend.lagom.internal.scaladsl.persistence.protobuf.msg.PersistenceMessages.Exception
import com.lightbend.lagom.scaladsl.api.transport.TransportException
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.InvalidCommandException
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}
import com.mattmartin.example.api.{ChangePasswordMessage, ChangePasswordResponseMessage, PasswordCopService}
import org.scalatest.concurrent.ScalaFutures

class PasswordCopServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra(true)
  ) { ctx =>
    new PasswordcopApplication(ctx) with LocalServiceLocator
  }

  val client = server.serviceClient.implement[PasswordCopService]

  override protected def afterAll() = server.stop()

  "password-cop service" should{

      "invalid password check should throw InvalidCommandException" in {

        recoverToSucceededIf[TransportException]{
          client.changePassword.invoke(ChangePasswordMessage("bob@bobsemail.com", "toosimple"))
        }
    }


    "valid password check not existing" in {
      client.changePassword.invoke( ChangePasswordMessage("bob@bobsemail.com", "pAssWordIs!@Strong123")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob@bobsemail.com", true, None) )
      }
    }

    "valid password check for existing" in {
      client.changePassword.invoke( ChangePasswordMessage("bob2@bobsemail.com", "pAssWordIs!@Strong123")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob2@bobsemail.com", true, None) )
      }

      client.changePassword.invoke( ChangePasswordMessage("bob2@bobsemail.com", "pAssWordIs!@Strong1234")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob2@bobsemail.com", true, None) )
      }
    }

    "invalid password check, reusing password for existing" in {
      client.changePassword.invoke( ChangePasswordMessage("bob3@bobsemail.com", "pAssWordIs!@Strong123")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob3@bobsemail.com", true, None) )
      }

      recoverToSucceededIf[TransportException] {
        client.changePassword.invoke(ChangePasswordMessage("bob3@bobsemail.com", "pAssWordIs!@Strong123")).map { answer =>
          answer should ===(ChangePasswordResponseMessage("bob3@bobsemail.com", true, None))
        }
      }
    }

  }
}
