package com.google.gwt.sample.hello.client;

import com.google.gwt.user.client.Window;

interface ExpectedSAM extends SAM {
  default void defender() {
    Window.alert("Defender");
  }
}