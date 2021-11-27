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
            [os]
            #_[sharetribe.util.money :as util.money]
            [sharetribe.flex-cli.exception :as exception]
            ["mkdirp" :rename {sync mkdirp-sync}]
            ["rimraf" :rename {sync rmrf-sync}]))

(def process-filename "process.edn")
(def templates-dir "templates")
(def template-subject-suffix "-subject.txt")
(def template-html-suffix "-html.html")

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

(defn rmrf
  "Remove a directory and possible subdirectories for the given
  path. Same as rm -rf."
  [path]
  (rmrf-sync path))

(defn join
  "Join the given paths"
  [& parts]
  (apply fs/path.join (remove nil? parts)))

(defn process-file-path [path]
  (join path process-filename))

(defn process-dir? [path]
  (file? (process-file-path path)))

(defn templates-path [path]
  (join path templates-dir))

(defn template-path [path template-name]
  (join (templates-path path) template-name))

(defn html-file-path [path template-name]
  (join (template-path path template-name) (str template-name template-html-suffix)))

(defn subject-file-path [path template-name]
  (join (template-path path template-name) (str template-name template-subject-suffix)))

(defn read-template [tmpl-path]
  (let [name (-> tmpl-path fs/path.parse .-name)]
    {:name (keyword name)
     :html (load-file (join tmpl-path (str name template-html-suffix)))
     :subject (load-file (join tmpl-path (str name template-subject-suffix)))}))

(defn read-templates [path]
  (let [tmpls-path (templates-path path)]
    (if-not (fs/dir? tmpls-path)
      []
      (->> tmpls-path
           fs/readdir
           (into
            #{}
            (comp
             (map (fn [template-name]
                    {:name (keyword template-name)
                     :html-file (html-file-path path template-name)
                     :subject-file (subject-file-path path template-name)}))
             (filter (fn [{:keys [html-file subject-file]}]
                       (and (file? html-file)
                            (file? subject-file))))
             (map (fn [{:keys [name html-file subject-file]}]
                    {:name name
                     :html (load-file html-file)
                     :subject (load-file subject-file)}))))))))

(defn write-templates [path templates]
  (when (dir? (templates-path path))
    (rmrf (templates-path path)))
  (doseq [tmpl templates]
    (let [{:emailTemplate/keys [name subject html]} tmpl
          name-str (clojure.core/name name)]
      (mkdirp (template-path path name-str))
      (save-file (html-file-path path name-str) html)
      (save-file (subject-file-path path name-str) subject))))

(defn- with-os-eol
  "Takes string `str` with `\n` end-of-line markers and replaces them
  with OS specific end-of-line markers."
  [str]
  (let [eol (.-EOL os)]
    (if-not (= "\n" eol)
      (str/replace str #"\n" eol)
      str)))

(defn write-process-file [path process-data]
  (save-file
   (process-file-path path)
   (with-os-eol process-data)))

(defn write-process [path process]
  (write-process-file path (:process/process process))
  (write-templates path (:process/emailTemplates process)))

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

(defn- construct-table [ks rows]
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
    (str \newline
         (prn-str (fmt-headers ks))
         (str/join
          (doseq [row rows]
            (prn-str (fmt-row row)))))))

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
   (let [widths (map
                 (fn [k]
                   (+ (apply max (count (str k)) (map #(count (str (get % k))) rows))
                      1))
                 ks)]
     (println (construct-table ks rows))
     widths)))

(defn print-table-continuation
  "Print more rows to continue a table that was printed earlier. Takes
  previously calculated column widths as the second argument (return
  value of 'print-table'). Skips printing the header but still needs
  the column information to order columns. Returns widths through."
  [ks widths rows]
  (let [right-pad (fn [s str-width col-width]
                    (let [pad-len (- col-width str-width)]
                      (apply str s (when (> pad-len 0)
                                     (repeat pad-len " ")))))
        fmt-row (fn [row]
                  (apply str
                         (interpose
                          " "
                          (for [[col width] (map vector (map #(get row %) ks) widths)]
                            (right-pad (str col) (count (str col)) width)))))]
    (doseq [row rows]
      (println (fmt-row row)))

    widths))

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

(def error-arrow (.bold.red chalk "\u203A"))

(defn format-error [error-str]
  (str error-arrow " " error-str "\n"))


(comment
  (go
    (println
     "inquirer result:"
     (<! (prompt [{:type :password
                   :name :api-key
                   :message "Copy-paste here your API key from Console"}
                  {:name :list-test
                   :choices ["bike-soil" "bike-soil-testing"]
                   :type :list}]))))
  )
