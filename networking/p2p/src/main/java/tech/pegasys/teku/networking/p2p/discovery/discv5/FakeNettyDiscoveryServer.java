/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.network.DatagramToEnvelope;
import org.ethereum.beacon.discovery.network.IncomingMessageSink;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.reactivestreams.Publisher;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

// The same as discovery NettyDiscoveryServerImpl but instead of bind() it makes connect() to a fake
// address
// So effectively this is not a server but rather a client socket
@SuppressWarnings("FutureReturnValueIgnored")
public class FakeNettyDiscoveryServer implements NettyDiscoveryServer {
  private static final Logger LOG = LogManager.getLogger(FakeNettyDiscoveryServer.class);
  private static final int RECREATION_TIMEOUT = 5000;

  private final ReplayProcessor<Envelope> incomingPackets = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> incomingSink = incomingPackets.sink();
  private final InetSocketAddress listenAddress;
  private final int trafficReadLimit; // bytes per sec
  private AtomicBoolean listen = new AtomicBoolean(false);
  private Channel channel;
  private NioEventLoopGroup nioGroup;

  public FakeNettyDiscoveryServer(final InetSocketAddress listenAddress) {
    this.listenAddress = listenAddress;
    this.trafficReadLimit = 250000;
  }

  @Override
  public CompletableFuture<NioDatagramChannel> start() {
    LOG.info("Starting discovery server listening on {}", listenAddress);
    if (!listen.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Attempted to start an already started server listening on " + listenAddress));
    }
    nioGroup = new NioEventLoopGroup(1);
    return startServer(nioGroup);
  }

  private CompletableFuture<NioDatagramChannel> startServer(final NioEventLoopGroup group) {
    final CompletableFuture<NioDatagramChannel> future = new CompletableFuture<>();
    final Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioDatagramChannel.class)
        .handler(
            new ChannelInitializer<NioDatagramChannel>() {
              @Override
              public void initChannel(NioDatagramChannel ch) {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline
                    .addFirst(new LoggingHandler(LogLevel.DEBUG))
                    .addLast(new DatagramToEnvelope())
                    .addLast(new IncomingMessageSink(incomingSink));

                if (trafficReadLimit != 0) {
                  pipeline.addFirst(new ChannelTrafficShapingHandler(0, trafficReadLimit));
                }
              }
            });

    //    final ChannelFuture bindFuture = b.connect("127.0.0.1", 37777);
    final ChannelFuture bindFuture = b.bind(listenAddress);
    bindFuture.addListener(
        result -> {
          if (!result.isSuccess()) {
            future.completeExceptionally(result.cause());
            return;
          }

          this.channel = bindFuture.channel();
          channel
              .closeFuture()
              .addListener(
                  closeFuture -> {
                    if (!listen.get()) {
                      LOG.info("Shutting down discovery server listening on {}", listenAddress);
                      group.shutdownGracefully();
                      return;
                    }
                    LOG.error(
                        String.format(
                            "Discovery server listening on %s has been closed. Trying to restore after %d milliseconds delay",
                            listenAddress, RECREATION_TIMEOUT),
                        closeFuture.cause());
                    Thread.sleep(RECREATION_TIMEOUT);
                    startServer(group);
                  });
          future.complete((NioDatagramChannel) this.channel);
        });
    return future;
  }

  @Override
  public InetSocketAddress getListenAddress() {
    return listenAddress;
  }

  @Override
  public Publisher<Envelope> getIncomingPackets() {
    return incomingPackets;
  }

  @Override
  public void stop() {
    if (listen.compareAndSet(true, false)) {
      LOG.info("Stopping discovery server listening on {}", listenAddress);
      if (channel != null) {
        try {
          channel.close().sync();
        } catch (InterruptedException ex) {
          LOG.error("Failed to stop discovery server listening on " + listenAddress, ex);
        }
        if (nioGroup != null) {
          try {
            nioGroup.shutdownGracefully().sync();
          } catch (InterruptedException ex) {
            LOG.error("Failed to stop NIO group", ex);
          }
        }
      }
    } else {
      LOG.warn("An attempt to stop already stopping/stopped discovery server");
    }
  }
}
