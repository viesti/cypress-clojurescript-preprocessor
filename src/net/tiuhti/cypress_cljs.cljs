(ns net.tiuhti.cypress-cljs
  (:require ["child_process" :as cp]
            ["fs" :as fs]
            ["string_decoder" :as st]
            ["process" :as process]
            ["path" :as path]
            ["chokidar" :as chokidar]
            ["events" :as EventEmitter]
            [goog.object :as g]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cljs.pprint :refer [pprint]]))

(def working-directory ".preprocessor-cljs")

(def shadow-cljs-bin-path "../node_modules/.bin/shadow-cljs")

(def default-config
  {:dependencies []
   :builds       {}})

(defn stat [dir]
  (let [stat (.statSync fs dir)]
    {:file-name dir
     :directory? (.isDirectory stat)}))

(defn read-dir [dir]
  (let [parent (if (string? dir)
                 (stat dir)
                 dir)]
    (map (fn [dirent]
           {:file-name (g/get dirent "name")
            :directory? (.isDirectory dirent)
            :path (str (or (:path parent) (:file-name parent)) "/" (g/get dirent "name"))})
         (.readdirSync fs
                       (or (:path parent) (:file-name parent))
                       (clj->js {:withFileTypes true})))))

(def decoder (st/StringDecoder. "utf8"))

(defn buffer->str [buf]
  (.write decoder buf))

(defn compile [build-ids]
  (let [process (cp/spawn shadow-cljs-bin-path
                          (clj->js (into ["compile"] build-ids))
                          #js {:cwd working-directory})]
    (-> process
        ^EventEmitter (g/get "stdout")
        (.on "data" (fn [data]
                      (println (str "Compile: " (.trimEnd (buffer->str data)))))))
    process))

(defn namespace-symbol [test-file]
  (-> test-file
      (.replace ".cljs" "")
      (.replace "/" ".")
      symbol))

(defn write-edn [path content]
  (.writeFileSync fs path (with-out-str (pprint content))))

(defn read-edn [path]
  (edn/read-string (buffer->str (.readFileSync fs path))))

(def watchers (atom {}))

(defn make-cljs-preprocessor [config]
  (let [integration-folder      (g/get config "integrationFolder")
        relative-to-integration (fn [path]
                                  (.replace path (str integration-folder "/") ""))
        test-files              (->> (tree-seq :directory? read-dir (stat integration-folder))
                                     (keep :path)
                                     (filter #(.endsWith % ".cljs"))
                                     (map relative-to-integration))
        builds                  (reduce (fn [acc test-file]
                                          (let [paths      (-> test-file
                                                               (.split "/"))
                                                test-name  (-> paths
                                                               last
                                                               (.split ".")
                                                               first)
                                                output-dir (str "out/" test-name)
                                                entry      (namespace-symbol test-file)
                                                build-id   (keyword test-name)]
                                            (assoc acc
                                                   build-id
                                                   {:target           :browser
                                                    :compiler-options {:optimizations :simple}
                                                    :output-dir       output-dir
                                                    :asset-path       (str "/" output-dir)
                                                    :modules          {build-id {:entries [entry]}}})))
                                        {}
                                        test-files)
        config-path             (str working-directory "/" "shadow-cljs.edn")
        config                  (-> default-config
                                    (assoc :source-paths [integration-folder])
                                    (assoc :builds builds))]
    (when-not (.existsSync fs working-directory)
      (.mkdirSync fs working-directory))
    (write-edn config-path config)
    (println "Starting shadow-cljs server")
    (let [shadow-process (cp/spawn shadow-cljs-bin-path #js ["server"] #js {:cwd working-directory})
          ready          (atom false)
          output-fn      (fn [data]
                           (let [s (buffer->str data)]
                             (when (.includes s "server version")
                               (reset! ready true))
                             (println (.trimEnd s))))
          stopping       (atom false)
          stop-fn        (fn []
                           (when @stopping
                             (println "Stop in progress"))
                           (when-not @stopping
                             (reset! stopping true)
                             (println "Stopping shadow-cljs server")
                             (let [output (cp/spawnSync shadow-cljs-bin-path #js ["stop"] #js {:cwd working-directory})]
                               (println "stdout:" (.trimEnd (buffer->str (g/get output "stdout"))))
                               (println "stderr:" (.trimEnd (buffer->str (g/get output "stderr")))))))]
      ;; TODO: Option for compiling all tests at start
      #_(add-watch ready :compile (fn [_]
                                  (compile (map name (keys builds)))))
      (-> ^EventEmitter (g/get shadow-process "stdout")
          (.on "data" output-fn))
      (-> ^EventEmitter (g/get shadow-process "stderr")
          (.on "data" output-fn))
      (.on process "SIGINT" stop-fn)
      (.on process "SIGTERM" stop-fn)
      (fn preprocessor [file]
        (let [filePath (g/get file "filePath")]
          (if-not (.endsWith filePath ".cljs")
            filePath
            (let [test-name     (-> filePath
                                    (.split "/")
                                    last
                                    (.split ".")
                                    first)
                  compiled-file (str/join [(path/resolve working-directory (str "out/" test-name))
                                           "/"
                                           test-name
                                           ".js"])
                  build-id      (keyword test-name)
                  test-file     (relative-to-integration filePath)
                  config        (read-edn config-path)]
              (when-not (contains? (:builds config) build-id)
                (println "Updating shadow-cljs.edn")
                (let [output-dir (str "out/" test-name)
                      config     (update config :builds conj [build-id {:target           :browser
                                                                        :compiler-options {:optimizations :simple}
                                                                        :output-dir       output-dir
                                                                        :asset-path       (str "/" output-dir)
                                                                        :modules          {build-id {:entries [(namespace-symbol test-file)]}}}])]
                  (write-edn config-path config)))
              (when (and (g/get file "shouldWatch")
                         (not (contains? @watchers filePath)))
                (println "Add watcher for" filePath)
                (swap! watchers assoc filePath {:watcher (let [watcher (chokidar/watch filePath)]
                                                           (.on watcher "change" (fn [path]
                                                                                   (println path "changed, recompiling")
                                                                                   (-> (compile [(name build-id)])
                                                                                       (.on "exit" (fn []
                                                                                                     (.emit ^EventEmitter file "rerun"))))))
                                                           watcher)
                                                :compiled-file compiled-file})
                (.on ^EventEmitter file "close" (fn []
                                                  (println "Remove watcher for" filePath)
                                                  (when-let [{:keys [watcher]} (get @watchers filePath)]
                                                    (.close watcher))
                                                  (swap! watchers dissoc filePath)
                                                  true)))
              (js/Promise. (fn [resolve _reject]
                             (println "Compiling" filePath)
                             (let [compile-fn (fn []
                                                (-> (compile [(name build-id)])
                                                    (.on "exit" (fn [_]
                                                                  (println "Compile done!" compiled-file)
                                                                  (resolve compiled-file)))))]
                               (if-not @ready
                                 (add-watch ready :compile (fn [& _args] (compile-fn)))
                                 (compile-fn))))))))))))
