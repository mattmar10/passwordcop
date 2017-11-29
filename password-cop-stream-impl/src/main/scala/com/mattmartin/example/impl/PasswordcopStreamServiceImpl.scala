package com.mattmartin.example.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.mattmartin.example.api.{PasswordCopService, PasswordcopStreamService}

import scala.concurrent.Future

/**
  * Implementation of the PasswordcopStreamService.
  */
class PasswordcopStreamServiceImpl(passwordcopService: PasswordCopService) extends PasswordcopStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(passwordcopService.hello(_).invoke()))
  }
}
