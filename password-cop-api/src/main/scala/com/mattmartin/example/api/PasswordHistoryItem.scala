package com.mattmartin.example.api

import java.time.Instant

import play.api.libs.json.{Format, Json}

/**
  * Represents a user/password (encrypted) pair, along with optional created and expiration times.
  *
  * @param userIdEmail
  * @param encryptedPassword
  * @param createdTimestamp
  * @param expiresTimeStamp
  */
case class PasswordHistoryItem(userIdEmail: String,
                               encryptedPassword: String,
                               createdTimestamp: Option[Long] = Some(Instant.now().toEpochMilli),
                               expiresTimeStamp: Option[Long] ) //not sure how to handle this


object PasswordHistoryItem{
  implicit val format: Format[PasswordHistoryItem] = Json.format
}