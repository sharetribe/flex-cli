(ns sharetribe.flex-cli.commands.events
  (:require [sharetribe.flex-cli.api.client :as api.client :refer [do-get]]
            [clojure.core.async :as async :refer [go <!]]
            [clojure.set :as set]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.exception :as exception]
            [clojure.string :as str]))

(declare list-events)
(declare start-live-tail)

(def cmd {:name "events"
          :handler #'list-events
          :desc "Get a list of events."
          :opts [{:id :resource
                  :long-opt "--resource"
                  :desc "Show events for a specific resource ID only."
                  :required "RESOURCE_ID"}
                 {:id :related-resource
                  :long-opt "--related-resource"
                  :desc "Show events that are related to a specific resource ID."
                  :required "RELATED_RESOURCE_ID"}
                 {:id :filter
                  :long-opt "--filter"
                  :desc "Show only events of given types, e.g. '--filter listing/updated,user'."
                  :required "EVENT_TYPES"}
                 {:id :seqid
                  :long-opt "--seqid"
                  :desc "Get only the event with the given sequence id."
                  :required "SEQUENCE_ID"}
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
                 {:id :limit
                  :long-opt "--limit"
                  :short-opt "-l"
                  :desc "Show given number of events (default and max is 100). Can be combined with other parameters."
                  :required "NUMBER"}
                 {:id :json
                  :long-opt "--json"
                  :desc "Print full event data as one JSON string."}
                 {:id :json-pretty
                  :long-opt "--json-pretty"
                  :desc "Print full event data as indented multi-line JSON string."}]
          :sub-cmds [{:name "tail"
                      :handler #'start-live-tail
                      :desc "Tail events live as they happen"
                      :opts [{:id :resource
                              :long-opt "--resource"
                              :desc "Show events for a specific resource ID only."
                              :required "RESOURCE_ID"}
                             {:id :related-resource
                              :long-opt "--related-resource"
                              :desc "Show events that are related to a specific resource ID."
                              :required "RELATED_RESOURCE_ID"}
                             {:id :filter
                              :long-opt "--filter"
                              :desc "Show only events of given types, e.g. '--filter listing/updated,user'."
                              :required "EVENT_TYPES"}
                             {:id :limit
                              :long-opt "--limit"
                              :short-opt "-l"
                              :desc "Show given number of latest events and then start tailing (default is 10 and max is 100). Can be combined with other parameters."
                              :required "NUMBER"}
                             {:id :json
                              :long-opt "--json"
                              :desc "Print full event data as one JSON string."}
                             {:id :json-pretty
                              :long-opt "--json-pretty"
                              :desc "Print full event data as indented multi-line JSON string."}]}]})

(def ^:private param->api-param
  {:resource :resource-id
   :related-resource :related-resource-id
   :seqid :sequence-id
   :filter :event-types
   :after-seqid :start-after-sequence-id
   :before-seqid :sequence-id-end
   :after-ts :start-after-created-at
   :before-ts :created-at-end
   :limit :perPage})

(def ^:private api-param->param (set/map-invert param->api-param))

;; Output formatting
;;

