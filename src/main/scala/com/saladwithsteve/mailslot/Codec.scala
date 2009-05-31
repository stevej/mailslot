package com.saladwithsteve.mailslot.smtp

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{IdleStatus, IoSession}
import org.apache.mina.filter.codec._
import net.lag.extensions._
import net.lag.logging.Logger
import net.lag.naggati.{COMPLETE, Decoder, End, NEED_DATA, ProtocolError, Step}
import net.lag.naggati.Steps._

case class Request(line: List[String], data: Option[Array[Byte]]) {
  override def toString = {
    "<Request: " + line.length + ": " + line.mkString("[", " ", "]") + (data match {
      case None => ""
      case Some(x) => ": " + x.hexlify
    }) + ">"
  }

  /**
   * This is an expensive operation if data is a large array.
   */
  override def equals(a: Any): Boolean = {
    a match {
      case a: Request => {
        if (a.line == this.line) {
          (a.data, this.data) match {
            // We have to hexlify because Arrays don't equals properly
            case (Some(x), Some(y)) => (x.hexlify == y.hexlify)
            case (None, None) => true
            case _ => false
          }
        } else {
          false
        }
      }
      case _ => false
    }
  }
}

case class Response(data: IoBuffer)


object Codec {
  private val log = Logger.get
  val WRITE_COMMANDS = List("DATA", "MAIL", "RCPT")
  val READ_COMMANDS = List("HELO", "HELP", "VRFY", "NOOP", "QUIT", "RSET", "STATS")

  val ALL_COMMANDS = WRITE_COMMANDS ++ READ_COMMANDS

  val encoder = new ProtocolEncoder {
    def encode(session: IoSession, message: AnyRef, out: ProtocolEncoderOutput) = {
      val buffer = message.asInstanceOf[Response].data
      //KestrelStats.bytesWritten.incr(buffer.remaining)
      out.write(buffer)
    }

    def dispose(session: IoSession): Unit = {
      // nothing.
    }
  }

  // Reads from DATA to the first \r\n.\r\n.
  def dataDecoder(line: String): Step = readDelimiterBuffer("\r\n.\r\n".getBytes()) { buf =>
    // FIXME: naggati is eating the \r\n at the end of the line.
    state.out.write(Request(List("DATABODY"), Some(line.getBytes ++ "\r\n".getBytes ++ buf)))
    End
  }

  /**
   * For a no-parameter command, takes the command line and writes it into the state stream.
   */
  def noParams(segments: Array[String]) = {
    state.out.write(Request(segments.toList, None))
    End
  }

  val decoder = new Decoder(readLine(true, "ISO-8859-1") { line =>
    //println(line)
    val segments = line.split(" ")
    // Determine if we are seeing a MIME Header signifying an email body.
    if (segments(0).endsWith(":")) { // then we have a MIME header
      // switch to using the dataDecoder and return.
      if (dataDecoder(line)() != COMPLETE) {
        throw new ProtocolError("501 Syntax: DATA requires body ended with /\r/\n./\r/\n")
      }
      End
    } else {
      segments(0) = segments(0).toUpperCase
      val command = segments(0)
      log.debug("attempting to process command %s", command)

      if (!ALL_COMMANDS.contains(command)) {
        log.debug("rejecting command %s", command)
        throw new ProtocolError("502 Error: command not allowed: %s".format(command))
      }

      command match {
        case "HELO" => {
          if (segments.length != 2) {
            throw new ProtocolError("501 Syntax: HELO hostname")
          } else {
            state.out.write(Request(segments.toList, None))
            End
          }
        }

        /**
         * MAIL FROM: email_address
         */
        case "MAIL" if segments.length > 1 => segments(1).toLowerCase match {
          case "from:" if segments.length == 3 => state.out.write(Request(segments.toList, None)); End
          case x if x.startsWith("from:") && x.length > 5 => {
            val pieces = x.split(":") // separate the email address from FROM:
            // FIXME: At some point, for my sanity, this needs to be a proper grammar.
            state.out.write(Request((List("MAIL", "FROM:", pieces(1)) ::: segments.subArray(2, segments.length).toList), None))
            End
          }
          case x => throw new ProtocolError("501 Syntax: MAIL FROM: <email_address>")
        }

        /**
         * RCPT TO: email_address
         */
        case "RCPT" if segments.length > 1 => segments(1).toLowerCase match {
          case "to:" if segments.length == 3 => state.out.write(Request(segments.toList, None)); End
          case x if x.startsWith("to:") && x.length > 5 => {
            val pieces = x.split(":") // separate the email address from FROM:
            // FIXME: At some point, for my sanity, this needs to be a proper grammar.
            state.out.write(Request((List("RCPT", "TO:", pieces(1)) ::: segments.subArray(2, segments.length).toList), None))
            End
          }
          case x => throw new ProtocolError("501 Syntax: RCPT TO: <email_address>")
        }

        case "DATA" => {
          state.out.write(Request(List("DATA"), None))
          End
        }

        case "VRFY" => noParams(segments)
        case "NOOP" if segments.length == 1 => noParams(segments)
        case "HELP" => noParams(segments)
        case "QUIT" => noParams(segments)
        case "RSET" => noParams(segments)
        case "STATS" => noParams(segments)

        case _ => {
          throw new ProtocolError("502 Error: command not implemented: %s".format(command))
        }
      }
    }
  })
}
