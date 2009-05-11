package com.saladwithsteve.mailslot.smtp

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{IdleStatus, IoSession}
import org.apache.mina.filter.codec._
import net.lag.extensions._
import net.lag.naggati.{Decoder, End, ProtocolError, Step}
import net.lag.naggati.Steps._

object Codec {
  val encoder = new ProtocolEncoder {
    def encode(session: IoSession, message: AnyRef, out: ProtocolEncoderOutput) = {}
    def dispose(session: IoSession): Unit = {
      // nothing.
    }
  }

  val decoder = new Decoder(readLine(true, "ISO-8859-1") { line =>
    println(line)
    End
  })
}
