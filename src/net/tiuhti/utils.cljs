(ns net.tiuhti.utils
  (:require ["string_decoder" :as st]
            ["fs" :as fs]
            [clojure.edn :as edn]
            [cljs.pprint :refer [pprint]]))

(defn stat [dir]
  (let [stat (.statSync fs dir)]
    {:file-name dir
     :directory? (.isDirectory stat)}))

(defn read-dir [dir]
  (let [parent (if (string? dir)
                 (stat dir)
                 dir)]
    (map (fn [dirent]
           {:file-name (.-name dirent)
            :directory? (.isDirectory dirent)
            :path (str (or (:path parent) (:file-name parent)) "/" (.-name dirent))})
         (.readdirSync fs
                       (or (:path parent) (:file-name parent))
                       (clj->js {:withFileTypes true})))))

(def decoder (st/StringDecoder. "utf8"))

(defn buffer->str [buf]
  (.write decoder buf))

(defn write-edn [path content]
  (.writeFileSync fs path (with-out-str (pprint content))))

(defn read-edn [path]
  (edn/read-string (buffer->str (.readFileSync fs path))))
