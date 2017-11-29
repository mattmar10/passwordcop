package com.mattmartin.example.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}

/**
  * The password-cop stream interface.
  *
  * This describes everything that Lagom needs to know about how to serve and
  * consume the PasswordcopStream service.
  */
trait PasswordcopStreamService extends Service {

  def stream: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

  override final def descriptor = {
    import Service._

    named("password-cop-stream")
      .withCalls(
        namedCall("stream", stream)
      ).withAutoAcl(true)
  }
}

