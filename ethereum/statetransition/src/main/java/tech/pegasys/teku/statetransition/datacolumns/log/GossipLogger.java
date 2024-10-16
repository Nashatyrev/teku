package tech.pegasys.teku.statetransition.datacolumns.log;

import java.util.Optional;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface GossipLogger<TMessage> {
  
  void onReceive(TMessage message, InternalValidationResult validationResult);

  void onPublish(TMessage message, Optional<Throwable> result);

}
