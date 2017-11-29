package com.mattmartin.example.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.mattmartin.example.api.PasswordCopService
import com.softwaremill.macwire._

class PasswordcopLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new PasswordcopApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new PasswordcopApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[PasswordCopService])
}

abstract class PasswordcopApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[PasswordCopService](wire[PasswordcopServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = PasswordcopSerializerRegistry

  // Register the password-cop persistent entity
  persistentEntityRegistry.register(wire[PasswordcopEntity])
}
