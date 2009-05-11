package com.saladwithsteve.mailslot

import net.lag.configgy.{Config, Configgy, RuntimeEnvironment}
import org.apache.mina.core.session.{IdleStatus, IoSession}
import scala.actors.Actor
import scala.actors.Actor._


class SmtpHandler(val session: IoSession, val config: Config) extends Actor {
  def act = {
    loop {
      react {
        case a => println(a)
      }
    }
  }
}
