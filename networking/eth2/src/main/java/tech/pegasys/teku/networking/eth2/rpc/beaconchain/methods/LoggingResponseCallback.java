package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespMethodLogger;

public record LoggingResponseCallback<T>(
    ResponseCallback<T> callback, ReqRespMethodLogger.ResponseLogger<T> logger)
    implements ResponseCallback<T> {

  @Override
  public SafeFuture<Void> respond(T data) {
    logger.onNextItem(data);
    return callback.respond(data);
  }

  @Override
  public void respondAndCompleteSuccessfully(T data) {
    logger.onNextItem(data);
    logger.onComplete();
    callback.respondAndCompleteSuccessfully(data);
  }

  @Override
  public void completeSuccessfully() {
    logger.onComplete();
    callback.completeSuccessfully();
  }

  @Override
  public void completeWithErrorResponse(RpcException error) {
    logger.onError(error);
    callback.completeWithErrorResponse(error);
  }

  @Override
  public void completeWithUnexpectedError(Throwable error) {
    logger.onError(error);
    callback.completeWithUnexpectedError(error);
  }
}
