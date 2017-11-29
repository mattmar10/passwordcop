package com.mattmartin.example.impl

import com.mattmartin.example.api.PasswordHistoryItem

object PasswordValidator {
  lazy val PATTERN = """^(?=.{8,32}$)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).*"""

  lazy val PASSWORD_EXPIRATION_MS : Long = 15552000000l

  def validatePasswordForUser(userIdEmail: String,
                              newPassword: String,
                              passwordHistory: Option[Seq[PasswordHistoryItem]]): Boolean = {

    //first validate pattern
    val valid = newPassword.matches(PATTERN)

    if(!valid){
      Console.println(s"New password invalid for [$userIdEmail].Password does not meet minium requirements")
    }

    //if there is no password history, ensure it meets requirements. If there is a history, cannot re-use passwords.
    passwordHistory match {
      case None => valid
      case Some(passwords) => valid && passwords.filter(p => p.encryptedPassword == newPassword).isEmpty
    }
  }
}
