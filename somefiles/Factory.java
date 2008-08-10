package com.boostfield.hbj.portal.config.thrift;

import com.boostfield.hbj.portal.thrift.hbjService;
import org.apache.thrift.transport.TTransportException;

public interface Factory {

  hbjService.Client getClient() throws TTransportException;
}
