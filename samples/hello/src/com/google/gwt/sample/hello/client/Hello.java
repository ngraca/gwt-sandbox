/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.sample.hello.client;

import java.util.function.Consumer;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * HelloWorld application.
 */
public class Hello implements EntryPoint, ExpectedSAM {

  public void test (ClickEvent e) {
    Window.alert("Hi!");
  }

  public void test2 (ClickEvent e) {
    Consumer<ClickEvent> handle = this::test;
    handle.accept(e);
  }

  public void callSAM(SAM sam) {
    sam.test(null);
  }

  public void callDefender(ExpectedSAM sam) {
    sam.defender();
  }

  /**
   * In order to prevent inlining the call to sam.defender() above,
   * we implement ExpectedSAM and override the defender to be sure
   * polymorphic dispatch is required
   */
  @Override
  public void defender() {
    throw new Error("Not allowed here!");
  }

  public void onModuleLoad() {
    Button a = new Button("Method Handle", extractHandle(this));
    Button b = new Button("lambdaToMethod", (ClickHandler)(e) -> test(e));
    Button c = new Button("Instance Handle", new HelloInstance(this)::test);
    Button d = new Button("Static Handle", HelloStatic::testStatic);
    Button f = new Button("Defender", (ClickHandler)(e)-> callDefender((ev)-> {} )) ;
    Button g = new Button("SAM", (ClickHandler)(e)-> callSAM((ev)->test(ev)));

    RootPanel.get().add(a);
    RootPanel.get().add(b);
    RootPanel.get().add(c);
    RootPanel.get().add(d);
    RootPanel.get().add(f);
    RootPanel.get().add(g);
  }

  private ClickHandler extractHandle(SAM hello) {
    return this::test;
  }
}
