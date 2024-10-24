(ns overtone.version)

(def OVERTONE-VERSION {:major 0, :minor 15, :patch 3295, :snapshot false})

(def OVERTONE-VERSION-STR
  (let [version OVERTONE-VERSION]
    (str "v"
         (:major version)
         "."
         (:minor version)
         (if-not (= 0 (:patch version)) (str "." (:patch version)) "")
         (if (:snapshot version) "-dev" ""))))
