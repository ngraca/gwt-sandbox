package com.google.gwt.sample.hello.client;

import com.google.gwt.event.dom.client.ClickEvent;

public class HelloInstance {

  private Hello hello;

  public HelloInstance(Hello hello) {
    this.hello = hello;
  }

  public void test(ClickEvent e) {
    hello.test2(e);
  }

}
