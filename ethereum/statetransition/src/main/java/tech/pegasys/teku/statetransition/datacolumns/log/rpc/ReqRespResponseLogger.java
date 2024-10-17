package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamVisitor;

public interface ReqRespResponseLogger<TResponse> {

  void onNextItem(TResponse s);

  void onComplete();

  void onError(Throwable error);

  default AsyncStreamVisitor<TResponse> asAsyncStreamVisitor() {
    return new AsyncStreamVisitor<>() {
      @Override
      public void onNext(TResponse tResponse) {
        ReqRespResponseLogger.this.onNextItem(tResponse);
      }

      @Override
      public void onComplete() {
        ReqRespResponseLogger.this.onComplete();
      }

      @Override
      public void onError(Throwable t) {
        ReqRespResponseLogger.this.onError(t);
      }
    };
  }
}
