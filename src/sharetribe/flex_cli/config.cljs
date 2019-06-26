(ns sharetribe.flex-cli.config
  "Very simple homegrown configuration framework.

  Reads configurations in following order:

  1. Environment variables
  2. env file (for development)
  3. Default configs
  "
  (:require [sharetribe.flex-cli.io-util :as io-util]))

(def ^:const env-file ".env.edn")

(def ^:private env-file-content (atom nil))

(defn- read-env-file []
  (try
    (cljs.reader/read-string
     (io-util/load-file env-file))
    (catch js/Error e
      ;; file not found. ignore.
      )))

(defn- env
  "Return the configuration value for `env-var-name`. The look up order is following:

  1. Environment variables
  2. env file
  3. Default value"
  ([env-var-name] (env env-var-name nil))
  ([env-var-name not-found]
   ;; Lazily read and memoize file content
   (when-not @env-file-content
     (reset! env-file-content (read-env-file)))

   ;; 1. Look up from environment vars
   (goog.object.get
    js/process.env
    env-var-name

    ;; 2. If not found, look from env file
    (get
     @env-file-content
     env-var-name

     ;; 3. If not found, use the not-found value
     not-found))))

(defn conf-map
  "Return the whole configuration map"
  []
  {:api-base-url (env "FLEX_API_BASE_URL" "https://flex-console.sharetribe.com/v1/build-api")})

(defn value
  "Return a configuration value for `key`"
  [key]
  (get (conf-map) key))
