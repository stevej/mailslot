/** Copyright 2009 Steve Jenson, licensed under the included Apache 2.0 License. */
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

/**
 * SmtpHandler receives groups of commands and optionally data from the Codec supplied to Mina.
 *
 * @author Steve Jenson &lt;stevej@twitter.com&gt;
 */
class SmtpHandler(val session: IoSession, val config: Config, val router: MailRouter) extends Actor {
  private val log = Logger.get

  val serverName = config.getString("server-name", "localhost")
  val passkey = config.getString("passkey", null)
  if (passkey == null) {
    log.error("passkey cannot be left empty")
    throw new IllegalStateException("passkey left empty in config file")
  }
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
            case e: ProtocolError => writeResponse(e.getMessage + "\n")
            case _: IOException =>
              // FIXME: create proper session IDs for message tracking.
              log.debug("IO Exception on session %d: %s", 0, cause.getMessage)
            case _ =>
              // FIXME: create proper session IDs for message tracking.
              log.error(cause, "Exception caught on session %d: %s", 0, cause.getMessage)
              writeResponse("502 ERROR\n")
          }
          MailStats.sessionErrors.incr
          session.close
        }

        case MinaMessage.SessionClosed =>
          log.debug("End of session %d", 0)
          // abortAnyTransaction
          MailStats.closedSessions.incr
          exit()

        case MinaMessage.SessionIdle(status) =>
          log.debug("Idle timeout on session %s", session)
          session.close

        case MinaMessage.SessionOpened =>
          log.debug("Session opened %d", 0)
          MailStats.totalSessions.incr
          writeResponse("220 %s SMTP\n".format(serverName))
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
      case "HELO" => helo(req)
      case "MAIL" => mail(req)
      case "RCPT" => rcpt(req)
      case "DATA" => data(req)
      // not an actual SMTP command but how we encode that we've seen the body after a DATA command.
      case "DATABODY" => databody(req)
      case "VRFY" => vrfy(req)
      case "HELP" => help(req)
      case "QUIT" => quit(req)
      case "NOOP" => noop(req)
      case "RSET" => rset(req)
      case "STATS" => stats(req)
    }
  }

  def helo(req: smtp.Request) {
    writeResponse("250 %s\n".format(serverName))
  }

  def mail(req: smtp.Request) {
    // put email address in FROM part of the envelope
    writeResponse("250 Ok\n")
  }

  def rcpt(req: smtp.Request) {
    // FIXME: put email address in TO part of the envelope
    writeResponse("250 Ok\n")
  }

  def data(req: smtp.Request) {
    log.debug("handling data in Thread %s", Thread.currentThread)
    writeResponse("354 Ok\n")
  }

  /**
   * Handles the email body supplied after the DATA command. Note that there is no explicit state switching here,
   * there may or may not be MAIL FROM and RCPT TO sent before this. We aim to fix this.
   */
  def databody(req: smtp.Request) {
    log.debug("handling databody in Thread %s", Thread.currentThread)
    // FIXME: generate a useful txn id for error handling.
    val txnId = 0

    // Once we've read the email, it's time to parse this with JavaMail and pass it along to the registered handler.
    req.data match {
      case Some(bytes) => router(EmailBuilder(bytes))
      case None => log.warning("cannot route email with no data")
    }
    writeResponse("250 Safely handled. txn %s\n".format(txnId))
  }

  def vrfy(req: smtp.Request) {
    writeResponse("252 send some mail, i'll try my best\n")
  }

  def help(req: smtp.Request) {
    writeResponse("214-SMTP servers help those who help themselves.\n214 Go read http://cr.yp.to/smtp.html.\n")
  }

  def quit(req: smtp.Request) {
    writeResponse("221 %s saying goodbye\n".format(serverName))
    session.close()
  }

  def noop(req: smtp.Request) {
    writeResponse("250 Ok\n")
  }

  def rset(req: smtp.Request) {
    // FIXME: actually reset the current envelope
    writeResponse("250 Ok\n")
  }

  def stats(req: smtp.Request) {
    // FIXME: add a secret key to protect against snoopers.
    if (req.line.length < 2 || req.line(1) != passkey) {
      log.debug("password expected: %s, received: %s", passkey, req.line(1))
      writeResponse("502 Password Incorrect\n")
    }
    var report = new mutable.ArrayBuffer[(String, Long)]
    report += (("bytesWritten", MailStats.bytesWritten()))
    report += (("totalSessions", MailStats.totalSessions()))
    report += (("closedSessions", MailStats.closedSessions()))
    report += (("sessionErrors", MailStats.sessionErrors()))

    val summary = {
      for ((key, value) <- report) yield "220 %s %s".format(key, value)
    }.mkString("", "\n", "\n")
    writeResponse(summary)
  }
}
