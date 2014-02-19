This is an **experimental** fork of the [Gwt project](gwtproject.org), hosted at [gwt.googlesource.com](https://gwt.googlesource.com/gwt/).  
Although we will do our best to maintain the bleeding edge features presented,  
we cannot guarantee support, as APIs or underlying dependencies may change.


Features on board:  
* Basic support for java 8 language features, picked up from [Ray Cromwell](plus.google.com/+RayCromwell)'s [github fork](https://github.com/cromwellian/gwt-sandbox/tree/java8).  
* @JsInterface and other JsInterop demoed at Gwt.create from an [experimental commit](https://gwt-review.googlesource.com/#/c/5567/)  
* [Magic method injection](https://github.com/WeTheInternet/xapi/tree/master/gwt/gwt-method-inject) from [xapi](https://github.com/WeTheInternet/xapi)

Works in progress:  
* [Reflection](https://github.com/WeTheInternet/xapi/tree/master/gwt/gwt-reflect) from [xapi](https://github.com/WeTheInternet/xapi) (just needs to be cherry-picked)
* [Enhanced Gwt.create](https://github.com/andrestesti/gwt-rebindingmethods) (allows sending parameters to GWT.create)

Request a feature:  
[Comment on G+](https://plus.google.com/+JamesNelsonX/posts/ZJo2r7wHu8e)

Report a bug:  
[Email developer](mailto:james@wetheinter.net)

There is a [precompiled zip](https://github.com/WeTheInternet/gwt-sandbox/releases/tag/java8) with java 8 support;  
this zip is rebased directly on top of the Gwt 2.6 release.

The next release will contain @JsInterface, magic method injection and reflection;  
we will post the link when it is ready for your perusal.


