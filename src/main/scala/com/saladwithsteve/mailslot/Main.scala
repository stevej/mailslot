package com.saladwithsteve.mailslot

import net.lag.configgy.{Config, ConfigMap, Configgy, RuntimeEnvironment}
import net.lag.logging.Logger
import net.lag.naggati.IoHandlerActorAdapter
import org.apache.mina.filter.codec.ProtocolCodecFilter
import org.apache.mina.transport.socket.SocketAcceptor
import org.apache.mina.transport.socket.nio.{NioProcessor, NioSocketAcceptor}
import java.net.InetSocketAddress
import java.util.concurrent.{CountDownLatch, Executors, ExecutorService, TimeUnit}
import scala.actors.{Actor, Scheduler}
import scala.actors.Actor._


object MailSlot {
  private val log = Logger.get
  private val deathSwitch = new CountDownLatch(1)

  val runtime = new RuntimeEnvironment(getClass)

  var acceptorExecutor: ExecutorService = null
  var acceptor: SocketAcceptor = null

  def main(args: Array[String]) {
    runtime.load(args)
    startup(Configgy.config)
  }

  def startup(config: Config) {
    val listenAddress = config.getString("listen_host", "0.0.0.0")
    val listenPort = config.getInt("listen_port", 10025)

    acceptorExecutor = Executors.newCachedThreadPool()
    acceptor = new NioSocketAcceptor(acceptorExecutor, new NioProcessor(acceptorExecutor))

    // mina setup cribbed from kestrel.
    acceptor.setBacklog(1000)
    acceptor.setReuseAddress(true)
    acceptor.getSessionConfig.setTcpNoDelay(true)
    acceptor.getFilterChain.addLast("codec", new ProtocolCodecFilter(smtp.Codec.encoder,
      smtp.Codec.decoder))
    acceptor.setHandler(new IoHandlerActorAdapter(session => new SmtpHandler(session, config)))
    acceptor.bind(new InetSocketAddress(listenAddress, listenPort))

    log.info("Listening on port %s", listenPort)

    actor {
      deathSwitch.await
    }

  }
}
