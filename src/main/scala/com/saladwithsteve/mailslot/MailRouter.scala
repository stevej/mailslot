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


class NoOpMailRouter(routeMap: Map[String, (MimeBodyPart) => Unit]) extends MailRouter {
  def apply(email: MimeBodyPart) {
    println("email: %s".format(email))
  }
}
