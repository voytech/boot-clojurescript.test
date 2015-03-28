(ns boot-clojurescript.test.tasks
  (:requrie [boot.core :as core]
            [boot.file :as file]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [boot.util :refer [sh]]))

(defn assert-cljs [path]
  (if (re-matches #".+\.cljs.edn$" path)
    (.replaceAll path "\\.cljs.edn$" ".cljs")
    path))

(defn- test-paths[]
  (get-env :test-paths))

(core/deftask ^:private gen-tests-edn
  "Tasks generates test.cljs.edn which requires all other source namespaces pointed
   by other.edn files. This task is a bootstrap for testing facility by
   providing edn which in turn helps to create loader script for test scripts."
  [ns namespaces #{sym} "A list of test namespaces to include in tests"]
   (core/with-pre-wrap fileset
     (let [test-edn-dir (core/temp-dir!)
           test-files   (core/temp-dir!)]
       (core/empty-dir! test-edn-dir)
       (core/empty-dir! test-files)
       (let [{:keys [main]} (deps/scan-fileset fileset)
             test-edn-file (doto (io/file test-edn-dir "test.cljs.edn") (io/make-parents))]

         (when (seq main)
           (->> (concat main namespaces)
                (mapv #(comp symbol util/path->ns assert-cljs core/tmppath));;is util/path->ns worsk to edn ?
                (assoc {} :require)
                (spit test-edn-file))
           (-> fileset
               (core/add-source test-edn-dir)
                core/commit!)))))
)

(core/deftask scaffold-runner-page [ns namespaces #{sym} "A list of test namespaces to include in tests"]
;;Read file from resource .
;;replace script tag with ${LOADER_SCRIPT} with actual  loader script name
;;replace code ${EVAL_TESTS_BLOCK} with dynamic call to cemerick's clojurescript.test with namespaces.
)

(defn detach-compiled-files [fileset func]
   fileset
    )

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
  [ns namespaces #{sym} "A list of test namespaces to include in tests"]
  (let [test-dir (core/temp-dir!)]   ;; create temp-dir for test sources
    (comp
     (core/with-pre-wrap fileset   ;; first append test namespaces to file set.
            (core/empty-dir! test-dir) ;; ensure it is empty
            (map #(file/copy-files % test-dir) (test-paths)) ;; just file system copy
            (core/add-source fileset test-dir) ;; add test source directory to fileset
            (core/commit!)                     ;; commit to fileset
            )
     (gen-test-edn) ;; generate edn files
     (cljs))) ;; clojure script compile with test namespaces appended.
)

(core/deftask test-runner-old []
  (let [current-dir (System/getProperty "user.dir"),
        slimer-home (System/getenv "SLIMER_HOME")]
    (let [result (sh (str slimer-home "\\slimerjs.bat")
                     (str current-dir (get-env :cljs-runner))
                     (str current-dir "\\" (get-env :out-path)
                          "\\" (get-env :cljs-out-path)
                          "\\main.js"))]
      (println (:out result))
      )))


(core/deftask test-runner []
  (let [current-dir (System/getProperty "user.dir")
        ]
    (let [result (sh (str current-dir "\\slimerjs.bat")
                     (str current-dir (get-env :cljs-runner))
                     (str current-dir "\\" (get-env :out-path)
                          "\\" (get-env :cljs-out-path)
                          "\\main.js"))]
      (println (:out result))
      )))



(core/deftask test []
  )
