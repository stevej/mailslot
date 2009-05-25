package com.saladwithsteve.mailslot

import net.lag.configgy.{Config, Configgy, RuntimeEnvironment}
import net.lag.logging.Logger
import net.lag.naggati.{IoHandlerActorAdapter, MinaMessage, ProtocolError}
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{IdleStatus, IoSession}
import java.io.IOException
import scala.actors.Actor
import scala.actors.Actor._
import scala.collection.mutable


class SmtpHandler(val session: IoSession, val config: Config, val router: MailRouter) extends Actor {
  private val log = Logger.get
  val serverName = config.getString("server-name", "localhost")

  session.getConfig.setReadBufferSize(config.getInt("mina-read-buffer-size", 2048))
  IoHandlerActorAdapter.filter(session) -= classOf[MinaMessage.MessageSent]

  val idleTimeout = config.getInt("idle-timeout", 2500)
  session.getConfig.setIdleTime(IdleStatus.BOTH_IDLE, idleTimeout)

  start
  def act = {
    loop {
      react {
        case MinaMessage.MessageReceived(msg) =>
          handle(msg.asInstanceOf[smtp.Request])

        case MinaMessage.ExceptionCaught(cause) => {
          cause.getCause match {
            case e: ProtocolError => writeResponse(e.getMessage + "\r\n")
            case _: IOException =>
              // FIXME: create proper session IDs for message tracking.
              log.debug("IO Exception on session %d: %s", 0, cause.getMessage)
            case _ =>
              // FIXME: create proper session IDs for message tracking.
              log.error(cause, "Exception caught on session %d: %s", 0, cause.getMessage)
              writeResponse("502 ERROR\r\n")
          }
          MailStats.sessionErrors.incr
          session.close
        }

        case MinaMessage.SessionClosed =>
          log.debug("End of session %d", 0)
          //abortAnyTransaction
          MailStats.closedSessions.incr
          exit()

        case MinaMessage.SessionIdle(status) =>
          log.debug("Idle timeout on session %s", session)
          session.close

        case MinaMessage.SessionOpened =>
          log.debug("Session opened %d", 0)
          MailStats.totalSessions.incr
          writeResponse("220 %s SMTP\r\n".format(serverName))
      }
    }
  }

  private def writeResponse(out: String) = {
    val bytes = out.getBytes
    session.write(new smtp.Response(IoBuffer.wrap(bytes)))
  }

  private def writeResponse(out: String, data: Array[Byte]) = {
    val bytes = out.getBytes
    val buffer = IoBuffer.allocate(bytes.length + data.length + 7)
    buffer.put(bytes)
    buffer.put(data)
    buffer.flip
    MailStats.bytesWritten.incr(buffer.capacity)
    session.write(new smtp.Response(buffer))
  }

  private def handle(req: smtp.Request) = {
    req.line(0) match {
      case "MAIL" => mail(req)
      case "RCPT" => rcpt(req)
      case "DATA" => data(req)
      case "VRFY" => vrfy(req)
      case "HELP" => help(req)
      case "QUIT" => quit(req)
      case "NOOP" => noop(req)
      case "RSET" => rset(req)
      case "STATS" => stats(req)
    }
  }

  def mail(req: smtp.Request) {
    // put email address in FROM part of the envelope
    writeResponse("250 Ok\r\n")
  }

  def rcpt(req: smtp.Request) {
    // FIXME: put email address in TO part of the envelope
    writeResponse("250 Ok\r\n")
  }

  def data(req: smtp.Request) {
    val txnId = 0
    writeResponse("354 Ok\r\n")
    // FIXME: the client actually sends the body _after_ we send the 354 header so this code won't work.
    writeResponse("250 Safely handled. %s".format(txnId))

    // Once we've read the email, it's time to parse this with JavaMail and pass it along to the registered handler.
    req.data match {
      case Some(bytes) => router(EmailBuilder(bytes))
      case None => log.warning("cannot route email with no data")
    }
  }

  def vrfy(req: smtp.Request) {
    writeResponse("252 send some mail, i'll try my best\r\n")
  }

  def help(req: smtp.Request) {
    writeResponse("214-SMTP servers help those who help themselves.\r\n214 Go read http://cr.yp.to/smtp.html.\r\n")
  }

  def quit(req: smtp.Request) {
    writeResponse("221 %s saying goodbye\r\n".format(serverName))
    session.close()
  }

  def noop(req: smtp.Request) {
    writeResponse("250 Ok\r\n")
  }

  def rset(req: smtp.Request) {
    // FIXME: actually reset the current envelope
    writeResponse("250 Ok\r\n")
  }
  def stats(req: smtp.Request) {
    var report = new mutable.ArrayBuffer[(String, String)]
    report += (("bytesWritten", MailStats.bytesWritten.toString))
    report += (("totalSessions", MailStats.totalSessions.toString))
    report += (("closedSessions", MailStats.closedSessions.toString))
    report += (("sessionErrors", MailStats.sessionErrors.toString))

val summary = {
      for ((key, value) <- report) yield "220-%s %s".format(key, value)
    }.mkString("", "\r\n", "\r\n")
    writeResponse(summary)
  }
}
