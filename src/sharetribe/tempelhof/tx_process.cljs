(ns sharetribe.tempelhof.tx-process
  (:require [clojure.spec.alpha :as s]
            ;; [cljs.reader :refer [read-string]]
            [edamame.core :as edamame]
            [sharetribe.tempelhof.process-validation :as process-validation]))

(def ^:const initializer-action {:name :action.initializer/init-listing-tx})
(def ^:const initial-state :state/initial)

(defn- expanded-transition [transition]
  (let [{:keys [from actor actions]} transition]
    (let [transition* (cond-> transition
                          (not from) (assoc :from :state/initial)
                          (not actor) (assoc :actor :actor.role/system))
          actions* (if (= (:from transition*) initial-state)
                     (cons initializer-action actions)
                     actions)]
      (assoc transition* :actions actions*))))

(defn parse-tx-process-string
  "Parse a tx process from an edn string."
  [edn-string]
  ;; TODO: split parsing and validation to separate functions
  (-> (edamame/parse-string edn-string)
      (process-validation/validate!)
      (update :transition #(map expanded-transition %))))

(defn transitions
  "A seq of all transitions of the process."
  [tx-process]
  (map expanded-transition
       (:transitions tx-process)))

(defn transition
  "A specific transition in the process."
  [tx-process tr-name]
  (->> (transitions tx-process)
       (filter #(= tr-name (:name %)))
       first))

(defn notifications
  "A seq of all notifications of the process."
  [tx-process]
  (:notifications tx-process))

(defn notifications-after-transition
  "A seq of all notifications queued after given transition."
  [tx-process tr-name]
  (->> (notifications tx-process)
       (filter #(= tr-name (:on %)))))

(defn states
  "A seq of all states as state maps with :name + :in and :out that
  specify the sequence of transition names that lead into the state
  and out of it."
  [tx-process]
  (vals
   (reduce
    (fn [state-map {:keys [name from to]}]
      (let [update* (fnil update {:name name :in [] :out []})]
        (-> state-map
            (update from
                    (fnil update {:name from :in [] :out []})
                    :out conj name)
            (update to
                    (fnil update {:name to :in [] :out []})
                    :in conj name))))
    {}
    (transitions tx-process))))
