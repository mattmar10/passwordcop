package com.mattmartin.example.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.mattmartin.example.api.{PasswordCopService, PasswordcopStreamService}
import com.softwaremill.macwire._

class PasswordcopStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new PasswordcopStreamApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new PasswordcopStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[PasswordcopStreamService])
}

abstract class PasswordcopStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[PasswordcopStreamService](wire[PasswordcopStreamServiceImpl])

  // Bind the PasswordcopService client
  lazy val passwordcopService = serviceClient.implement[PasswordCopService]
}
