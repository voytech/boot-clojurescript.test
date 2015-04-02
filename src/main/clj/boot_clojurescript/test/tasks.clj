(ns boot-clojurescript.test.tasks
  (:require [boot.core :as core]
            [boot.file :as file]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [clojure.walk :refer :all]
            [clojure.java.shell :as shell]
            [boot.util :as util]))

(def ^:private JS_TEST_FILE "__TEST__") ;;must be unique across all namespaces

(defn assert-cljs [path]
  (if (re-matches #".+\.cljs.edn$" path)
    (.replaceAll path "\\.cljs.edn$" ".cljs")
    path))

(defn cljs-path->ns [path]
  (-> (assert-cljs path)
      (.replaceAll "\\.cljs$" ""))
)

(defn- test-paths[]
  (core/get-env :test-paths))

(defn- test-paths-abs []
  (map #(str (System/getProperty "user.dir") "/" %)  (test-paths))
)

(core/deftask log-fileset[]
  (core/with-pre-wrap fileset
    (println "current environment:")
    (println "----------------------------------------")
    (println (core/get-env))
    (println "----------------------------------------")
    (println "fileset :")
    (println "-----------------------------------------")
    (postwalk #(when (map? %) (when (contains? % :path) (println (str (:dir %) "/" (:path %)))))
              fileset)
     fileset
))


(core/deftask make-edn
  "Tasks generates test.cljs.edn which requires all other source namespaces pointed
   by other.edn files. This task is a bootstrap for testing facility by
   providing edn which in turn helps to create loader script for test scripts."
  [n namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
  (let [test-edn-dir (core/temp-dir!)]
    (core/with-pre-wrap fileset
      (util/info "Creating EDN - test.cljs.edn - main test namespace file...\n")
      (core/empty-dir! test-edn-dir)
      (let [{:keys [main cljs]} (deps/scan-fileset fileset)
            test-edn-file (doto (io/file test-edn-dir (str JS_TEST_FILE ".cljs.edn")) (io/make-parents))]
        (->> (if (seq main) main cljs)
             (mapv #((comp symbol util/path->ns cljs-path->ns core/tmppath) %))
             (concat namespaces) ;;include ns for compilation if not included already
             (set)               ;;distinct elements required.
             (apply vector)      ;;I think .edn expects namespaces to be defined as vector.
             (assoc {} :require)
             (spit test-edn-file))
        (-> fileset
            (core/add-resource test-edn-dir) ;;I do not know what to choose add-source or add-resource
            core/commit!))))
  )

(core/deftask add-tests "Adds test sources to file set"
  []
 (let [test-dir (core/temp-dir!)]      ;; create temp-dir for test sources
     (core/with-pre-wrap fileset       ;; first append test namespaces to file set.
            (util/info "Adding test sources from :test-paths ...\n")
            (core/empty-dir! test-dir) ;; ensure it is empty
            (doseq [path (test-paths)]
              (file/copy-files path test-dir)) ;; just file system copy
            (-> fileset
                (core/add-source test-dir) ;; add test source directory to fileset
                core/commit!)                     ;; commit to fileset
            )))

(defn absolute-tmp-path [file]
  (->> file
       ((juxt core/tmpdir core/tmppath))
       (clojure.string/join "/")))

(defn- file-tmp-path [inputs-fileset name]
  (->> inputs-fileset
       (core/by-name [name])
       first
       absolute-tmp-path))

(defn- make-executable [app]
  (shell/sh "chmod" "777" app)
)

(defn setup-slimer []
  (shell/sh "export SLIMERJSLAUNCHER=/usr/bin/firefox")
)

(core/deftask launch-tests
  [s slimer-version VERSION str   "A version of slimer.js"  ;;I doubt I really need that!
   f firefox-path FIREFOXPATH str "A file system path to firefox binaries."
   n namespaces NAMESPACES #{sym} "A set of test namespaces"]
  (core/with-pre-wrap fileset
    (let [inputs (core/input-files fileset)
          test-engine     (file-tmp-path inputs "slimerjs")   ;;hard-coded name! subject to improve!
          test-runner     (file-tmp-path inputs "runner.js")  ;;hard-coded name! subject to improve!
          test-sources    (file-tmp-path inputs (str JS_TEST_FILE ".js"))] ;;hard-coded name! subject to improve!
      (util/info "Launching tests...\n")
      (util/info (str "test launcher:" test-engine "\n"))
      (util/info (str "test runner:" test-runner "\n"))
      (util/info (str "test sources:" test-sources "\n"))
      (make-executable test-engine)
      ;;(setup-slimer)
      (let [result (shell/sh test-engine
                             test-runner
                             test-sources)]
        (println (:out result))
        )
     )
    fileset))



;;TODO check if there would be possibility for cljs to compile only files
;;which were not yet compiled in current pipeline run.
;; there is core/rm methods which removes files from file set and returns new
;; fileset - this can be used for that purpose. Still we need to check
;; what was compiled.
(core/deftask cljs-tests
  "As long as there is test-paths environment variable test cljs sources and
   as long as test-paths are not included into fileset class-path by default,
   we have to add them to file-set classpath and compile them together with
   other sources. Rationale of introducing test-paths additionally to source-paths
   was to include test sources to class-path only for tests purposes. "
  [ns namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
    (comp
     (add-tests) ;; add test sources to fileset
     (make-edn)      ;; generate edn files and add to file set
     (cljs)              ;; clojurescript compile with test namespaces appended.
     (launch-tests)       ;; run tests on slimerjs runner.
     )
)
