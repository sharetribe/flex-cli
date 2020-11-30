(ns sharetribe.flex-cli.commands.events
  (:require [sharetribe.flex-cli.api.client :refer [do-get]]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [clojure.string :as str]))

(declare list-events)

(def cmd {:name "events"
          :handler #'list-events
          :desc "Get a list of events."
          :opts [{:id :resource
                  :long-opt "--resource"
                  :desc "Show events for a specific resource ID only."
                  :required "RESOURCE_ID"}
                 {:id :filter
                  :long-opt "--filter"
                  :desc "Show only events of given types, e.g. '--filter listing/updated,user'."
                  :required "EVENT_TYPES"}
                 {:id :after-seqid
                  :long-opt "--after-seqid"
                  :desc "Show events with sequence ID larger than (after) the specified."
                  :required "SEQUENCE_ID"}
                 {:id :before-seqid
                  :long-opt "--before-seqid"
                  :desc "Show events with sequence ID smaller than (before) the specified."
                  :required "SEQUENCE_ID"}
                 {:id :after-ts
                  :long-opt "--after-ts"
                  :desc "Show events created after the given timestamp, e.g. '--after-ts 2020-10-10' or '--after-ts 2020-10-10T10:00.000Z'"
                  :required "TIMESTAMP"}
                 {:id :before-ts
                  :long-opt "--before-ts"
                  :desc "Show events created before the given timestamp, e.g. '--before-ts 2020-11-15' or '--before-ts 2020-11-15T12:00.000Z'"
                  :required "TIMESTAMP"}
                 {:id :json
                  :long-opt "--json"
                  :desc "Print full event data as one JSON string."}
                 {:id :json-pretty
                  :long-opt "--json-pretty"
                  :desc "Print full event data as indented multi-line JSON string."} ]})

(defn terse-event-row [event]
  (let [{:event/keys [audit data]} event
        {:keys [sequenceId resourceId eventType source createdAt]} data]
    {:seq-ID sequenceId
     :resource-ID resourceId
     :event-type eventType
     :source (some-> source (str/replace #".*/" ""))
     :created-at (io-util/format-date-and-time (js/Date. createdAt))
     :actor (or (:user/email audit) (:admin/email audit))}))

(defn json-str-event [event]
  (.stringify js/JSON (-> event :event/data clj->js)))

(defn json-pretty-str-event [event]
  (.stringify js/JSON (-> event :event/data clj->js) nil 2))

(defn fetch-events [marketplace api-client params]
  (let [{:keys [resource filter after-seqid before-seqid after-ts before-ts]} params
        query-params (cond-> {:marketplace marketplace}
                       (some? resource) (assoc :resource-id resource)
                       (some? filter) (assoc :event-types filter)
                       (some? after-seqid) (assoc :start-after-sequence-id after-seqid)
                       (some? before-seqid) (assoc :sequence-id-end before-seqid)
                       (some? after-ts) (assoc :start-after-created-at after-ts)
                       (some? before-ts) (assoc :created-at-end before-ts))]
    (do-get api-client "/events/query" query-params)))

(defn list-events [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        {:keys [json json-pretty]} params]
    (go-try
     (let [res (<? (fetch-events marketplace api-client params))]

       ;; -- Debug - Remove me later --
       (def events res)
       ;; -- /Debug - Remove me later -

       (cond
         json (doseq [event-str (map json-str-event (:data res))]
                (print event-str))
         json-pretty (doseq [event-str (map json-pretty-str-event (:data res))]
                       (print event-str))
         :else (io-util/print-table
                [:seq-ID :resource-ID :event-type :created-at :source :actor]
                (->> (:data res) (map terse-event-row))))))))

;; TODO
;;
;; - [ ] Parameter validation
;; - [ ] Get a specific one event with seqid
;; - [ ] Limit parameter
;; - [ ] Tests?

(comment
  (sharetribe.flex-cli.core/main-dev-str "help events")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --json")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-seqid 8")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --before-seqid 5")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --filter user/updated,listing")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 7b98dd96-74c7-4ddc-9f46-38c0f91c4a19")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --before-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-seqid 6")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-seqid 6 --json")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-seqid 6 --json-pretty")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-ts 2020-11-10T22:00.000Z --filter listing/updated --json")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-ts 2020-11-10T22:00.000Z --filter listing/updated --json-pretty")
  )
