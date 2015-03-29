(ns boot-clojurescript.test.tasks
  (:require [boot.core :as core]
            [boot.file :as file]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [boot.util :as util]))

(defn assert-cljs [path]
  (if (re-matches #".+\.cljs.edn$" path)
    (.replaceAll path "\\.cljs.edn$" ".cljs")
    path))

(defn- test-paths[]
  (core/get-env :test-paths))

(core/deftask gen-test-edn
  "Tasks generates test.cljs.edn which requires all other source namespaces pointed
   by other.edn files. This task is a bootstrap for testing facility by
   providing edn which in turn helps to create loader script for test scripts."
  [ns namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
   (core/with-pre-wrap fileset
     (let [test-edn-dir (core/temp-dir!)
           test-files   (core/temp-dir!)]
       (core/empty-dir! test-edn-dir)
       (core/empty-dir! test-files)
       (let [{:keys [main cljs]} (deps/scan-fileset fileset)
             test-edn-file (doto (io/file test-edn-dir "test.cljs.edn") (io/make-parents))]
           (->> (if (seq main) (concat main namespaces) (concat cljs namespaces))
                (mapv #(comp symbol util/path->ns assert-cljs core/tmppath));;is util/path->ns worsk to edn ?
                (assoc {} :require)
                (spit test-edn-file))
           (-> fileset
               (core/add-source test-edn-dir)
                core/commit!))))
)

(core/deftask scaffold[ns namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
;;Read file from resource .
;;replace script tag with ${LOADER_SCRIPT} with actual  loader script name
;;replace code ${EVAL_TESTS_BLOCK} with dynamic call to cemerick's clojurescript.test with namespaces.
)

(defn detach-compiled-files [fileset func]
   fileset
    )

(core/deftask fileset-add-tests []
 (let [test-dir (core/temp-dir!)]   ;; create temp-dir for test sources
     (core/with-pre-wrap fileset   ;; first append test namespaces to file set.
            (core/empty-dir! test-dir) ;; ensure it is empty
            (map #(file/copy-files % test-dir) (test-paths)) ;; just file system copy
            (core/add-source fileset test-dir) ;; add test source directory to fileset
            (core/commit! fileset)                     ;; commit to fileset
            )))

(defn absolute-tmp-path [file]
  (->> file
       ((juxt core/tmpdir core/tmppath))
       (clojure.string/join "/")))

(core/deftask test-runner
  [sv slimer-version VERSION str "A version of slimer.js"]
  ;; find slimerjs.sh
  ;; find runner.js
  ;; find test.js - get path
  ;; sh slimerjs.sh runnerj.js test.js
  (core/with-pre-wrap fileset
    (let [inputs (core/input-files fileset)
          test-engine-dir (->> inputs
                               (core/by-name ["slimerjs"])
                               first
                               absolute-tmp-path)
          test-runner-dir (->> inputs
                               (core/by-name ["runner.js"])
                               first
                               absolute-tmp-path)
          test-sources ["to" "be" "done"]]

      (println (str "test engine:" test-engine-dir))
      (println (str "test runner:" test-runner-dir))
      (let [result (sh test-engine-dir
                       test-runner-dir
                       (apply str test-sources))]
        (println (:out result))
        )
      )))



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
     (fileset-add-tests) ;; add test sources to fileset
     (gen-test-edn) ;; generate edn files and add to file set
     (cljs)  ;; clojure script compile with test namespaces appended.
     (test-runner) ;; run tests
     )
)