(defn terse-event-row
  "Format an event as a map with only :seq-ID, :resource-ID,
  :event-type, :created-at and :actor."
  [event]
  (let [{:event/keys [audit data]} event
        {:keys [sequenceId resourceId eventType source createdAt]} data]
    {:seq-ID sequenceId
     :resource-ID resourceId
     :event-type eventType
     :source (some-> source (str/replace #".*/" ""))
     :created-at-local-time (io-util/format-date-and-time (js/Date. createdAt))
     :actor (or (:user/email audit) (:admin/email audit))}))

(defn json-str-event
  "Format event as a one line JSON string."
  [event]
  (.stringify js/JSON (-> event :event/data clj->js)))

(defn json-pretty-str-event
  "Format event as an indented multi line JSON string."
  [event]
  (.stringify js/JSON (-> event :event/data clj->js) nil 2))


;; Fetching events from Build API
;;

(defn fetch-events
  "Make the API call to fetch events with given parameters mapped to
  Build API endpoint params."
  [marketplace api-client params]
  (let [{:keys [resource related-resource filter seqid after-seqid before-seqid after-ts before-ts limit]} params
        query-params (cond-> {:marketplace marketplace :latest true}
                       (some? seqid) (assoc (param->api-param :seqid) seqid)
                       (some? resource) (assoc (param->api-param :resource) resource)
                       (some? related-resource) (assoc (param->api-param :related-resource) related-resource)
                       (some? filter) (assoc (param->api-param :filter) filter)
                       (some? after-seqid) (assoc (param->api-param :after-seqid) after-seqid)
                       (some? after-seqid) (assoc :latest false)
                       (some? before-seqid) (assoc (param->api-param :before-seqid) before-seqid)
                       (some? after-ts) (assoc (param->api-param :after-ts) after-ts)
                       (some? after-ts) (assoc :latest false)
                       (some? before-ts) (assoc (param->api-param :before-ts) before-ts)
                       (some? limit) (assoc (param->api-param :limit) limit))]
    (do-get api-client "/events/query" query-params)))


;; Input validation
;;

(defn validate-params [params]
  (let [{:keys [seqid after-seqid before-seqid after-ts before-ts resource related-resource]} params]
    (cond-> []
      (> (->> [seqid after-seqid before-seqid after-ts before-ts]
              (filter some?)
              count)
         1)
      (conj (io-util/format-error "Only one of seqid, after-seqid, before-seqid, after-ts or before-ts can be specified."))

      (> (->> [resource related-resource]
              (filter some?)
              count)
         1)
      (conj (io-util/format-error "Only one of resource or related-resource can be specified.")) )))

(defn validate-params! [params]
  (when-let [errors (seq (validate-params params))]
    (exception/throw! :command/invalid-args
                      {:command :events
                       :errors errors})))


;; Server error formatting
;;

(defn- str-val [val]
  (if (keyword? val) (name val) (str val)))

(defmethod exception/format-exception :events.query/api-call-failed [_ _ data]
  (let [{:keys [response status]} (-> data :res)
        {:keys [title details]} (-> response :errors first)
        {:keys [path val]} (:spec-problem details)]
    (if (and path val (= 400 status))
      (io-util/format-error
       (str "Invalid input value '" (str-val val)
            "' for param " (name (api-param->param (first path))) "."))
      (api.client/default-error-format data))))

;; Polling loop for live tailing
;;

(defonce polling-loop (atom nil))

(defn- stop-polling-loop! []
  (go
    (when-let [stop-loop-ch @polling-loop]
      (async/close! stop-loop-ch))))

(defn- start-polling-loop! [marketplace api-client params]
  (let [stop-loop-ch (async/chan)
        {:keys [json json-pretty]} params
        start-params (if (contains? params :limit)
                       params
                       (assoc params :limit 10))]

    (go
      (<! (stop-polling-loop!))
      (reset! polling-loop stop-loop-ch)

      (loop [next-params start-params
             widths nil
             wait-time 5000]
        (let [res (try (<? (fetch-events marketplace api-client next-params))
                       (catch js/Error e
                         (throw
                          (api.client/retype-ex e :events.query/api-call-failed))))
              last-seqid (or (-> res :data last :event/data :sequenceId)
                             (:after-seqid next-params))
              new-widths (cond
                           json (doseq [event-str (map json-str-event (:data res))]
                                  (println event-str))
                           json-pretty (doseq [event-str (map json-pretty-str-event (:data res))]
                                         (println event-str))
                           :else (if-not widths
                                   (io-util/print-table
                                    [:seq-ID :resource-ID :event-type :created-at-local-time :source :actor]
                                    (->> (:data res) (map terse-event-row)))
                                   (io-util/print-table-continuation
                                    [:seq-ID :resource-ID :event-type :created-at-local-time :source :actor]
                                    widths
                                    (->> (:data res) (map terse-event-row)))))
              new-wait-time (or (-> res :meta :liveTailPollInterval)
                                wait-time)
              full-response? (>= (-> res :data count)
                                 (-> res :meta :perPage))
              [_ ch] (async/alts! (if full-response?
                                    [stop-loop-ch (async/timeout 250)]
                                    [stop-loop-ch (async/timeout wait-time)]))]
          (when-not (= ch stop-loop-ch)
            (recur (-> next-params
                       (assoc :after-seqid last-seqid)
                       (dissoc :limit))
                   new-widths
                   new-wait-time)))))))

;; Command handlers
;;

(defn list-events [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        {:keys [json json-pretty]} params]
    (validate-params! params)
    (go-try
     (let [res (try (<? (fetch-events marketplace api-client params))
                    (catch js/Error e
                      (throw
                       (api.client/retype-ex e :events.query/api-call-failed))))]
       (cond
         json (doseq [event-str (map json-str-event (:data res))]
                (println event-str))
         json-pretty (doseq [event-str (map json-pretty-str-event (:data res))]
                       (println event-str))
         :else (io-util/print-table
                [:seq-ID :resource-ID :event-type :created-at-local-time :source :actor]
                (->> (:data res) (map terse-event-row))))))))


(defn start-live-tail [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        done-chan (async/chan)
        {:keys [json json-pretty]} params
        ;; Only print user friendly messages when output is not --json
        ;; or --json-pretty so that we don't mess up piping json to
        ;; parser.
        table-output? (not (or json json-pretty))]

    ;; Setup Ctrl+C handler to terminate process
    (.on js/process "SIGINT" (fn [_]
                               (go
                                 (when table-output?
                                   (println "Exiting..."))
                                 (<! (stop-polling-loop!))
                                 (async/close! done-chan))))
    (go
      (when table-output?
        (println "Starting live tail of events. Type <Ctrl>+C to quit."))
      (<! (start-polling-loop! marketplace api-client params))
      (<! done-chan))))


(comment

  (sharetribe.flex-cli.core/main-dev-str "help events tail")
  (sharetribe.flex-cli.core/main-dev-str "events tail -m drivelah -l 5")
  (sharetribe.flex-cli.core/main-dev-str "events tail -m drivelah -l 10")
  (stop-polling-loop!)

  (sharetribe.flex-cli.core/main-dev-str "help events")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --limit 3")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil -l 5")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --json")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --json-pretty")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --seqid 9 --after-seqid 7")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-seqid 7")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --before-seqid foo")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --filter user/updated,listing")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource foo5fc4")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 7b98dd96-74c7-4ddc-9f46-38c0f91c4a19")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --before-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-ts 2020-11-27T22:00.000Z")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --resource 5fc4a915-73b5-4467-809b-7094d3217c6b --after-seqid 6")
  (sharetribe.flex-cli.core/main-dev-str "events -m bike-soil --after-ts 2020-11-10T22:00.000Z --filter listing/updated")
  )
