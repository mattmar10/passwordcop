package com.mattmartin.example.impl

import com.mattmartin.example.api.PasswordHistoryItem
import com.github.t3hnar.bcrypt._
import org.slf4j.LoggerFactory


object PasswordValidator {
  private val log = LoggerFactory.getLogger(classOf[PasswordcopServiceImpl])

  lazy val PATTERN = """^(?=.{8,32}$)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).*"""
  lazy val PASSWORD_EXPIRATION_MS : Long = 15552000000l


  /**
    * Validates a password by first checking against requirements, then ensuring that the password has not beem used
    * previously.
    *
    * @param userIdEmail
    * @param newPassword
    * @param passwordHistory
    *
    * @return [[Option[String]] None if password is not valid. If valid, an option contain encrypted password.
    */
  def validatePasswordForUser(userIdEmail: String,
                              newPassword: String,
                              passwordHistory: Option[Seq[PasswordHistoryItem]]): Option[String] = {

    //first validate pattern
    val valid = newPassword.matches(PATTERN)

    valid match {
      case false => {
        Console.println(s"New password invalid for [$userIdEmail].Password does not meet minium requirements")
        None
      }
      case true => {
        val encrypted = encryptPassword(newPassword)

        //if there is no password history, ensure it meets requirements. If there is a history, cannot re-use passwords.
        passwordHistory match {
          case None => Some(encrypted)
          case Some(passwords) => {
            val usedPreviously = passwords.exists(p => p.encryptedPassword == encrypted)

            usedPreviously match {
              case false => Some(encrypted)
              case true =>{
                log.info(s"[$userIdEmail] attempted to use a previously used password")
                None
              }
            }
          }
        }
      }
    }
  }

  def encryptPassword(password: String): String = {
    password.bcrypt
  }
}
