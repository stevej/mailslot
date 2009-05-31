/** Copyright 2009 Steve Jenson, under the Apache 2.0 License */
package com.saladwithsteve.mailslot

import java.io.ByteArrayInputStream
import javax.mail.internet.MimeBodyPart

object EmailBuilder {
  def apply(buf: Array[Byte]): MimeBodyPart = {
    val inputStream = new ByteArrayInputStream(buf)
    new MimeBodyPart(inputStream)
  }
}

/**
 * A MailRouter takes all of the To: and Cc: headers and routes the email to correct destination.
 *
 */
trait MailRouter {
  def apply(email: MimeBodyPart)
}

/**
 * Simply prints the email to System.out
 */
class NoOpMailRouter(routeMap: Map[String, (MimeBodyPart) => Unit]) extends MailRouter {
  def apply(email: MimeBodyPart) {
    import javax.mail.Header

    val headers = email.getAllHeaders()
    while (headers.hasMoreElements()) {
      val header = headers.nextElement().asInstanceOf[Header]
      //println("%s: %s".format(header.getName, header.getValue))
      1 + 1
    }
    //email.writeTo(System.out)
  }
}
