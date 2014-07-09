package com.google.gwt.sample.hello.client;

import com.google.gwt.event.dom.client.ClickEvent;

public class HelloStatic {

  public static void testStatic (ClickEvent e) {
    new Hello().test2(e);
  }

}
