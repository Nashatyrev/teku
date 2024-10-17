package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

class NoopReqRespMethodLogger<TRequest, TResponse> implements ReqRespMethodLogger<TRequest, TResponse> {

  @Override
  public ReqRespResponseLogger<TResponse> onInboundRequest(LoggingPeerId fromPeer, TRequest request) {
    return noopResponseLogger();
  }

  @Override
  public ReqRespResponseLogger<TResponse> onOutboundRequest(LoggingPeerId toPeer, TRequest request) {
    return noopResponseLogger();
  }

  static <TResponse> ReqRespResponseLogger<TResponse> noopResponseLogger() {
    return new ReqRespResponseLogger<>() {
      @Override
      public void onNextItem(TResponse s) {}

      @Override
      public void onComplete() {}

      @Override
      public void onError(Throwable error) {}
    };
  }

}
