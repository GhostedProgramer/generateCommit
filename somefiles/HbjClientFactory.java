package com.boostfield.hbj.portal.config.thrift;

import com.boostfield.hbj.portal.thrift.hbjService;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransportException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HbjClientFactory implements Factory {

  @Autowired
  private TProtocolFactory tProtocolFactory;
  private static String HOST;
  private static int PORT;

  public HbjClientFactory(@Value("${hbj.server.host}") String host,
                          @Value("${hbj.server.port}") int port) {
    HOST = host;
    PORT = port;
  }

  @Override
  public hbjService.Client getClient() throws TTransportException {
    return new hbjService.Client(tProtocolFactory.getProtocol(new THttpClient("http://" + HOST + ":" + PORT + "/hbj")));
  }
}
