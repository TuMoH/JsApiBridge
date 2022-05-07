"use strict";

function _typeof(obj) { if (typeof Symbol === "function" && typeof Symbol.iterator === "symbol") { _typeof = function _typeof(obj) { return typeof obj; }; } else { _typeof = function _typeof(obj) { return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; }; } return _typeof(obj); }

(function () {
  "use strict";

  var urlForInject = "#_urlForInject_#";
  var currentUrl = window.location.href;
  if (urlForInject != currentUrl) {
    console.log("JsApiBridge_JS: inject canceled, url changed. urlForInject=" + urlForInject + ", currentUrl=" + currentUrl);
    return;
  }
  console.log("JsApiBridge_JS: inject to url=" + currentUrl);

  if (window.#_api_name_#) {
    console.log("JsApiBridge_JS: inject canceled, window.#_api_name_# already found in this page");
    return;
  }

  var promises = {};
  var eventHandlers = {};

  function s4() {
    return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
  }

  function guid() {
    return s4() + s4() + "-" + s4() + "-" + s4() + "-" + s4() + "-" + s4() + s4() + s4();
  }

  window.handleJsApiPromiseResult = function (e) {
    var promise = promises[e.promiseId];

    if (e.status === 'RESOLVE') {
      promise.resolve(e.data);
    } else {
      promise.reject(new Error(e.error));
    }
  };

  window.onJsApiEvent = function (event) {
    console.log("JsApiBridge_JS: onJsApiEvent: " + event);
    if (!eventHandlers[event]) {
      eventHandlers[event] = []
    }
    eventHandlers[event.name].forEach(function (handler) {
      handler(event.data);
    });
  };

  window.#_api_name_# = {
    on: function on(event, handler) {
      if (!eventHandlers[event]) {
        eventHandlers[event] = []
      }
      eventHandlers[event].push(handler);
      console.log("JsApiBridge_JS: added event handler: " + event);
    },
    off: function off(event, handler) {
      if (!eventHandlers[event]) {
        eventHandlers[event] = []
      }
      var index = eventHandlers[event].indexOf(handler);
      if (index >= 0) {
        eventHandlers[event].splice(index, 1);
        console.log("JsApiBridge_JS: removed event handler: " + event);
      } else {
        console.log("JsApiBridge_JS: handler not found for remove");
      }
    }
  };

  var _loop = function _loop(methodName) {
    #_api_name_#[methodName] = function () {
      var args = arguments;
      var promiseId = guid();
      return new Promise(function (resolve, reject) {
        promises[promiseId] = {
          resolve: resolve,
          reject: reject
        };

        try {
          var processedArgs = Array.from(args).map(function (arg) {
            return _typeof(arg) === 'object' ? JSON.stringify(arg) : arg;
          });
          window.NATIVE_JS_API.invokeMethod(
            '#_api_access_token_#',
            promiseId,
            methodName,
            JSON.stringify(processedArgs),
          );
        } catch (e) {
          reject(e);
        }
      });
    };
  };

  var jsApiMethods = #_methods_#;
  for (var methodName in jsApiMethods) {
    _loop(jsApiMethods[methodName]);
  }

  var launchOptions = #_launchOptions_#;

  if (!window.#_api_name_#_LAUNCH_OPTIONS) {
    // If JS code from native injected before client's code
    window.#_api_name_#_LAUNCH_OPTIONS = Promise.resolve(launchOptions);
  } else {
    // JS code from native injected after client's code
    window.#_api_name_#_LAUNCH_OPTIONS.resolve(launchOptions);
  }
  console.log("JsApiBridge_JS: window.#_api_name_# injected!");
})();
