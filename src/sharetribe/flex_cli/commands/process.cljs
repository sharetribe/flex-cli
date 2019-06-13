(ns sharetribe.flex-cli.commands.process
  (:require [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.tempelhof.tx-process :as tx-process]))

(declare describe-process)

(def cmd {:name "process"
          :handler #'describe-process
          :opts [{:id :path
                  :long-opt "--path"
                  :required "[WIP] LOCAL_PROCESS_DIR"}
                 {:id :process-name
                  :long-opt "--process"
                  :required "[WIP] PROCESS_NAME"}
                 {:id :version
                  :long-opt "--version"
                  :required "[WIP] VERSION_NUM"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "[WIP] PROCESS_ALIAS"}
                 {:id :transition-name
                  :long-opt "--transition"
                  :required "[WIP] TRANSITION_NAME"}
                 ;; TODO I don't know what's the plan for getting the
                 ;; marketplace ident into commands yet. Is it
                 ;; declared per command? Who implements the the logic
                 ;; of pulling marketplace ident from state when it's
                 ;; not explicitly given to the command?
                 ]})

(defn- load-tx-process-from-path
  "Load process from given path (directory). Encapsulates the idea of
  understanding process format on disk.

  TODO I don't belong to this namespace in the long term."
  [path]
  (let [path-to-process-file (str path "/process.edn")]
    ;; TODO Handle throwing error if process file not found / not
    ;; readable as .edn or something similar.
    (-> (io-util/load-file path-to-process-file)
        (tx-process/parse-tx-process-string))))

(defn- format-state [state]
  (-> state
      (update :name io-util/namespaced-str)
      (update :in #(apply str (interpose
                               ", "
                               (map io-util/namespaced-str %))))
      (update :out #(apply str (interpose
                               ", "
                               (map io-util/namespaced-str %))))))

(defn print-states [states]
  (println (io-util/section-title "States"))
  (let [ks [:name :in :out]]
    (io-util/print-table ks (map format-state states)))
  (println) (println))

(defn- format-transition [transition]
  (-> transition
      (update :actor io-util/kw->title)
      (update :name io-util/namespaced-str)
      (update :from io-util/namespaced-str)
      (update :to io-util/namespaced-str)
      (update :at io-util/format-code)))

(defn print-transitions [transitions]
  (println (io-util/section-title "Transitions"))
  (let [ks [:name :from :to :actor]]
    (io-util/print-table ks (map format-transition transitions)))
  (println) (println))

(defn- format-notification [notification]
  (-> notification
      (update :to io-util/kw->title)
      (update :name io-util/namespaced-str)
      (update :on io-util/namespaced-str)
      (update :template io-util/namespaced-str)
      (update :at io-util/format-code)))

(defn print-notifications [notifications]
  (println (io-util/section-title "Notifications"))
  (let [ks [:name :on :to :template]]
    (io-util/print-table ks (map format-notification notifications))))

(defn- describe-full-process [tx-process]
  (print-states (tx-process/states tx-process))
  (print-transitions (tx-process/transitions tx-process))
  (print-notifications (tx-process/notifications tx-process)))

(defn- describe-transition [tx-process tr-name]
  (if-let [transition (tx-process/transition tx-process tr-name)]
    (let [formatted-tr (format-transition transition)
          formatted-nts (->> (tx-process/notifications-after-transition
                              tx-process
                              tr-name)
                             (map format-notification))]
      (println
       (io-util/definition-list [:name :from :to :actor :at] formatted-tr))

      (println (io-util/section-title "Actions"))
      (io-util/print-table [:name :config] (:actions formatted-tr))
      (println)

      (println (io-util/section-title "Notifications") "\n")
      (if (seq formatted-nts)
        (println (apply str
                        (interpose
                         "\n--\n\n"
                         (map #(io-util/definition-list
                                 [:name :on :to :template :at] %)
                              formatted-nts))))
        (println "-"))
      (println))

    ;; TODO Extract error return with msg to io-util
    (do (println "No transition for name" tr-name "found.")
        {:exit-status 1})))

(defn describe-process
  "Describe a process or a process transition if --transition is
  given.

  The process is loaded either from disk (when --path is given) or
  from a live backend (with coordinates --process (--version ||
  --alias) and --marketplace)."
  [{:keys [path process-name version alias marketplace transition-name] :as opts}]
  (if (empty? path)
    (do (println "Currently only --path is supported and must be specified.")
        {:exit-status 1})
    (let [tx-process (load-tx-process-from-path path)]
      (if (seq transition-name)
        (describe-transition tx-process (keyword transition-name))
        (describe-full-process tx-process)))))

