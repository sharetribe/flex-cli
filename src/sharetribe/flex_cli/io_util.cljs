(ns sharetribe.flex-cli.io-util
  "I/O utility functions to facilitate common output printing style."
  (:refer-clojure :exclude [load-file])
  (:require [cljs-node-io.core :as io]
            [cljs.reader :refer [read-string]]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            #_[cljs-time.format :refer [formatter unparse]]
            #_[cljs-time.coerce :refer [to-date-time]]
            [chalk]
            #_[sharetribe.util.money :as util.money]
            [sharetribe.flex-cli.exception :as exception]
            ))

(defmethod exception/format-exception :io/file-not-found [_ _ {:keys [path]}]
  (str "File not found: " path))

(defn load-file
  "Load file as str from given file path."
  [path]
  (try
    (io/slurp path)
    (catch js/Error e
      (exception/throw! :io/file-not-found {:path path}))))

(defn kw->title
  "Create a title from a (unqualified) keyword by replacing dashes
  with spaces and capitalizing the first word."
  [k]
  (let [[x & xs] (-> k name (str/split #"-"))]
    (->> (interpose " " (cons (str/capitalize x) xs))
         (apply str))))

(defn namespaced-str
  "Turn a keyword into a string with the namespace part included."
  [kw]
  (when kw
    (if-let [ns (namespace kw)]
      (str ns "/" (name kw))
      (name kw))))

;; TODO Table printing should take max col length and now how to print
;; a column with line breaks while maintaining column alignment.
(defn print-table
  "Prints a seq of rows (maps with same keys) as a text
  table. Optionally takes ks key names to define the columns to print
  in order. If ks is not given, creates ks by `(keys (first
  rows))`. Key names in ks are turned into column headers via the
  kw->title fn."
  ([rows] (print-table (keys (first rows)) rows))
  ([ks rows]
   (when (seq rows)
     (let [widths (map
                   (fn [k]
                     (+ (apply max (count (str k)) (map #(count (str (get % k))) rows))
                        1))
                   ks)
           right-pad (fn [s str-width col-width]
                       (let [pad-len (- col-width str-width)]
                        (apply str s (when (> pad-len 0)
                                       (repeat pad-len " ")))))
           fmt-headers (fn [ks]
                         (apply str
                                (interpose
                                 " "
                                 (for [[col width] (map vector ks widths)]
                                   (let [fmt-col (kw->title col)]
                                     (right-pad (.bold.black chalk fmt-col) (count fmt-col) width))))))
           fmt-row (fn [row]
                     (apply str
                            (interpose
                             " "
                             (for [[col width] (map vector (map #(get row %) ks) widths)]
                               (right-pad (str col) (count (str col)) width)))))]
       (println)
       (println (fmt-headers ks))
       (doseq [row rows]
         (println (fmt-row row)))))))

(defn section-title
  "Format title string as section title."
  [title]
  (.bold.underline chalk title))

(defn definition-list
  "Format a map as a definition list.

   dl is a map of key value pairs. ks is an optional ordered seq of
  keys that when given define which keys are printed, in the given
  order. If ks is not given all key value pairs are printed in `(keys
  dl)` order."
  ([dl] (definition-list (keys dl) dl))
  ([ks dl]
   (apply str
          (for [[k v] (map vector ks (map #(get dl %) ks))]
            (let [title (.bold chalk (kw->title k))
                  value-lines (str/split-lines v)]
              (apply str
                     title "\n"
                     (map #(str "  " % "\n") value-lines)))))))

;; (defn format-money
;;   "Brutally simplistic money formatting."
;;   [m]
;;   (when m
;;     (str (.toString m) " " (.getCurrency m))))

;; (defn format-db-money [db-m]
;;   (when db-m
;;     (format-money (util.money/db->money db-m))))

;; (defn format-date
;;   "Format given inst as yyyy-MM-dd"
;;   [inst]
;;   (when inst
;;     (unparse (formatter "yyyy-MM-dd") (to-date-time inst))))

;; (defn format-date-and-time
;;   "Format given inst as yyyy-MM-dd HH:mm"
;;   [inst]
;;   (when inst
;;     (unparse (formatter "yyyy-MM-dd HH:mm") (to-date-time inst))))

(defn format-code [code]
  (if code
    (.green chalk (with-out-str (pprint code)))
    "-"))
