(ns sharetribe.flex-cli.io-util
  "I/O utility functions to facilitate common output printing style."
  (:refer-clojure :exclude [load-file])
  (:require [cljs-node-io.core :as io]
            [cljs-node-io.fs :as fs]
            [cljs.reader :refer [read-string]]
            [cljs.pprint :refer [pprint]]
            [fipp.engine :as fipp]
            [clojure.string :as str]
            [clojure.core.async :as async :refer [go <! chan put! take!]]
            #_[cljs-time.format :refer [formatter unparse]]
            #_[cljs-time.coerce :refer [to-date-time]]
            [chalk]
            [inquirer]
            #_[sharetribe.util.money :as util.money]
            [sharetribe.flex-cli.exception :as exception]
            ["mkdirp" :rename {sync mkdirp-sync}]))

(def ^:const process-filename "process.edn")

(defmethod exception/format-exception :io/file-not-found [_ _ {:keys [path]}]
  (str "File not found: " path))

(defmethod exception/format-exception :io/write-failed [_ _ {:keys [path]}]
  (str "Could not write to file: " path))

(defn log
  "Log the given message(s)

  Like `println`, but prints to stderr. Can be used for logging
  messages without messing up non-tty output.

  See:
  https://medium.com/@jdxcode/12-factor-cli-apps-dd3c227a0e46#0bf5"
  [& args]
  (binding [*print-fn* *print-err-fn*]
    (apply println args)))

(defn load-file
  "Load file as str from given file path."
  [path]
  (try
    (io/slurp path)
    (catch js/Error e
      (exception/throw! :io/file-not-found {:path path}))))

(defn save-file
  "Save the content to the given file path."
  [path content]
  (try
    (io/spit path content)
    (catch js/Error e
      (exception/throw! :io/write-failed {:path path}))))

(defn dir?
  "Check if the given path is a directory."
  [path]
  (fs/dir? path))

(defn file?
  "Check if the given path is a file."
  [path]
  (fs/file? path))

(defn mkdirp
  "Create a directory and possible subdirectories for the given path."
  [path]
  (mkdirp-sync path))

(defn join
  "Join the given paths"
  [& parts]
  (apply fs/path.join parts))

(defn process-file-path [path]
  (join path process-filename))

(defn process-dir? [path]
  (file? (process-file-path path)))

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

(defn format-date-and-time
  "Format given inst as a local date time

  Example:

  > (format-date-and-time (js/Date.))
  \"2019-09-06 9:22:47 AM\"
  "
  [inst]
  (when inst
    (let [pad (fn [n]
                (if (< n 10)
                  (str "0" n)
                  (str n)))]
      (str (.getFullYear inst)
           "-"
           (pad (inc (.getMonth inst)))
           "-"
           (pad (.getDate inst))
           " "
           (.toLocaleTimeString inst "en-US")))))

(defn format-code [code]
  (if code
    (.green chalk (with-out-str (pprint code)))
    "-"))

(defn ppd
  "Pretty print Fipp document"
  ([document] (ppd document {}))
  ([document options]
   ;; By default CLJS uses console.log as print-fn. console.log always
   ;; adds new line when it's called. This is not what Fipp
   ;; expects. Thus, we need to bind print-fn to directly print to
   ;; stdout. print-newline needs to be set true so that println adds
   ;; new line. This is by default false, because console.log does it
   ;; already.

   ;; Uncomment for debugging
   ;; (cljs.pprint/pprint document)

   (binding [*print-newline* true
             *print-fn* #(js/process.stdout.write %)]
     (fipp/pprint-document document options))))

(defn ppd-err
  "Pretty print Fipp document.

  Output to stderr"
  ([document] (ppd-err document {}))
  ([document options]
   (binding [*print-newline* true
             *print-fn* #(js/process.stderr.write %)]
     (fipp/pprint-document document options))))

(defn prompt
  "Thin wrapper around inquirer.

  Takes a list of questions and returns core async channel with answers.

  Outputs to stderr

  The documentation for question object can be found here:
  https://github.com/SBoudrias/Inquirer.js/#question
  "
  [questions]
  ;; Create a new prompt module and redirect output to stderr.  stderr
  ;; is used because that's the right place for errors, progress
  ;; e.g. information. We could have in the future a command that
  ;; prompts the user which process version to describe and then
  ;; outputs it to stdout. This output can be piped to file and thus
  ;; we want to keep the prompt output away from stdout.
  (let [prompt (.createPromptModule inquirer (clj->js {:output js/process.stderr}))
        c (chan)]
    (-> (prompt (clj->js questions))
        (.then (fn [answers]
                 (put! c (js->clj answers :keywordize-keys true)))))
    c))

(comment
  (go
    (println "inquirer result:"
     (<! (prompt [{:type :password
                   :name :api-key
                   :message "Copy-paste here your API key from Console"}
                  {:name :list-test
                   :choices ["bike-soil" "bike-soil-testing"]
                   :type :list}]))))
  )
