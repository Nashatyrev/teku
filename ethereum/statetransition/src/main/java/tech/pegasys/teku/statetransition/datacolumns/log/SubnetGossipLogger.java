package tech.pegasys.teku.statetransition.datacolumns.log;

public interface SubnetGossipLogger<TMessage> extends GossipLogger<TMessage>  {

  void onDataColumnSubnetSubscribe(int subnetId);

  void onDataColumnSubnetUnsubscribe(int subnetId);
}
