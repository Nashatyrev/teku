package tech.pegasys.teku.infrastructure.async.stream;

public interface AsyncStreamProcessor<TSource, TTarget> {

  AsyncStreamHandler<TSource> process(AsyncStreamHandler<TTarget> downstreamHandler);
}
