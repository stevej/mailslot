/** Copyright 2009 Steve Jenson under the Apache 2.0 License */
package com.saladwithsteve.mailslot

import com.twitter.commons.Stats.Counter

object MailStats {
  val bytesWritten = new Counter
  val totalSessions = new Counter
  val closedSessions = new Counter
  val sessionErrors = new Counter
}
