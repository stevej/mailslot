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
  val READ_COMMANDS = List("HELO", "HELP", "VRFY", "NOOP", "QUIT", "RSET")

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
  def dataDecoder(): Step = readDelimiterBuffer("\r\n.\r\n".getBytes()) { buf =>
    state.out.write(Request(List("DATA"), Some(buf)))
    End
  }

  val decoder = new Decoder(readLine(true, "ISO-8859-1") { line =>
    //println(line)
    val segments = line.split(" ")
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
      case "MAIL" if segments.length > 1 => segments(1).toUpperCase match {
        case "FROM:" if segments.length == 3 => state.out.write(Request(segments.toList, None)); End
        case _ => throw new ProtocolError("501 Syntax: MAIL FROM: <email_address>")
      }

      /**
       * RCPT TO: email_address
       */
      case "RCPT" if segments.length > 1 => segments(1).toUpperCase match {
        case "TO:"   if segments.length == 3 => state.out.write(Request(segments.toList, None)); End
        case _ => throw new ProtocolError("501 Syntax: RCPT TO: <email_address>")
      }

      case "DATA" => {
        // FIXME: we need to write out 354 at this point and THEN read in the response.
        if (dataDecoder()() != COMPLETE) {
          throw new ProtocolError("501 Syntax: DATA requires body ended with /\r/\n./\r/\n")
        }
        End
      }

      case "VRFY" => {
        // Ignore any parameters.
        state.out.write(Request(segments.toList, None))
        End
      }

      case "HELP" => {
        // Ignore any parameters
        state.out.write(Request(segments.toList, None))
        End
      }

      case "QUIT" => {
        state.out.write(Request(segments.toList, None))
        End
      }

      case "NOOP" if segments.length == 1 => {
        state.out.write(Request(segments.toList, None))
        End
      }

      case "RSET" => {
        state.out.write(Request(segments.toList, None))
        End
      }

      case _ => {
        throw new ProtocolError("502 Error: command not implemented: %s".format(command))
      }
    }
  })
}
