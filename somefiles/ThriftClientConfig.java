package com.boostfield.hbj.portal.config;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThriftClientConfig {
  @Bean
  public TProtocolFactory tProtocolFactory() {
    return new TBinaryProtocol.Factory();
  }
}
