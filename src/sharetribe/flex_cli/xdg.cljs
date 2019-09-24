(ns sharetribe.flex-cli.xdg
  "Namespace for implementing implementing cross-platform support for
  XDG-spec.

  Implementation heavily inspired by:
  https://github.com/oclif/config/blob/61371ac333599096696b3b651f7e55e7fac8d170/src/config.ts"
  (:require [goog.object]))

(def ^:const dirname "flex-cli")

(def platform (.-platform js/process))
(def windows? (= "win32" platform))

(defn- env [env-var-name]
  (goog.object/get js/process.env env-var-name))

(defn- windows-homedrive-home []
  (let [homedrive (env "HOMEDRIVE")
        homepath (env "HOMEPATH")]
    (when (and homedrive homepath)
      (io-util/join homedrive homepath))))

(defn- windows-userprofile-home []
  (env "USERPROFILE"))

(defn- windows-home []
  (or (windows-homedrive-home)
      (windows-userprofile-home)))

(def home (or (env "HOME")
              (when windows? (windows-home))
              (.homedir js/os)
              (.tmpdir js/os)))


(defn- xdg-home-fallback [category]
  (let [dir (case category
              :config ".config"
              :data ".local/share"
              :cache ".cache")]
    (io-util/join home dir)))

(defn- xdg-home [category]
  (case category
    :config (env "XDG_CONFIG_HOME")
    :data (env "XDG_DATA_HOME")
    :cache (env "XDG_CACHE_HOME")))

(defn- dir
  "Path to a user-specific data/config directory.

  Category can be `:config`, `:data` or `:cache`. Follows the XDG
  spec.

  Implementation heavily inspired by:
  https://github.com/oclif/config/blob/61371ac333599096696b3b651f7e55e7fac8d170/src/config.ts#L388"
  [category]
  (let [base (or (xdg-home category)
                 (when windows? (env "LOCALAPPDATA"))
                 (xdg-home-fallback category))]
    (io-util/join base dirname)))

;; Public API
;;

(defn config-dir []
  (dir :config))

(defn data-dir []
  (dir :data))

(defn cache-dir []
  (dir :cache))
