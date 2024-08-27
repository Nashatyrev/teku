package tech.pegasys.teku.statetransition.datacolumns.util.rx;

public interface AsyncIteratorCallback<T> {

  boolean onNext(T t);

  void onComplete();

  void onError(Throwable t);
}
