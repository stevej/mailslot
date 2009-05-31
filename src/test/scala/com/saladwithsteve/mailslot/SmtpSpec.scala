/** Copyright 2009 Steve Jenson, released under Apache 2.0 License */
package com.saladwithsteve.mailslot.smtp

import net.lag.extensions._
import net.lag.naggati._
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.filterchain.IoFilter
import org.apache.mina.core.session.{DummySession, IoSession}
import org.apache.mina.filter.codec._
import org.specs._

object SmtpCodecSpec extends Specification {

  private var fakeSession = new DummySession

  private val fakeDecoderOutput = new ProtocolDecoderOutput {
    override def flush(nextFilter: IoFilter.NextFilter, s: IoSession) = {}
    override def write(obj: AnyRef) = {
      written = obj :: written
    }
  }

  private var written: List[AnyRef] = Nil

  private var decoder = smtp.Codec.decoder

  "smtp" should {
    doBefore {
      fakeSession = new DummySession
      decoder = smtp.Codec.decoder
      written = Nil
    }

    "HELO" >> {
      "throw an exception with a bare HELO" >> {
        decoder.decode(fakeSession, IoBuffer.wrap("HELO\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
      }

      "accept a two-argument HELO" >> {
        decoder.decode(fakeSession, IoBuffer.wrap("HELO localhost\n".getBytes), fakeDecoderOutput)
        written mustEqual List(Request(List("HELO", "localhost"), None))
      }
    }

    "MAIL" >> {
      "Bare MAIL causes an error" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("MAIL\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
      }

      "MAIL FROM" >> {
        "MAIL FROM errors without a email address" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("MAIL FROM:\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
        }

        "MAIL FROM works with a close email address" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("MAIL FROM:stevej@pobox.com\n".getBytes), fakeDecoderOutput)
          written mustEqual List(Request(List("MAIL", "FROM:", "stevej@pobox.com"), None))
        }

        "MAIL FROM works with an email address" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("MAIL FROM: stevej@pobox.com\n".getBytes), fakeDecoderOutput)
          written mustEqual List(Request(List("MAIL", "FROM:", "stevej@pobox.com"), None))
        }

      }
    }

    "RCPT" >> {
      "RCPT TO:" >> {
        "RCPT TO: errors without an email address" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("RCPT TO:\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
        }

        "RCPT TO: works with an email address" >> {
          decoder.decode(fakeSession, IoBuffer.wrap("RCPT TO: stevej@pobox.com\n".getBytes), fakeDecoderOutput)
          written mustEqual List(Request(List("RCPT", "TO:", "stevej@pobox.com"), None))
        }
      }
    }

    "DATA" >> {
       "DATA requires a body" >> {
         decoder.decode(fakeSession, IoBuffer.wrap("DATA\n".getBytes), fakeDecoderOutput)
         written mustEqual List(Request(List("DATA"), None))
       }

      "A single header is accepted as an email body" >> {
        decoder.decode(fakeSession, IoBuffer.wrap("From: foo\nTo: bar\n\nthis is an email\r\n.\r\n".getBytes), fakeDecoderOutput)
        written(0) match {
          case Request(commands, Some(data)) => {
            commands mustEqual List("DATABODY")
            new String(data) mustEqual "From: foo\nTo: bar\n\nthis is an email\r\n.\r\n"
          }
          case _ => fail
        }
      }
    }

    "HELP responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("HELP\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("HELP"), None))
    }

    "VRFY responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("VRFY <stevej@pobox.com>\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("VRFY", "<stevej@pobox.com>"), None))
    }

    "NOOP" >> {
      "NOOP doesn't abide with your extra parameters" >> {
        decoder.decode(fakeSession, IoBuffer.wrap("NOOP fools\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
      }

      "NOOP responds with 250" >> {
        decoder.decode(fakeSession, IoBuffer.wrap("NOOP\n".getBytes), fakeDecoderOutput)
        written mustEqual List(Request(List("NOOP"), None))
      }
    }

    "QUIT responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("QUIT\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("QUIT"), None))
    }

    "RSET responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("RSET\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("RSET"), None))
    }

    "HELP responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("HELP\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("HELP"), None))
    }

    "STATS requires passkey" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("STATS\n".getBytes), fakeDecoderOutput) must throwA[ProtocolError]
    }

    "STATS passkey responds" >> {
      decoder.decode(fakeSession, IoBuffer.wrap("STATS foo\n".getBytes), fakeDecoderOutput)
      written mustEqual List(Request(List("STATS", "foo"), None))
    }

  }
}
