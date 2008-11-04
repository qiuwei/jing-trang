package com.thaiopensource.validate.nvdl;

import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.validate.auto.SchemaReceiver;
import com.thaiopensource.validate.auto.SchemaReceiverFactory;
import com.thaiopensource.validate.Option;

public class NvdlSchemaReceiverFactory implements SchemaReceiverFactory {
  public SchemaReceiver createSchemaReceiver(String namespaceUri, PropertyMap properties) {
    if (!SchemaImpl.NVDL_URI.equals(namespaceUri))
      return null;
    return new SchemaReceiverImpl(properties);
  }

  public Option getOption(String uri) {
    return null;
  }
}