package com.mattmartin.example.impl

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}
import com.mattmartin.example.api.{ChangePasswordMessage, ChangePasswordResponseMessage, PasswordCopService}

class PasswordCopServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra(true)
  ) { ctx =>
    new PasswordcopApplication(ctx) with LocalServiceLocator
  }

  val client = server.serviceClient.implement[PasswordCopService]

  override protected def afterAll() = server.stop()

  "password-cop service" should {

    /*"say hello" in {
      client.hello("Alice").invoke().map { answer =>
        answer should ===("Hello, Alice!")
      }
    }

    "allow responding with a custom message" in {
      for {
        _ <- client.useGreeting("Bob").invoke(GreetingMessage("Hi"))
        answer <- client.hello("Bob").invoke()
      } yield {
        answer should ===("Hi, Bob!")
      }
    }*/

    "invalid password check" in {
      client.changePassword.invoke( ChangePasswordMessage("bob@bobsemail.com", "pass")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob@bobsemail.com", false, Some("Unable to change password")) )
      }
    }

    "valid password check" in {
      client.changePassword.invoke( ChangePasswordMessage("bob@bobsemail.com", "pAssWordIs!@Strong123")).map { answer =>
        answer should === ( ChangePasswordResponseMessage("bob@bobsemail.com", true, None) )
      }
    }

  }
}
